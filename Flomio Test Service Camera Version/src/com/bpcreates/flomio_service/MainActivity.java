package com.bpcreates.flomio_service;


import versionchecker.VersionChecker;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;


public class MainActivity extends Activity {

	public static final String SM_BCAST_SCAN = "com.restock.serialmagic.gears.action.SCAN";
	IntentFilter filter = new IntentFilter(SM_BCAST_SCAN);
	
	public static final String FLOMIO_PING = "com.bpcreates.FLOMIO_PING";
	public static final String FLOMIO_PONG = "com.bpcreates.FLOMIO_PONG";
	IntentFilter pingFilter = new IntentFilter(FLOMIO_PING);
	
	@Override 
	public void onCreate(Bundle b){
		super.onCreate(b);
		registerReceiver(pingReceiver, pingFilter);
		
		Constants.sendStationInfoToServer(MainActivity.this, null, new PostSCCallback(MainActivity.this){
			@Override
			public void run(){
//				super.run();
				new Handler().post(new VersionChecker(MainActivity.this, getResponseBodyJSON(), null));
			}
		});
		
	}
	
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
        L.d("PONGING BACK");			
		Intent pong = new Intent();
		pong.setAction(FLOMIO_PONG);
        sendBroadcast(pong);
        
        registerReceiver(scanReceiver, filter);        
    }  
    
    public void onPause(){
    	super.onPause();
    	unregisterReceiver(scanReceiver);
    }
    
    public void onDestroy(){
    	super.onDestroy();
    	try{
    		unregisterReceiver(pingReceiver);
    	}catch(Exception e){
    		
    	}
    }
    
    
    BroadcastReceiver scanReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String s = intent.getAction();
			if (s.equals(SM_BCAST_SCAN)) {
				try{
					if(intent.hasExtra("error")){
						Toast.makeText(MainActivity.this, "Tag scan error, please try again", Toast.LENGTH_LONG).show();
						L.d("GOT ERROR BROADCAST: " + intent.getStringExtra("error") );
						return;
					}
					else if(intent.hasExtra("scan")){
						String scan = intent.getStringExtra("scan");
						Toast.makeText(MainActivity.this, "GOT SCAN " + scan, Toast.LENGTH_LONG).show();
						L.d("BROADCAST RECEIVED SCAN VALUE OF " + scan);
					}
				}catch(Exception e){
					Toast.makeText(MainActivity.this, "something went wrong: " + e, Toast.LENGTH_LONG).show();
				}				
			}
		}
	};
	
	BroadcastReceiver pingReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String s = intent.getAction();
			if(s.equals(FLOMIO_PING)){				
				if(getSharedPreferences(getPackageName(), MODE_PRIVATE).getBoolean(FJNFCService.AM_ALIVE, false) == false){
					Intent serviceIntent = new Intent(MainActivity.this, FJNFCService.class);
					try{
						PendingIntent.getService(MainActivity.this, 0, serviceIntent, 0).send();
					}catch(Exception e){
						L.e("unable to start flomio service "+ e);
					}
				}
				L.d("PONGING BACK FROM RECEIVER");			
				Intent i = new Intent();
		        i.setAction(FLOMIO_PONG);
		        sendBroadcast(i);
			}
		}
	};
}