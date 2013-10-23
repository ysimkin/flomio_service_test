//SERVER CALL 2.3.1
//improvements:
/*
*2.3.1
*added optional directory setting for file uploads
*
*
*2.3
 * added basic authentication
 * 
 * 
 * 2.2
 * Added TimeOut
 * 
 * POST params can be introduced after instantiation 
 * url with get param can be used with POST (instantiate the call as a GET with ?query then pass in NVP array and change sendType to POST)
 */


package com.bpcreates.flomio_service;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;

public class ServerCall {
	public static int POST = 1;
	public static int GET = 2;
	public String encoding = "";
	private int sendType = GET;
	
	//private NameValuePair[] ORIGINAL_PARAMS = null;
	public  String setEncodingString(String orig){
		encoding = Base64.encodeToString(orig.getBytes(), Base64.DEFAULT); 
		return encoding;
	}

	public int getSendType() { 
		return sendType;
	}
	public void setSendType(int sendType) {
		this.sendType = sendType;
	}
	
	private NameValuePair[] nvpArray = null;
	private String URL_PATH = null;
	
	
	private byte [] binaryResponseBody = null;
	class MakeBinaryCall extends Thread{
		Handler h;
		PostSCCallback r;
		public MakeBinaryCall(Handler h, PostSCCallback r){
			this.h = h;
			this.r = r;
		}
		
		@SuppressWarnings("deprecation")
		public void run(){
			HttpClient client = new HttpClient();
	        PostMethod filePost = new PostMethod(URL_PATH);
	        client.setConnectionTimeout(timeout);
		
	        try{             	       	
	        	if(nvpArray != null)
	        		filePost.setRequestBody(nvpArray);                   
	        }catch(Exception e){
	        	Log.d("GZIP TESTING", "upload failed: " + e.toString());
	        }              
	        try{        	
	        	responseCode = client.executeMethod(filePost);        	
	        	Log.d("GZIP TESTING","statusCode>>>" + responseCode);
	        	setBinaryResponseBody(filePost.getResponseBody());	        	
	        }catch(Exception e){
	        	Log.d("GZIP TESTING", e.toString());         	
	        }     
	      
			filePost.releaseConnection(); 	
			if(h!= null && r != null){
				r.myServerCall = ServerCall.this;
				h.post(r);
			}
			
		}
	}
	
	public String getURL_PATH() {
		return URL_PATH;
	}
	public void setURL_PATH(String cgi_path) {
		URL_PATH = cgi_path;
	}

	private int default_timeout = 15000;
	private int timeout = default_timeout;
	
	private Integer responseCode = null;
	
	
	public Integer getResponseCode() {
		return responseCode;
	}
	public void setResponseCode(Integer returnCode) {
		this.responseCode = returnCode;
	}
	
	
	
	private String fullQueryStringAsDeliveredAtInstatiation;
	public ServerCall( String CGI_PATH , int type) throws Exception{
		this.sendType = type;
		try{
			L.i("CREATING SERVER CALL WITH URL " + CGI_PATH);
			fullQueryStringAsDeliveredAtInstatiation = CGI_PATH;
			if(this.sendType  == GET ) {
			
				this.URL_PATH = CGI_PATH.replaceAll("'","\\'");
			}
			else{ //if POST
				//if there are no params behind the question mark there is nothing more to do
				if(!CGI_PATH.contains("?")){
					this.URL_PATH = CGI_PATH;
					return;
				}
				
				String [] parts = CGI_PATH.split("\\?");
				this.URL_PATH = parts[0];
				L.d("SET URL AS " + this.URL_PATH);
				parts[1] = parts[1].replaceAll(" & ", " %26 ");
				NameValuePair[] nvp = new NameValuePair[parts[1].split("\\&").length];
				//L.d("split length = " + parts[1].split("\\&").length );
				for(int i =0 ; i < parts[1].split("\\&").length; i++){
					nvp[i] = new NameValuePair(parts[1].split("\\&")[i].split("=")[0], parts[1].split("\\&")[i].split("=")[1].replaceAll("'","\\\\'").replaceAll("\\%26", " & "));
					//L.d( "nvp = " + nvp[i].getName() + "=" + nvp[i].getValue());
				}
				
				this.nvpArray = nvp;
				
			}
		}catch(Exception e){
			L.e("CAN'T CREATE QUERY " + e);
			throw new Exception(e);
		}
	}
	
	
	public void setNVPairs(NameValuePair [] nvp){
		L.i("setting NVP Array as " + nvp);
		this.nvpArray = nvp;		
	}
	
	
	
	
	public void setTimeout(int t){
		timeout = t;
	}
	public int getTimeout(int t){
		return timeout;
	}
	@SuppressWarnings("deprecation")
	public String makeCall(){
		L.i("making inline (non threaded) server call");
		HttpClient client = new HttpClient();
		PostMethod filePost = null;
		GetMethod fileGet = null;
		if(sendType == POST){
			filePost = new UTF8PostMethod(URL_PATH);
			filePost.setRequestHeader("ContentType",
					"application/x-www-form-urlencoded;charset=UTF-8");
		}
		else
			fileGet = new GetMethod(fullQueryStringAsDeliveredAtInstatiation);
        client.setConnectionTimeout(timeout);
        String ret = "";
        try{             	       	
        	if(nvpArray != null && sendType == POST){
        		for(NameValuePair nvp : nvpArray)
        			L.i("PARAM: " + nvp.getName() + "=" + nvp.getValue());
        		filePost.setRequestBody(nvpArray); 
        		
        	}
        	L.d("CALLING " + fullQueryStringAsDeliveredAtInstatiation); 

        }catch(Exception e){
        	Log.d("YS_DEBUG", "upload failed: " + e.toString());
        }              
        try{      		
        	//these are the blocking calls that make queries to the server
        	if(sendType == POST)
        		responseCode = client.executeMethod(filePost);
        	else
        		responseCode = client.executeMethod(fileGet);        	
        	
        		
        	if(sendType == POST)
        		ret = filePost.getResponseBodyAsString();	 
        	else
        		ret = fileGet.getResponseBodyAsString();        	
        }catch(HttpException e){
        	Log.d("YS_DEBUG", e.toString()); 
        	responseCode = -1;
        	
        }catch(IOException e){        	
        	Log.d("YS_DEBUG", e.toString());
        	responseCode = -1;
        	
        }        
        catch(Exception e){        	       	
        	Log.d("YS_DEBUG", e.toString());
        	responseCode = -1;
        	
        };        
        responseBody = ret;
        
        try{
	        if(sendType == POST)
        		filePost.releaseConnection();
        	else
        		fileGet.releaseConnection();
        }catch(Exception e){
        	
        }		
		return ret;		
	}
	
	
	
	
	public void makeCall(PostSCCallback callback){
		
		ThreadedCall t = new ThreadedCall( callback);
		t.start();	
		
	}
	
	public File saveResponseToFile(Context context, String filePath, String fileName) throws IOException{
		if(filePath == null)
			filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + context.getPackageName() + "/appData/";
		
		File dir = new File(filePath);
		if(!dir.exists())
			dir.mkdirs();
		if(filePath.charAt(filePath.length() - 1) != '/' )
			filePath += "/";
		
		BufferedWriter out = new BufferedWriter(new FileWriter(filePath + fileName));
	    out.write(this.responseBody);
	    out.close();
	    File ret = new File(filePath + fileName);
	    
	    return ret;
	}
	
	
	@SuppressWarnings("deprecation")
	public byte [] makeBinaryCall(){
		HttpClient client = new HttpClient();
        PostMethod filePost = new PostMethod(URL_PATH);
        client.setConnectionTimeout(timeout);
        byte [] ret ;
        try{             	       	
        	if(nvpArray != null)
        		filePost.setRequestBody(nvpArray);                   
        }catch(Exception e){
        	Log.d("YS_DEBUG", "upload failed: " + e.toString());
        }              
        try{        	
        	responseCode = client.executeMethod(filePost);        	
        	Log.d("YS_DEBUG","statusCode>>>" + responseCode);
        	ret = filePost.getResponseBody();	        	
        }catch(HttpException e){
        	Log.d("YS_DEBUG", e.toString());        	
        	return null;
        }catch(IOException e){        	
        	Log.d("YS_DEBUG", e.toString());
        	return null;
        }        
        catch(Exception e){        	       	
        	Log.d("YS_DEBUG", e.toString());
        	return null;
        };        
      
		filePost.releaseConnection(); 	
		return ret;
		
	}
	
	class ThreadedCall extends Thread{
		public Handler h = new Handler();
		public PostSCCallback callback;
		
		public ThreadedCall(PostSCCallback callback){			
			this.callback = callback;
		}
	
		
		private boolean timedOut = false;		
		Handler timeoutHandler = new Handler();
		
		//this class is used to timeout slow responses
		private class TimeOut implements Runnable{
			public PostMethod filePost = null;
			public GetMethod fileGet = null;
			public TimeOut(PostMethod pm){
				filePost = pm;
			}
			public TimeOut(GetMethod gm){
				fileGet = gm;
			}
			public void run(){
				if(handled)
					return; //the normal execution chain has worked so no need to do anything
				timedOut = true;
				responseCode = -1;
				responseBody = "server timout";
				
				
				
				L.w("server call timed out");
				if(filePost != null)
					try{
						filePost.releaseConnection(); 
					}catch(Exception e){}
				if(fileGet != null)
					try{
						fileGet.releaseConnection(); 
					}catch(Exception e){}
				if(callback != null){		
					callback.myServerCall = ServerCall.this;					
					h.post(callback);				
				}
			}
		};
		
		@SuppressWarnings("deprecation")
		public void run(){
			HttpClient client = new HttpClient();
			PostMethod filePost = null;
			GetMethod fileGet = null;
			if(sendType == POST){
				filePost = new UTF8PostMethod(URL_PATH);
				filePost.setRequestHeader("ContentType",
						"application/x-www-form-urlencoded;charset=UTF-8");
				
				if(!encoding.equals("")){
					filePost.setRequestHeader("Authorization", "Basic " + encoding);
				}
			}
			else{
				fileGet = new GetMethod(fullQueryStringAsDeliveredAtInstatiation);
				if(!encoding.equals("")){
					fileGet.setRequestHeader("Authorization", "Basic " + encoding);
				}
			}
	        client.setConnectionTimeout(timeout);
	        String ret = "";
	        try{             	       	
	        	if(nvpArray != null && sendType == POST){
	        		for(NameValuePair nvp : nvpArray)
	        			L.i("PARAM: " + nvp.getName() + "=" + nvp.getValue());
	        		filePost.setRequestBody(nvpArray); 
	        		
	        	}
	        	L.d("CALLING " + fullQueryStringAsDeliveredAtInstatiation); 

	        }catch(Exception e){
	        	Log.d("YS_DEBUG", "upload failed: " + e.toString());
	        }              
	        try{    
	        	
	        	//set timeout so that a slow response exits rather than sits around for ever and ever
	        	TimeOut to = null;
	        	if(sendType == POST){
		        	to = new TimeOut(filePost);
	        		timeoutHandler.postDelayed(to,timeout);	        		
	        	}else{
	        		to = new TimeOut(fileGet);
	        		timeoutHandler.postDelayed(to,timeout);	
	        	}        		      		
        		
	        	//these are the blocking calls that make queries to the server
	        	if(sendType == POST)
	        		responseCode = client.executeMethod(filePost);
	        	else
	        		responseCode = client.executeMethod(fileGet);
	        	
	        	//this is set in the TimeOut. If it's true then we've already quit, so nothing more to do
	        	if(timedOut)
        			return;	  

	        	timeoutHandler.removeCallbacks(to);
	        	
	        	Log.d("YS_DEBUG","RESPCODE>>>" + responseCode);
	        		
	        	if(sendType == POST)
	        		ret = filePost.getResponseBodyAsString();	 
	        	else
	        		ret = fileGet.getResponseBodyAsString();        	
	        }catch(HttpException e){
	        	Log.d("YS_DEBUG", e.toString()); 
	        	responseCode = -1;
	        	
	        }catch(IOException e){        	
	        	Log.d("YS_DEBUG", e.toString());
	        	responseCode = -1;
	        	
	        }        
	        catch(Exception e){        	       	
	        	Log.d("YS_DEBUG", e.toString());
	        	responseCode = -1;
	        	
	        };        
	        responseBody = ret;
	        
	        try{
		        if(sendType == POST)
	        		filePost.releaseConnection();
	        	else
	        		fileGet.releaseConnection();
	        }catch(Exception e){
	        	
	        }
			 
			if(callback != null){	
				handled = true;
				callback.myServerCall = ServerCall.this;
				L.i("INSIDE SC RESP=" + getResponseBody());
				h.post(callback);				
			}
		}		
	}
	private boolean handled = false;
	
	public static class UTF8PostMethod extends PostMethod {
		public UTF8PostMethod(String url) {
			super(url);
		}

		@Override
		public String getRequestCharSet() {
			// return super.getRequestCharSet();
			return "UTF-8";
		}
	}
	
	@SuppressWarnings("deprecation")
	public int ping(){
		int ret = 0;
		try{
			HttpClient client = new HttpClient();
	        PostMethod filePost = new PostMethod(URL_PATH);
	        client.setConnectionTimeout(timeout);
	        ret = client.executeMethod(filePost);	        
		}catch(HttpException e){
        	Log.d("YS_DEBUG", e.toString());        	
        }catch(IOException e){        	
        	Log.d("YS_DEBUG", e.toString());
        }        
        catch(Exception e){        	       	
        	Log.d("YS_DEBUG", e.toString());
        }; 
		return ret;
	}
	
	Bitmap bmImg = null;
	public void releaseBitmap(){
		try{
			bmImg.recycle();
			bmImg = null;
		}catch(Exception e){
			L.e("ServerCall can't release bitmap " + e);
		}
	}
	public Bitmap loadBitmap() {	
		bmImg = null;   
	         
		try{		 
			byte [] ba = makeBinaryCall();
			bmImg = BitmapFactory.decodeByteArray(ba, 0, ba.length);
			if(bmImg == null){
				Log.d("YS_DEBUG", "bmImg is null for " + URL_PATH);            	              	   
			}
			else{
				Log.d("YS_DEBUG", "loaded " + URL_PATH );
			}
			
			if(bmImg == null){
				Log.d("YS_DEBUG", URL_PATH + " is null ");
			}
	  }catch (Exception e) {
		  Log.d("YS_DEBUG", "RETRYING loadBitmap() failed:"  + e.toString());
	  }		     
	  return bmImg;
	}	
	
	private String responseBody;
	
	public Integer uploadByteArray(String remoteFilename, byte[] bitmapdata){		
		HttpClient client = new HttpClient();
		PostMethod filePost = new PostMethod( URL_PATH);
		
		Integer ret = null;
		
		try {			
			Part[] parts = new Part[2];
			parts[0] = new StringPart("file_name" ,remoteFilename);
			
			ByteArrayPartSource ps = new ByteArrayPartSource("file", bitmapdata);
			parts[1] = new FilePart("file", ps);

			filePost.setRequestEntity(new MultipartRequestEntity(parts,
					filePost.getParams()));

		} catch (Exception e) {
			Log.d("YS_DEBUG", e.toString());
		}

		try {
			ret = client.executeMethod(filePost);
			Log.d("YS_DEBUG", "statusCode>>>" + ret);
			if(ret != 200){
				Log.d("YS_DEBUG", "Error:" + ret + " from server. Please try again later.");
			}else{
				responseBody = filePost.getResponseBodyAsString();
				Log.d("YS_DEBUG", filePost.getResponseBodyAsString());
			}
		} catch (Exception e) {
			Log.d("YS_DEBUG", e.toString());
		}
				
		filePost.releaseConnection();
		
		return ret;
	}
	
	
	public int uploadFile(File f, String remoteFileName){
		return uploadFile(f, remoteFileName, null);
	}
	
	public int uploadFile(File f, String remoteFileName, String directory){
		
		int ret = -1;
		
		if (f != null)
			System.out.println("File Length = " + f.length());
		else {
			return ret;
		}

		HttpClient client = new HttpClient();
		PostMethod filePost = new PostMethod(URL_PATH);
		try {
			Part[] parts;
			int filePartIndex = 0;
			if(directory != null){
				parts = new Part[3];
				parts[filePartIndex++] = new StringPart("directory", directory);	
				L.d("added param directory=" + directory);
			}
			else
				parts = new Part[2];
			parts[filePartIndex++] = new StringPart("file_name" ,	remoteFileName);
			parts[filePartIndex] = new FilePart("file", f);

			filePost.setRequestEntity(new MultipartRequestEntity(parts, 	filePost.getParams()));

		} catch (Exception e) {
			Log.d("YS_DEBUG", e.toString());
		}

		try {
			ret = client.executeMethod(filePost);
			Log.d("YS_DEBUG", "statusCode>>>" + ret);
			responseBody = filePost.getResponseBodyAsString();
			L.d("RESP FROM UPLOAD = " + responseBody);
		} catch (Exception e) {
			Log.d("YS_DEBUG", e.toString());
		}
		return ret;
		
	}
	
	public String getResponseBody() {
		return responseBody;
	}
	public void setResponseBody(String responseBody) {
		this.responseBody = responseBody;
	}
	public int getTimeout() {
		return timeout;
	}
	
	public  boolean saveByteArrayToLocalFile(String filePath, String fileName ){
		FileOutputStream outStream = null;
		
		try {			
			byte[] bytes = makeBinaryCall();
			
			File path = new File(filePath);
			if(!path.exists())
				path.mkdir();
			Log.d("YS_DEBUG", "saving to: " + filePath + "/" + fileName);
			outStream = new FileOutputStream(filePath + "/" + fileName);
			outStream.write(bytes);
			outStream.close();
			return true;
			
			
		} catch (FileNotFoundException e) {			
			Log.d("YS_DEBUG", "saveLocalFile() FileNotFoundException fired: " + e.toString());
			return false;
		} catch (IOException e) {			
			Log.d("YS_DEBUG", "saveLocalFile() ISException fired: " + e.toString());
			return false;
		}catch(Exception e){
			Log.d("YS_DEBUG", "saveLocalFile() failed: " + e.toString());
			return false;
		}
		
	}
	
	
	public byte [] getBinaryResponseBody() {
		return binaryResponseBody;
	}
	public void setBinaryResponseBody(byte [] binaryResponseBody) {
		this.binaryResponseBody = binaryResponseBody;
	}
	
	public void makeBinaryCall(Handler h, PostSCCallback r){
		new MakeBinaryCall(h,r).start();		
	}
	
	public String getInitialURL(){
		return fullQueryStringAsDeliveredAtInstatiation;
	}	
	
	public boolean hasNVPArray(){
		L.i("returning nvpArray == null as " + (this.nvpArray == null));
		return !(this.nvpArray == null);
	}
	
	public NameValuePair[] getOriginalNVPArray(){
		return this.nvpArray;
	}
}
