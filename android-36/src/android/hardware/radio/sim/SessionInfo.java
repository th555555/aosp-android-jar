/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 4 --hash fc1a19a4f86a58981158cc8d956763c9d8ace630 --stability vintf --min_sdk_version current -pout/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio_interface/4/preprocessed.aidl -pout/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.config_interface/4/preprocessed.aidl --ninja -d out/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.sim-V4-java-source/gen/android/hardware/radio/sim/SessionInfo.java.d -o out/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.sim-V4-java-source/gen -Nhardware/interfaces/radio/aidl/aidl_api/android.hardware.radio.sim/4 hardware/interfaces/radio/aidl/aidl_api/android.hardware.radio.sim/4/android/hardware/radio/sim/SessionInfo.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.hardware.radio.sim;
/** @hide */
public class SessionInfo implements android.os.Parcelable
{
  public int sessionId = 0;
  public boolean isEs10 = false;
  @Override
   public final int getStability() { return android.os.Parcelable.PARCELABLE_STABILITY_VINTF; }
  public static final android.os.Parcelable.Creator<SessionInfo> CREATOR = new android.os.Parcelable.Creator<SessionInfo>() {
    @Override
    public SessionInfo createFromParcel(android.os.Parcel _aidl_source) {
      SessionInfo _aidl_out = new SessionInfo();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public SessionInfo[] newArray(int _aidl_size) {
      return new SessionInfo[_aidl_size];
    }
  };
  @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
  {
    int _aidl_start_pos = _aidl_parcel.dataPosition();
    _aidl_parcel.writeInt(0);
    _aidl_parcel.writeInt(sessionId);
    _aidl_parcel.writeBoolean(isEs10);
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
      sessionId = _aidl_parcel.readInt();
      if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
      isEs10 = _aidl_parcel.readBoolean();
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
    _aidl_sj.add("sessionId: " + (sessionId));
    _aidl_sj.add("isEs10: " + (isEs10));
    return "SessionInfo" + _aidl_sj.toString()  ;
  }
  @Override
  public int describeContents() {
    int _mask = 0;
    return _mask;
  }
}
