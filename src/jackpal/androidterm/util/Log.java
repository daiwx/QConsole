package jackpal.androidterm.util;

import java.io.IOException;
import java.io.Writer;

public class Log {
	private static final String DEBUG = "DEBUG: ";
	//private static final String WARN = "WARN: ";
	//private static final String INFO = "INFO: ";
	private static final String ERROR = "ERROR: ";
	
	private static final int MESSAGE_QUEUE_LENGTH = 50;
	private static String[] mMessages = new String[MESSAGE_QUEUE_LENGTH];
	private static int mMessageIdx = 0;
	private static int mMessageCnt = 0;
	//private static StringBuilder mStringBuilder = new StringBuilder();
	
	public static void d (String tag, String msg) {
		android.util.Log.d(tag, msg);
		log(DEBUG, tag, msg, null);
	}
	
	public static void d (String tag, Throwable t) {
		android.util.Log.d(tag, "", t);
		log(DEBUG, tag, "", t);
	}
	
	public static void d (String tag, String msg, Throwable t) {
		android.util.Log.d(tag, msg, t);
		log(DEBUG, tag, msg, t);
	}
	
	public static void e (String tag, String msg) {
		android.util.Log.e(tag, msg);
		log(ERROR, tag, msg, null);
	}
	
	public static void e (String tag, Throwable t) {
		android.util.Log.e(tag, "", t);
		log(ERROR, tag, "", t);
	}
	
	
	public static void e (String tag, String msg, Throwable t) {
		android.util.Log.e(tag, msg, t);
		log(ERROR, tag, msg, t);
	}
	
	private static void log(String type, String tag, String msg, Throwable t) {
		mMessages[mMessageIdx] = type + tag + ";" + msg + ((t != null) ? ";" + t : "");
		
		mMessageIdx = (mMessageIdx + 1) % MESSAGE_QUEUE_LENGTH;
		++mMessageCnt;
	}
	
	public static void dump(final Writer w) throws IOException {
		if (mMessageCnt >= MESSAGE_QUEUE_LENGTH) {
			for (int i = mMessageIdx; i < MESSAGE_QUEUE_LENGTH; i++) {
				w.append(mMessages[i] + "\n");
			}
		}
			
		for (int i = 0; i < mMessageIdx; i++) {
			w.append(mMessages[i] + "\n");
		}
		
		w.flush();
	}
}
