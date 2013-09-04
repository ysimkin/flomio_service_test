package com.bpcreates.flomio_service;

import android.util.Log;

public class L {
	public static void d(String str){		
		Log.d("ALEX_DEBUG", str);		
	}
	public static void e(String str){		
		Log.e("ALEX_DEBUG", str);		
	}
	public static void i(String str){
		Log.i("ALEX_DEBUG", str);
	}
	public static void v(String str){
		Log.v("ALEX_DEBUG", str);
	}
	public static void w(String str){
		Log.w("ALEX_DEBUG", str);
	}
	public static void s(String str){
		Log.wtf("ALEX_DEBUG", str);
	}
	
	
	public static void d(String prepend, String str){		
		Log.d(prepend+ "_" + "ALEX_DEBUG".toLowerCase(), str);		
	}
	public static void e(String prepend, String str){		
		Log.e(prepend+ "_" + "ALEX_DEBUG".toLowerCase(), str);		
	}
	public static void i(String prepend, String str){
		Log.i(prepend+ "_" + "ALEX_DEBUG".toLowerCase(), str);
	}
	public static void v(String prepend, String str){
		Log.v(prepend+ "_" + "ALEX_DEBUG".toLowerCase(), str);
	}
	public static void w(String prepend, String str){
		Log.w(prepend+ "_" + "ALEX_DEBUG".toLowerCase(), str);
	}
	public static void s(String prepend, String str){
		Log.d(prepend+ "_" + "ALEX_DEBUG".toLowerCase(), str);
	}
	
}
