package com.android.phone;

import android.content.Context;
import android.media.AudioManager;
import android.telephony.PhoneStateListener;

public class StateListener extends PhoneStateListener
{
  private Context mContext;

  public StateListener(Context paramContext)
  {
    this.mContext = paramContext;
  }

  public void onCallStateChanged(int paramInt, String paramString)
  {
    super.onCallStateChanged(paramInt, paramString);
    if (paramInt == 2)
      new Thread(new Runnable()
      {
        public void run()
        {
          AudioManager localAudioManager = (AudioManager)StateListener.this.mContext.getSystemService("audio");
          localAudioManager.getStreamVolume(0);
          localAudioManager.setStreamVolume(0, 0, 0);
          try
          {
            Thread.sleep(200L);
            label35: localAudioManager.setStreamVolume(0, 5, 0);
            return;
          }
          catch (Exception localException)
          {
            break label35;
          }
        }
      }).start();
  }
}

/* Location:           C:\Users\Win-7\Desktop\classes-dex2jar.jar
 * Qualified Name:     com.android.phone.StateListener
 * JD-Core Version:    0.6.2
 */