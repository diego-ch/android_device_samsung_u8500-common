package com.android.cmcallservice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
//import android.util.Log;

public class NoActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setVisible(false);
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.activity_no);
		startService(new Intent(this, cmcallsrv.class));
		finish();
		
	}
}
