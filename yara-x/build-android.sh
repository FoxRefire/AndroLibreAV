#!/usr/bin/env bash
# Build yara-x C API library for Android using NDK
#
# Prerequisites:
#   1. Install Android NDK (via Android Studio or standalone)
#   2. Set ANDROID_NDK_HOME (e.g., ~/Android/Sdk/ndk/27.2.12496018)
#   3. rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
#   4. Optional: cargo install cargo-ndk (simplifies the build)
#
# With cargo-ndk (recommended):
#   cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 -o ./jniLibs -p yara-x-capi build --release
#
# Manual build (this script):
#   ./build-android.sh

set -e

if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "Error: ANDROID_NDK_HOME is not set."
    echo "Set it to your NDK path, e.g.: export ANDROID_NDK_HOME=~/Android/Sdk/ndk/27.2.12496018"
    exit 1
fi

# Detect host for NDK toolchain path
case "$(uname -m)" in
    x86_64) HOST_TAG=linux-x86_64 ;;
    aarch64|arm64) HOST_TAG=linux-aarch64 ;;
    *) echo "Unsupported host"; exit 1 ;;
esac

NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin"
if [ ! -d "$NDK_BIN" ]; then
    echo "Error: NDK bin not found at $NDK_BIN"
    exit 1
fi

export PATH="$NDK_BIN:$PATH"

# API level for NDK clang. NDK r29+ dropped API 19, minimum is 21 for all targets.
# Override with ANDROID_NDK_API_32 and ANDROID_NDK_API_64 if needed.
API_64="${ANDROID_NDK_API_64:-21}"
API_32="${ANDROID_NDK_API_32:-21}"

# CC/AR for C build scripts (zstd-sys, etc.). cc-rs looks for CC_<target_with_underscores>
export CC_aarch64_linux_android="${NDK_BIN}/aarch64-linux-android${API_64}-clang"
export AR_aarch64_linux_android="${NDK_BIN}/llvm-ar"
export CC_armv7_linux_androideabi="${NDK_BIN}/armv7a-linux-androideabi${API_64}-clang"
export AR_armv7_linux_androideabi="${NDK_BIN}/llvm-ar"
export CC_i686_linux_android="${NDK_BIN}/i686-linux-android${API_32}-clang"
export AR_i686_linux_android="${NDK_BIN}/llvm-ar"
export CC_x86_64_linux_android="${NDK_BIN}/x86_64-linux-android${API_64}-clang"
export AR_x86_64_linux_android="${NDK_BIN}/llvm-ar"

TARGETS="${ANDROID_TARGETS:-aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android}"
OUTPUT_DIR="${ANDROID_OUTPUT:-./jniLibs}"

mkdir -p "$OUTPUT_DIR"

for target in $TARGETS; do
    case "$target" in
        aarch64-linux-android) abi=arm64-v8a ;;
        armv7-linux-androideabi) abi=armeabi-v7a ;;
        x86_64-linux-android) abi=x86_64 ;;
        i686-linux-android) abi=x86 ;;
        *) echo "Unknown target: $target"; continue ;;
    esac

    echo "Building for $target ($abi)..."
    cargo build -p yara-x-capi --target "$target" --release
    mkdir -p "$OUTPUT_DIR/$abi"
    cp "target/$target/release/libyara_x_capi.so" "$OUTPUT_DIR/$abi/"
done

echo "Done! Libraries are in $OUTPUT_DIR/"
