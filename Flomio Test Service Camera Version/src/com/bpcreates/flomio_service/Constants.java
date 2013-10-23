package com.bpcreates.flomio_service;

import static java.lang.String.format;

import java.util.ArrayList;

import org.apache.commons.httpclient.NameValuePair;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewFlipper;




public class Constants {	
	public static final String NODE_CGI_PATH =  "http://vzexperience.com/cgi-bin/node.cgi?j=";
	
	public static final int REQUEST_OK = 200;
	public static final int REQUEST_CANCEL = 400;
	public static final String HOST = "vzexperience.com";
	private static final Object SAVE_STATION_INFO_NJS = "saveStationInfo2";
		
	@SuppressWarnings("rawtypes")
	public static int getResourceId(String name,  Class resType){		
		try {		   
		    return resType.getField(name).getInt(null);		   
		}
		catch (Exception e) {
		   // Log.d(TAG, "Failure to get drawable id.", e);
		}
		return 0;
	}
	
	
	public static void setCViewAndRenderFonts(Context c, int layoutId){
		((Activity)c).setContentView(layoutId);
		if(layoutId == 0 )
			return;
		renderAllFontFields(c);
		setBackground((Activity)c);
		try{
			String layoutFolder = ((Activity)c).findViewById(getResourceId("__LAYOUT_NAME__", R.id.class)).getTag().toString();
			c.getSharedPreferences(c.getPackageName(), Context.MODE_PRIVATE).edit().putString("LAYOUT_FOLDER", layoutFolder).commit();
		}catch(Exception e){
			L.i("LAYOUT FOLDER TVTAG NOT FOUND");
		}
	}
	
	private static ViewGroup getBG(Activity c, ViewGroup parentGroup){
		try{
			for(int i = 0; i<parentGroup.getChildCount(); i++){
				try{
				Object t = parentGroup.getChildAt(i).getTag();
					if(t != null ){
						try{
							if(t.toString().indexOf("bg") == 0){
								return (ViewGroup)parentGroup.getChildAt(i);
							}
						}catch(Exception e){
							
						}
					}
				}catch(Exception e){
					
				}
				ViewGroup v =  null;
				try{
					v = getBG(c, (ViewGroup)parentGroup.getChildAt(i));
				}catch(Exception e){
					
				}
				if (v != null)
					return v;
			}
		}catch(Exception e){
			
		}
		return null;
	}
	
	private static void setBackground(Activity c){
    	try{
    		ViewGroup customBGLayout = getBG(c,((ViewGroup)c.getWindow().getDecorView().findViewById(android.R.id.content)));
    		L.i("Found BG to customize in layout " + customBGLayout);
    		String bg_type = customBGLayout.getTag().toString();
    		int bgId = 0;
    		try{
    			//String orientation = (((SlidePlayer)c).myOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)?"_land" : "";
    			String bgName = bg_type + "_" + c.getSharedPreferences(c.getPackageName(),Context.MODE_PRIVATE).getString("ACTIVATION_NAME", "").toLowerCase().replaceAll("\\s*\\-\\s*", "_").replaceAll("\\s+","_");
    			L.i("BGNAME = " + bgName);
    			bgId = Constants.getResourceId(bgName, R.drawable.class);
    		}catch(Exception e){
    			L.e("Can't find background art");
    		}
    		if(bgId != 0){
    			customBGLayout.setBackgroundResource(bgId);
    		}
    	}catch(Exception e){
    		L.e("Unable to set background " + e);
    	}
    }
	
	public static void renderAllFontFields(Context c){
				
		ViewGroup rl = (ViewGroup) ((Activity)c).getWindow().getDecorView().findViewById(android.R.id.content);
		
		ArrayList<Object> styleableViews = new ArrayList<Object>();
		getAllStylableViews(rl, styleableViews);
		
		for(Object v : styleableViews){
			View myView = (View)v;
			Integer fontSize = null;
			Integer fontColor = null;
			try{
				String [] fontSpec = myView.getTag().toString().split(":");
				if(fontSpec.length < 2)
					continue;
				
				if(fontSpec.length > 1 ){
					try{						
						fontSize = Integer.valueOf(fontSpec[2].replaceAll("\\D+",""));
					}catch(NumberFormatException e){
						L.e("CAN'T CONVERT " + fontSpec[2] + " into an integer for font sizing");
						fontSize = null;
					}
				}
				if(fontSpec.length > 2 ){
					//Log.d(TAG, "see color string as " + fontSpec[3].substring(1));
					try{	
						if(fontSpec[3].toLowerCase().matches("([a-f0-9]+$)|(0x[a-f0-9]+$)|(#[a-f0-9]+$)")){
							if(fontSpec[3].indexOf("#") == 0){
								//Log.d(TAG, "# found at char = " + fontSpec[3].indexOf("#"));
								String hex = fontSpec[3].substring(1);
								if(hex.length() == 6)
									hex = "FF" + hex;
								fontColor = (int)Long.parseLong( hex,16); 
							}
							else if(fontSpec[3].toLowerCase().indexOf("0x") == 0){
								String hex = fontSpec[3].substring(2);
								if(hex.length() == 6)
									hex = "FF" + hex;
								fontColor = (int) Long.parseLong(hex,16);
							}else{
								String hex = fontSpec[3];
								if(hex.length() == 6)
									hex = "FF" + hex;
								fontColor = (int) Long.parseLong(hex,16);
							}
							
							//Log.d(TAG, "set font color as " + fontColor);
						}
					}catch(NumberFormatException e){
						L.e("CAN'T CONVERT " + fontSpec[3] + " into an integer for font coloring");
						fontColor = null;
					}
				}
				
				if(v instanceof TextView){
					TextView tv = (TextView)myView;
					tv.setTypeface(getTypeFace(fontSpec[1], c));
					//L.d("SETTING FONT AS " + fontSpec[1]);
					if(fontSize != null){
						//L.d("SETTING SIZE AS " + fontSize);
						tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
					}
					if(fontColor != null){
						//Log.d(TAG,"setting color to " + fontColor);
						tv.setTextColor(fontColor);
					}
				}
				else if(v instanceof Button){
					Button bt = (Button)myView;
					bt.setTypeface(getTypeFace(fontSpec[1], c));
					if(fontSize != null){
						bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
					}
					if(fontColor != null)
						bt.setTextColor(fontColor);
				}
				else if(v instanceof EditText){
					EditText et = (EditText)myView;
					et.setTypeface(getTypeFace(fontSpec[1], c));
					if(fontSize != null){
						et.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
					}
					if(fontColor != null)
						et.setTextColor(fontColor);
				}
			}catch(Exception e){
				L.d("ERROR RENDERING FONTS " + e);
			}			
		}		
	}
	
	
	private static void getAllStylableViews(ViewGroup parent, ArrayList<Object> res){
		
		if(parent instanceof Spinner){
			Spinner p = (Spinner) parent;
			if(p.getAdapter() == null)
				return;
			for(int i = 0; i< p.getAdapter().getCount(); i++){
				TextView tv = (TextView)p.getAdapter().getDropDownView(i, null, null);
				if(tv.getTag() == null)
					continue;
				if(tv.getTag().toString().toLowerCase().indexOf("font:") == 0){					
					res.add(tv);
				}
			}
			
			return;
		}
		
		
		for(int i = 0; i< parent.getChildCount(); i++){		
			
			if(parent.getChildAt(i) instanceof FrameLayout || parent.getChildAt(i) instanceof LinearLayout || parent.getChildAt(i) instanceof RelativeLayout || parent.getChildAt(i) instanceof ScrollView || parent.getChildAt(i) instanceof Spinner || parent.getChildAt(i) instanceof ViewFlipper){	
				getAllStylableViews((ViewGroup)parent.getChildAt(i), res);
			}else{
				if(parent.getChildAt(i).getTag() == null)
					continue;
				if(parent.getChildAt(i).getTag().toString().toLowerCase().indexOf("font:") == 0){					
					res.add(parent.getChildAt(i));
				}
			}			
		}
	}
	
	
	
	public static Typeface getTypeFace(String type, Context c) {
		return Typeface.createFromAsset(c.getAssets(),
				format("fonts/%s.otf", type));
	}
	
	public static boolean sendStationInfoToServer(Context c, JSONObject additionalInfo, final PostSCCallback postSaveCallback){
    	try {
    		
    		JSONObject smgInfo = new JSONObject("{\"installed\":false}");
    		try{
    			PackageInfo pinfo = null;
    			pinfo = c.getPackageManager().getPackageInfo("com.restock.serialmagic.gears", 0);
    			smgInfo.put("versionCode",pinfo.versionCode);
    			smgInfo.put("versionName", pinfo.versionName);
    			smgInfo.put("installed", true);
    		}catch(Exception e1){
    			
    		}    		
    		  		
    		JSONObject jsonQuery = new JSONObject();
    		JSONObject info = new JSONObject();
    		
    		
    		
			info.put("stationId", c.getSharedPreferences(c.getPackageName(), Context.MODE_PRIVATE).getString("STATION_ID", "NULL"));
			info.put("installedEventId",c.getSharedPreferences(c.getPackageName(), Context.MODE_PRIVATE).getString("EVENT_ID", "NULL"));
    		
    		info.put("packageName", c.getPackageName());
    		info.put("deviceName",Build.PRODUCT);
    		info.put("appVersion", c.getApplicationContext().getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_META_DATA).versionName);
    		info.put("androidVersion", Build.VERSION.RELEASE);
    		
    		if(additionalInfo == null)
    			additionalInfo = new JSONObject(); 
    		additionalInfo.put("smgInfo", smgInfo);    		
    		info.put("additionalInfoJson", additionalInfo);
    		
    		jsonQuery.put("method",SAVE_STATION_INFO_NJS);
    		jsonQuery.put("context", "street_team");    		
    		
    		jsonQuery.put("info",info);
    		ServerCall sc = new ServerCall(NODE_CGI_PATH.replaceAll("\\?j=$", ""), ServerCall.POST);
    		
    		NameValuePair[] nvPairs = {new NameValuePair("j",jsonQuery.toString())};
			sc.setNVPairs(nvPairs);
			sc.makeCall(postSaveCallback);			
		} catch (Exception e2) {
			L.e("CAN'T MAKE SERVER CALL " + e2);
			return false;
		}
    		
    	return true;
	}
	
}
