package com.bpcreates.flomio_service;


import java.util.Calendar;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;


public class MainActivity extends Activity {

	public static final String SM_BCAST_SCAN = "com.restock.serialmagic.gears.action.SCAN";
	IntentFilter filter = new IntentFilter(SM_BCAST_SCAN);
    @Override
    protected void onResume() { 
        super.onResume();
        L.d("calling service constructor");
        
        Intent i = new Intent(MainActivity.this, FJNFCService.class);
        try{
        	PendingIntent.getService(MainActivity.this, 0, i, 0).send();
        }catch(Exception e){
        	L.e("fail " + e);
        }
        
        registerReceiver(scanReceiver, filter);        
    }  
    
    public void onPause(){
    	super.onPause();
    	unregisterReceiver(scanReceiver);
    }
    
    
    BroadcastReceiver scanReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String s = intent.getAction();
			if (s.equals(SM_BCAST_SCAN)) {
				try{
					String scan = intent.getStringExtra("scan");
					Toast.makeText(MainActivity.this, "GOT SCAN " + scan, Toast.LENGTH_LONG).show();
				}catch(Exception e){
					Toast.makeText(MainActivity.this, "something went wrong: " + e, Toast.LENGTH_LONG).show();
				}				
			}			
		}
	};
}