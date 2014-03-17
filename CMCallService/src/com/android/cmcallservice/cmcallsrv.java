package com.android.cmcallservice;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
//import android.widget.Toast;

public class cmcallsrv extends Service {
	Boolean Hooked = false, Speak = false;
	AudioManager audioManager;
	TelephonyManager telephonyManager;
	int vmax = 100;
	private IntentFilter mCallStateChangedFilter;
    private BroadcastReceiver mCallStateIntentReceiver;
    final Handler schandler = new Handler();
	final Handler vmhandler = new Handler();

    
    Runnable myscan = new Runnable() { 
        public void run() {        	
        	scanForSpeak();
        }   
    };

    Runnable myvolmax = new Runnable() { 
        public void run() {        	
        	mySetMaxVolume();
        }   
    };
    
        
    public void scanForSpeak() {
    	if (((!Speak) && (audioManager.isSpeakerphoneOn())) || ((Speak) && (!audioManager.isSpeakerphoneOn()))) {
    		Speak = audioManager.isSpeakerphoneOn();
    		CorrectMaxVolume();
    	}
    	schandler.postDelayed(myscan, 500);
    }
    
    public void mySetMaxVolume() {
		 audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, vmax, 0);
		 Log.i("cmcallservice", "curr. vol: "+Integer.toString(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)));
		 schandler.postDelayed(myscan, 500);
	 }
		 
	 public void CorrectMaxVolume() {	
    	audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 1, 0);    	
    	Log.i("cmcallservice", "curr. vol: "+Integer.toString(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)));
    	vmhandler.postDelayed(myvolmax, 200);
    	    	     	
	 }
	 
	 @Override
	 public IBinder onBind(Intent intent) {
	    // TODO: Return the communication channel to the service.
	     throw new UnsupportedOperationException("Not yet implemented");
	 }
	 
	 public cmcallsrv() {
		 
	 }
	 
	 @Override
	 public void onCreate() {
	    //Toast.makeText(this, "The new Service was Created", Toast.LENGTH_SHORT).show();
		mCallStateChangedFilter = new IntentFilter();
	    mCallStateChangedFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
	    mCallStateIntentReceiver = new BroadcastReceiver() {
	    	@Override
	    	public void onReceive(Context context, Intent intent) {
	    		audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
	    		vmax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
	    		telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
	    		telephonyManager.listen(new CustomPhoneStateListener(context), PhoneStateListener.LISTEN_CALL_STATE);
	    	}
	   };
	 }
	 
	 @Override
	 public void onStart(Intent intent, int startId) {
		 //Toast.makeText(this, "The new Service was Started", Toast.LENGTH_SHORT).show();
		 registerReceiver(mCallStateIntentReceiver, mCallStateChangedFilter);
	 }
	 
	 @Override
	 public void onDestroy() {
	    	unregisterReceiver(mCallStateIntentReceiver);	    	
	 }
	 
	 public class CustomPhoneStateListener extends PhoneStateListener {
	    	
	        //private static final String TAG = "PhoneStateChanged";
	        Context context; //Context to make Toast if required 
	        public CustomPhoneStateListener(Context context) {
	            super();
	            this.context = context;
	        }

	        @Override
	        public void onCallStateChanged(int state, String incomingNumber) {
	            super.onCallStateChanged(state, incomingNumber);	            
	            switch (state) {
	            case TelephonyManager.CALL_STATE_IDLE:
	            	//when Idle i.e no call	            	
	            	//Toast.makeText(context, "Phone state Idle", Toast.LENGTH_SHORT).show();
	            	Hooked = false;
	            	vmhandler.removeCallbacks(myvolmax);
	            	schandler.removeCallbacks(myscan);
	            	break;
	            case TelephonyManager.CALL_STATE_OFFHOOK:
	            	//when Off hook i.e in call
	            	//Make intent and start your service here           	
	            	//Toast.makeText(context, "Phone state Off hook", Toast.LENGTH_SHORT).show();            		
	            	Speak = audioManager.isSpeakerphoneOn();
	            	if (!Hooked) {CorrectMaxVolume();}
	            	Hooked = true;
	            	break;
	            case TelephonyManager.CALL_STATE_RINGING:
	            	//when Ringing	            	
	            	//Toast.makeText(context, "Phone state Ringing", Toast.LENGTH_SHORT).show();
	            	break;
	            default:
	            	break;
	            }
	        }	        
	    }
}
