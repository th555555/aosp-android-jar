/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 4 --hash fc7eeb47f5238e538dead4af7575507920c359f7 --stability vintf --min_sdk_version current -pout/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio_interface/4/preprocessed.aidl --ninja -d out/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.config-V4-java-source/gen/android/hardware/radio/config/MultipleEnabledProfilesMode.java.d -o out/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.config-V4-java-source/gen -Nhardware/interfaces/radio/aidl/aidl_api/android.hardware.radio.config/4 hardware/interfaces/radio/aidl/aidl_api/android.hardware.radio.config/4/android/hardware/radio/config/MultipleEnabledProfilesMode.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.hardware.radio.config;
/** @hide */
public @interface MultipleEnabledProfilesMode {
  public static final int NONE = 0;
  public static final int MEP_A1 = 1;
  public static final int MEP_A2 = 2;
  public static final int MEP_B = 3;
  interface $ {
    static String toString(int _aidl_v) {
      if (_aidl_v == NONE) return "NONE";
      if (_aidl_v == MEP_A1) return "MEP_A1";
      if (_aidl_v == MEP_A2) return "MEP_A2";
      if (_aidl_v == MEP_B) return "MEP_B";
      return Integer.toString(_aidl_v);
    }
    static String arrayToString(Object _aidl_v) {
      if (_aidl_v == null) return "null";
      Class<?> _aidl_cls = _aidl_v.getClass();
      if (!_aidl_cls.isArray()) throw new IllegalArgumentException("not an array: " + _aidl_v);
      Class<?> comp = _aidl_cls.getComponentType();
      java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(", ", "[", "]");
      if (comp.isArray()) {
        for (int _aidl_i = 0; _aidl_i < java.lang.reflect.Array.getLength(_aidl_v); _aidl_i++) {
          _aidl_sj.add(arrayToString(java.lang.reflect.Array.get(_aidl_v, _aidl_i)));
        }
      } else {
        if (_aidl_cls != int[].class) throw new IllegalArgumentException("wrong type: " + _aidl_cls);
        for (int e : (int[]) _aidl_v) {
          _aidl_sj.add(toString(e));
        }
      }
      return _aidl_sj.toString();
    }
  }
}
