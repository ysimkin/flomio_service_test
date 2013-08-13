package com.bpcreates.flomio_service;


import android.content.Intent;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Bundle;


public class MainActivity extends Activity {

    //public FJNFCService mFJNFCService;
    //public UIListener mUIListener;
    
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        L.d("calling service constructor");
        
        Intent i = new Intent(MainActivity.this, FJNFCService.class);
        try{
        	PendingIntent.getService(MainActivity.this, 0, i, 0).send();
        }catch(Exception e){
        	L.e("fail " + e);
        }
        finish();
    }    
}