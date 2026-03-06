#!/usr/bin/env bash
# Source this file to set CC/AR for Android cross-compilation (zstd-sys, etc.)
# Usage: source android-ndk-env.sh
# Then: cargo ndk -t arm64-v8a -t armeabi-v7a -o ./jniLibs -p yara-x-capi build --release

if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "Error: ANDROID_NDK_HOME is not set."
    return 1 2>/dev/null || exit 1
fi

case "$(uname -m)" in
    x86_64) HOST_TAG=linux-x86_64 ;;
    aarch64|arm64) HOST_TAG=linux-aarch64 ;;
    *) echo "Unsupported host"; return 1 2>/dev/null || exit 1 ;;
esac

NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin"
# NDK r29+ dropped API 19, minimum is 21 for all targets
API_64="${ANDROID_NDK_API_64:-21}"
API_32="${ANDROID_NDK_API_32:-21}"

export PATH="$NDK_BIN:$PATH"
export CC_aarch64_linux_android="${NDK_BIN}/aarch64-linux-android${API_64}-clang"
export AR_aarch64_linux_android="${NDK_BIN}/llvm-ar"
export CC_armv7_linux_androideabi="${NDK_BIN}/armv7a-linux-androideabi${API_64}-clang"
export AR_armv7_linux_androideabi="${NDK_BIN}/llvm-ar"
export CC_i686_linux_android="${NDK_BIN}/i686-linux-android${API_32}-clang"
export AR_i686_linux_android="${NDK_BIN}/llvm-ar"
export CC_x86_64_linux_android="${NDK_BIN}/x86_64-linux-android${API_64}-clang"
export AR_x86_64_linux_android="${NDK_BIN}/llvm-ar"

echo "Android NDK env loaded (NDK: $ANDROID_NDK_HOME)"
