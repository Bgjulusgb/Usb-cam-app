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
 *   - adds the native UVC library (libusb/libuvc) for isochronous USB video
 *   - enforces compileSdk=36 / targetSdk=36 / minSdk=23 (Android 16 + API 36)
 *   - bumps the Android Gradle Plugin to a version that supports API 36 (AGP 8.9+)
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

// ─── Version constants (Android 16 / API 36) ──────────────────────────────────
const KOTLIN_VERSION      = '2.1.0';
const COROUTINES_VERSION  = '1.10.1';
const DESUGAR_VERSION     = '2.1.5';
const COMPILE_SDK         = 36;
const TARGET_SDK          = 36;
const MIN_SDK             = 23;             // UVCAndroid needs ≥21; 23 keeps wide reach
// Native UVC backend (libusb + libuvc) – handles isochronous USB video that the
// pure-Java Android USB Host API cannot read. Published on Maven Central.
const UVC_LIB             = 'com.herohan:UVCAndroid:1.0.12';
// Android 16 (API 36) requires AGP ≥ 8.9.0; AGP 8.9.x pairs with Gradle 8.11.1
// (which is what Capacitor 7 already ships).
const AGP_MIN             = '8.9.1';
const GRADLE_MIN          = '8.11.1';

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

/** Compare dotted versions: returns true when `a` < `b`. */
function isLower(a, b) {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const x = pa[i] ?? 0, y = pb[i] ?? 0;
    if (x !== y) return x < y;
  }
  return false;
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

// ─── 6) Enforce SDK versions in variables.gradle ─────────────────────────────
// Capacitor stores compile/target/min SDK in android/variables.gradle (an ext
// block applied by the root build.gradle), so that's where we patch them.
const variablesPath = join(ANDROID, 'variables.gradle');
if (existsSync(variablesPath)) {
  let vars = readFileSync(variablesPath, 'utf8');
  vars = vars
    .replace(/compileSdkVersion\s*=\s*\d+/, `compileSdkVersion = ${COMPILE_SDK}`)
    .replace(/targetSdkVersion\s*=\s*\d+/,  `targetSdkVersion = ${TARGET_SDK}`)
    .replace(/minSdkVersion\s*=\s*\d+/,     `minSdkVersion = ${MIN_SDK}`);
  writeFileSync(variablesPath, vars);
  console.log(`✓ variables.gradle: SDK set to compile/target ${COMPILE_SDK}, min ${MIN_SDK}`);
} else {
  console.warn('! variables.gradle not found – SDK versions will fall back to app/build.gradle patch');
}

// ─── 7) Patch root build.gradle – Kotlin plugin + AGP version ────────────────
const rootGradlePath = join(ANDROID, 'build.gradle');
let rootGradle = readFileSync(rootGradlePath, 'utf8');

if (!rootGradle.includes('kotlin-gradle-plugin')) {
  rootGradle = rootGradle.replace(
    /(classpath ['"]com\.android\.tools\.build:gradle:[^'"]+['"])/,
    `$1\n        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${KOTLIN_VERSION}"`,
  );
}

// Bump the Android Gradle Plugin only if the version Capacitor shipped is below
// the minimum required for API 36 (avoids accidentally downgrading a newer AGP).
rootGradle = rootGradle.replace(
  /classpath ['"]com\.android\.tools\.build:gradle:([^'"]+)['"]/,
  (match, ver) => {
    if (isLower(ver, AGP_MIN)) {
      console.log(`✓ AGP ${ver} < ${AGP_MIN} → bumping to ${AGP_MIN} (needed for API 36)`);
      return `classpath 'com.android.tools.build:gradle:${AGP_MIN}'`;
    }
    console.log(`✓ AGP ${ver} already supports API 36 – left unchanged`);
    return match;
  },
);

// Fallback: enforce SDK versions here too if they happen to be inlined.
rootGradle = rootGradle
  .replace(/compileSdkVersion\s*=\s*\d+/, `compileSdkVersion = ${COMPILE_SDK}`)
  .replace(/targetSdkVersion\s*=\s*\d+/,  `targetSdkVersion = ${TARGET_SDK}`)
  .replace(/minSdkVersion\s*=\s*\d+/,     `minSdkVersion = ${MIN_SDK}`);

writeFileSync(rootGradlePath, rootGradle);
console.log('✓ Root build.gradle: Kotlin plugin + AGP version ensured');

// ─── 8) Patch app/build.gradle – Kotlin + coroutines + desugar + UVC lib ─────
const appGradlePath = join(ANDROID, 'app/build.gradle');
let appGradle = readFileSync(appGradlePath, 'utf8');

if (!appGradle.includes('kotlin-android')) {
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

// Native UVC backend (isochronous USB video via libusb/libuvc).
if (!appGradle.includes('UVCAndroid')) {
  appGradle = appGradle.replace(
    /dependencies\s*\{/,
    `dependencies {\n    implementation "${UVC_LIB}"`,
  );
}

// Enforce compile/target/min SDK in the app module as a fallback for both the
// `compileSdkVersion X` and `compileSdk = X` syntaxes (no-op when they use
// rootProject.ext.* from variables.gradle, which we patched above).
appGradle = appGradle
  .replace(/compileSdkVersion\s+\d+/, `compileSdkVersion ${COMPILE_SDK}`)
  .replace(/compileSdk\s*=?\s*\d+/, `compileSdk ${COMPILE_SDK}`)
  .replace(/targetSdkVersion\s+\d+/, `targetSdkVersion ${TARGET_SDK}`)
  .replace(/minSdkVersion\s+\d+/, `minSdkVersion ${MIN_SDK}`);

// Enable core library desugaring (needed for java.time API on API <26, harmless above).
if (!appGradle.includes('coreLibraryDesugaringEnabled')) {
  appGradle = appGradle.replace(
    /(compileOptions\s*\{[^}]*)(})/s,
    `$1    coreLibraryDesugaringEnabled true\n$2`,
  );
}
if (!appGradle.includes('desugar_jdk_libs')) {
  appGradle = appGradle.replace(
    /dependencies\s*\{/,
    `dependencies {\n    coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:${DESUGAR_VERSION}"`,
  );
}

writeFileSync(appGradlePath, appGradle);
console.log('✓ app/build.gradle: Kotlin + coroutines + desugar + UVC lib added');

// ─── 9) Ensure Gradle wrapper is new enough for the AGP version ───────────────
const wrapperPath = join(ANDROID, 'gradle/wrapper/gradle-wrapper.properties');
if (existsSync(wrapperPath)) {
  let wrapper = readFileSync(wrapperPath, 'utf8');
  wrapper = wrapper.replace(
    /gradle-([\d.]+)-(all|bin)\.zip/,
    (match, ver, kind) => {
      if (isLower(ver, GRADLE_MIN)) {
        console.log(`✓ Gradle ${ver} < ${GRADLE_MIN} → bumping wrapper to ${GRADLE_MIN}`);
        return `gradle-${GRADLE_MIN}-${kind}.zip`;
      }
      return match;
    },
  );
  writeFileSync(wrapperPath, wrapper);
}

// ─── 10) Ensure gradle.properties has Kotlin JVM target ───────────────────────
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
console.log(' Android project ready (API 36 / Android 16, Kotlin 2.1, native UVC)');
console.log(' Debug APK:       npm run apk:debug');
console.log(' Release APK:     npm run apk:release');
console.log(' Release AAB:     npm run bundle:aab');
console.log(' Open in Studio:  npm run open:android');
console.log('────────────────────────────────────────────────────────────');
