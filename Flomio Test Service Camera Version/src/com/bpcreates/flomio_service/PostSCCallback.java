package com.bpcreates.flomio_service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;

import org.apache.commons.httpclient.NameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

public class PostSCCallback implements Runnable{
	private boolean preventRetry = false;
	private Context context = null;
	public ServerCall myServerCall;
	
	public PostSCCallback(Context c){
		this.context = c;
	}
	
	public String getResponseBody(){
		return myServerCall.getResponseBody();
	}
	public JSONObject getResponseBodyJSON() {
		try{
			return new JSONObject(getResponseBody());
		}catch(Exception e){
			L.e("can't convert response to JSON " + e);
			return null;
		}
	}
	
	public byte[] getBinaryResponseBody(){
		return myServerCall.getBinaryResponseBody();
	}
	
	public Integer getResponseCode(){
		return myServerCall.getResponseCode();
	}
	
	
	
	@Override
	public void run() {	
		try{
			//L.d("PostSCCallback Resp:" + getResponseBody());
			if(getResponseCode() < 200 || getResponseCode() >= 300)
				throw new Exception("Response code not 200, writing file for later retry.");
			L.i("CALLBACK RESP: " + getResponseBody());
		}catch(Exception e){
			try{
				if(context != null){
					Say.t(context, "Unable to reach server, please verify connection");
				}
			}catch(Exception e1){
				L.e("unable to call myApp.scheduleAlarmReceiver(): " + e);
			}
			L.i("Response code not in the 200 range, checking whether to write file for retry later.");
			if( !preventRetry ){
				L.i("Set to retry later, setting up file.");
				String fileBase = Environment.getExternalStorageDirectory() + "/" + getClass().getPackage().getName() + "/failedServerCalls/";
				L.i("Using base path: " + fileBase);
				if(!new File(fileBase).exists())
					new File(fileBase).mkdirs();
				String fileName = getFileName(fileBase);
				L.i("Using final file: " + fileName);
				File retryFile = new File(fileName);

				try {
					JSONObject fileData = new JSONObject();
					if(myServerCall.getSendType() == ServerCall.GET)
						fileData.put("URL", myServerCall.getInitialURL());
					else
						fileData.put("URL", myServerCall.getURL_PATH());
					
					fileData.put("SEND_TYPE", myServerCall.getSendType());
					if( myServerCall.hasNVPArray() ){
						L.d("saving NVP Array values to delayed file");
						NameValuePair[] nvpArray = myServerCall.getOriginalNVPArray(); 
						JSONArray myNVPArray = new JSONArray();
						for( int i = 0; i < nvpArray.length; i++ ){							
							JSONObject nvpObj = new JSONObject();
							nvpObj.put("name", nvpArray[i].getName());
							nvpObj.put("value", nvpArray[i].getValue());
							myNVPArray.put(nvpObj);
						}
						fileData.put("NVP_ARRAY", myNVPArray);
					}else{
						L.w("no nvp values to save to file");
					}
					L.i("Writing json data to file: " + fileData.toString());
					FileWriter fos = new FileWriter(retryFile);
					BufferedWriter os = new BufferedWriter(fos);
					os.write(fileData.toString());
					os.close();
				} catch (JSONException e1) {
					L.e("ERROR CREATING JSON OBJECT FOR RETRY FILE: " + Log.getStackTraceString(e1) );
				} catch (IOException e1) {
					L.e("ERROR TRYING TO WRITE TO FILE: " + Log.getStackTraceString(e1));
				} catch(Exception e1){
					L.e("ERROR can't do this or that " + e1);
				}
			}
		}
	}
	
	private String getFileName(String fileBase){
		String fileName = fileBase +  Calendar.getInstance().getTimeInMillis();
		
		L.i("Attempting filename: " + fileName + ".txt");
		
		if( ! new File(fileBase).exists() ){
			L.i("Folder did not exist, creating it now.");
			new File(fileBase).mkdirs();
		}
		
		if( new File(fileName).exists() ){
			L.i("File already exists, trying next filename.");
			return getFileName(fileBase);
		}
		
		fileName += ".txt";
		
		L.i("Found a file name with no collision, using " + fileName);
		return fileName;
	}
	
	
	
	public PostSCCallback setPreventRetry(){
		this.preventRetry = true;
		return this;
	}
}
