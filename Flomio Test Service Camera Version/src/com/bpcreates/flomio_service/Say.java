package com.bpcreates.flomio_service;

import android.content.Context;
import android.widget.Toast;

public class Say {
	public static void t(Context c, String s){
		Toast.makeText(c, s, Toast.LENGTH_LONG).show();
	}
}
