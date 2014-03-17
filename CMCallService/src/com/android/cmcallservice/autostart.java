package com.android.cmcallservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class autostart extends BroadcastReceiver 
{
    public void onReceive(Context arg0, Intent arg1) 
    {
    	Intent intent = new Intent(arg0,cmcallsrv.class);
        arg0.startService(intent);
    }
    
}