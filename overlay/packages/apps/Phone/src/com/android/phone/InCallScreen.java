package com.android.phone;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UpdateLock;
import android.provider.Settings.Global;
import android.text.method.DialerKeyListener;
import android.util.EventLog;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.DisconnectCause;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.MmiCode.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.Phone.SuppService;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import java.util.Iterator;
import java.util.List;

public class InCallScreen extends Activity
  implements View.OnClickListener
{
  private static final boolean DBG = false;
  private PhoneGlobals mApp;
  private BluetoothAdapter mBluetoothAdapter;
  private boolean mBluetoothConnectionPending;
  private long mBluetoothConnectionRequestTime;
  private BluetoothHeadset mBluetoothHeadset;
  private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener = new BluetoothProfile.ServiceListener()
  {
    public void onServiceConnected(int paramAnonymousInt, BluetoothProfile paramAnonymousBluetoothProfile)
    {
      InCallScreen.access$2402(InCallScreen.this, (BluetoothHeadset)paramAnonymousBluetoothProfile);
    }

    public void onServiceDisconnected(int paramAnonymousInt)
    {
      InCallScreen.access$2402(InCallScreen.this, null);
    }
  };
  private CallManager mCM;
  private CallCard mCallCard;
  private AlertDialog mCallLostDialog;
  private DTMFTwelveKeyDialer mDialer;
  private AlertDialog mExitingECMDialog;
  private int mFlipAction;
  private final ResettableSensorEventListener mFlipListener = new ResettableSensorEventListener()
  {
    private int mSampleIndex;
    private boolean[] mSamples = new boolean[3];
    private boolean mStopped;
    private boolean mWasFaceUp;

    private boolean filterSamples()
    {
      int i = 0;
      boolean[] arrayOfBoolean = this.mSamples;
      int j = arrayOfBoolean.length;
      for (int k = 0; k < j; k++)
        if (arrayOfBoolean[k] != 0)
          i++;
      return i >= 2;
    }

    public void onAccuracyChanged(Sensor paramAnonymousSensor, int paramAnonymousInt)
    {
    }

    public void onSensorChanged(SensorEvent paramAnonymousSensorEvent)
    {
      float f = paramAnonymousSensorEvent.values[2];
      if (this.mStopped)
        return;
      if (!this.mWasFaceUp)
      {
        boolean[] arrayOfBoolean2 = this.mSamples;
        int k = this.mSampleIndex;
        if (f > 7.0F);
        for (int m = 1; ; m = 0)
        {
          arrayOfBoolean2[k] = m;
          if (!filterSamples())
            break;
          this.mWasFaceUp = true;
          for (int n = 0; n < 3; n++)
            this.mSamples[n] = false;
        }
      }
      boolean[] arrayOfBoolean1 = this.mSamples;
      int i = this.mSampleIndex;
      boolean bool = f < -7.0F;
      int j = 0;
      if (bool)
        j = 1;
      arrayOfBoolean1[i] = j;
      if (filterSamples())
      {
        this.mStopped = true;
        InCallScreen.this.handleAction(InCallScreen.this.mFlipAction);
      }
      this.mSampleIndex = ((1 + this.mSampleIndex) % 3);
    }

    public void reset()
    {
      this.mWasFaceUp = false;
      this.mStopped = false;
      for (int i = 0; i < 3; i++)
        this.mSamples[i] = false;
    }
  };
  private AlertDialog mGenericErrorDialog;
  private Handler mHandler = new Handler()
  {
    public void handleMessage(Message paramAnonymousMessage)
    {
      if (InCallScreen.this.mIsDestroyed)
        if (InCallScreen.DBG)
          InCallScreen.this.log("Handler: ignoring message " + paramAnonymousMessage + "; we're destroyed!");
      do
      {
        do
        {
          do
          {
            do
            {
              do
              {
                return;
                if ((!InCallScreen.this.mIsForegroundActivity) && (InCallScreen.DBG))
                  InCallScreen.this.log("Handler: handling message " + paramAnonymousMessage + " while not in foreground");
                switch (paramAnonymousMessage.what)
                {
                default:
                  Log.wtf("InCallScreen", "mHandler: unexpected message: " + paramAnonymousMessage);
                  return;
                case 110:
                  InCallScreen.this.onSuppServiceFailed((AsyncResult)paramAnonymousMessage.obj);
                  return;
                case 125:
                  InCallScreen.this.onSuppServiceNotification((AsyncResult)paramAnonymousMessage.obj);
                  return;
                case 101:
                  InCallScreen.this.onPhoneStateChanged((AsyncResult)paramAnonymousMessage.obj);
                  return;
                case 102:
                  InCallScreen.this.onDisconnect((AsyncResult)paramAnonymousMessage.obj);
                  return;
                case 103:
                  InCallScreen.this.updateScreen();
                  InCallScreen.this.mInCallTouchUi.refreshAudioModePopup();
                  return;
                case 53:
                  InCallScreen.this.onMMICancel();
                  return;
                case 52:
                  InCallScreen.this.onMMIComplete((MmiCode)((AsyncResult)paramAnonymousMessage.obj).result);
                  return;
                case 104:
                  InCallScreen.this.handlePostOnDialChars((AsyncResult)paramAnonymousMessage.obj, (char)paramAnonymousMessage.arg1);
                  return;
                case 106:
                  InCallScreen.this.addVoiceMailNumberPanel();
                  return;
                case 107:
                  InCallScreen.this.dontAddVoiceMailNumber();
                  return;
                case 108:
                  InCallScreen.this.delayedCleanupAfterDisconnect();
                  return;
                case 114:
                  InCallScreen.this.updateScreen();
                  return;
                case 115:
                  if (InCallScreen.DBG)
                    InCallScreen.this.log("Received PHONE_CDMA_CALL_WAITING event ...");
                  break;
                case 118:
                case 119:
                case 120:
                case 121:
                case 122:
                case 123:
                case 124:
                }
              }
              while (InCallScreen.this.mCM.getFirstActiveRingingCall().getLatestConnection() == null);
              InCallScreen.this.updateScreen();
              InCallScreen.this.mApp.updateWakeState();
              return;
            }
            while (InCallScreen.this.mApp.otaUtils == null);
            InCallScreen.this.mApp.otaUtils.onOtaCloseSpcNotice();
            return;
          }
          while (InCallScreen.this.mApp.otaUtils == null);
          InCallScreen.this.mApp.otaUtils.onOtaCloseFailureNotice();
          return;
        }
        while (InCallScreen.this.mPausePromptDialog == null);
        if (InCallScreen.DBG)
          InCallScreen.this.log("- DISMISSING mPausePromptDialog.");
        InCallScreen.this.mPausePromptDialog.dismiss();
        InCallScreen.access$1902(InCallScreen.this, null);
        return;
        InCallScreen.this.mApp.inCallUiState.providerInfoVisible = false;
      }
      while (InCallScreen.this.mCallCard == null);
      InCallScreen.this.mCallCard.updateState(InCallScreen.this.mCM);
      return;
      InCallScreen.this.updateScreen();
      return;
      InCallScreen.this.onIncomingRing();
      return;
      InCallScreen.this.onNewRingingConnection();
    }
  };
  private InCallControlState mInCallControlState;
  private InCallTouchUi mInCallTouchUi;
  private boolean mIsDestroyed = false;
  private boolean mIsForegroundActivity = false;
  private boolean mIsForegroundActivityForProximity = false;
  private Connection.DisconnectCause mLastDisconnectCause;
  private ManageConferenceUtils mManageConferenceUtils;
  private AlertDialog mMissingVoicemailDialog;
  private Dialog mMmiStartedDialog;
  private boolean mPauseInProgress = false;
  private AlertDialog mPausePromptDialog;
  private Phone mPhone;
  private String mPostDialStrAfterPause;
  private PowerManager mPowerManager;
  private ProgressDialog mProgressDialog;
  private final BroadcastReceiver mReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramAnonymousContext, Intent paramAnonymousIntent)
    {
      if (paramAnonymousIntent.getAction().equals("android.intent.action.HEADSET_PLUG"))
      {
        Message localMessage = Message.obtain(InCallScreen.this.mHandler, 103, paramAnonymousIntent.getIntExtra("state", 0), 0);
        InCallScreen.this.mHandler.sendMessage(localMessage);
      }
    }
  };
  private boolean mRegisteredForPhoneStates;
  private RespondViaSmsManager mRespondViaSmsManager;
  private AlertDialog mSuppServiceFailureDialog;
  private AlertDialog mWaitPromptDialog;
  private AlertDialog mWildPromptDialog;
  private EditText mWildPromptText;

  private void addVoiceMailNumberPanel()
  {
    if (this.mMissingVoicemailDialog != null)
    {
      this.mMissingVoicemailDialog.dismiss();
      this.mMissingVoicemailDialog = null;
    }
    if (DBG)
      log("addVoiceMailNumberPanel: finishing InCallScreen...");
    endInCallScreenSession();
    if (DBG)
      log("show vm setting");
    Intent localIntent = new Intent("com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL");
    localIntent.setClass(this, CallFeaturesSetting.class);
    startActivity(localIntent);
  }

  private void attachListeners()
  {
    SensorManager localSensorManager = getSensorManager();
    if (this.mFlipAction != 0)
    {
      this.mFlipListener.reset();
      localSensorManager.registerListener(this.mFlipListener, localSensorManager.getDefaultSensor(1), 3);
    }
  }

  private void bailOutAfterErrorDialog()
  {
    if (this.mGenericErrorDialog != null)
    {
      if (DBG)
        log("bailOutAfterErrorDialog: DISMISSING mGenericErrorDialog.");
      this.mGenericErrorDialog.dismiss();
      this.mGenericErrorDialog = null;
    }
    if (DBG)
      log("bailOutAfterErrorDialog(): end InCallScreen session...");
    this.mApp.inCallUiState.clearPendingCallStatusCode();
    endInCallScreenSession(true);
  }

  private boolean checkOtaspStateOnResume()
  {
    if (this.mApp.otaUtils == null)
    {
      if (DBG)
        log("checkOtaspStateOnResume: no OtaUtils instance; nothing to do.");
      return false;
    }
    if ((this.mApp.cdmaOtaScreenState == null) || (this.mApp.cdmaOtaProvisionData == null))
      throw new IllegalStateException("checkOtaspStateOnResume: app.cdmaOta* objects(s) not initialized");
    OtaUtils.CdmaOtaInCallScreenUiState.State localState = this.mApp.otaUtils.getCdmaOtaInCallScreenUiState();
    boolean bool;
    if ((localState == OtaUtils.CdmaOtaInCallScreenUiState.State.NORMAL) || (localState == OtaUtils.CdmaOtaInCallScreenUiState.State.ENDED))
    {
      bool = true;
      if (!bool)
        break label170;
      this.mApp.otaUtils.updateUiWidgets(this, this.mInCallTouchUi, this.mCallCard);
      if (localState != OtaUtils.CdmaOtaInCallScreenUiState.State.NORMAL)
        break label140;
      if (DBG)
        log("checkOtaspStateOnResume - in OTA Normal mode");
      setInCallScreenMode(InCallUiState.InCallScreenMode.OTA_NORMAL);
    }
    while (true)
    {
      return bool;
      bool = false;
      break;
      label140: if (localState == OtaUtils.CdmaOtaInCallScreenUiState.State.ENDED)
      {
        if (DBG)
          log("checkOtaspStateOnResume - in OTA END mode");
        setInCallScreenMode(InCallUiState.InCallScreenMode.OTA_ENDED);
        continue;
        label170: if (DBG)
          log("checkOtaspStateOnResume - Set OTA NORMAL Mode");
        setInCallScreenMode(InCallUiState.InCallScreenMode.OTA_NORMAL);
        if (this.mApp.otaUtils != null)
          this.mApp.otaUtils.cleanOtaScreen(false);
      }
    }
  }

  private void closeDialpadInternal(boolean paramBoolean)
  {
    this.mDialer.closeDialer(paramBoolean);
    this.mApp.inCallUiState.showDialpad = false;
  }

  private void confirmAddBlacklist()
  {
    Connection localConnection = PhoneUtils.getConnection(this.mPhone, PhoneUtils.getCurrentCall(this.mPhone));
    if (localConnection == null)
      return;
    final String str1 = localConnection.getAddress();
    String str2 = getString(2131296316, new Object[] { str1 });
    new AlertDialog.Builder(this).setTitle(2131296315).setMessage(str2).setPositiveButton(2131296750, new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
      {
        PhoneGlobals.getInstance().blackList.add(str1);
        InCallScreen.this.internalHangup();
      }
    }).setNegativeButton(2131296751, null).show();
  }

  private View createWildPromptView()
  {
    LinearLayout localLinearLayout = new LinearLayout(this);
    localLinearLayout.setOrientation(1);
    localLinearLayout.setPadding(5, 5, 5, 5);
    LinearLayout.LayoutParams localLayoutParams1 = new LinearLayout.LayoutParams(-1, -2);
    TextView localTextView = new TextView(this);
    localTextView.setTextSize(14.0F);
    localTextView.setTypeface(Typeface.DEFAULT_BOLD);
    localTextView.setText(getResources().getText(2131296385));
    localLinearLayout.addView(localTextView, localLayoutParams1);
    this.mWildPromptText = new EditText(this);
    this.mWildPromptText.setKeyListener(DialerKeyListener.getInstance());
    this.mWildPromptText.setMovementMethod(null);
    this.mWildPromptText.setTextSize(14.0F);
    this.mWildPromptText.setMaxLines(1);
    this.mWildPromptText.setHorizontallyScrolling(true);
    this.mWildPromptText.setBackgroundResource(17301528);
    LinearLayout.LayoutParams localLayoutParams2 = new LinearLayout.LayoutParams(-1, -2);
    localLayoutParams2.setMargins(0, 3, 0, 0);
    localLinearLayout.addView(this.mWildPromptText, localLayoutParams2);
    return localLinearLayout;
  }

  private void delayedCleanupAfterDisconnect()
  {
    this.mCM.clearDisconnected();
    if ((phoneIsInUse()) || (this.mApp.inCallUiState.isProgressIndicationActive()));
    for (int i = 1; i != 0; i = 0)
    {
      if (DBG)
        log("- delayedCleanupAfterDisconnect: staying on the InCallScreen...");
      return;
    }
    if (DBG)
      log("- delayedCleanupAfterDisconnect: phone is idle...");
    Intent localIntent;
    ActivityOptions localActivityOptions;
    if (this.mIsForegroundActivity)
    {
      if (DBG)
        log("- delayedCleanupAfterDisconnect: finishing InCallScreen...");
      if ((this.mLastDisconnectCause != Connection.DisconnectCause.INCOMING_MISSED) && (this.mLastDisconnectCause != Connection.DisconnectCause.INCOMING_REJECTED) && (PhoneUtils.PhoneSettings.showCallLogAfterCall(this)) && (!isPhoneStateRestricted()) && (PhoneGlobals.sVoiceCapable))
      {
        localIntent = this.mApp.createPhoneEndIntentUsingCallOrigin();
        localActivityOptions = ActivityOptions.makeCustomAnimation(this, 2131034112, 2131034113);
      }
    }
    try
    {
      startActivity(localIntent, localActivityOptions.toBundle());
      endInCallScreenSession();
      this.mApp.setLatestActiveCallOrigin(null);
      return;
    }
    catch (ActivityNotFoundException localActivityNotFoundException)
    {
      while (true)
        Log.w("InCallScreen", "delayedCleanupAfterDisconnect: transition to call log failed; intent = " + localIntent);
    }
  }

  private void detachListeners()
  {
    SensorManager localSensorManager = getSensorManager();
    if (this.mFlipAction != 0)
      localSensorManager.unregisterListener(this.mFlipListener);
  }

  private void dismissAllDialogs()
  {
    if (DBG)
      log("dismissAllDialogs()...");
    if (this.mMissingVoicemailDialog != null)
    {
      this.mMissingVoicemailDialog.dismiss();
      this.mMissingVoicemailDialog = null;
    }
    if (this.mMmiStartedDialog != null)
    {
      this.mMmiStartedDialog.dismiss();
      this.mMmiStartedDialog = null;
    }
    if (this.mGenericErrorDialog != null)
    {
      this.mGenericErrorDialog.dismiss();
      this.mGenericErrorDialog = null;
    }
    if (this.mSuppServiceFailureDialog != null)
    {
      this.mSuppServiceFailureDialog.dismiss();
      this.mSuppServiceFailureDialog = null;
    }
    if (this.mWaitPromptDialog != null)
    {
      this.mWaitPromptDialog.dismiss();
      this.mWaitPromptDialog = null;
    }
    if (this.mWildPromptDialog != null)
    {
      this.mWildPromptDialog.dismiss();
      this.mWildPromptDialog = null;
    }
    if (this.mCallLostDialog != null)
    {
      this.mCallLostDialog.dismiss();
      this.mCallLostDialog = null;
    }
    if (((this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL) || (this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_ENDED)) && (this.mApp.otaUtils != null))
      this.mApp.otaUtils.dismissAllOtaDialogs();
    if (this.mPausePromptDialog != null)
    {
      if (DBG)
        log("- DISMISSING mPausePromptDialog.");
      this.mPausePromptDialog.dismiss();
      this.mPausePromptDialog = null;
    }
    if (this.mExitingECMDialog != null)
    {
      if (DBG)
        log("- DISMISSING mExitingECMDialog.");
      this.mExitingECMDialog.dismiss();
      this.mExitingECMDialog = null;
    }
  }

  private void dismissProgressIndication()
  {
    if (DBG)
      log("dismissProgressIndication()...");
    if (this.mProgressDialog != null)
    {
      this.mProgressDialog.dismiss();
      this.mProgressDialog = null;
    }
  }

  private void dontAddVoiceMailNumber()
  {
    if (this.mMissingVoicemailDialog != null)
    {
      this.mMissingVoicemailDialog.dismiss();
      this.mMissingVoicemailDialog = null;
    }
    if (DBG)
      log("dontAddVoiceMailNumber: finishing InCallScreen...");
    endInCallScreenSession();
  }

  private void endInCallScreenSession(boolean paramBoolean)
  {
    if (DBG)
      log("endInCallScreenSession(" + paramBoolean + ")...  phone state = " + this.mCM.getState());
    if (paramBoolean)
    {
      Log.i("InCallScreen", "endInCallScreenSession(): FORCING a call to super.finish()!");
      super.finish();
    }
    while (true)
    {
      setInCallScreenMode(InCallUiState.InCallScreenMode.UNDEFINED);
      return;
      moveTaskToBack(true);
    }
  }

  private SensorManager getSensorManager()
  {
    return (SensorManager)getSystemService("sensor");
  }

  private void handleAction(int paramInt)
  {
    switch (paramInt)
    {
    default:
    case 1:
    case 2:
    }
    while (true)
    {
      detachListeners();
      return;
      internalSilenceRinger();
      continue;
      internalHangup();
    }
  }

  private boolean handleCallKey()
  {
    boolean bool1 = this.mCM.hasActiveRingingCall();
    boolean bool2 = this.mCM.hasActiveFgCall();
    boolean bool3 = this.mCM.hasActiveBgCall();
    int i = this.mPhone.getPhoneType();
    CdmaPhoneCallState.PhoneCallState localPhoneCallState;
    if (i == 2)
    {
      localPhoneCallState = this.mApp.cdmaPhoneCallState.getCurrentCallState();
      if (bool1)
      {
        if (DBG)
          log("answerCall: First Incoming and Call Waiting scenario");
        internalAnswerCall();
      }
    }
    do
    {
      do
      {
        return true;
        if ((localPhoneCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) && (bool2))
        {
          if (DBG)
            log("answerCall: Merge 3-way call scenario");
          PhoneUtils.mergeCalls(this.mCM);
          return true;
        }
      }
      while (localPhoneCallState != CdmaPhoneCallState.PhoneCallState.CONF_CALL);
      if (DBG)
        log("answerCall: Switch btwn 2 calls scenario");
      internalSwapCalls();
      return true;
      if ((i != 1) && (i != 3))
        break;
      if (bool1)
      {
        Log.w("InCallScreen", "handleCallKey: incoming call is ringing! (PhoneWindowManager should have handled this key.)");
        internalAnswerCall();
        return true;
      }
      if ((bool2) && (bool3))
      {
        if (DBG)
          log("handleCallKey: both lines in use ==> swap calls.");
        internalSwapCalls();
        return true;
      }
    }
    while (!bool3);
    if (DBG)
      log("handleCallKey: call on hold ==> unhold.");
    PhoneUtils.switchHoldingAndActive(this.mCM.getFirstActiveBgCall());
    return true;
    throw new IllegalStateException("Unexpected phone type: " + i);
  }

  private boolean handleDialerKeyDown(int paramInt, KeyEvent paramKeyEvent)
  {
    if (okToDialDTMFTones())
      return this.mDialer.onDialerKeyDown(paramKeyEvent);
    return false;
  }

  private void handleMissingVoiceMailNumber()
  {
    if (DBG)
      log("handleMissingVoiceMailNumber");
    final Message localMessage1 = Message.obtain(this.mHandler);
    localMessage1.what = 107;
    final Message localMessage2 = Message.obtain(this.mHandler);
    localMessage2.what = 106;
    this.mMissingVoicemailDialog = new AlertDialog.Builder(this).setTitle(2131296386).setMessage(2131296387).setPositiveButton(2131296370, new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
      {
        localMessage1.sendToTarget();
        InCallScreen.this.mApp.pokeUserActivity();
      }
    }).setNegativeButton(2131296388, new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
      {
        localMessage2.sendToTarget();
        InCallScreen.this.mApp.pokeUserActivity();
      }
    }).setOnCancelListener(new DialogInterface.OnCancelListener()
    {
      public void onCancel(DialogInterface paramAnonymousDialogInterface)
      {
        localMessage1.sendToTarget();
        InCallScreen.this.mApp.pokeUserActivity();
      }
    }).create();
    this.mMissingVoicemailDialog.getWindow().addFlags(2);
    this.mMissingVoicemailDialog.show();
  }

  private void handlePostOnDialChars(AsyncResult paramAsyncResult, char paramChar)
  {
    Connection localConnection = (Connection)paramAsyncResult.result;
    Connection.PostDialState localPostDialState;
    if (localConnection != null)
      localPostDialState = (Connection.PostDialState)paramAsyncResult.userObj;
    switch (19.$SwitchMap$com$android$internal$telephony$Connection$PostDialState[localPostDialState.ordinal()])
    {
    default:
      return;
    case 1:
      this.mDialer.stopLocalToneIfNeeded();
      if (this.mPauseInProgress)
        showPausePromptDialog(localConnection, this.mPostDialStrAfterPause);
      this.mPauseInProgress = false;
      this.mDialer.startLocalToneIfNeeded(paramChar);
      return;
    case 2:
      if (DBG)
        log("handlePostOnDialChars: show WAIT prompt...");
      this.mDialer.stopLocalToneIfNeeded();
      showWaitPromptDialog(localConnection, localConnection.getRemainingPostDialString());
      return;
    case 3:
      if (DBG)
        log("handlePostOnDialChars: show WILD prompt");
      this.mDialer.stopLocalToneIfNeeded();
      showWildPromptDialog(localConnection);
      return;
    case 4:
      this.mDialer.stopLocalToneIfNeeded();
      return;
    case 5:
    }
    this.mDialer.stopLocalToneIfNeeded();
    this.mPostDialStrAfterPause = localConnection.getRemainingPostDialString();
    this.mPauseInProgress = true;
  }

  private void initInCallScreen()
  {
    getWindow().addFlags(32768);
    this.mCallCard = ((CallCard)findViewById(2131230787));
    this.mCallCard.setInCallScreenInstance(this);
    initInCallTouchUi();
    this.mInCallControlState = new InCallControlState(this, this.mCM);
    this.mManageConferenceUtils = new ManageConferenceUtils(this, this.mCM);
    this.mDialer = new DTMFTwelveKeyDialer(this, (ViewStub)findViewById(2131230795));
    this.mPowerManager = ((PowerManager)getSystemService("power"));
  }

  private void initInCallTouchUi()
  {
    if (DBG)
      log("initInCallTouchUi()...");
    this.mInCallTouchUi = ((InCallTouchUi)findViewById(2131230793));
    this.mInCallTouchUi.setInCallScreenInstance(this);
    this.mRespondViaSmsManager = new RespondViaSmsManager();
    this.mRespondViaSmsManager.setInCallScreenInstance(this);
  }

  private void internalAnswerCall()
  {
    if (DBG)
      log("internalAnswerCall()...");
    Call localCall;
    int i;
    if (this.mCM.hasActiveRingingCall())
    {
      detachListeners();
      Phone localPhone = this.mCM.getRingingPhone();
      localCall = this.mCM.getFirstActiveRingingCall();
      i = localPhone.getPhoneType();
      if (i != 2)
        break label133;
      if (DBG)
        log("internalAnswerCall: answering (CDMA)...");
      if ((!this.mCM.hasActiveFgCall()) || (this.mCM.getFgPhone().getPhoneType() != 3))
        break label125;
      if (DBG)
        log("internalAnswerCall: answer CDMA incoming and end SIP ongoing");
      PhoneUtils.answerAndEndActive(this.mCM, localCall);
    }
    while (true)
    {
      this.mApp.setLatestActiveCallOrigin(null);
      return;
      label125: PhoneUtils.answerCall(localCall);
      continue;
      label133: if (i == 3)
      {
        if (DBG)
          log("internalAnswerCall: answering (SIP)...");
        if ((this.mCM.hasActiveFgCall()) && (this.mCM.getFgPhone().getPhoneType() == 2))
        {
          if (DBG)
            log("internalAnswerCall: answer SIP incoming and end CDMA ongoing");
          PhoneUtils.answerAndEndActive(this.mCM, localCall);
        }
        else
        {
          PhoneUtils.answerCall(localCall);
        }
      }
      else
      {
        if (i != 1)
          break;
        if (DBG)
          log("internalAnswerCall: answering (GSM)...");
        boolean bool1 = this.mCM.hasActiveFgCall();
        boolean bool2 = this.mCM.hasActiveBgCall();
        if ((bool1) && (bool2))
        {
          if (DBG)
            log("internalAnswerCall: answering (both lines in use!)...");
          PhoneUtils.answerAndEndActive(this.mCM, localCall);
        }
        else
        {
          if (DBG)
            log("internalAnswerCall: answering...");
          PhoneUtils.answerCall(localCall);
        }
      }
    }
    throw new IllegalStateException("Unexpected phone type: " + i);
  }

  private void internalHangup()
  {
    PhoneConstants.State localState = this.mCM.getState();
    log("internalHangup()...  phone state = " + localState);
    PhoneUtils.hangup(this.mCM);
    if (localState == PhoneConstants.State.IDLE)
      Log.w("InCallScreen", "internalHangup(): phone is already IDLE!");
  }

  private void internalResolveIntent(Intent paramIntent)
  {
    if ((paramIntent == null) || (paramIntent.getAction() == null));
    String str;
    do
    {
      boolean bool1;
      boolean bool2;
      boolean bool3;
      do
      {
        do
        {
          return;
          str = paramIntent.getAction();
          if (DBG)
            log("internalResolveIntent: action=" + str);
          if (!str.equals("android.intent.action.MAIN"))
            break;
        }
        while (!paramIntent.hasExtra("com.android.phone.ShowDialpad"));
        bool1 = paramIntent.getBooleanExtra("com.android.phone.ShowDialpad", false);
        this.mApp.inCallUiState.showDialpad = bool1;
        bool2 = this.mCM.hasActiveFgCall();
        bool3 = this.mCM.hasActiveBgCall();
      }
      while ((!bool1) || (bool2) || (!bool3));
      PhoneUtils.switchHoldingAndActive(this.mCM.getFirstActiveBgCall());
      return;
      if (!str.equals("com.android.phone.DISPLAY_ACTIVATION_SCREEN"))
        break;
      if (!TelephonyCapabilities.supportsOtasp(this.mPhone))
        throw new IllegalStateException("Received ACTION_DISPLAY_ACTIVATION_SCREEN intent on non-OTASP-capable device: " + paramIntent);
      setInCallScreenMode(InCallUiState.InCallScreenMode.OTA_NORMAL);
    }
    while ((this.mApp.cdmaOtaProvisionData == null) || (this.mApp.cdmaOtaProvisionData.isOtaCallIntentProcessed));
    this.mApp.cdmaOtaProvisionData.isOtaCallIntentProcessed = true;
    this.mApp.cdmaOtaScreenState.otaScreenState = OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION;
    return;
    if (str.equals("com.android.phone.PERFORM_CDMA_PROVISIONING"))
      throw new IllegalStateException("Unexpected ACTION_PERFORM_CDMA_PROVISIONING received by InCallScreen: " + paramIntent);
    if ((str.equals("android.intent.action.CALL")) || (str.equals("android.intent.action.CALL_EMERGENCY")))
      throw new IllegalStateException("Unexpected CALL action received by InCallScreen: " + paramIntent);
    if (str.equals("com.android.phone.InCallScreen.UNDEFINED"))
    {
      Log.wtf("InCallScreen", "internalResolveIntent: got launched with ACTION_UNDEFINED");
      return;
    }
    Log.wtf("InCallScreen", "internalResolveIntent: unexpected intent action: " + str);
  }

  private void internalRespondViaSms()
  {
    log("internalRespondViaSms()...");
    Call localCall = this.mCM.getFirstActiveRingingCall();
    this.mRespondViaSmsManager.showRespondViaSmsPopup(localCall);
    internalSilenceRinger();
  }

  private void internalSilenceRinger()
  {
    if (DBG)
      log("internalSilenceRinger()...");
    CallNotifier localCallNotifier = this.mApp.notifier;
    if (localCallNotifier.isRinging())
      localCallNotifier.silenceRinger();
  }

  private void internalSwapCalls()
  {
    if (DBG)
      log("internalSwapCalls()...");
    closeDialpadInternal(true);
    this.mDialer.clearDigits();
    PhoneUtils.switchHoldingAndActive(this.mCM.getFirstActiveBgCall());
    IBluetoothHeadsetPhone localIBluetoothHeadsetPhone;
    if (this.mCM.getBgPhone().getPhoneType() == 2)
    {
      localIBluetoothHeadsetPhone = this.mApp.getBluetoothPhoneService();
      if (localIBluetoothHeadsetPhone == null);
    }
    try
    {
      localIBluetoothHeadsetPhone.cdmaSwapSecondCallState();
      return;
    }
    catch (RemoteException localRemoteException)
    {
      Log.e("InCallScreen", Log.getStackTraceString(new Throwable()));
    }
  }

  private void log(String paramString)
  {
    Log.d("InCallScreen", paramString);
  }

  private void onDisconnect(AsyncResult paramAsyncResult)
  {
    Connection localConnection = (Connection)paramAsyncResult.result;
    Connection.DisconnectCause localDisconnectCause = localConnection.getDisconnectCause();
    if (DBG)
      log("onDisconnect: connection '" + localConnection + "', cause = " + localDisconnectCause + ", showing screen: " + this.mApp.isShowingCallScreen());
    int i;
    int j;
    if (!phoneIsInUse())
    {
      i = 1;
      if (this.mPhone.getPhoneType() != 2)
        break label183;
      j = 1;
      label95: if ((j == 0) || (i == 0))
        break label1080;
    }
    label183: label1080: for (int k = Settings.Global.getInt(this.mPhone.getContext().getContentResolver(), "call_auto_retry", 0); ; k = 0)
    {
      if ((this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL) && (this.mApp.cdmaOtaProvisionData != null) && (!this.mApp.cdmaOtaProvisionData.inOtaSpcState))
      {
        setInCallScreenMode(InCallUiState.InCallScreenMode.OTA_ENDED);
        updateScreen();
      }
      label242: Call localCall;
      label345: 
      do
      {
        do
        {
          return;
          i = 0;
          break;
          j = 0;
          break label95;
          if ((this.mApp.inCallUiState.inCallScreenMode != InCallUiState.InCallScreenMode.OTA_ENDED) && ((this.mApp.cdmaOtaProvisionData == null) || (!this.mApp.cdmaOtaProvisionData.inOtaSpcState)))
            break label242;
        }
        while (!DBG);
        log("onDisconnect: OTA Call end already handled");
        return;
        this.mDialer.clearDigits();
        Call.State localState;
        if (localDisconnectCause == Connection.DisconnectCause.INCOMING_MISSED)
        {
          if (this.mApp.inCallUiState.needToShowAdditionalCallForwardedDialog)
          {
            showGenericErrorDialog(2131296256, false);
            this.mApp.inCallUiState.needToShowAdditionalCallForwardedDialog = false;
          }
          if (j != 0)
          {
            localState = this.mApp.notifier.getPreviousCdmaCallState();
            if ((localState != Call.State.ACTIVE) || (localDisconnectCause == Connection.DisconnectCause.INCOMING_MISSED) || (localDisconnectCause == Connection.DisconnectCause.NORMAL) || (localDisconnectCause == Connection.DisconnectCause.LOCAL) || (localDisconnectCause == Connection.DisconnectCause.INCOMING_REJECTED))
              break label596;
            showCallLostDialog();
          }
          localCall = localConnection.getCall();
          if (localCall != null)
          {
            List localList = localCall.getConnections();
            if ((localList != null) && (localList.size() > 1))
            {
              Iterator localIterator = localList.iterator();
              while (localIterator.hasNext())
                if (((Connection)localIterator.next()).getState() == Call.State.ACTIVE)
                {
                  this.mApp.updateWakeState();
                  this.mCM.clearDisconnected();
                }
            }
          }
          this.mLastDisconnectCause = localDisconnectCause;
          if (((localDisconnectCause != Connection.DisconnectCause.INCOMING_MISSED) && (localDisconnectCause != Connection.DisconnectCause.INCOMING_REJECTED)) || (i == 0))
            break label708;
        }
        for (int m = 1; ; m = 0)
        {
          RespondViaSmsManager localRespondViaSmsManager = this.mRespondViaSmsManager;
          int n = 0;
          if (localRespondViaSmsManager != null)
          {
            boolean bool = this.mRespondViaSmsManager.isShowingPopup();
            n = 0;
            if (bool)
              n = 1;
          }
          if ((m == 0) || (n == 0))
            break label714;
          if (!DBG)
            break;
          log("- onDisconnect: Respond-via-SMS dialog is still being displayed...");
          return;
          if (localDisconnectCause == Connection.DisconnectCause.CALL_BARRED)
          {
            showGenericErrorDialog(2131296363, false);
            return;
          }
          if (localDisconnectCause == Connection.DisconnectCause.FDN_BLOCKED)
          {
            showGenericErrorDialog(2131296362, false);
            return;
          }
          if (localDisconnectCause == Connection.DisconnectCause.CS_RESTRICTED)
          {
            showGenericErrorDialog(2131296364, false);
            return;
          }
          if (localDisconnectCause == Connection.DisconnectCause.CS_RESTRICTED_EMERGENCY)
          {
            showGenericErrorDialog(2131296365, false);
            return;
          }
          if (localDisconnectCause != Connection.DisconnectCause.CS_RESTRICTED_NORMAL)
            break label288;
          showGenericErrorDialog(2131296366, false);
          return;
          if (((localState != Call.State.DIALING) && (localState != Call.State.ALERTING)) || (localDisconnectCause == Connection.DisconnectCause.INCOMING_MISSED) || (localDisconnectCause == Connection.DisconnectCause.NORMAL) || (localDisconnectCause == Connection.DisconnectCause.LOCAL) || (localDisconnectCause == Connection.DisconnectCause.INCOMING_REJECTED))
            break label345;
          if (this.mApp.inCallUiState.needToShowCallLostDialog)
          {
            showCallLostDialog();
            this.mApp.inCallUiState.needToShowCallLostDialog = false;
            break label345;
          }
          if (k == 0)
          {
            showCallLostDialog();
            this.mApp.inCallUiState.needToShowCallLostDialog = false;
            break label345;
          }
          this.mApp.inCallUiState.needToShowCallLostDialog = true;
          break label345;
        }
        if (m != 0)
        {
          if (DBG)
            log("- onDisconnect: bailOutImmediately...");
          delayedCleanupAfterDisconnect();
          return;
        }
        if (DBG)
          log("- onDisconnect: delayed bailout...");
        if ((i != 0) && ((this.mCM.hasDisconnectedFgCall()) || (this.mCM.hasDisconnectedBgCall())))
        {
          if (DBG)
            log("- onDisconnect: switching to 'Call ended' state...");
          setInCallScreenMode(InCallUiState.InCallScreenMode.CALL_ENDED);
        }
        updateScreen();
        if (!this.mCM.hasActiveFgCall())
        {
          if (DBG)
            log("- onDisconnect: cleaning up after FG call disconnect...");
          if (this.mWaitPromptDialog != null)
          {
            this.mWaitPromptDialog.dismiss();
            this.mWaitPromptDialog = null;
          }
          if (this.mWildPromptDialog != null)
          {
            this.mWildPromptDialog.dismiss();
            this.mWildPromptDialog = null;
          }
          if (this.mPausePromptDialog != null)
          {
            if (DBG)
              log("- DISMISSING mPausePromptDialog.");
            this.mPausePromptDialog.dismiss();
            this.mPausePromptDialog = null;
          }
        }
        if ((this.mPhone.getPhoneType() != 2) || (i != 0))
          break label944;
        this.mCM.clearDisconnected();
        if (DBG)
          log("onDisconnect: Call Collision case - staying on InCallScreen.");
      }
      while (!DBG);
      label596: PhoneUtils.dumpCallState(this.mPhone);
      label708: label714: return;
      if ((i != 0) && (!isForegroundActivity()) && (isForegroundActivityForProximity()))
      {
        log("Force waking up the screen to let users see \"disconnected\" state");
        if (localCall != null)
          this.mCallCard.updateElapsedTimeWidget(localCall);
        this.mApp.inCallUiState.showAlreadyDisconnectedState = true;
        this.mApp.wakeUpScreen();
        return;
      }
      int i1;
      switch (19.$SwitchMap$com$android$internal$telephony$Connection$DisconnectCause[localDisconnectCause.ordinal()])
      {
      default:
        i1 = 5000;
      case 1:
      case 2:
      case 3:
      }
      while (true)
      {
        this.mHandler.removeMessages(108);
        this.mHandler.sendEmptyMessageDelayed(108, i1);
        return;
        i1 = 200;
        continue;
        i1 = 2000;
      }
    }
  }

  private void onHoldClick()
  {
    boolean bool1 = this.mCM.hasActiveFgCall();
    boolean bool2 = this.mCM.hasActiveBgCall();
    log("onHoldClick: hasActiveCall = " + bool1 + ", hasHoldingCall = " + bool2);
    if ((bool1) && (!bool2))
      PhoneUtils.switchHoldingAndActive(this.mCM.getFirstActiveBgCall());
    while (true)
    {
      closeDialpadInternal(true);
      return;
      if ((!bool1) && (bool2))
        PhoneUtils.switchHoldingAndActive(this.mCM.getFirstActiveBgCall());
    }
  }

  private void onIncomingRing()
  {
    if (DBG)
      log("onIncomingRing()...");
    if ((this.mIsForegroundActivity) && (this.mInCallTouchUi != null))
      this.mInCallTouchUi.onIncomingRing();
  }

  private void onMMICancel()
  {
    PhoneUtils.cancelMmiCode(this.mPhone);
    if (DBG)
      log("onMMICancel: finishing InCallScreen...");
    dismissAllDialogs();
    endInCallScreenSession();
  }

  private void onMMIComplete(MmiCode paramMmiCode)
  {
    int i = this.mPhone.getPhoneType();
    if (i == 2)
      PhoneUtils.displayMMIComplete(this.mPhone, this.mApp, paramMmiCode, null, null);
    while ((i != 1) || (paramMmiCode.getState() == MmiCode.State.PENDING))
      return;
    if (DBG)
      log("Got MMI_COMPLETE, finishing InCallScreen...");
    dismissAllDialogs();
    endInCallScreenSession();
  }

  private void onMuteClick()
  {
    if (!PhoneUtils.getMute());
    for (boolean bool = true; ; bool = false)
    {
      log("onMuteClick(): newMuteState = " + bool);
      PhoneUtils.setMute(bool);
      return;
    }
  }

  private void onNewRingingConnection()
  {
    if (DBG)
      log("onNewRingingConnection()...");
    this.mRespondViaSmsManager.dismissPopup();
  }

  private void onOpenCloseDialpad()
  {
    if (this.mDialer.isOpened())
      closeDialpadInternal(true);
    while (true)
    {
      this.mApp.updateProximitySensorMode(this.mCM.getState());
      return;
      openDialpadInternal(true);
    }
  }

  private void onPhoneStateChanged(AsyncResult paramAsyncResult)
  {
    PhoneConstants.State localState = this.mCM.getState();
    if (DBG)
      log("onPhoneStateChanged: current state = " + localState);
    if (!this.mIsForegroundActivity)
    {
      if (DBG)
        log("onPhoneStateChanged: Activity not in foreground! Bailing out...");
      return;
    }
    updateExpandedViewState();
    requestUpdateScreen();
    this.mApp.updateWakeState();
  }

  private void onSuppServiceNotification(AsyncResult paramAsyncResult)
  {
    SuppServiceNotification localSuppServiceNotification = (SuppServiceNotification)paramAsyncResult.result;
    if ((localSuppServiceNotification.notificationType == 1) && (localSuppServiceNotification.code == 10) && (!PhoneUtils.getCurrentCall(this.mPhone).isIdle()))
      this.mApp.inCallUiState.needToShowAdditionalCallForwardedDialog = true;
  }

  private void openDialpadInternal(boolean paramBoolean)
  {
    this.mDialer.openDialer(paramBoolean);
    this.mApp.inCallUiState.showDialpad = true;
  }

  private boolean phoneIsInUse()
  {
    return this.mCM.getState() != PhoneConstants.State.IDLE;
  }

  private void registerForPhoneStates()
  {
    if (!this.mRegisteredForPhoneStates)
    {
      this.mCM.registerForPreciseCallStateChanged(this.mHandler, 101, null);
      this.mCM.registerForDisconnect(this.mHandler, 102, null);
      this.mCM.registerForMmiComplete(this.mHandler, 52, null);
      this.mCM.registerForCallWaiting(this.mHandler, 115, null);
      this.mCM.registerForPostDialCharacter(this.mHandler, 104, null);
      this.mCM.registerForSuppServiceFailed(this.mHandler, 110, null);
      this.mCM.registerForIncomingRing(this.mHandler, 123, null);
      this.mCM.registerForNewRingingConnection(this.mHandler, 124, null);
      this.mCM.registerForSuppServiceNotification(this.mHandler, 125, null);
      this.mRegisteredForPhoneStates = true;
    }
  }

  private void setInCallScreenMode(InCallUiState.InCallScreenMode paramInCallScreenMode)
  {
    if (DBG)
      log("setInCallScreenMode: " + paramInCallScreenMode);
    this.mApp.inCallUiState.inCallScreenMode = paramInCallScreenMode;
    switch (19.$SwitchMap$com$android$phone$InCallUiState$InCallScreenMode[paramInCallScreenMode.ordinal()])
    {
    default:
    case 1:
    case 2:
    case 3:
    case 4:
    case 5:
    case 6:
    }
    do
    {
      return;
      if (!PhoneUtils.isConferenceCall(this.mCM.getActiveFgCall()))
      {
        Log.w("InCallScreen", "MANAGE_CONFERENCE: no active conference call!");
        setInCallScreenMode(InCallUiState.InCallScreenMode.NORMAL);
        return;
      }
      List localList = this.mCM.getFgCallConnections();
      if ((localList == null) || (localList.size() <= 1))
      {
        Log.w("InCallScreen", "MANAGE_CONFERENCE: Bogus TRUE from isConferenceCall(); connections = " + localList);
        setInCallScreenMode(InCallUiState.InCallScreenMode.NORMAL);
        return;
      }
      this.mManageConferenceUtils.initManageConferencePanel();
      this.mManageConferenceUtils.updateManageConferencePanel(localList);
      this.mManageConferenceUtils.setPanelVisible(true);
      long l = this.mCM.getActiveFgCall().getEarliestConnection().getDurationMillis();
      this.mManageConferenceUtils.startConferenceTime(SystemClock.elapsedRealtime() - l);
      return;
      this.mManageConferenceUtils.setPanelVisible(false);
      this.mManageConferenceUtils.stopConferenceTime();
      return;
      this.mApp.otaUtils.setCdmaOtaInCallScreenUiState(OtaUtils.CdmaOtaInCallScreenUiState.State.NORMAL);
      return;
      this.mApp.otaUtils.setCdmaOtaInCallScreenUiState(OtaUtils.CdmaOtaInCallScreenUiState.State.ENDED);
      return;
      setIntent(new Intent("com.android.phone.InCallScreen.UNDEFINED"));
      if (this.mCM.getState() == PhoneConstants.State.OFFHOOK)
        break;
    }
    while (this.mApp.otaUtils == null);
    this.mApp.otaUtils.cleanOtaScreen(true);
    return;
    log("WARNING: Setting mode to UNDEFINED but phone is OFFHOOK, skip cleanOtaScreen.");
  }

  private void showCallLostDialog()
  {
    if (DBG)
      log("showCallLostDialog()...");
    if (!this.mIsForegroundActivity)
      if (DBG)
        log("showCallLostDialog: not the foreground Activity! Bailing out...");
    do
    {
      return;
      if (this.mCallLostDialog == null)
        break;
    }
    while (!DBG);
    log("showCallLostDialog: There is a mCallLostDialog already.");
    return;
    this.mCallLostDialog = new AlertDialog.Builder(this).setMessage(2131296369).setIconAttribute(16843605).create();
    this.mCallLostDialog.show();
  }

  private void showExitingECMDialog()
  {
    Log.i("InCallScreen", "showExitingECMDialog()...");
    if (this.mExitingECMDialog != null)
    {
      if (DBG)
        log("- DISMISSING mExitingECMDialog.");
      this.mExitingECMDialog.dismiss();
      this.mExitingECMDialog = null;
    }
    final InCallUiState localInCallUiState = this.mApp.inCallUiState;
    DialogInterface.OnClickListener local17 = new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
      {
        localInCallUiState.clearPendingCallStatusCode();
      }
    };
    DialogInterface.OnCancelListener local18 = new DialogInterface.OnCancelListener()
    {
      public void onCancel(DialogInterface paramAnonymousDialogInterface)
      {
        localInCallUiState.clearPendingCallStatusCode();
      }
    };
    this.mExitingECMDialog = new AlertDialog.Builder(this).setMessage(2131296749).setPositiveButton(2131296370, local17).setOnCancelListener(local18).create();
    this.mExitingECMDialog.getWindow().addFlags(4);
    this.mExitingECMDialog.show();
  }

  private void showGenericErrorDialog(int paramInt, boolean paramBoolean)
  {
    CharSequence localCharSequence = getResources().getText(paramInt);
    if (DBG)
      log("showGenericErrorDialog('" + localCharSequence + "')...");
    Object localObject1;
    if (paramBoolean)
      localObject1 = new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
        {
          InCallScreen.this.bailOutAfterErrorDialog();
        }
      };
    for (Object localObject2 = new DialogInterface.OnCancelListener()
    {
      public void onCancel(DialogInterface paramAnonymousDialogInterface)
      {
        InCallScreen.this.bailOutAfterErrorDialog();
      }
    }
    ; ; localObject2 = new DialogInterface.OnCancelListener()
    {
      public void onCancel(DialogInterface paramAnonymousDialogInterface)
      {
        InCallScreen.this.delayedCleanupAfterDisconnect();
      }
    })
    {
      this.mGenericErrorDialog = new AlertDialog.Builder(this).setMessage(localCharSequence).setPositiveButton(2131296370, (DialogInterface.OnClickListener)localObject1).setOnCancelListener((DialogInterface.OnCancelListener)localObject2).create();
      this.mGenericErrorDialog.getWindow().addFlags(2);
      this.mGenericErrorDialog.show();
      return;
      localObject1 = new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
        {
          InCallScreen.this.delayedCleanupAfterDisconnect();
        }
      };
    }
  }

  private void showPausePromptDialog(Connection paramConnection, String paramString)
  {
    Resources localResources = getResources();
    StringBuilder localStringBuilder = new StringBuilder();
    localStringBuilder.append(localResources.getText(2131296381));
    localStringBuilder.append(paramString);
    if (this.mPausePromptDialog != null)
    {
      if (DBG)
        log("- DISMISSING mPausePromptDialog.");
      this.mPausePromptDialog.dismiss();
      this.mPausePromptDialog = null;
    }
    this.mPausePromptDialog = new AlertDialog.Builder(this).setMessage(localStringBuilder.toString()).create();
    this.mPausePromptDialog.show();
    Message localMessage = Message.obtain(this.mHandler, 120);
    this.mHandler.sendMessageDelayed(localMessage, 2000L);
  }

  private void showProgressIndication(int paramInt1, int paramInt2)
  {
    if (DBG)
      log("showProgressIndication(message " + paramInt2 + ")...");
    dismissProgressIndication();
    this.mProgressDialog = new ProgressDialog(this);
    this.mProgressDialog.setTitle(getText(paramInt1));
    this.mProgressDialog.setMessage(getText(paramInt2));
    this.mProgressDialog.setIndeterminate(true);
    this.mProgressDialog.setCancelable(false);
    this.mProgressDialog.getWindow().setType(2008);
    this.mProgressDialog.getWindow().addFlags(4);
    this.mProgressDialog.show();
  }

  private void showStatusIndication(Constants.CallStatusCode paramCallStatusCode)
  {
    switch (19.$SwitchMap$com$android$phone$Constants$CallStatusCode[paramCallStatusCode.ordinal()])
    {
    default:
      throw new IllegalStateException("showStatusIndication: unexpected status code: " + paramCallStatusCode);
    case 1:
      Log.wtf("InCallScreen", "showStatusIndication: nothing to display");
    case 9:
    case 2:
    case 3:
    case 4:
    case 5:
    case 6:
      do
      {
        return;
        showGenericErrorDialog(2131296687, true);
        return;
        showGenericErrorDialog(2131296688, true);
        return;
        showGenericErrorDialog(2131296689, true);
        return;
        showGenericErrorDialog(2131296690, true);
        return;
      }
      while (this.mCM.getState() != PhoneConstants.State.OFFHOOK);
      Toast.makeText(this.mApp, 2131296692, 0).show();
      return;
    case 7:
      showGenericErrorDialog(2131296691, true);
      return;
    case 8:
      handleMissingVoiceMailNumber();
      return;
    case 10:
    }
    showExitingECMDialog();
  }

  private void showWaitPromptDialog(final Connection paramConnection, String paramString)
  {
    if (DBG)
      log("showWaitPromptDialogChoice: '" + paramString + "'...");
    Resources localResources = getResources();
    StringBuilder localStringBuilder = new StringBuilder();
    localStringBuilder.append(localResources.getText(2131296380));
    localStringBuilder.append(paramString);
    if (this.mWaitPromptDialog != null)
    {
      if (DBG)
        log("- DISMISSING mWaitPromptDialog.");
      this.mWaitPromptDialog.dismiss();
      this.mWaitPromptDialog = null;
    }
    this.mWaitPromptDialog = new AlertDialog.Builder(this).setMessage(localStringBuilder.toString()).setPositiveButton(2131296383, new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
      {
        if (InCallScreen.DBG)
          InCallScreen.this.log("handle WAIT_PROMPT_CONFIRMED, proceed...");
        paramConnection.proceedAfterWaitChar();
      }
    }).setNegativeButton(2131296384, new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
      {
        if (InCallScreen.DBG)
          InCallScreen.this.log("handle POST_DIAL_CANCELED!");
        paramConnection.cancelPostDial();
      }
    }).create();
    this.mWaitPromptDialog.getWindow().addFlags(4);
    this.mWaitPromptDialog.show();
  }

  private void showWildPromptDialog(final Connection paramConnection)
  {
    View localView = createWildPromptView();
    if (this.mWildPromptDialog != null)
    {
      this.mWildPromptDialog.dismiss();
      this.mWildPromptDialog = null;
    }
    this.mWildPromptDialog = new AlertDialog.Builder(this).setView(localView).setPositiveButton(2131296382, new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface paramAnonymousDialogInterface, int paramAnonymousInt)
      {
        EditText localEditText = InCallScreen.this.mWildPromptText;
        String str = null;
        if (localEditText != null)
        {
          str = InCallScreen.this.mWildPromptText.getText().toString();
          InCallScreen.access$2502(InCallScreen.this, null);
        }
        paramConnection.proceedAfterWildChar(str);
        InCallScreen.this.mApp.pokeUserActivity();
      }
    }).setOnCancelListener(new DialogInterface.OnCancelListener()
    {
      public void onCancel(DialogInterface paramAnonymousDialogInterface)
      {
        paramConnection.cancelPostDial();
        InCallScreen.this.mApp.pokeUserActivity();
      }
    }).create();
    this.mWildPromptDialog.getWindow().addFlags(4);
    this.mWildPromptDialog.show();
    this.mWildPromptText.requestFocus();
  }

  private void stopTimer()
  {
    if (this.mCallCard != null)
      this.mCallCard.stopTimer();
  }

  private SyncWithPhoneStateStatus syncWithPhoneState()
  {
    int i = 1;
    if (DBG)
      log("syncWithPhoneState()...");
    if (DBG)
      PhoneUtils.dumpCallState(this.mPhone);
    if ((TelephonyCapabilities.supportsOtasp(this.mCM.getFgPhone())) && ((this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL) || (this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_ENDED)))
      return SyncWithPhoneStateStatus.SUCCESS;
    if ((this.mPhone.getPhoneType() == i) && (!this.mPhone.getPendingMmiCodes().isEmpty()));
    while (true)
    {
      boolean bool1 = this.mApp.inCallUiState.isProgressIndicationActive();
      boolean bool2 = this.mApp.inCallUiState.showAlreadyDisconnectedState;
      if ((!this.mCM.hasActiveFgCall()) && (!this.mCM.hasActiveBgCall()) && (!this.mCM.hasActiveRingingCall()) && (i == 0) && (!bool1) && (!bool2))
        break;
      updateScreen();
      return SyncWithPhoneStateStatus.SUCCESS;
      i = 0;
    }
    Log.i("InCallScreen", "syncWithPhoneState: phone is idle (shouldn't be here)");
    return SyncWithPhoneStateStatus.PHONE_NOT_IN_USE;
  }

  private void unregisterForPhoneStates()
  {
    this.mCM.unregisterForPreciseCallStateChanged(this.mHandler);
    this.mCM.unregisterForDisconnect(this.mHandler);
    this.mCM.unregisterForMmiInitiate(this.mHandler);
    this.mCM.unregisterForMmiComplete(this.mHandler);
    this.mCM.unregisterForCallWaiting(this.mHandler);
    this.mCM.unregisterForPostDialCharacter(this.mHandler);
    this.mCM.unregisterForSuppServiceFailed(this.mHandler);
    this.mCM.unregisterForIncomingRing(this.mHandler);
    this.mCM.unregisterForNewRingingConnection(this.mHandler);
    this.mCM.unregisterForSuppServiceNotification(this.mHandler);
    this.mRegisteredForPhoneStates = false;
  }

  private void updateCallCardVisibilityPerDialerState(boolean paramBoolean)
  {
    if (isDialerOpened())
      if (paramBoolean)
        AnimationUtils.Fade.hide(this.mCallCard, 8);
    while ((this.mApp.inCallUiState.inCallScreenMode != InCallUiState.InCallScreenMode.NORMAL) && (this.mApp.inCallUiState.inCallScreenMode != InCallUiState.InCallScreenMode.CALL_ENDED))
    {
      return;
      this.mCallCard.setVisibility(8);
      return;
    }
    if (paramBoolean)
    {
      AnimationUtils.Fade.show(this.mCallCard);
      return;
    }
    this.mCallCard.setVisibility(0);
  }

  private void updateExpandedViewState()
  {
    boolean bool = true;
    if (this.mIsForegroundActivity)
    {
      if (this.mApp.proximitySensorModeEnabled())
      {
        NotificationMgr.StatusBarHelper localStatusBarHelper = this.mApp.notificationMgr.statusBarHelper;
        if (this.mCM.getState() != PhoneConstants.State.RINGING);
        while (true)
        {
          localStatusBarHelper.enableExpandedView(bool);
          return;
          bool = false;
        }
      }
      this.mApp.notificationMgr.statusBarHelper.enableExpandedView(false);
      return;
    }
    this.mApp.notificationMgr.statusBarHelper.enableExpandedView(bool);
  }

  private void updateInCallTouchUi()
  {
    if (this.mInCallTouchUi != null)
      this.mInCallTouchUi.updateState(this.mCM);
  }

  private void updateManageConferencePanelIfNecessary()
  {
    List localList = this.mCM.getFgCallConnections();
    if (localList == null)
    {
      setInCallScreenMode(InCallUiState.InCallScreenMode.NORMAL);
      SyncWithPhoneStateStatus localSyncWithPhoneStateStatus2 = syncWithPhoneState();
      if (localSyncWithPhoneStateStatus2 != SyncWithPhoneStateStatus.SUCCESS)
      {
        Log.w("InCallScreen", "- syncWithPhoneState failed! status = " + localSyncWithPhoneStateStatus2);
        if (DBG)
          log("updateManageConferencePanelIfNecessary: endInCallScreenSession... 1");
        endInCallScreenSession();
      }
    }
    int i;
    do
    {
      SyncWithPhoneStateStatus localSyncWithPhoneStateStatus1;
      do
      {
        return;
        i = localList.size();
        if (i > 1)
          break;
        setInCallScreenMode(InCallUiState.InCallScreenMode.NORMAL);
        localSyncWithPhoneStateStatus1 = syncWithPhoneState();
      }
      while (localSyncWithPhoneStateStatus1 == SyncWithPhoneStateStatus.SUCCESS);
      Log.w("InCallScreen", "- syncWithPhoneState failed! status = " + localSyncWithPhoneStateStatus1);
      if (DBG)
        log("updateManageConferencePanelIfNecessary: endInCallScreenSession... 2");
      endInCallScreenSession();
      return;
    }
    while (i == this.mManageConferenceUtils.getNumCallersInConference());
    this.mManageConferenceUtils.updateManageConferencePanel(localList);
  }

  private void updateProgressIndication()
  {
    if (this.mCM.hasActiveRingingCall())
    {
      dismissProgressIndication();
      return;
    }
    InCallUiState localInCallUiState = this.mApp.inCallUiState;
    switch (19.$SwitchMap$com$android$phone$InCallUiState$ProgressIndicationType[localInCallUiState.getProgressIndication().ordinal()])
    {
    default:
      Log.wtf("InCallScreen", "updateProgressIndication: unexpected value: " + localInCallUiState.getProgressIndication());
      dismissProgressIndication();
      return;
    case 1:
      dismissProgressIndication();
      return;
    case 2:
      showProgressIndication(2131296701, 2131296702);
      return;
    case 3:
    }
    showProgressIndication(2131296701, 2131296703);
  }

  private void updateScreen()
  {
    if (DBG)
      log("updateScreen()...");
    InCallUiState.InCallScreenMode localInCallScreenMode = this.mApp.inCallUiState.inCallScreenMode;
    if (!this.mIsForegroundActivity)
      if (DBG)
        log("- updateScreen: not the foreground Activity! Bailing out...");
    int i;
    while (true)
    {
      return;
      if (localInCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL)
      {
        if (DBG)
          log("- updateScreen: OTA call state NORMAL (NOT updating in-call UI)...");
        this.mCallCard.setVisibility(8);
        if (this.mApp.otaUtils != null)
        {
          this.mApp.otaUtils.otaShowProperScreen();
          return;
        }
        Log.w("InCallScreen", "OtaUtils object is null, not showing any screen for that.");
        return;
      }
      if (localInCallScreenMode == InCallUiState.InCallScreenMode.OTA_ENDED)
      {
        if (DBG)
          log("- updateScreen: OTA call ended state (NOT updating in-call UI)...");
        this.mCallCard.setVisibility(8);
        this.mApp.wakeUpScreen();
        if (this.mApp.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION)
        {
          if (DBG)
            log("- updateScreen: OTA_STATUS_ACTIVATION");
          if (this.mApp.otaUtils != null)
          {
            if (DBG)
              log("- updateScreen: mApp.otaUtils is not null, call otaShowActivationScreen");
            this.mApp.otaUtils.otaShowActivateScreen();
          }
        }
        else
        {
          if (DBG)
            log("- updateScreen: OTA Call end state for Dialogs");
          if (this.mApp.otaUtils != null)
          {
            if (DBG)
              log("- updateScreen: Show OTA Success Failure dialog");
            this.mApp.otaUtils.otaShowSuccessFailure();
          }
        }
      }
      else
      {
        if (localInCallScreenMode == InCallUiState.InCallScreenMode.MANAGE_CONFERENCE)
        {
          if (DBG)
            log("- updateScreen: manage conference mode (NOT updating in-call UI)...");
          this.mCallCard.setVisibility(8);
          updateManageConferencePanelIfNecessary();
          return;
        }
        if ((localInCallScreenMode == InCallUiState.InCallScreenMode.CALL_ENDED) && (DBG))
          log("- updateScreen: call ended state...");
        if (DBG)
          log("- updateScreen: updating the in-call UI...");
        updateInCallTouchUi();
        this.mCallCard.updateState(this.mCM);
        if (this.mCM.getState() == PhoneConstants.State.RINGING)
        {
          attachListeners();
          if (this.mDialer.isOpened())
          {
            Log.i("InCallScreen", "During RINGING state we force hiding dialpad.");
            closeDialpadInternal(false);
          }
          this.mDialer.clearDigits();
        }
        updateCallCardVisibilityPerDialerState(false);
        updateProgressIndication();
        if (this.mCM.hasActiveRingingCall())
        {
          dismissAllDialogs();
          return;
        }
        List localList = this.mCM.getFgCallConnections();
        i = this.mCM.getFgPhone().getPhoneType();
        if (i == 2)
        {
          Connection localConnection2 = this.mCM.getFgCallLatestConnection();
          if (this.mApp.cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.CONF_CALL)
          {
            Iterator localIterator2 = localList.iterator();
            while (localIterator2.hasNext())
            {
              Connection localConnection3 = (Connection)localIterator2.next();
              if ((localConnection3 != null) && (localConnection3.getPostDialState() == Connection.PostDialState.WAIT))
                localConnection3.cancelPostDial();
            }
          }
          else if ((localConnection2 != null) && (localConnection2.getPostDialState() == Connection.PostDialState.WAIT))
          {
            if (DBG)
              log("show the Wait dialog for CDMA");
            showWaitPromptDialog(localConnection2, localConnection2.getRemainingPostDialString());
          }
        }
        else
        {
          if ((i != 1) && (i != 3))
            break;
          Iterator localIterator1 = localList.iterator();
          while (localIterator1.hasNext())
          {
            Connection localConnection1 = (Connection)localIterator1.next();
            if ((localConnection1 != null) && (localConnection1.getPostDialState() == Connection.PostDialState.WAIT))
              showWaitPromptDialog(localConnection1, localConnection1.getRemainingPostDialString());
          }
        }
      }
    }
    throw new IllegalStateException("Unexpected phone type: " + i);
  }

  void connectBluetoothAudio()
  {
    if (this.mBluetoothHeadset != null)
      this.mBluetoothHeadset.connectAudio();
    this.mBluetoothConnectionPending = true;
    this.mBluetoothConnectionRequestTime = SystemClock.elapsedRealtime();
  }

  void disconnectBluetoothAudio()
  {
    if (this.mBluetoothHeadset != null)
      this.mBluetoothHeadset.disconnectAudio();
    this.mBluetoothConnectionPending = false;
  }

  public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent paramAccessibilityEvent)
  {
    super.dispatchPopulateAccessibilityEvent(paramAccessibilityEvent);
    this.mCallCard.dispatchPopulateAccessibilityEvent(paramAccessibilityEvent);
    return true;
  }

  public void endInCallScreenSession()
  {
    if (DBG)
      log("endInCallScreenSession()... phone state = " + this.mCM.getState());
    endInCallScreenSession(false);
  }

  public void finish()
  {
    if (DBG)
      log("finish()...");
    moveTaskToBack(true);
  }

  InCallTouchUi getInCallTouchUi()
  {
    return this.mInCallTouchUi;
  }

  public InCallControlState getUpdatedInCallControlState()
  {
    this.mInCallControlState.update();
    return this.mInCallControlState;
  }

  void handleOnscreenButtonClick(int paramInt)
  {
    if (DBG)
      log("handleOnscreenButtonClick(id " + paramInt + ")...");
    switch (paramInt)
    {
    default:
      Log.w("InCallScreen", "handleOnscreenButtonClick: unexpected ID " + paramInt);
    case 2131230728:
    case 2131230729:
    case 2131230730:
    case 2131230802:
    case 2131230803:
    case 2131230798:
    case 2131230799:
    case 2131230801:
    case 2131230805:
    case 2131230780:
    case 2131230806:
    case 2131230777:
    case 2131230808:
    }
    while (true)
    {
      this.mApp.pokeUserActivity();
      updateInCallTouchUi();
      return;
      internalAnswerCall();
      continue;
      hangupRingingCall();
      continue;
      internalRespondViaSms();
      continue;
      onHoldClick();
      continue;
      internalSwapCalls();
      continue;
      internalHangup();
      continue;
      onOpenCloseDialpad();
      continue;
      onMuteClick();
      continue;
      PhoneUtils.startNewCall(this.mCM);
      continue;
      PhoneUtils.mergeCalls(this.mCM);
      continue;
      setInCallScreenMode(InCallUiState.InCallScreenMode.MANAGE_CONFERENCE);
      requestUpdateScreen();
      continue;
      confirmAddBlacklist();
    }
  }

  public void handleOtaCallEnd()
  {
    if (DBG)
      log("handleOtaCallEnd entering");
    if (((this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL) || ((this.mApp.cdmaOtaScreenState != null) && (this.mApp.cdmaOtaScreenState.otaScreenState != OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_UNDEFINED))) && (this.mApp.cdmaOtaProvisionData != null) && (!this.mApp.cdmaOtaProvisionData.inOtaSpcState))
    {
      if (DBG)
        log("handleOtaCallEnd - Set OTA Call End stater");
      setInCallScreenMode(InCallUiState.InCallScreenMode.OTA_ENDED);
      updateScreen();
    }
  }

  void hangupRingingCall()
  {
    if (DBG)
      log("hangupRingingCall()...");
    PhoneUtils.hangupRingingCall(this.mCM.getFirstActiveRingingCall());
  }

  boolean isBluetoothAudioConnected()
  {
    if (this.mBluetoothHeadset == null);
    List localList;
    do
    {
      return false;
      localList = this.mBluetoothHeadset.getConnectedDevices();
    }
    while (localList.isEmpty());
    BluetoothDevice localBluetoothDevice = (BluetoothDevice)localList.get(0);
    return this.mBluetoothHeadset.isAudioConnected(localBluetoothDevice);
  }

  boolean isBluetoothAudioConnectedOrPending()
  {
    if (isBluetoothAudioConnected());
    do
    {
      return true;
      if (!this.mBluetoothConnectionPending)
        break;
    }
    while (SystemClock.elapsedRealtime() - this.mBluetoothConnectionRequestTime < 5000L);
    this.mBluetoothConnectionPending = false;
    return false;
    return false;
  }

  boolean isBluetoothAvailable()
  {
    BluetoothHeadset localBluetoothHeadset = this.mBluetoothHeadset;
    boolean bool = false;
    if (localBluetoothHeadset != null)
    {
      List localList = this.mBluetoothHeadset.getConnectedDevices();
      int i = localList.size();
      bool = false;
      if (i > 0)
      {
        ((BluetoothDevice)localList.get(0));
        bool = true;
      }
    }
    return bool;
  }

  boolean isDialerOpened()
  {
    return (this.mDialer != null) && (this.mDialer.isOpened());
  }

  boolean isForegroundActivity()
  {
    return this.mIsForegroundActivity;
  }

  boolean isForegroundActivityForProximity()
  {
    return this.mIsForegroundActivityForProximity;
  }

  boolean isManageConferenceMode()
  {
    return this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.MANAGE_CONFERENCE;
  }

  public boolean isOtaCallInActiveState()
  {
    return (this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL) || ((this.mApp.cdmaOtaScreenState != null) && (this.mApp.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION));
  }

  public boolean isOtaCallInEndState()
  {
    return this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_ENDED;
  }

  public boolean isPhoneStateRestricted()
  {
    int i = this.mCM.getServiceState();
    return (i == 2) || (i == 1) || (this.mApp.getKeyguardManager().inKeyguardRestrictedInputMode());
  }

  public boolean isQuickResponseDialogShowing()
  {
    return (this.mRespondViaSmsManager != null) && (this.mRespondViaSmsManager.isShowingPopup());
  }

  boolean okToDialDTMFTones()
  {
    boolean bool = this.mCM.hasActiveRingingCall();
    Call.State localState = this.mCM.getActiveFgCallState();
    return ((localState == Call.State.ACTIVE) || (localState == Call.State.ALERTING)) && (!bool) && (this.mApp.inCallUiState.inCallScreenMode != InCallUiState.InCallScreenMode.MANAGE_CONFERENCE);
  }

  boolean okToShowDialpad()
  {
    Call.State localState = this.mCM.getActiveFgCallState();
    return (okToDialDTMFTones()) || (localState == Call.State.DIALING);
  }

  public void onBackPressed()
  {
    if (DBG)
      log("onBackPressed()...");
    if (this.mCM.hasActiveRingingCall())
    {
      if (DBG)
        log("BACK key while ringing: ignored");
      return;
    }
    if (this.mDialer.isOpened())
    {
      closeDialpadInternal(true);
      return;
    }
    if (this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.MANAGE_CONFERENCE)
    {
      setInCallScreenMode(InCallUiState.InCallScreenMode.NORMAL);
      requestUpdateScreen();
      return;
    }
    super.onBackPressed();
  }

  public void onClick(View paramView)
  {
    int i = paramView.getId();
    Object[] arrayOfObject;
    switch (i)
    {
    default:
      if (((this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL) || (this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_ENDED)) && (this.mApp.otaUtils != null))
      {
        this.mApp.otaUtils.onClickHandler(i);
        arrayOfObject = new Object[1];
        if (!(paramView instanceof TextView))
          break label204;
      }
      break;
    case 2131230814:
    case 2131230857:
    }
    label204: for (Object localObject = ((TextView)paramView).getText(); ; localObject = "")
    {
      arrayOfObject[0] = localObject;
      EventLog.writeEvent(70303, arrayOfObject);
      this.mApp.pokeUserActivity();
      return;
      setInCallScreenMode(InCallUiState.InCallScreenMode.NORMAL);
      requestUpdateScreen();
      break;
      if (!this.mInCallControlState.canSwap)
        break;
      internalSwapCalls();
      break;
      Log.w("InCallScreen", "onClick: unexpected click from ID " + i + " (View = " + paramView + ")");
      break;
    }
  }

  public void onConfigurationChanged(Configuration paramConfiguration)
  {
    int i = 1;
    if (DBG)
      log("onConfigurationChanged: newConfig = " + paramConfiguration);
    super.onConfigurationChanged(paramConfiguration);
    if (paramConfiguration.keyboardHidden == i)
    {
      int k = i;
      if (DBG)
        log("  - isKeyboardOpen = " + k);
      if (paramConfiguration.orientation != 2)
        break label154;
    }
    while (true)
    {
      if (DBG)
        log("  - isLandscape = " + i);
      if (DBG)
        log("  - uiMode = " + paramConfiguration.uiMode);
      return;
      int m = 0;
      break;
      label154: int j = 0;
    }
  }

  protected void onCreate(Bundle paramBundle)
  {
    Log.i("InCallScreen", "onCreate()...  this = " + this);
    Profiler.callScreenOnCreate();
    super.onCreate(paramBundle);
    if (!PhoneGlobals.sVoiceCapable)
    {
      Log.wtf("InCallScreen", "onCreate() reached on non-voice-capable device");
      finish();
      return;
    }
    this.mApp = PhoneGlobals.getInstance();
    this.mApp.setInCallScreenInstance(this);
    int i = 2621440;
    if (this.mApp.getPhoneState() == PhoneConstants.State.OFFHOOK)
      i |= 4194304;
    WindowManager.LayoutParams localLayoutParams = getWindow().getAttributes();
    localLayoutParams.flags = (i | localLayoutParams.flags);
    if (!this.mApp.proximitySensorModeEnabled())
      localLayoutParams.inputFeatures = (0x4 | localLayoutParams.inputFeatures);
    getWindow().setAttributes(localLayoutParams);
    setPhone(this.mApp.phone);
    this.mCM = this.mApp.mCM;
    log("- onCreate: phone state = " + this.mCM.getState());
    this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (this.mBluetoothAdapter != null)
      this.mBluetoothAdapter.getProfileProxy(getApplicationContext(), this.mBluetoothProfileServiceListener, 1);
    requestWindowFeature(1);
    setContentView(2130968591);
    if (this.mPhone.getPhoneType() == 2);
    for (int j = 2131230792; ; j = 2131230791)
    {
      ViewStub localViewStub = (ViewStub)findViewById(j);
      if (localViewStub != null)
        localViewStub.inflate();
      initInCallScreen();
      registerForPhoneStates();
      if (paramBundle == null)
      {
        if (DBG)
          log("onCreate(): this is our very first launch, checking intent...");
        internalResolveIntent(getIntent());
      }
      Profiler.callScreenCreated();
      if (!DBG)
        break;
      log("onCreate(): exit");
      return;
    }
  }

  protected void onDestroy()
  {
    Log.i("InCallScreen", "onDestroy()...  this = " + this);
    super.onDestroy();
    this.mIsDestroyed = true;
    this.mApp.setInCallScreenInstance(null);
    if (this.mCallCard != null)
      this.mCallCard.setInCallScreenInstance(null);
    if (this.mInCallTouchUi != null)
      this.mInCallTouchUi.setInCallScreenInstance(null);
    this.mRespondViaSmsManager.setInCallScreenInstance(null);
    this.mDialer.clearInCallScreenReference();
    this.mDialer = null;
    unregisterForPhoneStates();
    if (this.mBluetoothHeadset != null)
    {
      this.mBluetoothAdapter.closeProfileProxy(1, this.mBluetoothHeadset);
      this.mBluetoothHeadset = null;
    }
    dismissAllDialogs();
    if (this.mApp.otaUtils != null)
      this.mApp.otaUtils.clearUiWidgets();
  }

  void onDialerClose(boolean paramBoolean)
  {
    if (DBG)
      log("onDialerClose()...");
    if (((this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL) || (this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_ENDED) || ((this.mApp.cdmaOtaScreenState != null) && (this.mApp.cdmaOtaScreenState.otaScreenState == OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION))) && (this.mApp.otaUtils != null))
      this.mApp.otaUtils.otaShowProperScreen();
    updateInCallTouchUi();
    updateCallCardVisibilityPerDialerState(paramBoolean);
    this.mApp.pokeUserActivity();
  }

  void onDialerOpen(boolean paramBoolean)
  {
    if (DBG)
      log("onDialerOpen()...");
    updateInCallTouchUi();
    updateCallCardVisibilityPerDialerState(paramBoolean);
    this.mApp.pokeUserActivity();
    if (((this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_NORMAL) || (this.mApp.inCallUiState.inCallScreenMode == InCallUiState.InCallScreenMode.OTA_ENDED)) && (this.mApp.otaUtils != null))
      this.mApp.otaUtils.hideOtaScreen();
  }

  public boolean onKeyDown(int paramInt, KeyEvent paramKeyEvent)
  {
    switch (paramInt)
    {
    case 70:
    case 76:
    default:
    case 27:
    case 5:
    case 24:
    case 25:
    case 164:
    case 91:
    }
    while ((paramKeyEvent.getRepeatCount() == 0) && (handleDialerKeyDown(paramInt, paramKeyEvent)))
    {
      do
        return true;
      while (handleCallKey());
      Log.w("InCallScreen", "InCallScreen should always handle KEYCODE_CALL in onKeyDown");
      return true;
      if (this.mCM.getState() == PhoneConstants.State.RINGING)
      {
        Log.w("InCallScreen", "VOLUME key: incoming call is ringing! (PhoneWindowManager should have handled this key.)");
        internalSilenceRinger();
        return true;
        onMuteClick();
        return true;
      }
    }
    return super.onKeyDown(paramInt, paramKeyEvent);
  }

  public boolean onKeyUp(int paramInt, KeyEvent paramKeyEvent)
  {
    if ((this.mDialer != null) && (this.mDialer.onDialerKeyUp(paramKeyEvent)));
    while (paramInt == 5)
      return true;
    return super.onKeyUp(paramInt, paramKeyEvent);
  }

  protected void onNewIntent(Intent paramIntent)
  {
    log("onNewIntent: intent = " + paramIntent + ", phone state = " + this.mCM.getState());
    setIntent(paramIntent);
    internalResolveIntent(paramIntent);
  }

  protected void onPause()
  {
    if (DBG)
      log("onPause()...");
    super.onPause();
    detachListeners();
    if (this.mPowerManager.isScreenOn())
      this.mIsForegroundActivityForProximity = false;
    this.mIsForegroundActivity = false;
    this.mApp.inCallUiState.providerInfoVisible = false;
    this.mApp.inCallUiState.showAlreadyDisconnectedState = false;
    this.mApp.setBeginningCall(false);
    this.mManageConferenceUtils.stopConferenceTime();
    this.mDialer.onDialerKeyUp(null);
    this.mDialer.stopDialerSession();
    if ((this.mHandler.hasMessages(108)) && (this.mCM.getState() != PhoneConstants.State.RINGING))
    {
      if (DBG)
        log("DELAYED_CLEANUP_AFTER_DISCONNECT detected, moving UI to background.");
      endInCallScreenSession();
    }
    EventLog.writeEvent(70302, new Object[0]);
    dismissAllDialogs();
    updateExpandedViewState();
    this.mApp.notificationMgr.updateInCallNotification();
    this.mApp.notificationMgr.statusBarHelper.enableSystemBarNavigation(true);
    unregisterReceiver(this.mReceiver);
    this.mApp.updateWakeState();
    updateKeyguardPolicy(false);
    if ((this.mApp.getUpdateLock().isHeld()) && (this.mApp.getPhoneState() == PhoneConstants.State.IDLE))
    {
      if (DBG)
        log("Release UpdateLock on onPause() because there's no active phone call.");
      this.mApp.getUpdateLock().release();
    }
  }

  protected void onResume()
  {
    if (DBG)
      log("onResume()...");
    super.onResume();
    this.mFlipAction = PhoneUtils.PhoneSettings.flipAction(this);
    this.mIsForegroundActivity = true;
    this.mIsForegroundActivityForProximity = true;
    if ((this.mCM.hasActiveFgCall()) || (this.mCM.hasActiveBgCall()) || (this.mCM.hasActiveRingingCall()))
      this.mApp.inCallUiState.showAlreadyDisconnectedState = false;
    InCallUiState localInCallUiState = this.mApp.inCallUiState;
    updateExpandedViewState();
    this.mApp.notificationMgr.updateInCallNotification();
    registerReceiver(this.mReceiver, new IntentFilter("android.intent.action.HEADSET_PLUG"));
    this.mDialer.startDialerSession();
    if (localInCallUiState.showDialpad)
    {
      openDialpadInternal(false);
      this.mDialer.setDialpadContext(localInCallUiState.dialpadContextText);
      this.mRespondViaSmsManager.dismissPopup();
      boolean bool1 = localInCallUiState.hasPendingCallStatusCode();
      int i = 0;
      if (bool1)
      {
        if (DBG)
          log("- onResume: need to show status indication!");
        showStatusIndication(localInCallUiState.getPendingCallStatusCode());
        i = 1;
      }
      if (!isBluetoothAudioConnected())
        break label476;
      setVolumeControlStream(6);
      label203: takeKeyEvents(true);
      boolean bool2 = TelephonyCapabilities.supportsOtasp(this.mPhone);
      boolean bool3 = false;
      if (bool2)
        bool3 = checkOtaspStateOnResume();
      if (!bool3)
        setInCallScreenMode(InCallUiState.InCallScreenMode.NORMAL);
      this.mCM.clearDisconnected();
      SyncWithPhoneStateStatus localSyncWithPhoneStateStatus = syncWithPhoneState();
      if (localSyncWithPhoneStateStatus == SyncWithPhoneStateStatus.SUCCESS)
        break label504;
      if (DBG)
        log("- onResume: syncWithPhoneState failed! status = " + localSyncWithPhoneStateStatus);
      if (i == 0)
        break label484;
      Log.i("InCallScreen", "  ==> syncWithPhoneState failed, but staying here anyway.");
    }
    label476: label484: label504: 
    while ((!TelephonyCapabilities.supportsOtasp(this.mPhone)) || ((localInCallUiState.inCallScreenMode != InCallUiState.InCallScreenMode.OTA_NORMAL) && (localInCallUiState.inCallScreenMode != InCallUiState.InCallScreenMode.OTA_ENDED)))
    {
      EventLog.writeEvent(70301, new Object[0]);
      this.mApp.updateWakeState();
      if (this.mApp.getRestoreMuteOnInCallResume())
      {
        PhoneUtils.restoreMuteState();
        this.mApp.setRestoreMuteOnInCallResume(false);
      }
      Profiler.profileViewCreate(getWindow(), InCallScreen.class.getName());
      if ((!this.mPhone.getPendingMmiCodes().isEmpty()) && (this.mMmiStartedDialog == null))
        this.mMmiStartedDialog = PhoneUtils.displayMMIInitiate(this, (MmiCode)this.mPhone.getPendingMmiCodes().get(0), Message.obtain(this.mHandler, 53), this.mMmiStartedDialog);
      if (this.mApp.inCallUiState.showAlreadyDisconnectedState)
      {
        log("onResume(): detected \"show already disconnected state\" situation. set up DELAYED_CLEANUP_AFTER_DISCONNECT message with 2000 msec delay.");
        this.mHandler.removeMessages(108);
        this.mHandler.sendEmptyMessageDelayed(108, 2000L);
      }
      return;
      closeDialpadInternal(false);
      break;
      setVolumeControlStream(0);
      break label203;
      Log.i("InCallScreen", "  ==> syncWithPhoneState failed; bailing out!");
      dismissAllDialogs();
      endInCallScreenSession(true);
      return;
    }
    if (this.mCallCard != null)
      this.mCallCard.setVisibility(8);
    updateScreen();
  }

  protected void onStop()
  {
    if (DBG)
      log("onStop()...");
    super.onStop();
    stopTimer();
    PhoneConstants.State localState = this.mCM.getState();
    if (DBG)
      log("onStop: state = " + localState);
    if (localState == PhoneConstants.State.IDLE)
    {
      if (this.mRespondViaSmsManager.isShowingPopup())
        this.mRespondViaSmsManager.dismissPopup();
      if ((this.mApp.cdmaOtaProvisionData != null) && (this.mApp.cdmaOtaScreenState != null) && (this.mApp.cdmaOtaScreenState.otaScreenState != OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION) && (this.mApp.cdmaOtaScreenState.otaScreenState != OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_SUCCESS_FAILURE_DLG) && (!this.mApp.cdmaOtaProvisionData.inOtaSpcState))
      {
        if (DBG)
          log("- onStop: calling finish() to clear activity history...");
        moveTaskToBack(true);
        if (this.mApp.otaUtils != null)
          this.mApp.otaUtils.cleanOtaScreen(true);
      }
    }
  }

  void onSuppServiceFailed(AsyncResult paramAsyncResult)
  {
    Phone.SuppService localSuppService = (Phone.SuppService)paramAsyncResult.result;
    if (DBG)
      log("onSuppServiceFailed: " + localSuppService);
    int i;
    switch (19.$SwitchMap$com$android$internal$telephony$Phone$SuppService[localSuppService.ordinal()])
    {
    default:
      i = 2131296693;
    case 1:
    case 2:
    case 3:
    case 4:
    case 5:
    case 6:
    }
    while (true)
    {
      if (this.mSuppServiceFailureDialog != null)
      {
        if (DBG)
          log("- DISMISSING mSuppServiceFailureDialog.");
        this.mSuppServiceFailureDialog.dismiss();
        this.mSuppServiceFailureDialog = null;
      }
      this.mSuppServiceFailureDialog = new AlertDialog.Builder(this).setMessage(i).setPositiveButton(2131296370, null).create();
      this.mSuppServiceFailureDialog.getWindow().addFlags(4);
      this.mSuppServiceFailureDialog.show();
      return;
      i = 2131296694;
      continue;
      i = 2131296695;
      continue;
      i = 2131296696;
      continue;
      i = 2131296697;
      continue;
      i = 2131296698;
      continue;
      i = 2131296699;
    }
  }

  public void onWindowFocusChanged(boolean paramBoolean)
  {
    if ((!paramBoolean) && (this.mDialer != null))
      this.mDialer.onDialerKeyUp(null);
  }

  void requestCloseOtaFailureNotice(long paramLong)
  {
    if (DBG)
      log("requestCloseOtaFailureNotice() with timeout: " + paramLong);
    this.mHandler.sendEmptyMessageDelayed(119, paramLong);
  }

  void requestCloseSpcErrorNotice(long paramLong)
  {
    if (DBG)
      log("requestCloseSpcErrorNotice() with timeout: " + paramLong);
    this.mHandler.sendEmptyMessageDelayed(118, paramLong);
  }

  void requestRemoveProviderInfoWithDelay()
  {
    this.mHandler.removeMessages(121);
    Message localMessage = Message.obtain(this.mHandler, 121);
    this.mHandler.sendMessageDelayed(localMessage, 5000L);
    if (DBG)
      log("Requested to remove provider info after 5000 msec.");
  }

  void requestUpdateBluetoothIndication()
  {
    this.mHandler.removeMessages(114);
    this.mHandler.sendEmptyMessage(114);
  }

  void requestUpdateScreen()
  {
    if (DBG)
      log("requestUpdateScreen()...");
    this.mHandler.removeMessages(122);
    this.mHandler.sendEmptyMessage(122);
  }

  public void resetInCallScreenMode()
  {
    if (DBG)
      log("resetInCallScreenMode: setting mode to UNDEFINED...");
    setInCallScreenMode(InCallUiState.InCallScreenMode.UNDEFINED);
  }

  void setPhone(Phone paramPhone)
  {
    this.mPhone = paramPhone;
  }

  public void switchInCallAudio(InCallAudioMode paramInCallAudioMode)
  {
    log("switchInCallAudio: new mode = " + paramInCallAudioMode);
    switch (19.$SwitchMap$com$android$phone$InCallScreen$InCallAudioMode[paramInCallAudioMode.ordinal()])
    {
    default:
      Log.wtf("InCallScreen", "switchInCallAudio: unexpected mode " + paramInCallAudioMode);
    case 1:
    case 2:
    case 3:
    }
    while (true)
    {
      updateInCallTouchUi();
      return;
      if (!PhoneUtils.isSpeakerOn(this))
      {
        if ((isBluetoothAvailable()) && (isBluetoothAudioConnected()))
          disconnectBluetoothAudio();
        PhoneUtils.turnOnSpeaker(this, true, true);
        AudioManager localAudioManager3 = (AudioManager)getSystemService("audio");
        int k = localAudioManager3.getStreamVolume(0);
        localAudioManager3.setStreamVolume(0, 0, 0);
        localAudioManager3.setStreamVolume(0, k, 0);
        continue;
        if ((isBluetoothAvailable()) && (!isBluetoothAudioConnected()))
        {
          if (PhoneUtils.isSpeakerOn(this))
          {
            PhoneUtils.turnOnSpeaker(this, false, true);
            AudioManager localAudioManager2 = (AudioManager)getSystemService("audio");
            int j = localAudioManager2.getStreamVolume(0);
            localAudioManager2.setStreamVolume(0, 0, 0);
            localAudioManager2.setStreamVolume(0, j, 0);
          }
          connectBluetoothAudio();
          continue;
          if ((isBluetoothAvailable()) && (isBluetoothAudioConnected()))
            disconnectBluetoothAudio();
          if (PhoneUtils.isSpeakerOn(this))
            PhoneUtils.turnOnSpeaker(this, false, true);
          AudioManager localAudioManager1 = (AudioManager)getSystemService("audio");
          int i = localAudioManager1.getStreamVolume(0);
          localAudioManager1.setStreamVolume(0, 0, 0);
          localAudioManager1.setStreamVolume(0, i, 0);
        }
      }
    }
  }

  public void toggleSpeaker()
  {
    if (!PhoneUtils.isSpeakerOn(this));
    for (boolean bool = true; ; bool = false)
    {
      log("toggleSpeaker(): newSpeakerState = " + bool);
      if ((bool) && (isBluetoothAvailable()) && (isBluetoothAudioConnected()))
        disconnectBluetoothAudio();
      PhoneUtils.turnOnSpeaker(this, bool, true);
      AudioManager localAudioManager = (AudioManager)getSystemService("audio");
      int i = localAudioManager.getStreamVolume(0);
      localAudioManager.setStreamVolume(0, 0, 0);
      localAudioManager.setStreamVolume(0, i, 0);
      updateInCallTouchUi();
      return;
    }
  }

  void updateAfterRadioTechnologyChange()
  {
    if (DBG)
      Log.d("InCallScreen", "updateAfterRadioTechnologyChange()...");
    resetInCallScreenMode();
    unregisterForPhoneStates();
    registerForPhoneStates();
    requestUpdateScreen();
  }

  void updateButtonStateOutsideInCallTouchUi()
  {
    if (this.mCallCard != null)
      this.mCallCard.setSecondaryCallClickable(this.mInCallControlState.canSwap);
  }

  void updateIncomingCallWidgetHint(int paramInt1, int paramInt2)
  {
    if (this.mCallCard != null)
    {
      this.mCallCard.setIncomingCallWidgetHint(paramInt1, paramInt2);
      this.mCallCard.updateState(this.mCM);
    }
  }

  void updateKeyguardPolicy(boolean paramBoolean)
  {
    if (paramBoolean)
    {
      getWindow().addFlags(4194304);
      return;
    }
    getWindow().clearFlags(4194304);
  }

  public static enum InCallAudioMode
  {
    static
    {
      BLUETOOTH = new InCallAudioMode("BLUETOOTH", 1);
      EARPIECE = new InCallAudioMode("EARPIECE", 2);
      InCallAudioMode[] arrayOfInCallAudioMode = new InCallAudioMode[3];
      arrayOfInCallAudioMode[0] = SPEAKER;
      arrayOfInCallAudioMode[1] = BLUETOOTH;
      arrayOfInCallAudioMode[2] = EARPIECE;
    }
  }

  private static abstract interface ResettableSensorEventListener extends SensorEventListener
  {
    public abstract void reset();
  }

  private static enum SyncWithPhoneStateStatus
  {
    static
    {
      PHONE_NOT_IN_USE = new SyncWithPhoneStateStatus("PHONE_NOT_IN_USE", 1);
      SyncWithPhoneStateStatus[] arrayOfSyncWithPhoneStateStatus = new SyncWithPhoneStateStatus[2];
      arrayOfSyncWithPhoneStateStatus[0] = SUCCESS;
      arrayOfSyncWithPhoneStateStatus[1] = PHONE_NOT_IN_USE;
    }
  }
}

/* Location:           C:\Users\Win-7\Desktop\classes-dex2jar.jar
 * Qualified Name:     com.android.phone.InCallScreen
 * JD-Core Version:    0.6.2
 */