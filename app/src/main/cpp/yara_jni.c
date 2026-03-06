/**
 * JNI bridge for YARA-X C API
 * Wraps yrx_compile, yrx_scanner_create, yrx_scanner_scan for Android
 */

#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "yara_x.h"

#define LOG_TAG "YaraXJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/* User data for matching rule callback - holds JNI env and ArrayList to append to */
typedef struct {
    JNIEnv *env;
    jobject array_list;
    jmethodID add_method;
} callback_data_t;

static void matching_rule_callback(const struct YRX_RULE *rule, void *user_data) {
    callback_data_t *ctx = (callback_data_t *)user_data;
    JNIEnv *env = ctx->env;
    if (!env || !ctx->array_list || !ctx->add_method) return;

    const uint8_t *ident = NULL;
    size_t len = 0;
    if (yrx_rule_identifier(rule, &ident, &len) != YRX_SUCCESS || !ident) return;

    jstring jname = (*env)->NewStringUTF(env, (const char *)ident);
    if (jname) {
        (*env)->CallBooleanMethod(env, ctx->array_list, ctx->add_method, jname);
        (*env)->DeleteLocalRef(env, jname);
    }
}

JNIEXPORT jlong JNICALL
Java_com_example_yaraxsample_YaraX_nativeCompileRules(JNIEnv *env, jclass clazz, jstring rules_src) {
    if (!rules_src) return 0;

    const char *src = (*env)->GetStringUTFChars(env, rules_src, NULL);
    if (!src) return 0;

    struct YRX_RULES *rules = NULL;
    enum YRX_RESULT result = yrx_compile(src, &rules);

    (*env)->ReleaseStringUTFChars(env, rules_src, src);

    if (result != YRX_SUCCESS || !rules) {
        const char *err = yrx_last_error();
        if (err) LOGI("yrx_compile failed: %s", err);
        return 0;
    }
    return (jlong)(uintptr_t)rules;
}

JNIEXPORT jlong JNICALL
Java_com_example_yaraxsample_YaraX_nativeCreateScanner(JNIEnv *env, jclass clazz, jlong rules_handle) {
    if (rules_handle == 0) return 0;

    struct YRX_RULES *rules = (struct YRX_RULES *)(uintptr_t)rules_handle;
    struct YRX_SCANNER *scanner = NULL;

    if (yrx_scanner_create(rules, &scanner) != YRX_SUCCESS || !scanner) {
        return 0;
    }
    return (jlong)(uintptr_t)scanner;
}

JNIEXPORT jobject JNICALL
Java_com_example_yaraxsample_YaraX_nativeScanBytes(JNIEnv *env, jclass clazz,
        jlong scanner_handle, jbyteArray data) {
    if (scanner_handle == 0) return NULL;

    struct YRX_SCANNER *scanner = (struct YRX_SCANNER *)(uintptr_t)scanner_handle;
    const uint8_t *bytes = NULL;
    jsize len = 0;

    if (data) {
        bytes = (const uint8_t *)(*env)->GetByteArrayElements(env, data, NULL);
        len = (*env)->GetArrayLength(env, data);
    }

    /* Create ArrayList for results */
    jclass array_list_class = (*env)->FindClass(env, "java/util/ArrayList");
    if (!array_list_class) {
        if (data && bytes) (*env)->ReleaseByteArrayElements(env, data, (jbyte *)bytes, JNI_ABORT);
        return NULL;
    }
    jmethodID array_list_init = (*env)->GetMethodID(env, array_list_class, "<init>", "()V");
    jmethodID add_method = (*env)->GetMethodID(env, array_list_class, "add", "(Ljava/lang/Object;)Z");
    if (!array_list_init || !add_method) {
        if (data && bytes) (*env)->ReleaseByteArrayElements(env, data, (jbyte *)bytes, JNI_ABORT);
        return NULL;
    }

    jobject result_list = (*env)->NewObject(env, array_list_class, array_list_init);
    if (!result_list) {
        if (data && bytes) (*env)->ReleaseByteArrayElements(env, data, (jbyte *)bytes, JNI_ABORT);
        return NULL;
    }

    callback_data_t ctx = {
        .env = env,
        .array_list = result_list,
        .add_method = add_method
    };

    yrx_scanner_on_matching_rule(scanner, matching_rule_callback, &ctx);

    enum YRX_RESULT result = yrx_scanner_scan(scanner, bytes, (size_t)len);

    if (data && bytes) {
        (*env)->ReleaseByteArrayElements(env, data, (jbyte *)bytes, JNI_ABORT);
    }

    if (result != YRX_SUCCESS && result != YRX_SCAN_TIMEOUT) {
        const char *err = yrx_last_error();
        if (err) LOGI("yrx_scanner_scan failed: %s", err);
    }

    return result_list;
}

JNIEXPORT void JNICALL
Java_com_example_yaraxsample_YaraX_nativeDestroyScanner(JNIEnv *env, jclass clazz, jlong scanner_handle) {
    if (scanner_handle != 0) {
        yrx_scanner_destroy((struct YRX_SCANNER *)(uintptr_t)scanner_handle);
    }
}

JNIEXPORT void JNICALL
Java_com_example_yaraxsample_YaraX_nativeDestroyRules(JNIEnv *env, jclass clazz, jlong rules_handle) {
    if (rules_handle != 0) {
        yrx_rules_destroy((struct YRX_RULES *)(uintptr_t)rules_handle);
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_yaraxsample_YaraX_nativeLastError(JNIEnv *env, jclass clazz) {
    const char *err = yrx_last_error();
    if (!err) return NULL;
    return (*env)->NewStringUTF(env, err);
}

/* Read file content into heap-allocated string. Caller must free(). Returns NULL on failure. */
static char *read_file_contents(const char *path, size_t *out_len) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    if (fseek(f, 0, SEEK_END) != 0) {
        fclose(f);
        return NULL;
    }
    long sz = ftell(f);
    if (sz < 0 || sz > 50 * 1024 * 1024) { /* 50MB limit per file */
        fclose(f);
        return NULL;
    }
    rewind(f);
    char *buf = (char *)malloc((size_t)sz + 1);
    if (!buf) {
        fclose(f);
        return NULL;
    }
    size_t n = fread(buf, 1, (size_t)sz, f);
    fclose(f);
    buf[n] = '\0';
    if (out_len) *out_len = n;
    return buf;
}

JNIEXPORT jlong JNICALL
Java_com_example_yaraxsample_YaraX_nativeCompileRulesFromPaths(JNIEnv *env, jclass clazz,
        jobjectArray yar_paths, jstring include_dir) {
    if (!yar_paths || !include_dir) return 0;

    const char *inc_dir = (*env)->GetStringUTFChars(env, include_dir, NULL);
    if (!inc_dir) return 0;

    struct YRX_COMPILER *compiler = NULL;
    enum YRX_RESULT result = yrx_compiler_create(YRX_RELAXED_RE_SYNTAX, &compiler);
    if (result != YRX_SUCCESS || !compiler) {
        (*env)->ReleaseStringUTFChars(env, include_dir, inc_dir);
        return 0;
    }

    yrx_compiler_add_include_dir(compiler, inc_dir);
    (*env)->ReleaseStringUTFChars(env, include_dir, inc_dir);

    jsize count = (*env)->GetArrayLength(env, yar_paths);
    int added = 0;
    for (jsize i = 0; i < count; i++) {
        jstring jpath = (jstring)(*env)->GetObjectArrayElement(env, yar_paths, i);
        if (!jpath) continue;
        const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
        if (!path) {
            (*env)->DeleteLocalRef(env, jpath);
            continue;
        }

        char *content = read_file_contents(path, NULL);
        if (!content) {
            (*env)->ReleaseStringUTFChars(env, jpath, path);
            (*env)->DeleteLocalRef(env, jpath);
            continue;
        }

        result = yrx_compiler_add_source_with_origin(compiler, content, path);
        free(content);
        if (result != YRX_SUCCESS) {
            LOGI("Skipping rule file (compile error): %s - %s", path, yrx_last_error());
        } else {
            added++;
        }
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        (*env)->DeleteLocalRef(env, jpath);
    }

    if (added == 0) {
        yrx_compiler_destroy(compiler);
        LOGI("No rule files were successfully added");
        return 0;
    }

    struct YRX_RULES *rules = yrx_compiler_build(compiler);
    yrx_compiler_destroy(compiler);
    if (!rules) {
        const char *err = yrx_last_error();
        if (err) LOGI("yrx_compiler_build failed: %s", err);
        return 0;
    }

    return (jlong)(uintptr_t)rules;
}
