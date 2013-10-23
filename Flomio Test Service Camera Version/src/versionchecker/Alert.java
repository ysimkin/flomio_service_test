package versionchecker;

/**
 * Short cut to Android's AlertDialog.
 * Allows for either alerting with no follow up, or passing in a DialogInterface.OnClickListener callback to execute upon "OK"
 */
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/**
 * Short cut to Android's AlertDialog.
 * Allows for either alerting with no follow up, or passing in a DialogInterface.OnClickListener callback to execute upon "OK"
 */
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class Alert {
	public static void alert(Context c, String str, String OK, String CANCEL, DialogInterface.OnClickListener posCl, DialogInterface.OnClickListener negCl){
    	new AlertDialog.Builder(c).setMessage(str)
        .setPositiveButton(OK, posCl)
        .setNeutralButton(CANCEL, negCl)
        .setOnCancelListener(null).show();
    }
	public static void alert(Context c, String str, DialogInterface.OnClickListener posCl, DialogInterface.OnClickListener negCl){
    	new AlertDialog.Builder(c).setMessage(str)
        .setPositiveButton("OK", posCl)
        .setNeutralButton("Cancel", negCl)
        .setOnCancelListener(null).show();
    }
	public static void alert(Context c, String str, DialogInterface.OnClickListener cl){
    	new AlertDialog.Builder(c).setMessage(str)
        .setPositiveButton("OK", cl)
        .setOnCancelListener(null).show();
    }
	public static void alert(Context c, String str){
    	new AlertDialog.Builder(c).setMessage(str)
        .setPositiveButton("OK", null)
        .setOnCancelListener(null).show();
    }
}