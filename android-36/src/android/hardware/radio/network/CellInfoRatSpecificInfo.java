/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 4 --hash 5867b4f5be491ec815fafea8a3f268b0295427df --stability vintf --min_sdk_version current -pout/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio_interface/4/preprocessed.aidl --ninja -d out/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.network-V4-java-source/gen/android/hardware/radio/network/CellInfoRatSpecificInfo.java.d -o out/soong/.intermediates/hardware/interfaces/radio/aidl/android.hardware.radio.network-V4-java-source/gen -Nhardware/interfaces/radio/aidl/aidl_api/android.hardware.radio.network/4 hardware/interfaces/radio/aidl/aidl_api/android.hardware.radio.network/4/android/hardware/radio/network/CellInfoRatSpecificInfo.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.hardware.radio.network;
/** @hide */
public final class CellInfoRatSpecificInfo implements android.os.Parcelable {
  // tags for union fields
  public final static int gsm = 0;  // android.hardware.radio.network.CellInfoGsm gsm;
  public final static int wcdma = 1;  // android.hardware.radio.network.CellInfoWcdma wcdma;
  public final static int tdscdma = 2;  // android.hardware.radio.network.CellInfoTdscdma tdscdma;
  public final static int lte = 3;  // android.hardware.radio.network.CellInfoLte lte;
  public final static int nr = 4;  // android.hardware.radio.network.CellInfoNr nr;
  public final static int cdma = 5;  // android.hardware.radio.network.CellInfoCdma cdma;

  private int _tag;
  private Object _value;

  public CellInfoRatSpecificInfo() {
    android.hardware.radio.network.CellInfoGsm _value = null;
    this._tag = gsm;
    this._value = _value;
  }

  private CellInfoRatSpecificInfo(android.os.Parcel _aidl_parcel) {
    readFromParcel(_aidl_parcel);
  }

  private CellInfoRatSpecificInfo(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }

  public int getTag() {
    return _tag;
  }

  // android.hardware.radio.network.CellInfoGsm gsm;

  public static CellInfoRatSpecificInfo gsm(android.hardware.radio.network.CellInfoGsm _value) {
    return new CellInfoRatSpecificInfo(gsm, _value);
  }

  public android.hardware.radio.network.CellInfoGsm getGsm() {
    _assertTag(gsm);
    return (android.hardware.radio.network.CellInfoGsm) _value;
  }

  public void setGsm(android.hardware.radio.network.CellInfoGsm _value) {
    _set(gsm, _value);
  }

  // android.hardware.radio.network.CellInfoWcdma wcdma;

  public static CellInfoRatSpecificInfo wcdma(android.hardware.radio.network.CellInfoWcdma _value) {
    return new CellInfoRatSpecificInfo(wcdma, _value);
  }

  public android.hardware.radio.network.CellInfoWcdma getWcdma() {
    _assertTag(wcdma);
    return (android.hardware.radio.network.CellInfoWcdma) _value;
  }

  public void setWcdma(android.hardware.radio.network.CellInfoWcdma _value) {
    _set(wcdma, _value);
  }

  // android.hardware.radio.network.CellInfoTdscdma tdscdma;

  public static CellInfoRatSpecificInfo tdscdma(android.hardware.radio.network.CellInfoTdscdma _value) {
    return new CellInfoRatSpecificInfo(tdscdma, _value);
  }

  public android.hardware.radio.network.CellInfoTdscdma getTdscdma() {
    _assertTag(tdscdma);
    return (android.hardware.radio.network.CellInfoTdscdma) _value;
  }

  public void setTdscdma(android.hardware.radio.network.CellInfoTdscdma _value) {
    _set(tdscdma, _value);
  }

  // android.hardware.radio.network.CellInfoLte lte;

  public static CellInfoRatSpecificInfo lte(android.hardware.radio.network.CellInfoLte _value) {
    return new CellInfoRatSpecificInfo(lte, _value);
  }

  public android.hardware.radio.network.CellInfoLte getLte() {
    _assertTag(lte);
    return (android.hardware.radio.network.CellInfoLte) _value;
  }

  public void setLte(android.hardware.radio.network.CellInfoLte _value) {
    _set(lte, _value);
  }

  // android.hardware.radio.network.CellInfoNr nr;

  public static CellInfoRatSpecificInfo nr(android.hardware.radio.network.CellInfoNr _value) {
    return new CellInfoRatSpecificInfo(nr, _value);
  }

  public android.hardware.radio.network.CellInfoNr getNr() {
    _assertTag(nr);
    return (android.hardware.radio.network.CellInfoNr) _value;
  }

  public void setNr(android.hardware.radio.network.CellInfoNr _value) {
    _set(nr, _value);
  }

  // android.hardware.radio.network.CellInfoCdma cdma;

  /** @deprecated Legacy CDMA is unsupported. */
  @Deprecated
  public static CellInfoRatSpecificInfo cdma(android.hardware.radio.network.CellInfoCdma _value) {
    return new CellInfoRatSpecificInfo(cdma, _value);
  }

  public android.hardware.radio.network.CellInfoCdma getCdma() {
    _assertTag(cdma);
    return (android.hardware.radio.network.CellInfoCdma) _value;
  }

  public void setCdma(android.hardware.radio.network.CellInfoCdma _value) {
    _set(cdma, _value);
  }

  @Override
  public final int getStability() {
    return android.os.Parcelable.PARCELABLE_STABILITY_VINTF;
  }

  public static final android.os.Parcelable.Creator<CellInfoRatSpecificInfo> CREATOR = new android.os.Parcelable.Creator<CellInfoRatSpecificInfo>() {
    @Override
    public CellInfoRatSpecificInfo createFromParcel(android.os.Parcel _aidl_source) {
      return new CellInfoRatSpecificInfo(_aidl_source);
    }
    @Override
    public CellInfoRatSpecificInfo[] newArray(int _aidl_size) {
      return new CellInfoRatSpecificInfo[_aidl_size];
    }
  };

  @Override
  public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
    _aidl_parcel.writeInt(_tag);
    switch (_tag) {
    case gsm:
      _aidl_parcel.writeTypedObject(getGsm(), _aidl_flag);
      break;
    case wcdma:
      _aidl_parcel.writeTypedObject(getWcdma(), _aidl_flag);
      break;
    case tdscdma:
      _aidl_parcel.writeTypedObject(getTdscdma(), _aidl_flag);
      break;
    case lte:
      _aidl_parcel.writeTypedObject(getLte(), _aidl_flag);
      break;
    case nr:
      _aidl_parcel.writeTypedObject(getNr(), _aidl_flag);
      break;
    case cdma:
      _aidl_parcel.writeTypedObject(getCdma(), _aidl_flag);
      break;
    }
  }

  public void readFromParcel(android.os.Parcel _aidl_parcel) {
    int _aidl_tag;
    _aidl_tag = _aidl_parcel.readInt();
    switch (_aidl_tag) {
    case gsm: {
      android.hardware.radio.network.CellInfoGsm _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.hardware.radio.network.CellInfoGsm.CREATOR);
      _set(_aidl_tag, _aidl_value);
      return; }
    case wcdma: {
      android.hardware.radio.network.CellInfoWcdma _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.hardware.radio.network.CellInfoWcdma.CREATOR);
      _set(_aidl_tag, _aidl_value);
      return; }
    case tdscdma: {
      android.hardware.radio.network.CellInfoTdscdma _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.hardware.radio.network.CellInfoTdscdma.CREATOR);
      _set(_aidl_tag, _aidl_value);
      return; }
    case lte: {
      android.hardware.radio.network.CellInfoLte _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.hardware.radio.network.CellInfoLte.CREATOR);
      _set(_aidl_tag, _aidl_value);
      return; }
    case nr: {
      android.hardware.radio.network.CellInfoNr _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.hardware.radio.network.CellInfoNr.CREATOR);
      _set(_aidl_tag, _aidl_value);
      return; }
    case cdma: {
      android.hardware.radio.network.CellInfoCdma _aidl_value;
      _aidl_value = _aidl_parcel.readTypedObject(android.hardware.radio.network.CellInfoCdma.CREATOR);
      _set(_aidl_tag, _aidl_value);
      return; }
    }
    throw new IllegalArgumentException("union: unknown tag: " + _aidl_tag);
  }

  @Override
  public int describeContents() {
    int _mask = 0;
    switch (getTag()) {
    case gsm:
      _mask |= describeContents(getGsm());
      break;
    case wcdma:
      _mask |= describeContents(getWcdma());
      break;
    case tdscdma:
      _mask |= describeContents(getTdscdma());
      break;
    case lte:
      _mask |= describeContents(getLte());
      break;
    case nr:
      _mask |= describeContents(getNr());
      break;
    case cdma:
      _mask |= describeContents(getCdma());
      break;
    }
    return _mask;
  }
  private int describeContents(Object _v) {
    if (_v == null) return 0;
    if (_v instanceof android.os.Parcelable) {
      return ((android.os.Parcelable) _v).describeContents();
    }
    return 0;
  }

  @Override
  public String toString() {
    switch (_tag) {
    case gsm: return "CellInfoRatSpecificInfo.gsm(" + (java.util.Objects.toString(getGsm())) + ")";
    case wcdma: return "CellInfoRatSpecificInfo.wcdma(" + (java.util.Objects.toString(getWcdma())) + ")";
    case tdscdma: return "CellInfoRatSpecificInfo.tdscdma(" + (java.util.Objects.toString(getTdscdma())) + ")";
    case lte: return "CellInfoRatSpecificInfo.lte(" + (java.util.Objects.toString(getLte())) + ")";
    case nr: return "CellInfoRatSpecificInfo.nr(" + (java.util.Objects.toString(getNr())) + ")";
    case cdma: return "CellInfoRatSpecificInfo.cdma(" + (java.util.Objects.toString(getCdma())) + ")";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }
  private void _assertTag(int tag) {
    if (getTag() != tag) {
      throw new IllegalStateException("bad access: " + _tagString(tag) + ", " + _tagString(getTag()) + " is available.");
    }
  }

  private String _tagString(int _tag) {
    switch (_tag) {
    case gsm: return "gsm";
    case wcdma: return "wcdma";
    case tdscdma: return "tdscdma";
    case lte: return "lte";
    case nr: return "nr";
    case cdma: return "cdma";
    }
    throw new IllegalStateException("unknown field: " + _tag);
  }

  private void _set(int _tag, Object _value) {
    this._tag = _tag;
    this._value = _value;
  }
  public static @interface Tag {
    public static final int gsm = 0;
    public static final int wcdma = 1;
    public static final int tdscdma = 2;
    public static final int lte = 3;
    public static final int nr = 4;
    /** @deprecated Legacy CDMA is unsupported. */
    @Deprecated
    public static final int cdma = 5;
  }
}
