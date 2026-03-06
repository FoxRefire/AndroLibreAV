cmake_minimum_required(VERSION 3.22.1)
project("yara_jni")

# Path to yara-x C API header (yara-x is a git submodule)
set(YARA_X_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/../../../../yara-x/capi/include)
# Path to prebuilt libyara_x_capi.so (from FoxRefire/yara-x jniLibs)
set(YARA_X_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../yara-x/jniLibs/${ANDROID_ABI})

include_directories(${YARA_X_INCLUDE_DIR})

add_library(yara_jni SHARED
    yara_jni.c
)

target_link_libraries(yara_jni
    ${YARA_X_LIB_DIR}/libyara_x_capi.so
    log
)

# 16KB page size alignment for Android 15+ (fallback for NDK < r27)
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    target_link_options(yara_jni PRIVATE "LINKER:-z,max-page-size=16384")
endif()
