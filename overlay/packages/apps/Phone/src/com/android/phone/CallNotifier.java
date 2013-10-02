package com.android.phone;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemVibrator;
import android.os.Vibrator;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery.OnQueryCompleteListener;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.DisconnectCause;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaDisplayInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.SignalToneUtil;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class CallNotifier extends Handler
  implements CallerInfoAsyncQuery.OnQueryCompleteListener
{
  private static final boolean DBG = false;
  private static CallNotifier sInstance;
  private PhoneGlobals mApplication;
  private AudioManager mAudioManager;
  private BluetoothHeadset mBluetoothHeadset;
  private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener = new BluetoothProfile.ServiceListener()
  {
    public void onServiceConnected(int paramAnonymousInt, BluetoothProfile paramAnonymousBluetoothProfile)
    {
      CallNotifier.access$702(CallNotifier.this, (BluetoothHeadset)paramAnonymousBluetoothProfile);
    }

    public void onServiceDisconnected(int paramAnonymousInt)
    {
      CallNotifier.access$702(CallNotifier.this, null);
    }
  };
  private CallManager mCM;
  private CallLogAsync mCallLog;
  private boolean mCallWaitingTimeOut = false;
  private InCallTonePlayer mCallWaitingTonePlayer;
  private int mCallerInfoQueryState;
  private Object mCallerInfoQueryStateGuard = new Object();
  private int mCurrentEmergencyToneState = 0;
  private EmergencyTonePlayerVibrator mEmergencyTonePlayerVibrator;
  private Set<Connection> mForwardedCalls;
  private InCallTonePlayer mInCallRingbackTonePlayer;
  private boolean mIsCdmaRedialCall = false;
  private int mIsEmergencyToneOn;
  private boolean mNextGsmCallIsForwarded;
  PhoneStateListener mPhoneStateListener = new PhoneStateListener()
  {
    public void onCallForwardingIndicatorChanged(boolean paramAnonymousBoolean)
    {
      CallNotifier.this.onCfiChanged(paramAnonymousBoolean);
    }

    public void onMessageWaitingIndicatorChanged(boolean paramAnonymousBoolean)
    {
      CallNotifier.this.onMwiChanged(paramAnonymousBoolean);
    }
  };
  private Call.State mPreviousCdmaCallState;
  private Ringer mRinger;
  private ToneGenerator mSignalInfoToneGenerator;
  private boolean mSilentRingerRequested;
  private Vibrator mVibrator;
  private boolean mVoicePrivacyState = false;
  private Set<Connection> mWaitingCalls;

  private CallNotifier(PhoneGlobals paramPhoneGlobals, Phone paramPhone, Ringer paramRinger, CallLogAsync paramCallLogAsync)
  {
    this.mApplication = paramPhoneGlobals;
    this.mCM = paramPhoneGlobals.mCM;
    this.mCallLog = paramCallLogAsync;
    this.mForwardedCalls = new HashSet();
    this.mWaitingCalls = new HashSet();
    this.mAudioManager = ((AudioManager)this.mApplication.getSystemService("audio"));
    this.mVibrator = ((Vibrator)this.mApplication.getSystemService("vibrator"));
    registerForNotifications();
    createSignalInfoToneGenerator();
    this.mRinger = paramRinger;
    BluetoothAdapter localBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (localBluetoothAdapter != null)
      localBluetoothAdapter.getProfileProxy(this.mApplication.getApplicationContext(), this.mBluetoothProfileServiceListener, 1);
    ((TelephonyManager)paramPhoneGlobals.getSystemService("phone")).listen(this.mPhoneStateListener, 12);
  }

  private void createSignalInfoToneGenerator()
  {
    if (this.mSignalInfoToneGenerator == null)
      try
      {
        this.mSignalInfoToneGenerator = new ToneGenerator(0, 80);
        Log.d("CallNotifier", "CallNotifier: mSignalInfoToneGenerator created when toneplay");
        return;
      }
      catch (RuntimeException localRuntimeException)
      {
        Log.w("CallNotifier", "CallNotifier: Exception caught while creating mSignalInfoToneGenerator: " + localRuntimeException);
        this.mSignalInfoToneGenerator = null;
        return;
      }
    Log.d("CallNotifier", "mSignalInfoToneGenerator created already, hence skipping");
  }

  private CallerInfo getCallerInfoFromConnection(Connection paramConnection)
  {
    Object localObject = paramConnection.getUserData();
    if ((localObject == null) || ((localObject instanceof CallerInfo)))
      return (CallerInfo)localObject;
    return ((PhoneUtils.CallerInfoToken)localObject).currentInfo;
  }

  private String getLogNumber(Connection paramConnection, CallerInfo paramCallerInfo)
  {
    String str1;
    if (paramConnection.isIncoming())
      str1 = paramConnection.getAddress();
    while (str1 == null)
    {
      return null;
      if ((paramCallerInfo == null) || (TextUtils.isEmpty(paramCallerInfo.phoneNumber)) || (paramCallerInfo.isEmergencyNumber()) || (paramCallerInfo.isVoiceMailNumber()))
      {
        if (paramConnection.getCall().getPhone().getPhoneType() == 2)
          str1 = paramConnection.getOrigDialString();
        else
          str1 = paramConnection.getAddress();
      }
      else
        str1 = paramCallerInfo.phoneNumber;
    }
    int i = paramConnection.getNumberPresentation();
    String str2 = PhoneUtils.modifyForSpecialCnapCases(this.mApplication, paramCallerInfo, str1, i);
    if (!PhoneNumberUtils.isUriNumber(str2))
      str2 = PhoneNumberUtils.stripSeparators(str2);
    return str2;
  }

  private int getPresentation(Connection paramConnection, CallerInfo paramCallerInfo)
  {
    int i;
    if (paramCallerInfo == null)
      i = paramConnection.getNumberPresentation();
    while (true)
    {
      if (DBG)
        log("- getPresentation: presentation: " + i);
      return i;
      i = paramCallerInfo.numberPresentation;
      if (DBG)
        log("- getPresentation(): ignoring connection's presentation: " + paramConnection.getNumberPresentation());
    }
  }

  private int getSuppServiceToastTextResId(SuppServiceNotification paramSuppServiceNotification)
  {
    if (!PhoneUtils.PhoneSettings.showInCallEvents(this.mApplication));
    do
    {
      return -1;
      if (paramSuppServiceNotification.notificationType == 0)
      {
        switch (paramSuppServiceNotification.code)
        {
        case 3:
        default:
          return -1;
        case 0:
          return 2131296265;
        case 1:
          return 2131296266;
        case 2:
          return 2131296267;
        case 4:
          return 2131296268;
        case 5:
          return 2131296269;
        case 6:
          return 2131296270;
        case 7:
          return 2131296271;
        case 8:
        }
        return 2131296272;
      }
    }
    while (paramSuppServiceNotification.notificationType != 1);
    switch (paramSuppServiceNotification.code)
    {
    case 2:
    case 3:
    case 9:
    default:
      return -1;
    case 1:
      return 2131296268;
    case 4:
      return 2131296274;
    case 5:
      return 2131296275;
    case 6:
      return 2131296276;
    case 7:
      return 2131296277;
    case 8:
      return 2131296278;
    case 10:
    }
    return 2131296273;
  }

  private boolean ignoreAllIncomingCalls(Phone paramPhone)
  {
    if (!PhoneGlobals.sVoiceCapable)
    {
      Log.w("CallNotifier", "Got onNewRingingConnection() on non-voice-capable device! Ignoring...");
      return true;
    }
    if (PhoneUtils.isPhoneInEcm(paramPhone))
    {
      if (DBG)
        log("Incoming call while in ECM: always allow...");
      return false;
    }
    if (Settings.Global.getInt(this.mApplication.getContentResolver(), "device_provisioned", 0) != 0);
    for (int i = 1; i == 0; i = 0)
    {
      Log.i("CallNotifier", "Ignoring incoming call: not provisioned");
      return true;
    }
    if (TelephonyCapabilities.supportsOtasp(paramPhone))
    {
      int j;
      if (this.mApplication.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION)
      {
        j = 1;
        if (this.mApplication.cdmaOtaScreenState.otaScreenState != OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_SUCCESS_FAILURE_DLG)
          break label151;
      }
      label151: for (int k = 1; ; k = 0)
      {
        if (!this.mApplication.cdmaOtaProvisionData.inOtaSpcState)
          break label157;
        Log.i("CallNotifier", "Ignoring incoming call: OTA call is active");
        return true;
        j = 0;
        break;
      }
      label157: if ((j != 0) || (k != 0))
      {
        if (k != 0)
          this.mApplication.dismissOtaDialogs();
        this.mApplication.clearOtaState();
        this.mApplication.clearInCallScreenMode();
        return false;
      }
    }
    return false;
  }

  static CallNotifier init(PhoneGlobals paramPhoneGlobals, Phone paramPhone, Ringer paramRinger, CallLogAsync paramCallLogAsync)
  {
    try
    {
      if (sInstance == null)
        sInstance = new CallNotifier(paramPhoneGlobals, paramPhone, paramRinger, paramCallLogAsync);
      while (true)
      {
        CallNotifier localCallNotifier = sInstance;
        return localCallNotifier;
        Log.wtf("CallNotifier", "init() called multiple times!  sInstance = " + sInstance);
      }
    }
    finally
    {
    }
  }

  private void log(String paramString)
  {
    Log.d("CallNotifier", paramString);
  }

  private void onCdmaCallWaiting(AsyncResult paramAsyncResult)
  {
    removeMessages(22);
    removeMessages(23);
    this.mApplication.cdmaPhoneCallState.setCurrentCallState(CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
    if (!this.mApplication.isShowingCallScreen())
    {
      if (DBG)
        log("- showing incoming call (CDMA call waiting)...");
      showIncomingCall();
    }
    this.mCallWaitingTimeOut = false;
    sendEmptyMessageDelayed(22, 20000L);
    this.mApplication.cdmaPhoneCallState.setAddCallMenuStateAfterCallWaiting(false);
    sendEmptyMessageDelayed(23, 30000L);
    CdmaCallWaitingNotification localCdmaCallWaitingNotification = (CdmaCallWaitingNotification)paramAsyncResult.result;
    int i = localCdmaCallWaitingNotification.isPresent;
    if (DBG)
      log("onCdmaCallWaiting: isPresent=" + i);
    if (i == 1)
    {
      int j = localCdmaCallWaitingNotification.signalType;
      int k = localCdmaCallWaitingNotification.alertPitch;
      int m = localCdmaCallWaitingNotification.signal;
      if (DBG)
        log("onCdmaCallWaiting: uSignalType=" + j + ", uAlertPitch=" + k + ", uSignal=" + m);
      new SignalInfoTonePlayer(SignalToneUtil.getAudioToneFromSignalInfo(j, k, m)).start();
    }
  }

  private void onCdmaCallWaitingReject()
  {
    Call localCall = this.mCM.getFirstActiveRingingCall();
    Connection localConnection;
    int i;
    int j;
    Object localObject;
    CallerInfo localCallerInfo;
    label86: int k;
    if (localCall.getState() == Call.State.WAITING)
    {
      localConnection = localCall.getLatestConnection();
      if (localConnection != null)
      {
        String str1 = localConnection.getAddress();
        i = localConnection.getNumberPresentation();
        long l1 = localConnection.getCreateTime();
        long l2 = localConnection.getDurationMillis();
        if (!this.mCallWaitingTimeOut)
          break label212;
        j = 3;
        localObject = localConnection.getUserData();
        if ((localObject != null) && (!(localObject instanceof CallerInfo)))
          break label218;
        localCallerInfo = (CallerInfo)localObject;
        String str2 = PhoneUtils.modifyForSpecialCnapCases(this.mApplication, localCallerInfo, str1, i);
        if (localCallerInfo == null)
          break label231;
        k = localCallerInfo.numberPresentation;
        label112: if (DBG)
          log("- onCdmaCallWaitingReject(): logNumber set to: " + str2 + ", newPresentation value is: " + k);
        CallLogAsync.AddCallArgs localAddCallArgs = new CallLogAsync.AddCallArgs(this.mApplication, localCallerInfo, str2, i, j, l1, l2);
        this.mCallLog.addCall(localAddCallArgs);
        if (j != 3)
          break label238;
        showMissedCallNotification(localConnection, l1);
      }
    }
    while (true)
    {
      PhoneUtils.hangup(localConnection);
      this.mCallWaitingTimeOut = false;
      return;
      label212: j = 1;
      break;
      label218: localCallerInfo = ((PhoneUtils.CallerInfoToken)localObject).currentInfo;
      break label86;
      label231: k = i;
      break label112;
      label238: removeMessages(22);
    }
  }

  private void onCfiChanged(boolean paramBoolean)
  {
    this.mApplication.notificationMgr.updateCfi(paramBoolean);
  }

  private void onCustomRingQueryComplete()
  {
    synchronized (this.mCallerInfoQueryStateGuard)
    {
      int i = this.mCallerInfoQueryState;
      int j = 0;
      if (i == -1)
      {
        this.mCallerInfoQueryState = 0;
        j = 1;
      }
      if (j != 0)
      {
        Log.w("CallNotifier", "CallerInfo query took too long; falling back to default ringtone");
        EventLog.writeEvent(70304, new Object[0]);
      }
      if (this.mCM.getState() != PhoneConstants.State.RINGING)
      {
        Log.i("CallNotifier", "onCustomRingQueryComplete: No incoming call! Bailing out...");
        return;
      }
    }
    this.mRinger.ring();
    if (DBG)
      log("- showing incoming call (custom ring query complete)...");
    showIncomingCall();
  }

  private void onCustomRingtoneQueryTimeout(String paramString)
  {
    Log.w("CallNotifier", "CallerInfo query took too long; look up local fallback cache.");
    CallerInfoCache.CacheEntry localCacheEntry = this.mApplication.callerInfoCache.getCacheEntry(paramString);
    if (localCacheEntry != null)
    {
      if (localCacheEntry.sendToVoicemail)
      {
        log("send to voicemail flag detected (in fallback cache). hanging up.");
        PhoneUtils.hangupRingingCall(this.mCM.getFirstActiveRingingCall());
        return;
      }
      if (localCacheEntry.customRingtone != null)
      {
        log("custom ringtone found (in fallback cache), setting up ringer: " + localCacheEntry.customRingtone);
        this.mRinger.setCustomRingtoneUri(Uri.parse(localCacheEntry.customRingtone));
      }
    }
    while (true)
    {
      onCustomRingQueryComplete();
      return;
      log("Failed to find fallback cache. Use default ringer tone.");
    }
  }

  private void onDisconnect(AsyncResult paramAsyncResult)
  {
    this.mVoicePrivacyState = false;
    Connection localConnection = (Connection)paramAsyncResult.result;
    if (localConnection != null)
    {
      log("onDisconnect: cause = " + localConnection.getDisconnectCause() + ", incoming = " + localConnection.isIncoming() + ", date = " + localConnection.getCreateTime());
      if (localConnection != null)
      {
        this.mForwardedCalls.remove(localConnection);
        this.mWaitingCalls.remove(localConnection);
      }
      if ((localConnection == null) || (localConnection.getCall().getPhone().getPhoneType() != 2))
        break label1227;
    }
    label300: label581: label1227: for (int i = Settings.Global.getInt(this.mApplication.getContentResolver(), "call_auto_retry", 0); ; i = 0)
    {
      stopSignalInfoTone();
      if ((localConnection != null) && (localConnection.getCall().getPhone().getPhoneType() == 2))
      {
        this.mApplication.cdmaPhoneCallState.resetCdmaPhoneCallState();
        removeMessages(22);
        removeMessages(23);
      }
      if (localConnection != null)
        if (!"Blacklist".equals(localConnection.getUserData()));
      int j;
      Connection.DisconnectCause localDisconnectCause3;
      int k;
      do
      {
        return;
        Log.w("CallNotifier", "onDisconnect: null connection");
        break;
        if ((PhoneUtils.PhoneSettings.vibHangup(this.mApplication)) && (localConnection.getDurationMillis() > 0L))
          vibrate(50, 100, 50);
        removeMessages(28);
        Call localCall = this.mCM.getFirstActiveRingingCall();
        if (localCall.getPhone().getPhoneType() != 2)
          break label897;
        if (!PhoneUtils.isRealIncomingCall(localCall.getState()))
          break label874;
        if (DBG)
          log("cancelCallInProgressNotifications()... (onDisconnect)");
        this.mApplication.notificationMgr.cancelCallInProgressNotifications();
        if (this.mCallWaitingTonePlayer != null)
        {
          this.mCallWaitingTonePlayer.stopTone();
          this.mCallWaitingTonePlayer = null;
        }
        if ((localConnection != null) && (TelephonyCapabilities.supportsOtasp(localConnection.getCall().getPhone())))
        {
          String str3 = localConnection.getAddress();
          if (localConnection.getCall().getPhone().isOtaSpNumber(str3))
          {
            if (DBG)
              log("onDisconnect: this was an OTASP call!");
            this.mApplication.handleOtaspDisconnect();
          }
        }
        j = 0;
        if (localConnection != null)
        {
          localDisconnectCause3 = localConnection.getDisconnectCause();
          if (localDisconnectCause3 != Connection.DisconnectCause.BUSY)
            break label920;
          if (DBG)
            log("- need to play BUSY tone!");
          j = 2;
        }
        PhoneUtils.turnOnNoiseSuppression(this.mApplication.getApplicationContext(), false);
        if ((j == 0) && (this.mCM.getState() == PhoneConstants.State.IDLE) && (localConnection != null))
        {
          Connection.DisconnectCause localDisconnectCause2 = localConnection.getDisconnectCause();
          if ((localDisconnectCause2 == Connection.DisconnectCause.NORMAL) || (localDisconnectCause2 == Connection.DisconnectCause.LOCAL))
          {
            j = 4;
            this.mIsCdmaRedialCall = false;
          }
        }
        k = j;
        if (this.mCM.getState() == PhoneConstants.State.IDLE)
        {
          if (k == 0)
            resetAudioStateAfterDisconnect();
          this.mApplication.notificationMgr.cancelCallInProgressNotifications();
        }
      }
      while (localConnection == null);
      String str1 = localConnection.getAddress();
      long l1 = localConnection.getCreateTime();
      long l2 = localConnection.getDurationMillis();
      Connection.DisconnectCause localDisconnectCause1 = localConnection.getDisconnectCause();
      Phone localPhone = localConnection.getCall().getPhone();
      boolean bool1 = PhoneNumberUtils.isLocalEmergencyNumber(str1, this.mApplication);
      int m;
      CallerInfo localCallerInfo;
      String str2;
      int n;
      int i1;
      if (localConnection.isIncoming())
        if (localDisconnectCause1 == Connection.DisconnectCause.INCOMING_MISSED)
        {
          m = 3;
          localCallerInfo = getCallerInfoFromConnection(localConnection);
          str2 = getLogNumber(localConnection, localCallerInfo);
          if (DBG)
            log("- onDisconnect(): logNumber set to: xxxxxxx");
          n = getPresentation(localConnection, localCallerInfo);
          if ((localPhone.getPhoneType() == 2) && (bool1) && (this.mCurrentEmergencyToneState != 0) && (this.mEmergencyTonePlayerVibrator != null))
            this.mEmergencyTonePlayerVibrator.stop();
          boolean bool2 = this.mApplication.getResources().getBoolean(2131427340);
          if ((!TelephonyCapabilities.supportsOtasp(localPhone)) || (!localPhone.isOtaSpNumber(str1)))
            break label1203;
          i1 = 1;
          if (((bool1) && (!bool2)) || (i1 != 0))
            break label1209;
        }
      for (int i2 = 1; ; i2 = 0)
      {
        if (i2 != 0)
        {
          CallLogAsync.AddCallArgs localAddCallArgs = new CallLogAsync.AddCallArgs(this.mApplication, localCallerInfo, str2, n, m, l1, l2);
          this.mCallLog.addCall(localAddCallArgs);
        }
        if (m == 3)
          showMissedCallNotification(localConnection, l1);
        if (k != 0)
          new InCallTonePlayer(k).start();
        if (((this.mPreviousCdmaCallState != Call.State.DIALING) && (this.mPreviousCdmaCallState != Call.State.ALERTING)) || (bool1) || (localDisconnectCause1 == Connection.DisconnectCause.INCOMING_MISSED) || (localDisconnectCause1 == Connection.DisconnectCause.NORMAL) || (localDisconnectCause1 == Connection.DisconnectCause.LOCAL) || (localDisconnectCause1 == Connection.DisconnectCause.INCOMING_REJECTED))
          break;
        if (this.mIsCdmaRedialCall)
          break label1221;
        if (i != 1)
          break label1215;
        PhoneUtils.placeCall(this.mApplication, localPhone, str1, null, false, null);
        this.mIsCdmaRedialCall = true;
        return;
        label874: if (DBG)
          log("stopRing()... (onDisconnect)");
        this.mRinger.stopRing();
        break label300;
        if (DBG)
          log("stopRing()... (onDisconnect)");
        this.mRinger.stopRing();
        break label300;
        if (localDisconnectCause3 == Connection.DisconnectCause.CONGESTION)
        {
          if (DBG)
            log("- need to play CONGESTION tone!");
          j = 3;
          break label416;
        }
        if (((localDisconnectCause3 == Connection.DisconnectCause.NORMAL) || (localDisconnectCause3 == Connection.DisconnectCause.LOCAL)) && (this.mApplication.isOtaCallInActiveState()))
        {
          if (DBG)
            log("- need to play OTA_CALL_END tone!");
          j = 11;
          break label416;
        }
        if (localDisconnectCause3 == Connection.DisconnectCause.CDMA_REORDER)
        {
          if (DBG)
            log("- need to play CDMA_REORDER tone!");
          j = 6;
          break label416;
        }
        if (localDisconnectCause3 == Connection.DisconnectCause.CDMA_INTERCEPT)
        {
          if (DBG)
            log("- need to play CDMA_INTERCEPT tone!");
          j = 7;
          break label416;
        }
        if (localDisconnectCause3 == Connection.DisconnectCause.CDMA_DROP)
        {
          if (DBG)
            log("- need to play CDMA_DROP tone!");
          j = 8;
          break label416;
        }
        if (localDisconnectCause3 == Connection.DisconnectCause.OUT_OF_SERVICE)
        {
          if (DBG)
            log("- need to play OUT OF SERVICE tone!");
          j = 9;
          break label416;
        }
        if (localDisconnectCause3 == Connection.DisconnectCause.UNOBTAINABLE_NUMBER)
        {
          if (DBG)
            log("- need to play TONE_UNOBTAINABLE_NUMBER tone!");
          j = 13;
          break label416;
        }
        Connection.DisconnectCause localDisconnectCause4 = Connection.DisconnectCause.ERROR_UNSPECIFIED;
        j = 0;
        if (localDisconnectCause3 != localDisconnectCause4)
          break label416;
        if (DBG)
          log("- DisconnectCause is ERROR_UNSPECIFIED: play TONE_CALL_ENDED!");
        j = 4;
        break label416;
        if ((localDisconnectCause1 == Connection.DisconnectCause.INCOMING_REJECTED) && (PhoneUtils.PhoneSettings.markRejectedCallsAsMissed(this.mApplication)))
        {
          m = 3;
          break label581;
        }
        m = 1;
        break label581;
        m = 2;
        break label581;
        i1 = 0;
        break label694;
      }
      this.mIsCdmaRedialCall = false;
      return;
      this.mIsCdmaRedialCall = false;
      return;
    }
  }

  private void onDisplayInfo(AsyncResult paramAsyncResult)
  {
    CdmaInformationRecords.CdmaDisplayInfoRec localCdmaDisplayInfoRec = (CdmaInformationRecords.CdmaDisplayInfoRec)paramAsyncResult.result;
    if (localCdmaDisplayInfoRec != null)
    {
      String str = localCdmaDisplayInfoRec.alpha;
      if (DBG)
        log("onDisplayInfo: displayInfo=" + str);
      CdmaDisplayInfo.displayInfoRecord(this.mApplication, str);
      sendEmptyMessageDelayed(24, 2000L);
    }
  }

  private void onMwiChanged(boolean paramBoolean)
  {
    int i = 1;
    if (!PhoneGlobals.sVoiceCapable)
      Log.w("CallNotifier", "Got onMwiChanged() on non-voice-capable device! Ignoring...");
    while (true)
    {
      return;
      boolean bool = this.mApplication.getResources().getBoolean(2131427343);
      if (Settings.System.getInt(PhoneGlobals.getPhone().getContext().getContentResolver(), "enable_mwi_notification", 0) == i);
      while ((!bool) || (i != 0))
      {
        this.mApplication.notificationMgr.updateMwi(paramBoolean);
        return;
        i = 0;
      }
    }
  }

  private void onNewRingingConnection(AsyncResult paramAsyncResult)
  {
    Connection localConnection = (Connection)paramAsyncResult.result;
    log("onNewRingingConnection(): state = " + this.mCM.getState() + ", conn = { " + localConnection + " }");
    Call localCall = localConnection.getCall();
    Phone localPhone = localCall.getPhone();
    if (ignoreAllIncomingCalls(localPhone))
    {
      PhoneUtils.hangupRingingCall(localCall);
      return;
    }
    if (!localConnection.isRinging())
    {
      Log.i("CallNotifier", "CallNotifier.onNewRingingConnection(): connection not ringing!");
      return;
    }
    String str = localConnection.getAddress();
    if (TextUtils.isEmpty(str))
      str = "0000";
    if (DBG)
      log("Incoming number is: " + str);
    int i = this.mApplication.blackList.isListed(str);
    if (i != 0)
    {
      if (DBG)
        log("Incoming call from " + str + " blocked.");
      localConnection.setUserData("Blacklist");
      try
      {
        localConnection.hangup();
        this.mApplication.notificationMgr.notifyBlacklistedCall(str, localConnection.getCreateTime(), i);
        return;
      }
      catch (CallStateException localCallStateException)
      {
        localCallStateException.printStackTrace();
        return;
      }
    }
    if ((localPhone.getPhoneType() == 1) && (this.mNextGsmCallIsForwarded))
    {
      this.mForwardedCalls.add(localConnection);
      this.mNextGsmCallIsForwarded = false;
    }
    stopSignalInfoTone();
    Call.State localState = localConnection.getState();
    this.mApplication.requestWakeState(PhoneGlobals.WakeState.PARTIAL);
    if (PhoneUtils.isRealIncomingCall(localState))
    {
      startIncomingCallQuery(localConnection);
      return;
    }
    if (PhoneUtils.PhoneSettings.vibCallWaiting(this.mApplication))
      vibrate(200, 300, 500);
    if (this.mCallWaitingTonePlayer == null)
    {
      this.mCallWaitingTonePlayer = new InCallTonePlayer(1);
      this.mCallWaitingTonePlayer.start();
    }
    if (DBG)
      log("- showing incoming call (this is a WAITING call)...");
    showIncomingCall();
  }

  private void onPhoneStateChanged(AsyncResult paramAsyncResult)
  {
    PhoneConstants.State localState = this.mCM.getState();
    NotificationMgr.StatusBarHelper localStatusBarHelper = this.mApplication.notificationMgr.statusBarHelper;
    boolean bool;
    Phone localPhone;
    Call.State localState1;
    if (localState == PhoneConstants.State.IDLE)
    {
      bool = true;
      localStatusBarHelper.enableNotificationAlerts(bool);
      localPhone = this.mCM.getFgPhone();
      if (localPhone.getPhoneType() == 2)
      {
        if ((localPhone.getForegroundCall().getState() == Call.State.ACTIVE) && ((this.mPreviousCdmaCallState == Call.State.DIALING) || (this.mPreviousCdmaCallState == Call.State.ALERTING)))
        {
          if (this.mIsCdmaRedialCall)
            new InCallTonePlayer(10).start();
          stopSignalInfoTone();
        }
        this.mPreviousCdmaCallState = localPhone.getForegroundCall().getState();
      }
      this.mApplication.updateBluetoothIndication(false);
      this.mApplication.updatePhoneState(localState);
      if (localState == PhoneConstants.State.OFFHOOK)
      {
        if (this.mCallWaitingTonePlayer != null)
        {
          this.mCallWaitingTonePlayer.stopTone();
          this.mCallWaitingTonePlayer = null;
        }
        Call localCall = PhoneUtils.getCurrentCall(localPhone);
        Connection localConnection2 = PhoneUtils.getConnection(localPhone, localCall);
        if ((localCall.getState() == Call.State.ACTIVE) && (!localConnection2.isIncoming()))
        {
          long l = localConnection2.getDurationMillis();
          if ((PhoneUtils.PhoneSettings.vibOutgoing(this.mApplication)) && (l < 200L))
            vibrate(100, 0, 0);
          if (PhoneUtils.PhoneSettings.vibOn45Secs(this.mApplication))
            start45SecondVibration(l % 60000L);
          this.mWaitingCalls.remove(localConnection2);
        }
        PhoneUtils.setAudioMode(this.mCM);
        if (!this.mApplication.isShowingCallScreen())
          this.mApplication.requestWakeState(PhoneGlobals.WakeState.SLEEP);
        if (DBG)
          log("stopRing()... (OFFHOOK state)");
        this.mRinger.stopRing();
        if (DBG)
          log("- posting UPDATE_IN_CALL_NOTIFICATION request...");
        removeMessages(27);
        sendEmptyMessageDelayed(27, 1000L);
      }
      if (localPhone.getPhoneType() == 2)
      {
        Connection localConnection1 = localPhone.getForegroundCall().getLatestConnection();
        if ((localConnection1 != null) && (PhoneNumberUtils.isLocalEmergencyNumber(localConnection1.getAddress(), this.mApplication)))
        {
          localState1 = localPhone.getForegroundCall().getState();
          if (this.mEmergencyTonePlayerVibrator == null)
            this.mEmergencyTonePlayerVibrator = new EmergencyTonePlayerVibrator();
          if ((localState1 != Call.State.DIALING) && (localState1 != Call.State.ALERTING))
            break label545;
          this.mIsEmergencyToneOn = Settings.Global.getInt(this.mApplication.getContentResolver(), "emergency_tone", 0);
          if ((this.mIsEmergencyToneOn != 0) && (this.mCurrentEmergencyToneState == 0) && (this.mEmergencyTonePlayerVibrator != null))
            this.mEmergencyTonePlayerVibrator.start();
        }
      }
    }
    while (true)
    {
      if (((localPhone.getPhoneType() == 1) || (localPhone.getPhoneType() == 3)) && (!this.mCM.getActiveFgCallState().isDialing()) && (this.mInCallRingbackTonePlayer != null))
      {
        this.mInCallRingbackTonePlayer.stopTone();
        this.mInCallRingbackTonePlayer = null;
      }
      return;
      bool = false;
      break;
      label545: if ((localState1 == Call.State.ACTIVE) && (this.mCurrentEmergencyToneState != 0) && (this.mEmergencyTonePlayerVibrator != null))
        this.mEmergencyTonePlayerVibrator.stop();
    }
  }

  private void onResendMute()
  {
    boolean bool1 = PhoneUtils.getMute();
    if (!bool1);
    for (boolean bool2 = true; ; bool2 = false)
    {
      PhoneUtils.setMute(bool2);
      PhoneUtils.setMute(bool1);
      return;
    }
  }

  private void onRingbackTone(AsyncResult paramAsyncResult)
  {
    if (((Boolean)paramAsyncResult.result).booleanValue() == true)
      if ((this.mCM.getActiveFgCallState().isDialing()) && (this.mInCallRingbackTonePlayer == null))
      {
        this.mInCallRingbackTonePlayer = new InCallTonePlayer(12);
        this.mInCallRingbackTonePlayer.start();
      }
    while (this.mInCallRingbackTonePlayer == null)
      return;
    this.mInCallRingbackTonePlayer.stopTone();
    this.mInCallRingbackTonePlayer = null;
  }

  private void onSignalInfo(AsyncResult paramAsyncResult)
  {
    if (!PhoneGlobals.sVoiceCapable)
      Log.w("CallNotifier", "Got onSignalInfo() on non-voice-capable device! Ignoring...");
    CdmaInformationRecords.CdmaSignalInfoRec localCdmaSignalInfoRec;
    boolean bool;
    do
    {
      do
      {
        return;
        if (PhoneUtils.isRealIncomingCall(this.mCM.getFirstActiveRingingCall().getState()))
        {
          stopSignalInfoTone();
          return;
        }
        localCdmaSignalInfoRec = (CdmaInformationRecords.CdmaSignalInfoRec)paramAsyncResult.result;
      }
      while (localCdmaSignalInfoRec == null);
      bool = localCdmaSignalInfoRec.isPresent;
      if (DBG)
        log("onSignalInfo: isPresent=" + bool);
    }
    while (!bool);
    int i = localCdmaSignalInfoRec.signalType;
    int j = localCdmaSignalInfoRec.alertPitch;
    int k = localCdmaSignalInfoRec.signal;
    if (DBG)
      log("onSignalInfo: uSignalType=" + i + ", uAlertPitch=" + j + ", uSignal=" + k);
    new SignalInfoTonePlayer(SignalToneUtil.getAudioToneFromSignalInfo(i, j, k)).start();
  }

  private void onSuppServiceNotification(AsyncResult paramAsyncResult)
  {
    SuppServiceNotification localSuppServiceNotification = (SuppServiceNotification)paramAsyncResult.result;
    Phone localPhone = PhoneUtils.getGsmPhone(this.mCM);
    if (DBG)
      log("SS Notification: " + localSuppServiceNotification);
    if (localSuppServiceNotification.notificationType == 1)
      if ((localSuppServiceNotification.code == 0) || (localSuppServiceNotification.code == 9))
      {
        Call localCall2 = localPhone.getRingingCall();
        if (localCall2.getState().isRinging())
          this.mForwardedCalls.add(PhoneUtils.getConnection(localPhone, localCall2));
      }
      else
      {
        if (localSuppServiceNotification.code != 2)
          break label194;
        Call localCall4 = PhoneUtils.getCurrentCall(localPhone);
        if (localCall4.getState() == Call.State.ACTIVE)
          this.mWaitingCalls.add(PhoneUtils.getConnection(localPhone, localCall4));
      }
    while (true)
    {
      this.mApplication.updateInCallScreen();
      int i = getSuppServiceToastTextResId(localSuppServiceNotification);
      if (i >= 0)
        Toast.makeText(this.mApplication, this.mApplication.getString(i), 1).show();
      return;
      this.mNextGsmCallIsForwarded = true;
      break;
      label194: if (localSuppServiceNotification.code == 3)
      {
        Call localCall3 = PhoneUtils.getCurrentCall(localPhone);
        this.mWaitingCalls.remove(PhoneUtils.getConnection(localPhone, localCall3));
        continue;
        if ((localSuppServiceNotification.notificationType == 0) && (localSuppServiceNotification.code == 3))
        {
          Call localCall1 = PhoneUtils.getCurrentCall(localPhone);
          if (localCall1.getState().isDialing())
            this.mWaitingCalls.add(PhoneUtils.getConnection(localPhone, localCall1));
        }
      }
    }
  }

  private void onUnknownConnectionAppeared(AsyncResult paramAsyncResult)
  {
    if (this.mCM.getState() == PhoneConstants.State.OFFHOOK)
    {
      onPhoneStateChanged(paramAsyncResult);
      if (DBG)
        log("- showing incoming call (unknown connection appeared)...");
      showIncomingCall();
    }
  }

  private void registerForNotifications()
  {
    this.mCM.registerForNewRingingConnection(this, 2, null);
    this.mCM.registerForPreciseCallStateChanged(this, 1, null);
    this.mCM.registerForDisconnect(this, 3, null);
    this.mCM.registerForUnknownConnection(this, 4, null);
    this.mCM.registerForIncomingRing(this, 5, null);
    this.mCM.registerForCdmaOtaStatusChange(this, 25, null);
    this.mCM.registerForCallWaiting(this, 8, null);
    this.mCM.registerForDisplayInfo(this, 6, null);
    this.mCM.registerForSignalInfo(this, 7, null);
    this.mCM.registerForInCallVoicePrivacyOn(this, 9, null);
    this.mCM.registerForInCallVoicePrivacyOff(this, 10, null);
    this.mCM.registerForRingbackTone(this, 11, null);
    this.mCM.registerForResendIncallMute(this, 12, null);
    this.mCM.registerForSuppServiceNotification(this, 29, null);
  }

  private void resetAudioStateAfterDisconnect()
  {
    if (this.mBluetoothHeadset != null)
      this.mBluetoothHeadset.disconnectAudio();
    PhoneUtils.turnOnSpeaker(this.mApplication, false, true);
    PhoneUtils.setAudioMode(this.mCM);
  }

  private void showIncomingCall()
  {
    log("showIncomingCall()...  phone state = " + this.mCM.getState());
    try
    {
      ActivityManagerNative.getDefault().closeSystemDialogs("call");
      label41: this.mApplication.requestWakeState(PhoneGlobals.WakeState.FULL);
      if (DBG)
        log("- updating notification from showIncomingCall()...");
      this.mApplication.notificationMgr.updateNotificationAndLaunchIncomingCallUi();
      return;
    }
    catch (RemoteException localRemoteException)
    {
      break label41;
    }
  }

  private void showMissedCallNotification(Connection paramConnection, long paramLong)
  {
    PhoneUtils.CallerInfoToken localCallerInfoToken = PhoneUtils.startGetCallerInfo(this.mApplication, paramConnection, this, Long.valueOf(paramLong));
    if (localCallerInfoToken != null)
    {
      CallerInfo localCallerInfo;
      String str1;
      String str2;
      if (localCallerInfoToken.isFinal)
      {
        localCallerInfo = localCallerInfoToken.currentInfo;
        str1 = localCallerInfo.name;
        str2 = localCallerInfo.phoneNumber;
        if (localCallerInfo.numberPresentation != PhoneConstants.PRESENTATION_RESTRICTED)
          break label103;
        str1 = this.mApplication.getString(2131296346);
      }
      while (true)
      {
        this.mApplication.notificationMgr.notifyMissedCall(str1, str2, localCallerInfo.phoneLabel, localCallerInfo.cachedPhoto, localCallerInfo.cachedPhotoIcon, paramLong);
        return;
        label103: if (localCallerInfo.numberPresentation != PhoneConstants.PRESENTATION_ALLOWED)
          str1 = this.mApplication.getString(2131296345);
        else
          str2 = PhoneUtils.modifyForSpecialCnapCases(this.mApplication, localCallerInfo, str2, localCallerInfo.numberPresentation);
      }
    }
    Log.w("CallNotifier", "showMissedCallNotification: got null CallerInfo for Connection " + paramConnection);
  }

  private void start45SecondVibration(long paramLong)
  {
    removeMessages(28);
    if (paramLong > 45000L);
    for (long l = 105000L - paramLong; ; l = 45000L - paramLong)
    {
      sendEmptyMessageDelayed(28, l);
      return;
    }
  }

  private void startIncomingCallQuery(Connection paramConnection)
  {
    synchronized (this.mCallerInfoQueryStateGuard)
    {
      int i = this.mCallerInfoQueryState;
      int j = 0;
      if (i == 0)
      {
        this.mCallerInfoQueryState = -1;
        j = 1;
      }
      if (j == 0)
        break label102;
      this.mRinger.setCustomRingtoneUri(Settings.System.DEFAULT_RINGTONE_URI);
      PhoneUtils.CallerInfoToken localCallerInfoToken = PhoneUtils.startGetCallerInfo(this.mApplication, paramConnection, this, this);
      if (localCallerInfoToken.isFinal)
      {
        onQueryComplete(0, this, localCallerInfoToken.currentInfo);
        return;
      }
    }
    sendMessageDelayed(Message.obtain(this, 100, paramConnection.getAddress()), 500L);
    return;
    label102: EventLog.writeEvent(70305, new Object[0]);
    this.mRinger.ring();
    if (DBG)
      log("- showing incoming call (couldn't start query)...");
    showIncomingCall();
  }

  boolean getIsCdmaRedialCall()
  {
    return this.mIsCdmaRedialCall;
  }

  Call.State getPreviousCdmaCallState()
  {
    return this.mPreviousCdmaCallState;
  }

  boolean getVoicePrivacyState()
  {
    return this.mVoicePrivacyState;
  }

  public void handleMessage(Message paramMessage)
  {
    switch (paramMessage.what)
    {
    default:
    case 2:
    case 5:
    case 1:
    case 3:
    case 4:
    case 100:
    case 21:
    case 8:
    case 26:
    case 22:
    case 23:
    case 6:
    case 7:
    case 24:
    case 25:
    case 9:
    case 10:
      do
      {
        do
        {
          do
          {
            do
            {
              return;
              log("RINGING... (new)");
              onNewRingingConnection((AsyncResult)paramMessage.obj);
              this.mSilentRingerRequested = false;
              return;
            }
            while ((paramMessage.obj == null) || (((AsyncResult)paramMessage.obj).result == null));
            if ((((PhoneBase)((AsyncResult)paramMessage.obj).result).getState() == PhoneConstants.State.RINGING) && (!this.mSilentRingerRequested))
            {
              if (DBG)
                log("RINGING... (PHONE_INCOMING_RING event)");
              this.mRinger.ring();
              return;
            }
          }
          while (!DBG);
          log("RING before NEW_RING, skipping");
          return;
          onPhoneStateChanged((AsyncResult)paramMessage.obj);
          return;
          if (DBG)
            log("DISCONNECT");
          onDisconnect((AsyncResult)paramMessage.obj);
          return;
          onUnknownConnectionAppeared((AsyncResult)paramMessage.obj);
          return;
          onCustomRingtoneQueryTimeout((String)paramMessage.obj);
          return;
          onMwiChanged(this.mApplication.phone.getMessageWaitingIndicator());
          return;
          if (DBG)
            log("Received PHONE_CDMA_CALL_WAITING event");
          onCdmaCallWaiting((AsyncResult)paramMessage.obj);
          return;
          Log.i("CallNotifier", "Received CDMA_CALL_WAITING_REJECT event");
          onCdmaCallWaitingReject();
          return;
          Log.i("CallNotifier", "Received CALLWAITING_CALLERINFO_DISPLAY_DONE event");
          this.mCallWaitingTimeOut = true;
          onCdmaCallWaitingReject();
          return;
          if (DBG)
            log("Received CALLWAITING_ADDCALL_DISABLE_TIMEOUT event ...");
          this.mApplication.cdmaPhoneCallState.setAddCallMenuStateAfterCallWaiting(true);
          this.mApplication.updateInCallScreen();
          return;
          if (DBG)
            log("Received PHONE_STATE_DISPLAYINFO event");
          onDisplayInfo((AsyncResult)paramMessage.obj);
          return;
          if (DBG)
            log("Received PHONE_STATE_SIGNALINFO event");
          onSignalInfo((AsyncResult)paramMessage.obj);
          return;
          if (DBG)
            log("Received Display Info notification done event ...");
          CdmaDisplayInfo.dismissDisplayInfoRecord();
          return;
          if (DBG)
            log("EVENT_OTA_PROVISION_CHANGE...");
          this.mApplication.handleOtaspEvent(paramMessage);
          return;
          if (DBG)
            log("PHONE_ENHANCED_VP_ON...");
        }
        while (this.mVoicePrivacyState);
        new InCallTonePlayer(5).start();
        this.mVoicePrivacyState = true;
        if (DBG)
          log("- updating notification for VP state...");
        this.mApplication.notificationMgr.updateInCallNotification();
        return;
        if (DBG)
          log("PHONE_ENHANCED_VP_OFF...");
      }
      while (!this.mVoicePrivacyState);
      new InCallTonePlayer(5).start();
      this.mVoicePrivacyState = false;
      if (DBG)
        log("- updating notification for VP state...");
      this.mApplication.notificationMgr.updateInCallNotification();
      return;
    case 11:
      onRingbackTone((AsyncResult)paramMessage.obj);
      return;
    case 12:
      onResendMute();
      return;
    case 27:
      this.mApplication.notificationMgr.updateInCallNotification();
      return;
    case 28:
      vibrate(70, 0, 0);
      sendEmptyMessageDelayed(28, 60000L);
      return;
    case 29:
    }
    if (DBG)
      log("Received Supplementary Notification");
    onSuppServiceNotification((AsyncResult)paramMessage.obj);
  }

  public boolean isCallForwarded(Call paramCall)
  {
    Iterator localIterator = this.mForwardedCalls.iterator();
    while (localIterator.hasNext())
      if (paramCall.hasConnection((Connection)localIterator.next()))
        return true;
    return false;
  }

  public boolean isCallWaiting(Call paramCall)
  {
    Iterator localIterator = this.mWaitingCalls.iterator();
    while (localIterator.hasNext())
      if (paramCall.hasConnection((Connection)localIterator.next()))
        return true;
    return false;
  }

  boolean isRinging()
  {
    return this.mRinger.isRinging();
  }

  public void onQueryComplete(int paramInt, Object paramObject, CallerInfo paramCallerInfo)
  {
    if ((paramObject instanceof Long))
      this.mApplication.notificationMgr.notifyMissedCall(paramCallerInfo.name, paramCallerInfo.phoneNumber, paramCallerInfo.phoneLabel, paramCallerInfo.cachedPhoto, paramCallerInfo.cachedPhotoIcon, ((Long)paramObject).longValue());
    while (true)
    {
      return;
      if ((paramObject instanceof CallNotifier))
      {
        removeMessages(100);
        synchronized (this.mCallerInfoQueryStateGuard)
        {
          int i = this.mCallerInfoQueryState;
          int j = 0;
          if (i == -1)
          {
            this.mCallerInfoQueryState = 0;
            j = 1;
          }
          if (j != 0)
            if (paramCallerInfo.shouldSendToVoicemail)
            {
              if (DBG)
                log("send to voicemail flag detected. hanging up.");
              PhoneUtils.hangupRingingCall(this.mCM.getFirstActiveRingingCall());
              return;
            }
        }
      }
    }
    if (paramCallerInfo.contactRingtoneUri != null)
    {
      if (DBG)
        log("custom ringtone found, setting up ringer.");
      ((CallNotifier)paramObject).mRinger.setCustomRingtoneUri(paramCallerInfo.contactRingtoneUri);
    }
    onCustomRingQueryComplete();
  }

  void restartRinger()
  {
    if (DBG)
      log("restartRinger()...");
    if (isRinging());
    Call localCall;
    do
    {
      return;
      localCall = this.mCM.getFirstActiveRingingCall();
      if (DBG)
        log("- ringingCall state: " + localCall.getState());
    }
    while (localCall.getState() != Call.State.INCOMING);
    this.mRinger.ring();
  }

  void sendCdmaCallWaitingReject()
  {
    sendEmptyMessage(26);
  }

  void sendMwiChangedDelayed(long paramLong)
  {
    sendMessageDelayed(Message.obtain(this, 21), paramLong);
  }

  void silenceRinger()
  {
    this.mSilentRingerRequested = true;
    if (DBG)
      log("stopRing()... (silenceRinger)");
    this.mRinger.stopRing();
  }

  void stopSignalInfoTone()
  {
    if (DBG)
      log("stopSignalInfoTone: Stopping SignalInfo tone player");
    new SignalInfoTonePlayer(98).start();
  }

  void updateCallNotifierRegistrationsAfterRadioTechnologyChange()
  {
    if (DBG)
      Log.d("CallNotifier", "updateCallNotifierRegistrationsAfterRadioTechnologyChange...");
    this.mCM.unregisterForNewRingingConnection(this);
    this.mCM.unregisterForPreciseCallStateChanged(this);
    this.mCM.unregisterForDisconnect(this);
    this.mCM.unregisterForUnknownConnection(this);
    this.mCM.unregisterForIncomingRing(this);
    this.mCM.unregisterForCallWaiting(this);
    this.mCM.unregisterForDisplayInfo(this);
    this.mCM.unregisterForSignalInfo(this);
    this.mCM.unregisterForCdmaOtaStatusChange(this);
    this.mCM.unregisterForRingbackTone(this);
    this.mCM.unregisterForResendIncallMute(this);
    this.mCM.unregisterForSuppServiceNotification(this);
    if (this.mSignalInfoToneGenerator != null)
    {
      this.mSignalInfoToneGenerator.release();
      this.mSignalInfoToneGenerator = null;
    }
    this.mInCallRingbackTonePlayer = null;
    this.mCallWaitingTonePlayer = null;
    this.mCM.unregisterForInCallVoicePrivacyOn(this);
    this.mCM.unregisterForInCallVoicePrivacyOff(this);
    createSignalInfoToneGenerator();
    registerForNotifications();
  }

  public void vibrate(int paramInt1, int paramInt2, int paramInt3)
  {
    long[] arrayOfLong = new long[4];
    arrayOfLong[0] = 0L;
    arrayOfLong[1] = paramInt1;
    arrayOfLong[2] = paramInt2;
    arrayOfLong[3] = paramInt3;
    this.mVibrator.vibrate(arrayOfLong, -1);
  }

  private class EmergencyTonePlayerVibrator
  {
    private final int EMG_VIBRATE_LENGTH = 1000;
    private final int EMG_VIBRATE_PAUSE = 1000;
    private Vibrator mEmgVibrator = new SystemVibrator();
    private int mInCallVolume;
    private ToneGenerator mToneGenerator;
    private final long[] mVibratePattern = { 1000L, 1000L };

    public EmergencyTonePlayerVibrator()
    {
    }

    private void start()
    {
      int i = CallNotifier.this.mAudioManager.getRingerMode();
      if ((CallNotifier.this.mIsEmergencyToneOn == 1) && (i == 2))
      {
        CallNotifier.this.log("EmergencyTonePlayerVibrator.start(): emergency tone...");
        this.mToneGenerator = new ToneGenerator(0, 100);
        if (this.mToneGenerator != null)
        {
          this.mInCallVolume = CallNotifier.this.mAudioManager.getStreamVolume(0);
          CallNotifier.this.mAudioManager.setStreamVolume(0, CallNotifier.this.mAudioManager.getStreamMaxVolume(0), 0);
          this.mToneGenerator.startTone(92);
        }
      }
      do
      {
        CallNotifier.access$1302(CallNotifier.this, 1);
        do
          return;
        while (CallNotifier.this.mIsEmergencyToneOn != 2);
        CallNotifier.this.log("EmergencyTonePlayerVibrator.start(): emergency vibrate...");
      }
      while (this.mEmgVibrator == null);
      this.mEmgVibrator.vibrate(this.mVibratePattern, 0);
      CallNotifier.access$1302(CallNotifier.this, 2);
    }

    private void stop()
    {
      if ((CallNotifier.this.mCurrentEmergencyToneState == 1) && (this.mToneGenerator != null))
      {
        this.mToneGenerator.stopTone();
        this.mToneGenerator.release();
        CallNotifier.this.mAudioManager.setStreamVolume(0, this.mInCallVolume, 0);
      }
      while (true)
      {
        CallNotifier.access$1302(CallNotifier.this, 0);
        return;
        if ((CallNotifier.this.mCurrentEmergencyToneState == 2) && (this.mEmgVibrator != null))
          this.mEmgVibrator.cancel();
      }
    }
  }

  private class InCallTonePlayer extends Thread
  {
    private int mState;
    private int mToneId;

    InCallTonePlayer(int arg2)
    {
      int i;
      this.mToneId = i;
      this.mState = 0;
    }

    public void run()
    {
      CallNotifier.this.log("InCallTonePlayer.run(toneId = " + this.mToneId + ")...");
      int i = CallNotifier.this.mCM.getFgPhone().getPhoneType();
      int j;
      int k;
      int m;
      switch (this.mToneId)
      {
      default:
        throw new IllegalArgumentException("Bad toneId: " + this.mToneId);
      case 1:
        j = 22;
        k = 80;
        m = 2147483627;
      case 2:
      case 3:
      case 4:
      case 11:
      case 5:
      case 6:
      case 7:
      case 8:
      case 9:
      case 10:
      case 12:
      case 13:
      }
      try
      {
        if (CallNotifier.this.mBluetoothHeadset != null)
        {
          boolean bool = CallNotifier.this.mBluetoothHeadset.isAudioOn();
          i3 = 0;
          if (bool)
            i3 = 6;
          localToneGenerator = new ToneGenerator(i3, k);
          n = 1;
          if (localToneGenerator != null)
          {
            i1 = CallNotifier.this.mAudioManager.getRingerMode();
            if (i != 2)
              break label814;
            if (j != 93)
              break label659;
            i2 = 0;
            if (i1 != 0)
            {
              i2 = 0;
              if (i1 != 1)
              {
                if (CallNotifier.DBG)
                  CallNotifier.this.log("- InCallTonePlayer: start playing call tone=" + j);
                i2 = 1;
                n = 0;
              }
            }
            if (i2 == 0);
          }
        }
      }
      catch (RuntimeException localRuntimeException)
      {
        try
        {
          while (true)
          {
            int i3;
            ToneGenerator localToneGenerator;
            int n;
            int i1;
            int i2;
            long l;
            if (this.mState != 2)
            {
              this.mState = 1;
              localToneGenerator.startTone(j);
              l = m + 20;
            }
            try
            {
              wait(l);
              if (n != 0)
                localToneGenerator.stopTone();
              localToneGenerator.release();
              this.mState = 0;
              if (CallNotifier.this.mCM.getState() == PhoneConstants.State.IDLE)
                CallNotifier.this.resetAudioStateAfterDisconnect();
              return;
              if (i == 2)
              {
                j = 96;
                k = 50;
                m = 1000;
                continue;
              }
              if ((i == 1) || (i == 3))
              {
                j = 17;
                k = 80;
                m = 4000;
                continue;
              }
              throw new IllegalStateException("Unexpected phone type: " + i);
              j = 18;
              k = 80;
              m = 4000;
              continue;
              j = 27;
              k = 80;
              m = 200;
              continue;
              if (CallNotifier.this.mApplication.cdmaOtaConfigData.otaPlaySuccessFailureTone == 1)
              {
                j = 93;
                k = 80;
                m = 750;
                continue;
              }
              j = 27;
              k = 80;
              m = 200;
              continue;
              j = 86;
              k = 80;
              m = 5000;
              continue;
              j = 38;
              k = 80;
              m = 4000;
              continue;
              j = 37;
              k = 50;
              m = 500;
              continue;
              j = 95;
              k = 50;
              m = 375;
              continue;
              j = 87;
              k = 50;
              m = 5000;
              continue;
              j = 23;
              k = 80;
              m = 2147483627;
              continue;
              j = 21;
              k = 80;
              m = 4000;
              continue;
              i3 = 0;
              continue;
              localRuntimeException = localRuntimeException;
              Log.w("CallNotifier", "InCallTonePlayer: Exception caught while creating ToneGenerator: " + localRuntimeException);
              localToneGenerator = null;
              continue;
              label659: if ((j == 96) || (j == 38) || (j == 39) || (j == 37) || (j == 95))
              {
                i2 = 0;
                if (i1 == 0)
                  continue;
                if (CallNotifier.DBG)
                  CallNotifier.this.log("InCallTonePlayer:playing call fail tone:" + j);
                i2 = 1;
                n = 0;
                continue;
              }
              if ((j == 87) || (j == 86))
              {
                i2 = 0;
                if (i1 == 0)
                  continue;
                i2 = 0;
                if (i1 == 1)
                  continue;
                if (CallNotifier.DBG)
                  CallNotifier.this.log("InCallTonePlayer:playing tone for toneType=" + j);
                i2 = 1;
                n = 0;
                continue;
              }
              i2 = 1;
              continue;
              label814: i2 = 1;
            }
            catch (InterruptedException localInterruptedException)
            {
              while (true)
                Log.w("CallNotifier", "InCallTonePlayer stopped: " + localInterruptedException);
            }
          }
        }
        finally
        {
        }
      }
    }

    public void stopTone()
    {
      try
      {
        if (this.mState == 1)
          notify();
        this.mState = 2;
        return;
      }
      finally
      {
      }
    }
  }

  private class SignalInfoTonePlayer extends Thread
  {
    private int mToneId;

    SignalInfoTonePlayer(int arg2)
    {
      int i;
      this.mToneId = i;
    }

    public void run()
    {
      CallNotifier.this.log("SignalInfoTonePlayer.run(toneId = " + this.mToneId + ")...");
      if (CallNotifier.this.mSignalInfoToneGenerator != null)
      {
        CallNotifier.this.mSignalInfoToneGenerator.stopTone();
        CallNotifier.this.mSignalInfoToneGenerator.startTone(this.mToneId);
      }
    }
  }
}

/* Location:           C:\Users\Win-7\Desktop\classes-dex2jar.jar
 * Qualified Name:     com.android.phone.CallNotifier
 * JD-Core Version:    0.6.2
 */