#!/usr/bin/env node
/**
 * One-time Android setup (idempotent – safe to run again after clean:android).
 *
 * Capacitor generates a complete Android project (Gradle wrapper, launcher
 * icons, splash theme, capacitor.build.gradle, …). We let it do that and then
 * inject our native USB camera code on top:
 *   - copies Kotlin sources + MainActivity into the generated project
 *   - replaces the generated AndroidManifest.xml with our USB-host version
 *   - copies device_filter.xml / file_paths.xml / network_security_config.xml
 *   - enables Kotlin 2.x and adds coroutines in the Gradle files
 *   - enforces compileSdk=35 / targetSdk=35 / minSdk=23 (Android 15 + API 35)
 *
 * Run with:  npm run setup:android
 */
import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync, readFileSync, writeFileSync, readdirSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT    = join(dirname(fileURLToPath(import.meta.url)), '..');
const NATIVE  = join(ROOT, 'native-android');
const ANDROID = join(ROOT, 'android');
const PKG_PATH = 'com/usbcam/app';

// ─── Version constants ────────────────────────────────────────────────────────
const KOTLIN_VERSION      = '2.0.21';
const COROUTINES_VERSION  = '1.9.0';
const COMPILE_SDK         = 35;
const TARGET_SDK          = 35;
const MIN_SDK             = 23;

function run(cmd) {
  console.log(`\n$ ${cmd}`);
  execSync(cmd, { cwd: ROOT, stdio: 'inherit' });
}

function copyTree(src, dst) {
  mkdirSync(dst, { recursive: true });
  for (const entry of readdirSync(src, { withFileTypes: true })) {
    const s = join(src, entry.name);
    const d = join(dst, entry.name);
    if (entry.isDirectory()) copyTree(s, d);
    else copyFileSync(s, d);
  }
}

function patch(filePath, transforms) {
  let text = readFileSync(filePath, 'utf8');
  for (const [search, replace] of transforms) {
    text = text.replace(search, replace);
  }
  writeFileSync(filePath, text);
}

// ─── 1) Build web assets ──────────────────────────────────────────────────────
if (!existsSync(join(ROOT, 'dist'))) run('npm run build');

// ─── 2) Add Android platform ──────────────────────────────────────────────────
if (!existsSync(ANDROID)) {
  run('npx cap add android');
} else {
  console.log('\nandroid/ already exists – patching in place.');
}

// ─── 3) Copy Kotlin sources ───────────────────────────────────────────────────
const srcKotlin = join(NATIVE, 'kotlin', PKG_PATH);
const dstJava   = join(ANDROID, 'app/src/main/java', PKG_PATH);
const genMainJava = join(dstJava, 'MainActivity.java');
if (existsSync(genMainJava)) rmSync(genMainJava);
copyTree(srcKotlin, dstJava);
console.log('✓ Kotlin sources copied to app/src/main/java/' + PKG_PATH);

// ─── 4) Replace AndroidManifest ───────────────────────────────────────────────
copyFileSync(
  join(NATIVE, 'AndroidManifest.xml'),
  join(ANDROID, 'app/src/main/AndroidManifest.xml'),
);
console.log('✓ AndroidManifest.xml installed');

// ─── 5) Copy XML resources ────────────────────────────────────────────────────
const dstXml = join(ANDROID, 'app/src/main/res/xml');
mkdirSync(dstXml, { recursive: true });
for (const file of ['device_filter.xml', 'file_paths.xml', 'network_security_config.xml']) {
  const src = join(NATIVE, 'res/xml', file);
  if (existsSync(src)) {
    copyFileSync(src, join(dstXml, file));
  }
}
console.log('✓ res/xml resources copied');

// ─── 6) Patch root build.gradle – Kotlin Gradle plugin ───────────────────────
const rootGradlePath = join(ANDROID, 'build.gradle');
let rootGradle = readFileSync(rootGradlePath, 'utf8');

if (!rootGradle.includes('kotlin-gradle-plugin')) {
  rootGradle = rootGradle.replace(
    /(classpath ['"]com\.android\.tools\.build:gradle:[^'"]+['"])/,
    `$1\n        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${KOTLIN_VERSION}"`,
  );
}

// Enforce compileSdkVersion / targetSdkVersion / minSdkVersion in ext block.
// Capacitor 7 already sets these to 35/35/23; the replacements below are
// no-ops if the values are already correct.
rootGradle = rootGradle
  .replace(/compileSdkVersion\s*=\s*\d+/, `compileSdkVersion = ${COMPILE_SDK}`)
  .replace(/targetSdkVersion\s*=\s*\d+/,  `targetSdkVersion = ${TARGET_SDK}`)
  .replace(/minSdkVersion\s*=\s*\d+/,     `minSdkVersion = ${MIN_SDK}`);

writeFileSync(rootGradlePath, rootGradle);
console.log('✓ Root build.gradle: Kotlin plugin added, SDK versions enforced');

// ─── 7) Patch app/build.gradle – Kotlin plugin + coroutines ──────────────────
const appGradlePath = join(ANDROID, 'app/build.gradle');
let appGradle = readFileSync(appGradlePath, 'utf8');

if (!appGradle.includes("kotlin-android")) {
  appGradle = appGradle.replace(
    /apply plugin:\s*['"]com\.android\.application['"]/,
    `apply plugin: 'com.android.application'\napply plugin: 'kotlin-android'`,
  );
}

if (!appGradle.includes('kotlinx-coroutines-android')) {
  appGradle = appGradle.replace(
    /dependencies\s*\{/,
    `dependencies {\n    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:${COROUTINES_VERSION}"`,
  );
}

// Enforce compileSdk / targetSdk in the app module as well (handles both
// the `compileSdkVersion X` and `compileSdk = X` syntaxes).
appGradle = appGradle
  .replace(/compileSdkVersion\s+\d+/, `compileSdkVersion ${COMPILE_SDK}`)
  .replace(/compileSdk\s+rootProject\.ext\.compileSdkVersion/, `compileSdk ${COMPILE_SDK}`)
  .replace(/targetSdkVersion\s+rootProject\.ext\.targetSdkVersion/, `targetSdkVersion ${TARGET_SDK}`)
  .replace(/targetSdkVersion\s+\d+/, `targetSdkVersion ${TARGET_SDK}`)
  .replace(/minSdkVersion\s+rootProject\.ext\.minSdkVersion/, `minSdkVersion ${MIN_SDK}`)
  .replace(/minSdkVersion\s+\d+/, `minSdkVersion ${MIN_SDK}`);

// Enable core library desugaring (needed for java.time API on API <26, harmless above).
if (!appGradle.includes('coreLibraryDesugaringEnabled')) {
  appGradle = appGradle.replace(
    /(compileOptions\s*\{[^}]*)(})/s,
    `$1    coreLibraryDesugaringEnabled true\n$2`,
  );
}
if (!appGradle.includes('desugar_jdk_libs') && !appGradle.includes('coreLibraryDesugaring')) {
  appGradle = appGradle.replace(
    /dependencies\s*\{/,
    `dependencies {\n    coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.1.4"`,
  );
}

writeFileSync(appGradlePath, appGradle);
console.log('✓ app/build.gradle: Kotlin + coroutines + desugar added, SDK versions enforced');

// ─── 8) Ensure gradle.properties has Kotlin JVM target ────────────────────────
const gradlePropsPath = join(ANDROID, 'gradle.properties');
if (existsSync(gradlePropsPath)) {
  let props = readFileSync(gradlePropsPath, 'utf8');
  if (!props.includes('kotlin.jvm.target')) {
    props += '\nkotlin.jvm.target=17\n';
    writeFileSync(gradlePropsPath, props);
    console.log('✓ gradle.properties: Kotlin JVM target set to 17');
  }
}

run('npm run sync');

console.log('\n────────────────────────────────────────────────────────────');
console.log(' Android project ready (API 35 / Android 15, Kotlin 2.0)');
console.log(' Debug APK:       npm run apk:debug');
console.log(' Release APK:     npm run apk:release');
console.log(' Release AAB:     npm run bundle:aab');
console.log(' Open in Studio:  npm run open:android');
console.log('────────────────────────────────────────────────────────────');
