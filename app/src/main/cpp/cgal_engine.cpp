#include <jni.h>
#include <string>
#include <atomic>
#include <android/log.h>
#include "nlohmann/json.hpp"
#include "cgal_compute.h"
#include "scene_builder.h"
#include "text_renderer.h"

#define LOG_TAG "CgalEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using json = nlohmann::json;

static std::atomic<bool> g_cancel_flag{false};

// Reference to the text renderer in scene_builder.cpp
extern TextRenderer& get_text_renderer();

/**
 * Helper: Build a NativeResult jobject with mesh data (success case).
 */
static jobject build_native_result(JNIEnv* env, const ComputeResult& result) {
    jclass cls = env->FindClass("com/openscadviewer/engine/NativeResult");
    if (!cls) {
        LOGE("Failed to find NativeResult class");
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(cls, "<init>",
        "([F[F[FLjava/lang/String;Ljava/lang/String;)V");
    if (!ctor) {
        LOGE("Failed to find NativeResult constructor");
        return nullptr;
    }

    // Create float arrays for vertices, normals, colors
    jfloatArray jVertices = nullptr;
    jfloatArray jNormals = nullptr;
    jfloatArray jColors = nullptr;

    if (!result.vertices.empty()) {
        jVertices = env->NewFloatArray(static_cast<jsize>(result.vertices.size()));
        env->SetFloatArrayRegion(jVertices, 0,
            static_cast<jsize>(result.vertices.size()), result.vertices.data());
    }

    if (!result.normals.empty()) {
        jNormals = env->NewFloatArray(static_cast<jsize>(result.normals.size()));
        env->SetFloatArrayRegion(jNormals, 0,
            static_cast<jsize>(result.normals.size()), result.normals.data());
    }

    if (!result.colors.empty()) {
        jColors = env->NewFloatArray(static_cast<jsize>(result.colors.size()));
        env->SetFloatArrayRegion(jColors, 0,
            static_cast<jsize>(result.colors.size()), result.colors.data());
    }

    // errorCategory and errorMessage are null for success
    return env->NewObject(cls, ctor, jVertices, jNormals, jColors, nullptr, nullptr);
}

/**
 * Helper: Build a NativeResult jobject with error information (failure case).
 */
static jobject build_error_result(JNIEnv* env, const char* category, const char* message) {
    jclass cls = env->FindClass("com/openscadviewer/engine/NativeResult");
    if (!cls) {
        LOGE("Failed to find NativeResult class");
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(cls, "<init>",
        "([F[F[FLjava/lang/String;Ljava/lang/String;)V");
    if (!ctor) {
        LOGE("Failed to find NativeResult constructor");
        return nullptr;
    }

    jstring jCategory = env->NewStringUTF(category);
    jstring jMessage = env->NewStringUTF(message);

    // vertices, normals, colors are null for error
    return env->NewObject(cls, ctor, nullptr, nullptr, nullptr, jCategory, jMessage);
}

/**
 * Helper: Invoke a Kotlin Function1<String, Unit> progress callback from native code.
 */
static void invoke_progress_callback(JNIEnv* env, jobject callback, const char* message) {
    if (!callback) return;

    jclass funcClass = env->GetObjectClass(callback);
    if (!funcClass) return;

    jmethodID invokeMethod = env->GetMethodID(funcClass, "invoke",
        "(Ljava/lang/Object;)Ljava/lang/Object;");
    if (!invokeMethod) return;

    jstring jMessage = env->NewStringUTF(message);
    env->CallObjectMethod(callback, invokeMethod, jMessage);
    env->DeleteLocalRef(jMessage);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_openscadviewer_engine_CgalComputeEngine_nativeCompute(
    JNIEnv* env, jobject /* thiz */, jbyteArray sceneJson, jobject progressCallback) {

    g_cancel_flag.store(false);

    // Extract JSON byte array into std::string
    jsize len = env->GetArrayLength(sceneJson);
    jbyte* bytes = env->GetByteArrayElements(sceneJson, nullptr);
    if (!bytes) {
        return build_error_result(env, "INVALID_INPUT", "Failed to access JSON byte array");
    }

    std::string jsonStr(reinterpret_cast<char*>(bytes), static_cast<size_t>(len));
    env->ReleaseByteArrayElements(sceneJson, bytes, JNI_ABORT);

    // Parse JSON and compute
    try {
        json sceneData = json::parse(jsonStr);
        ComputeResult result = cgal_compute(sceneData, g_cancel_flag);

        // Check if the result contains an error from cgal_compute
        if (!result.error_category.empty()) {
            return build_error_result(env,
                result.error_category.c_str(),
                result.error_message.c_str());
        }

        return build_native_result(env, result);
    } catch (const json::parse_error& e) {
        return build_error_result(env, "INVALID_INPUT", e.what());
    } catch (const std::bad_alloc&) {
        return build_error_result(env, "OUT_OF_MEMORY", "Native memory allocation failed");
    } catch (const std::exception& e) {
        return build_error_result(env, "COMPUTATION_FAILURE", e.what());
    } catch (...) {
        return build_error_result(env, "COMPUTATION_FAILURE", "Unknown native error");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_openscadviewer_engine_CgalComputeEngine_nativeCancel(
    JNIEnv* /* env */, jobject /* thiz */, jlong /* handle */) {
    g_cancel_flag.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_openscadviewer_engine_CgalComputeEngine_nativeSetFontPath(
    JNIEnv* env, jobject /* thiz */, jstring fontPath) {
    if (!fontPath) return;

    const char* path = env->GetStringUTFChars(fontPath, nullptr);
    if (path) {
        get_text_renderer().set_font_path(std::string(path));
        env->ReleaseStringUTFChars(fontPath, path);
    }
}
