/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 3 --hash f2ec48a74490bf9d5675f48cb89ecdb3e5cd9c35 --stability vintf --min_sdk_version current -pout/soong/.intermediates/system/hardware/interfaces/media/android.media.soundtrigger.types_interface/3/preprocessed.aidl --ninja -d out/soong/.intermediates/hardware/interfaces/soundtrigger/aidl/android.hardware.soundtrigger3-V3-java-source/gen/android/hardware/soundtrigger3/ISoundTriggerHwCallback.java.d -o out/soong/.intermediates/hardware/interfaces/soundtrigger/aidl/android.hardware.soundtrigger3-V3-java-source/gen -Nhardware/interfaces/soundtrigger/aidl/aidl_api/android.hardware.soundtrigger3/3 hardware/interfaces/soundtrigger/aidl/aidl_api/android.hardware.soundtrigger3/3/android/hardware/soundtrigger3/ISoundTriggerHwCallback.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.hardware.soundtrigger3;
public interface ISoundTriggerHwCallback extends android.os.IInterface
{
  /**
   * The version of this interface that the caller is built against.
   * This might be different from what {@link #getInterfaceVersion()
   * getInterfaceVersion} returns as that is the version of the interface
   * that the remote object is implementing.
   */
  public static final int VERSION = 3;
  public static final String HASH = "f2ec48a74490bf9d5675f48cb89ecdb3e5cd9c35";
  /** Default implementation for ISoundTriggerHwCallback. */
  public static class Default implements android.hardware.soundtrigger3.ISoundTriggerHwCallback
  {
    @Override public void modelUnloaded(int model) throws android.os.RemoteException
    {
    }
    @Override public void phraseRecognitionCallback(int model, android.media.soundtrigger.PhraseRecognitionEvent event) throws android.os.RemoteException
    {
    }
    @Override public void recognitionCallback(int model, android.media.soundtrigger.RecognitionEvent event) throws android.os.RemoteException
    {
    }
    @Override
    public int getInterfaceVersion() {
      return 0;
    }
    @Override
    public String getInterfaceHash() {
      return "";
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.hardware.soundtrigger3.ISoundTriggerHwCallback
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.markVintfStability();
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.hardware.soundtrigger3.ISoundTriggerHwCallback interface,
     * generating a proxy if needed.
     */
    public static android.hardware.soundtrigger3.ISoundTriggerHwCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.hardware.soundtrigger3.ISoundTriggerHwCallback))) {
        return ((android.hardware.soundtrigger3.ISoundTriggerHwCallback)iin);
      }
      return new android.hardware.soundtrigger3.ISoundTriggerHwCallback.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      else if (code == TRANSACTION_getInterfaceVersion) {
        reply.writeNoException();
        reply.writeInt(getInterfaceVersion());
        return true;
      }
      else if (code == TRANSACTION_getInterfaceHash) {
        reply.writeNoException();
        reply.writeString(getInterfaceHash());
        return true;
      }
      switch (code)
      {
        case TRANSACTION_modelUnloaded:
        {
          int _arg0;
          _arg0 = data.readInt();
          data.enforceNoDataAvail();
          this.modelUnloaded(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_phraseRecognitionCallback:
        {
          int _arg0;
          _arg0 = data.readInt();
          android.media.soundtrigger.PhraseRecognitionEvent _arg1;
          _arg1 = data.readTypedObject(android.media.soundtrigger.PhraseRecognitionEvent.CREATOR);
          data.enforceNoDataAvail();
          this.phraseRecognitionCallback(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_recognitionCallback:
        {
          int _arg0;
          _arg0 = data.readInt();
          android.media.soundtrigger.RecognitionEvent _arg1;
          _arg1 = data.readTypedObject(android.media.soundtrigger.RecognitionEvent.CREATOR);
          data.enforceNoDataAvail();
          this.recognitionCallback(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.hardware.soundtrigger3.ISoundTriggerHwCallback
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      private int mCachedVersion = -1;
      private String mCachedHash = "-1";
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void modelUnloaded(int model) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(model);
          boolean _status = mRemote.transact(Stub.TRANSACTION_modelUnloaded, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method modelUnloaded is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void phraseRecognitionCallback(int model, android.media.soundtrigger.PhraseRecognitionEvent event) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(model);
          _data.writeTypedObject(event, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_phraseRecognitionCallback, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method phraseRecognitionCallback is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void recognitionCallback(int model, android.media.soundtrigger.RecognitionEvent event) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(model);
          _data.writeTypedObject(event, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_recognitionCallback, _data, _reply, 0);
          if (!_status) {
            throw new android.os.RemoteException("Method recognitionCallback is unimplemented.");
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override
      public int getInterfaceVersion() throws android.os.RemoteException {
        if (mCachedVersion == -1) {
          android.os.Parcel data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel reply = android.os.Parcel.obtain();
          try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceVersion, data, reply, 0);
            reply.readException();
            mCachedVersion = reply.readInt();
          } finally {
            reply.recycle();
            data.recycle();
          }
        }
        return mCachedVersion;
      }
      @Override
      public synchronized String getInterfaceHash() throws android.os.RemoteException {
        if ("-1".equals(mCachedHash)) {
          android.os.Parcel data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel reply = android.os.Parcel.obtain();
          try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean _status = mRemote.transact(Stub.TRANSACTION_getInterfaceHash, data, reply, 0);
            reply.readException();
            mCachedHash = reply.readString();
          } finally {
            reply.recycle();
            data.recycle();
          }
        }
        return mCachedHash;
      }
    }
    static final int TRANSACTION_modelUnloaded = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_phraseRecognitionCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_recognitionCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_getInterfaceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777214);
    static final int TRANSACTION_getInterfaceHash = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777213);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android$hardware$soundtrigger3$ISoundTriggerHwCallback".replace('$', '.');
  public void modelUnloaded(int model) throws android.os.RemoteException;
  public void phraseRecognitionCallback(int model, android.media.soundtrigger.PhraseRecognitionEvent event) throws android.os.RemoteException;
  public void recognitionCallback(int model, android.media.soundtrigger.RecognitionEvent event) throws android.os.RemoteException;
  public int getInterfaceVersion() throws android.os.RemoteException;
  public String getInterfaceHash() throws android.os.RemoteException;
}
