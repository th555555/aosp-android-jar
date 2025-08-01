// This file was generated by
//     //third_party/jni_zero/jni_zero.py
// For
//     android.net.connectivity.org.chromium.url.IDNStringUtil

#ifndef android_net_connectivity_org_chromium_url_IDNStringUtil_JNI
#define android_net_connectivity_org_chromium_url_IDNStringUtil_JNI

#include <jni.h>

#include "third_party/jni_zero/jni_export.h"
#include "third_party/jni_zero/jni_zero_internal.h"

// Class Accessors
#ifndef android_net_connectivity_org_chromium_url_IDNStringUtil_clazz_defined
#define android_net_connectivity_org_chromium_url_IDNStringUtil_clazz_defined
inline jclass android_net_connectivity_org_chromium_url_IDNStringUtil_clazz(JNIEnv* env) {
  static const char kClassName[] = "android.net.connectivity.org.chromium.url.IDNStringUtil";
  static std::atomic<jclass> cached_class;
  return jni_zero::internal::LazyGetClass(env, kClassName, &cached_class);
}
#endif


namespace url::android {

// Native to Java functions
static jni_zero::ScopedJavaLocalRef<jstring> Java_IDNStringUtil_idnToASCII(
    JNIEnv* env,
    const jni_zero::JavaRef<jstring>& src) {
  static std::atomic<jmethodID> cached_method_id(nullptr);
  jclass clazz = android_net_connectivity_org_chromium_url_IDNStringUtil_clazz(env);
  CHECK_CLAZZ(env, clazz, clazz, nullptr);
  jni_zero::internal::JniJavaCallContext<true> call_context;
  call_context.Init<jni_zero::MethodID::TYPE_STATIC>(
      env,
      clazz,
      "idnToASCII",
      "(Ljava/lang/String;)Ljava/lang/String;",
      &cached_method_id);
  auto _ret = env->CallStaticObjectMethod(clazz, call_context.method_id(), src.obj());
  jstring _ret2 = static_cast<jstring>(_ret);
  return jni_zero::ScopedJavaLocalRef<jstring>(env, _ret2);
}



}  // namespace url::android

#endif  // android_net_connectivity_org_chromium_url_IDNStringUtil_JNI
