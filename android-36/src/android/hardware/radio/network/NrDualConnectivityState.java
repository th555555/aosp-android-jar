/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 4 --hash 5867b4f5be491ec815fafea8a3f268b0295427df --stability vintf --min_sdk_version current -pout/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio_interface/4/preprocessed.aidl --ninja -d out/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.network-V4-java-source/gen/android/hardware/radio/network/NrDualConnectivityState.java.d -o out/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.network-V4-java-source/gen -Nhardware/interfaces/radio/aidl/aidl_api/android.hardware.radio.network/4 hardware/interfaces/radio/aidl/aidl_api/android.hardware.radio.network/4/android/hardware/radio/network/NrDualConnectivityState.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.hardware.radio.network;
/** @hide */
public @interface NrDualConnectivityState {
  public static final byte ENABLE = 1;
  public static final byte DISABLE = 2;
  public static final byte DISABLE_IMMEDIATE = 3;
  interface $ {
    static String toString(byte _aidl_v) {
      if (_aidl_v == ENABLE) return "ENABLE";
      if (_aidl_v == DISABLE) return "DISABLE";
      if (_aidl_v == DISABLE_IMMEDIATE) return "DISABLE_IMMEDIATE";
      return Byte.toString(_aidl_v);
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
        if (_aidl_cls != byte[].class) throw new IllegalArgumentException("wrong type: " + _aidl_cls);
        for (byte e : (byte[]) _aidl_v) {
          _aidl_sj.add(toString(e));
        }
      }
      return _aidl_sj.toString();
    }
  }
}
