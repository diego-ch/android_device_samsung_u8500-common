package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public class PhoneReceiver extends BroadcastReceiver
{
  public void onReceive(Context paramContext, Intent paramIntent)
  {
    ((TelephonyManager)paramContext.getSystemService("phone")).listen(new StateListener(paramContext), 32);
  }
}

/* Location:           C:\Users\Win-7\Desktop\classes-dex2jar.jar
 * Qualified Name:     com.android.phone.PhoneReceiver
 * JD-Core Version:    0.6.2
 */