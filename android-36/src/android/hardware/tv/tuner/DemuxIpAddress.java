/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 3 --hash b0d0067a930514438d7772c2e02069c7370f3620 --stability vintf --min_sdk_version current -pout/soong/.intermediates/hardware/interfaces/common/aidl/android.hardware.common_interface/2/preprocessed.aidl -pout/soong/.intermediates/hardware/interfaces/common/fmq/aidl/android.hardware.common.fmq_interface/1/preprocessed.aidl --ninja -d out/soong/.intermediates/hardware/interfaces/tv/tuner/aidl/android.hardware.tv.tuner-V3-java-source/gen/android/hardware/tv/tuner/DemuxIpAddress.java.d -o out/soong/.intermediates/hardware/interfaces/tv/tuner/aidl/android.hardware.tv.tuner-V3-java-source/gen -Nhardware/interfaces/tv/tuner/aidl/aidl_api/android.hardware.tv.tuner/3 hardware/interfaces/tv/tuner/aidl/aidl_api/android.hardware.tv.tuner/3/android/hardware/tv/tuner/DemuxIpAddress.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.hardware.tv.tuner;
/** @hide */
public class DemuxIpAddress implements android.os.Parcelable
{
  public android.hardware.tv.tuner.DemuxIpAddressIpAddress srcIpAddress;
  public android.hardware.tv.tuner.DemuxIpAddressIpAddress dstIpAddress;
  public int srcPort = 0;
  public int dstPort = 0;
  @Override
   public final int getStability() { return android.os.Parcelable.PARCELABLE_STABILITY_VINTF; }
  public static final android.os.Parcelable.Creator<DemuxIpAddress> CREATOR = new android.os.Parcelable.Creator<DemuxIpAddress>() {
    @Override
    public DemuxIpAddress createFromParcel(android.os.Parcel _aidl_source) {
      DemuxIpAddress _aidl_out = new DemuxIpAddress();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public DemuxIpAddress[] newArray(int _aidl_size) {
      return new DemuxIpAddress[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeTypedObject(srcIpAddress, _aidl_flag);
    _aidl_parcel.writeTypedObject(dstIpAddress, _aidl_flag);
    _aidl_parcel.writeInt(srcPort);
    _aidl_parcel.writeInt(dstPort);
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
      srcIpAddress = _aidl_parcel.readTypedObject(android.hardware.tv.tuner.DemuxIpAddressIpAddress.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      dstIpAddress = _aidl_parcel.readTypedObject(android.hardware.tv.tuner.DemuxIpAddressIpAddress.CREATOR);
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      srcPort = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      dstPort = _aidl_parcel.readInt();
    } finally {
      if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
        throw new android.os.BadParcelableException("Overflow in the size of parcelable");
      }
      _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
    }
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    _mask |= describeContents(srcIpAddress);
    _mask |= describeContents(dstIpAddress);
    return _mask;
  }
  private int describeContents(Object _v) {
    if (_v == null) return 0;
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }
}
