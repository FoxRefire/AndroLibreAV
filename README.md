# YARA-X Android Sample

This sample app demonstrates how to use the YARA-X library on Android.

## Prerequisites

1. **JDK**: Java 17 or higher with the `jlink` tool included (minimal JRE is insufficient).
   - If you get a `jlink` error: Set `org.gradle.java.home` in `gradle.properties` to the path of a JDK that includes `jlink`.

2. **yara-x (submodule)**: [FoxRefire/yara-x](https://github.com/FoxRefire/yara-x) is included as a Git submodule (with prebuilt `jniLibs`).

   Initialize the submodule after the first clone:

   ```bash
   git submodule update --init --recursive
   ```

3. **Android Studio** or **Android SDK** (API 34)

4. **16KB page size support**: Native libraries are built with 16KB ELF alignment for Android 15+ and 16KB page size devices. When using the submodule's prebuilt `jniLibs`, run `yara-x/build-android.sh` to rebuild them with 16KB support.

## Build and Run

1. Open this directory in Android Studio
2. Connect a device or emulator
3. Click Run to build, install, and launch

Or from the command line:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## How to Use the App

1. **YARA Rules**: Enter YARA rules in the text area at the top
2. **Text to Scan**: Enter the text to scan
3. Click **Scan** to run the scan
4. **Matching Rules**: Matched rule names are listed

## Sample Rules

The following rules are preloaded by default:

```yara
rule example_string {
    strings:
        $hello = "Hello"
        $world = "World"
        $malicious = "malware"
    condition:
        any of them
}
```

Enter "Hello World" and click Scan to match `example_string`.

## Project Structure

- `app/src/main/cpp/` - JNI bridge (wrapper for yara-x C API)
- `app/src/main/java/.../YaraX.kt` - Kotlin wrapper class
- `app/src/main/java/.../MainActivity.kt` - Main UI
- `yara-x/` - Git submodule (FoxRefire/yara-x with prebuilt `jniLibs`)
