package versionchecker;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.bpcreates.flomio_service.L;

public class VersionChecker implements Runnable{

	private JSONObject versionObject;
	private Context ctx;
	private Runnable cb;
	private boolean updateAvailable;
	public VersionChecker(Context ctx, JSONObject response, Runnable cb) {
		super();
		this.versionObject = response;
		this.ctx = ctx;
		this.cb = cb;
		updateAvailable = false;
	}

	@Override
	public void run() {
		try{
			if( versionObject.has("updateAvailable") )
				updateAvailable = versionObject.getBoolean("updateAvailable");
			if( updateAvailable ){
				OnClickListener pos = null;
				OnClickListener neg = new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						L.i("Skipping update.");
						
						new Thread(cb).start();
						return;
					}
				};
				
				if( versionObject.has("url") && versionObject.getString("url").equals("MARKET")){
					pos = new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							try {
							    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + ctx.getPackageName())));
							} catch (android.content.ActivityNotFoundException anfe) {
							    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + ctx.getPackageName())));
							}
						}
					};
				} else if( versionObject.has("url") ){
					final String updateURL = versionObject.getString("url");
					pos = new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
					    	Intent i = new Intent();
							i.setAction(Intent.ACTION_VIEW);
							i.addCategory(Intent.CATEGORY_BROWSABLE);
							Uri update_url = Uri.parse(updateURL); 
							i.setData(update_url);
							ctx.startActivity(i); 
						}
					};
				} else {
					L.e("UNABLE TO DETERMINE URL TO UPDATE FROM! " + versionObject.toString());
					if( cb != null )
						new Thread(cb).start();
					return;
				}
				
				Alert.alert(ctx, "An update for your app is available: " + versionObject.getString("latest_version"), "UPDATE", "SKIP",pos,neg);
			}
			
			if( versionObject.has("updateAvailable") && !updateAvailable ){
				L.i("No update available");
				if( cb != null)
					new Thread(cb).start();
			}
		} catch(JSONException e){
			L.e("ERROR PULLING DATA FROM RESPONSE " + Log.getStackTraceString(e));
		} catch(Exception e){
			L.e("GENERAL ERROR " + Log.getStackTraceString(e));
		}
	}
	
}

/*public class VersionChecker extends Thread {
	private JSONObject versionObject;
	private Context ctx;
	private Runnable cb;
	private boolean updateAvailable;
	public VersionChecker(Context ctx, JSONObject response, Runnable cb) {
		super();
		this.versionObject = response;
		this.ctx = ctx;
		this.cb = cb;
		updateAvailable = false;
	}
	@Override
	public void run() {
		try{
			if( versionObject.has("updateAvailable") )
				updateAvailable = versionObject.getBoolean("updateAvailable");
			if( versionObject.has("updateAvailable") && updateAvailable ){
				OnClickListener pos = null;
				OnClickListener neg = new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						L.i("Skipping update.");
						
						new Thread(cb).start();
						return;
					}
				};
				
				if( versionObject.has("url") && versionObject.getString("url").equals("MARKET")){
					pos = new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							try {
							    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + ctx.getPackageName())));
							} catch (android.content.ActivityNotFoundException anfe) {
							    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + ctx.getPackageName())));
							}
						}
					};
				} else if( versionObject.has("url") ){
					final String updateURL = versionObject.getString("url");
					pos = new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
					    	Intent i = new Intent();
							i.setAction(Intent.ACTION_VIEW);
							i.addCategory(Intent.CATEGORY_BROWSABLE);
							Uri update_url = Uri.parse(updateURL); 
							i.setData(update_url);
							ctx.startActivity(i); 
						}
					};
				} else {
					L.e("UNABLE TO DETERMINE URL TO UPDATE FROM! " + versionObject.toString());
					new Thread(cb).start();
					return;
				}
				
				Alert.alert(ctx, "An update for your app is available: " + versionObject.getString("latest_version"), "UPDATE", "SKIP",pos,neg);
			}
			
			if( versionObject.has("updateAvailable") && !updateAvailable ){
				L.i("No update available");
				new Thread(cb).start();
			}
		} catch(JSONException e){
			L.e("ERROR PULLING DATA FROM RESPONSE " + Log.getStackTraceString(e));
		} catch(Exception e){
			L.e("GENERAL ERROR " + Log.getStackTraceString(e));
		}
		super.run();
	}
}*/
