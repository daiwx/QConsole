package jackpal.androidterm.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class NStorage {

	public static String getSP(Context context, String key)	{
		String val;
		SharedPreferences obj = context.getSharedPreferences("passinger_db",0);
		val = obj.getString(key,"");
		return val;
	}
	public static void setSP(Context context, String key,String val) {
		SharedPreferences obj = context.getSharedPreferences("passinger_db",0);
		Editor wobj; 
		wobj = obj.edit();
		wobj.putString(key, val);
		wobj.commit();
	}
}