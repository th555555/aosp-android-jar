/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 4 --hash af71e6ae2c6861fc2b09bb477e7285e6777cd41c --stability vintf --min_sdk_version 29 --ninja -d out/soong/.intermediates/system/hardware/interfaces/media/android.media.audio.common.types-V4-java-source/gen/android/media/audio/common/AudioHalCapCriterion.java.d -o out/soong/.intermediates/system/hardware/interfaces/media/android.media.audio.common.types-V4-java-source/gen -Nsystem/hardware/interfaces/media/aidl_api/android.media.audio.common.types/4 system/hardware/interfaces/media/aidl_api/android.media.audio.common.types/4/android/media/audio/common/AudioHalCapCriterion.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.media.audio.common;
/** @hide */
public class AudioHalCapCriterion implements android.os.Parcelable
{
  public java.lang.String name;
  public java.lang.String criterionTypeName;
  public java.lang.String defaultLiteralValue;
  @Override
   public final int getStability() { return android.os.Parcelable.PARCELABLE_STABILITY_VINTF; }
  public static final android.os.Parcelable.Creator<AudioHalCapCriterion> CREATOR = new android.os.Parcelable.Creator<AudioHalCapCriterion>() {
    @Override
    public AudioHalCapCriterion createFromParcel(android.os.Parcel _aidl_source) {
      AudioHalCapCriterion _aidl_out = new AudioHalCapCriterion();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public AudioHalCapCriterion[] newArray(int _aidl_size) {
      return new AudioHalCapCriterion[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeString(name);
    _aidl_parcel.writeString(criterionTypeName);
    _aidl_parcel.writeString(defaultLiteralValue);
    int _aidl_end_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.setDataPosition(_aidl_start_pos);
    _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
    _aidl_parcel.setDataPosition(_aidl_end_pos);
  }
  public final void readFromParcel(android.os.Parcel _aidl_parcel)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    int _aidl_parcelable_size = _aidl_parcel.readInt();
    try {
      if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      name = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      criterionTypeName = _aidl_parcel.readString();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      defaultLiteralValue = _aidl_parcel.readString();
    } finally {
      if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
        throw new android.os.BadParcelableException("Overflow in the size of parcelable");
      }
      _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    }
  }
  @Override
  public String toString() {
    java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(", ", "{", "}");
    _aidl_sj.add("name: " + (java.util.Objects.toString(name)));
    _aidl_sj.add("criterionTypeName: " + (java.util.Objects.toString(criterionTypeName)));
    _aidl_sj.add("defaultLiteralValue: " + (java.util.Objects.toString(defaultLiteralValue)));
    return "AudioHalCapCriterion" + _aidl_sj.toString()  ;
  }
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (!(other instanceof AudioHalCapCriterion)) return false;
    AudioHalCapCriterion that = (AudioHalCapCriterion)other;
    if (!java.util.Objects.deepEquals(name, that.name)) return false;
    if (!java.util.Objects.deepEquals(criterionTypeName, that.criterionTypeName)) return false;
    if (!java.util.Objects.deepEquals(defaultLiteralValue, that.defaultLiteralValue)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return java.util.Arrays.deepHashCode(java.util.Arrays.asList(name, criterionTypeName, defaultLiteralValue).toArray());
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    return _mask;
  }
}
