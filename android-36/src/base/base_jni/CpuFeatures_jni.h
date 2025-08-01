// This file was generated by
//     //third_party/jni_zero/jni_zero.py
// For
//     android.net.connectivity.org.chromium.base.CpuFeatures

#ifndef android_net_connectivity_org_chromium_base_CpuFeatures_JNI
#define android_net_connectivity_org_chromium_base_CpuFeatures_JNI

#include <jni.h>

#include "third_party/jni_zero/jni_export.h"
#include "third_party/jni_zero/jni_zero_internal.h"
namespace base::android {

// Java to native functions
// Forward declaration. To be implemented by the including .cc file.
static jint JNI_CpuFeatures_GetCoreCount(JNIEnv* env);

JNI_ZERO_BOUNDARY_EXPORT jint Java_android_net_connectivity_J_N_M58U6lZz(
    JNIEnv* env,
    jclass jcaller) {
  auto _ret = JNI_CpuFeatures_GetCoreCount(env);
  return _ret;
}

// Forward declaration. To be implemented by the including .cc file.
static jlong JNI_CpuFeatures_GetCpuFeatures(JNIEnv* env);

JNI_ZERO_BOUNDARY_EXPORT jlong Java_android_net_connectivity_J_N_MXUVvXga(
    JNIEnv* env,
    jclass jcaller) {
  auto _ret = JNI_CpuFeatures_GetCpuFeatures(env);
  return _ret;
}



}  // namespace base::android

#endif  // android_net_connectivity_org_chromium_base_CpuFeatures_JNI
