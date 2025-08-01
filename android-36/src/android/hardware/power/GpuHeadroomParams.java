/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 6 --hash 13171cf98a48de298baf85167633376ea3db4ea0 -t --stability vintf --min_sdk_version platform_apis -pout/soong/.intermediates/hardware/interfaces/common/fmq/aidl/android.hardware.common.fmq_interface/1/preprocessed.aidl -pout/soong/.intermediates/hardware/interfaces/common/aidl/android.hardware.common_interface/2/preprocessed.aidl --ninja -d out/soong/.intermediates/hardware/interfaces/power/aidl/android.hardware.power-V6-java-source/gen/android/hardware/power/GpuHeadroomParams.java.d -o out/soong/.intermediates/hardware/interfaces/power/aidl/android.hardware.power-V6-java-source/gen -Nhardware/interfaces/power/aidl/aidl_api/android.hardware.power/6 hardware/interfaces/power/aidl/aidl_api/android.hardware.power/6/android/hardware/power/GpuHeadroomParams.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.hardware.power;
public class GpuHeadroomParams implements android.os.Parcelable
{
  public byte calculationType = android.hardware.power.GpuHeadroomParams.CalculationType.MIN;
  public int calculationWindowMillis = 1000;
  @Override
   public final int getStability() { return android.os.Parcelable.PARCELABLE_STABILITY_VINTF; }
  public static final android.os.Parcelable.Creator<GpuHeadroomParams> CREATOR = new android.os.Parcelable.Creator<GpuHeadroomParams>() {
    @Override
    public GpuHeadroomParams createFromParcel(android.os.Parcel _aidl_source) {
      GpuHeadroomParams _aidl_out = new GpuHeadroomParams();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public GpuHeadroomParams[] newArray(int _aidl_size) {
      return new GpuHeadroomParams[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeByte(calculationType);
    _aidl_parcel.writeInt(calculationWindowMillis);
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
      calculationType = _aidl_parcel.readByte();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      calculationWindowMillis = _aidl_parcel.readInt();
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
    _aidl_sj.add("calculationType: " + (calculationType));
    _aidl_sj.add("calculationWindowMillis: " + (calculationWindowMillis));
    return "GpuHeadroomParams" + _aidl_sj.toString()  ;
  }
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (!(other instanceof GpuHeadroomParams)) return false;
    GpuHeadroomParams that = (GpuHeadroomParams)other;
    if (!java.util.Objects.deepEquals(calculationType, that.calculationType)) return false;
    if (!java.util.Objects.deepEquals(calculationWindowMillis, that.calculationWindowMillis)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return java.util.Arrays.deepHashCode(java.util.Arrays.asList(calculationType, calculationWindowMillis).toArray());
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    return _mask;
  }
  public static @interface CalculationType {
    public static final byte MIN = 0;
    public static final byte AVERAGE = 1;
  }
}
