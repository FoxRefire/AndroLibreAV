<p align="center">
  <img width="180" src="https://github.com/FoxRefire/android-antimalware-app/blob/main/app/src/main/res/drawable/icon.png?raw=true">
</p>
<h1 align="center">AndroLibreAV</h1>
<p align="center">Open source anti-malware app for Android using YARA rules</p>

---

## Overview

AndroLibreAV scans installed Android apps and files using [YARA](https://yara.readthedocs.io/) pattern matching to help identify potentially malicious applications. It is fully open source and respects your privacy—all scanning runs locally on your device.

## Features

- **App scan** – Scan all installed apps against YARA rules
- **File scan** – Scan selected files (APK, DEX, etc.) from storage or share sheet
- **Rule management** – Download community rules from yara-forge, or add custom YARA rules
- **Scheduled scans** – Optional periodic scanning and automatic rule updates
- **Multi-language** – English, 日本語, 中文, and more

## Requirements

- Android 5.0 (API 21) or later
- ARM64, ARMv7, x86, or x86_64

## Installation

Download the latest signed APK from [Releases](https://github.com/FoxRefire/android-antimalware-app/releases) and install it on your device.

## Usage

1. **Update rules** – Tap "Update rules" to download yara-forge rules (or use custom rules in Settings)
2. **Start scan** – Tap "Start scan" to scan all installed apps
3. **Scan a file** – Use "Scan file" from the menu or share a file from another app
4. **View results** – Tap any detection for details; uninstall or open app settings as needed

## Build from source

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (or Android SDK + NDK)
- JDK 17 with `jlink` (e.g. Temurin, Adoptium)

### Steps

```bash
# Clone with submodules (yara-x is required for native libs)
git clone --recurse-submodules https://github.com/FoxRefire/android-antimalware-app.git
cd android-antimalware-app

# If your system JDK lacks jlink (e.g. JRE-only OpenJDK), set Java home:
# echo "org.gradle.java.home=/path/to/jdk17" >> ~/.gradle/gradle.properties

# Build debug APK
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Dependencies

| Project | Purpose |
|--------|---------|
| [YARA-X](https://github.com/VirusTotal/yara-x) (VirusTotal) | YARA engine, pattern matching |
| [yara-forge](https://github.com/YARAHQ/yara-forge) (YARAHQ) | Community YARA rule collection |

## License

GNU Affero General Public License v3.0 (AGPL-3.0). See [LICENSE](LICENSE) for details.

## Links

- **App repository**: [FoxRefire/android-antimalware-app](https://github.com/FoxRefire/android-antimalware-app)
- **YARA-X**: [VirusTotal/yara-x](https://github.com/VirusTotal/yara-x)
- **yara-forge**: [YARAHQ/yara-forge](https://github.com/YARAHQ/yara-forge)