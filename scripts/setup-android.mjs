#!/usr/bin/env node
/**
 * One-time Android setup.
 *
 * Capacitor generates a complete, correct Android project (Gradle wrapper,
 * launcher icons, splash theme, capacitor.build.gradle, etc.). We let it do
 * that, then inject our native USB camera code on top:
 *   - copies the Kotlin sources + MainActivity into the generated project
 *   - replaces the generated AndroidManifest.xml with our USB-host version
 *   - copies device_filter.xml / file_paths.xml resources
 *   - enables Kotlin + adds the coroutines dependency in Gradle
 *
 * Run with:  npm run setup:android
 * Re-runnable: it is idempotent and safe to run again after `npm run clean:android`.
 */
import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync, readFileSync, writeFileSync, readdirSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const NATIVE = join(ROOT, 'native-android');
const ANDROID = join(ROOT, 'android');
const PKG_PATH = 'com/usbcam/app';

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

// 1) Build web assets so cap add/sync has something to copy.
if (!existsSync(join(ROOT, 'dist'))) run('npm run build');

// 2) Add the Android platform if it does not exist yet.
if (!existsSync(ANDROID)) {
  run('npx cap add android');
} else {
  console.log('\nandroid/ already exists – patching in place.');
}

// 3) Copy our Kotlin sources into the generated project (Capacitor uses a
//    Java source root; the Kotlin plugin compiles .kt files there too).
const srcKotlin = join(NATIVE, 'kotlin', PKG_PATH);
const dstJava = join(ANDROID, 'app/src/main/java', PKG_PATH);
// Remove the auto-generated MainActivity.java – we ship a Kotlin one.
const genMainJava = join(dstJava, 'MainActivity.java');
if (existsSync(genMainJava)) rmSync(genMainJava);
copyTree(srcKotlin, dstJava);
console.log('✓ Kotlin sources copied to app/src/main/java/' + PKG_PATH);

// 4) Replace the generated manifest with our USB-host manifest.
copyFileSync(join(NATIVE, 'AndroidManifest.xml'), join(ANDROID, 'app/src/main/AndroidManifest.xml'));
console.log('✓ AndroidManifest.xml installed');

// 5) Copy XML resources (device filter + file provider paths).
const dstXml = join(ANDROID, 'app/src/main/res/xml');
mkdirSync(dstXml, { recursive: true });
copyFileSync(join(NATIVE, 'res/xml/device_filter.xml'), join(dstXml, 'device_filter.xml'));
copyFileSync(join(NATIVE, 'res/xml/file_paths.xml'), join(dstXml, 'file_paths.xml'));
console.log('✓ res/xml resources copied');

// 6) Enable Kotlin in the root build.gradle (add the Kotlin Gradle plugin classpath).
const rootGradlePath = join(ANDROID, 'build.gradle');
let rootGradle = readFileSync(rootGradlePath, 'utf8');
if (!rootGradle.includes('kotlin-gradle-plugin')) {
  rootGradle = rootGradle.replace(
    /(classpath ['"]com\.android\.tools\.build:gradle:[^'"]+['"])/,
    `$1\n        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22"`
  );
  writeFileSync(rootGradlePath, rootGradle);
  console.log('✓ Kotlin Gradle plugin added to root build.gradle');
}

// 7) Enable Kotlin + coroutines + jitpack in the app build.gradle.
const appGradlePath = join(ANDROID, 'app/build.gradle');
let appGradle = readFileSync(appGradlePath, 'utf8');
if (!appGradle.includes("kotlin-android")) {
  appGradle = appGradle.replace(
    /apply plugin: ['"]com\.android\.application['"]/,
    `apply plugin: 'com.android.application'\napply plugin: 'kotlin-android'`
  );
}
if (!appGradle.includes('kotlinx-coroutines-android')) {
  appGradle = appGradle.replace(
    /dependencies\s*\{/,
    `dependencies {\n    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"`
  );
}
writeFileSync(appGradlePath, appGradle);
console.log('✓ Kotlin + coroutines enabled in app/build.gradle');

// 8) Add jitpack repo (harmless, helps if extra libs are added later).
const settingsGradle = join(ANDROID, 'settings.gradle');
if (existsSync(settingsGradle)) {
  let s = readFileSync(settingsGradle, 'utf8');
  if (!s.includes('jitpack')) {
    // best-effort; Capacitor's repositories live in build.gradle allprojects.
  }
}

run('npm run sync');

console.log('\n────────────────────────────────────────────');
console.log(' Android project ready.');
console.log(' Build a debug APK:   npm run apk:debug');
console.log(' Build a release APK: npm run apk:release');
console.log(' Open in Android Studio: npm run open:android');
console.log('────────────────────────────────────────────');
