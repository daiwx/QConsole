package jackpal.androidterm;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import com.zuowuxuxi.util.NAction;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import jackpal.androidterm.emulatorview.ColorScheme;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;

import jackpal.androidterm.compat.FileCompat;
import jackpal.androidterm.util.NStorage;
import jackpal.androidterm.util.TermSettings;

/**
 * A terminal session, consisting of a TerminalEmulator, a TranscriptScreen,
 * the PID of the process attached to the session, and the I/O streams used to
 * talk to the process.
 */
public class ShellTermSession extends TermSession {
    //** Set to true to force into 80 x 24 for testing with vttest. */
    private static final boolean VTTEST_MODE = false;
    private TermSettings mSettings;
    private Context context;

    private int mProcId;
    private FileDescriptor mTermFd;
    private Thread mWatcherThread;

    // A cookie which uniquely identifies this session.
    private String mHandle;
    private String pyPath = "";

    private String mInitialCommand;
    private boolean isEnd = false;

    public static final int PROCESS_EXIT_FINISHES_SESSION = 0;
    public static final int PROCESS_EXIT_DISPLAYS_MESSAGE = 1;
    private int mProcessExitBehavior = PROCESS_EXIT_FINISHES_SESSION;

    private String mProcessExitMessage;

    private static final int PROCESS_EXITED = 1;
    
    
    @SuppressLint("HandlerLeak")
	private Handler mMsgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (!isRunning()) {
            	Log.d("TERM", "isRunning");
                return;
            }
            if (msg.what == PROCESS_EXITED) {
            	Log.d("TERM", "PROCESS_EXITED");

                onProcessExit((Integer) msg.obj);
            }
        }
    };

    private UpdateCallback mUTF8ModeNotify = new UpdateCallback() {
        public void onUpdate() {
            Exec.setPtyUTF8Mode(mTermFd, getUTF8Mode());
        }
    };

    public boolean getEndStat() {
    	return this.isEnd;
    }
    
    public ShellTermSession(Context context, TermSettings settings, String cmd, String pyPath) {
        super();
        this.context = context;
        this.pyPath = pyPath;
        this.isEnd = false;
        
        updatePrefs(settings);

        initializeSession(cmd);
        this.mInitialCommand = cmd;

        mWatcherThread = new Thread() {
             @Override
             public void run() {
                Log.i(TermDebug.LOG_TAG, "waiting for: " + mProcId);
                int result = Exec.waitFor(mProcId);
                Log.i(TermDebug.LOG_TAG, "Subprocess exited: " + result);
                mMsgHandler.sendMessage(mMsgHandler.obtainMessage(PROCESS_EXITED, result));
                isEnd = true;
             }
        };
        mWatcherThread.setName("Process watcher");
        Log.d(TermDebug.LOG_TAG, "ShellTermSession:"+cmd);
    }

    public void shellRun() {
        //Exec.setPtyUTF8Mode(mTermFd, getUTF8Mode());
        //setUTF8ModeUpdateCallback(mUTF8ModeNotify);
    	initializeEmulator(80,24);
        /*mWatcherThread.start();
        sendInitialCommand(mInitialCommand);*/

    }
    
    public void updatePrefs(TermSettings settings) {
        mSettings = settings;
        setColorScheme(new ColorScheme(settings.getColorScheme()));
        setDefaultUTF8Mode(settings.defaultToUTF8Mode());
    }
    
    private void initializeSession(String cmd) {
    	//String code = getCode(context);
        boolean isQPy3 =  NAction.isQPy3(context);
        /*if (cmd!=null) {
        	if (cmd.startsWith("python ")) {
        		isQPy3 = false;
        	} else if (cmd.startsWith("python ")) {
        		isQPy3 = true;
        	}
        }*/
        
        Log.d("ShellTermSession", "initializeSession:"+cmd+"-"+isQPy3);
        TermSettings settings = mSettings;

        int[] processId = new int[1];

        String path = System.getenv("PATH");
        if (settings.doPathExtensions()) {
            String appendPath = settings.getAppendPath();
            if (appendPath != null && appendPath.length() > 0) {
                path = path + ":" + appendPath;
            }

            if (settings.allowPathPrepend()) {
                String prependPath = settings.getPrependPath();
                if (prependPath != null && prependPath.length() > 0) {
                    path = prependPath + ":" + path;
                }
            }
        }
        if (settings.verifyPath()) {
            path = checkPath(path);
        }
        String[] env = new String[18];
        env[0] = "TERM=" + settings.getTermType();
        env[1] = "PATH=" + this.context.getFilesDir()+"/bin"+":"+path;

        // HACKED FOR QPython
        File filesDir = this.context.getFilesDir();
        File externalStorage;
        String code = NAction.getCode(context);
        if (code.startsWith("qpy")) {
        	externalStorage = new File(Environment.getExternalStorageDirectory(), "com.hipipal.qpyplus");

        } else {
        	externalStorage = new File(Environment.getExternalStorageDirectory(), "Tubebook");

        }
        env[2] = "LD_LIBRARY_PATH="+filesDir+"/lib"+":"+filesDir.getParentFile()+"/files:"+filesDir.getParentFile()+"/lib";

        env[3] = "PYTHONHOME="+filesDir;
        env[4] = "ANDROID_PRIVATE="+filesDir;
        
        if (isQPy3) {
	        env[5] = "PYTHONPATH="
	        		+externalStorage+"/lib/python3.2/site-packages/:"
    				+filesDir+"/lib/python3.2/lib/:"
    				+filesDir+"/lib/python3.2/site-packages/:"
    				+filesDir+"/lib/python3.2/python32.zip:"
    				+filesDir+"/lib/python3.2/lib-dynload/:"
	        		+pyPath;
	        
	        env[14] = "IS_QPY3=1";
	        
	        
        } else {

	        env[5] = "PYTHONPATH="
	        		+filesDir+"/lib/python2.7/:"
	        		+filesDir+"/lib/python2.7/lib-dynload/:"
	        		+filesDir+"/lib/python2.7/site-packages/:"
	        		+externalStorage+"/lib/python2.7/site-packages/:"
	        		+pyPath;
	        
	        env[14] = "PYTHONSTARTUP="+externalStorage+"/lib/python2.7/site-packages/qpythoninit.py";
        }
        
        env[6] = "PYTHONOPTIMIZE=2";
        env[7] = "TMPDIR="+externalStorage+"/cache";
        
        env[8] = "AP_HOST="+NStorage.getSP(this.context, "sl4a.hostname");
        env[9] = "AP_PORT="+NStorage.getSP(this.context, "sl4a.port");
        env[10] = "AP_HANDSHAKE="+NStorage.getSP(this.context, "sl4a.secue");

        env[11] = "ANDROID_PUBLIC="+externalStorage;
        env[12] = "ANDROID_PRIVATE="+this.context.getFilesDir().getAbsolutePath();
        env[13] = "ANDROID_ARGUMENT="+pyPath;

        env[15] = "QPY_USERNO="+NAction.getUserNoId(context);
        env[16] = "QPY_ARGUMENT="+NAction.getExtConf(context);
        env[17] = "PYTHONDONTWRITEBYTECODE=1";

        createSubprocess(processId, settings.getShell(), env);
        mProcId = processId[0];

        setTermOut(new FileOutputStream(mTermFd));
        setTermIn(new FileInputStream(mTermFd));
    }

    private String checkPath(String path) {
        String[] dirs = path.split(":");
        StringBuilder checkedPath = new StringBuilder(path.length());
        for (String dirname : dirs) {
            File dir = new File(dirname);
            if (dir.isDirectory() && FileCompat.canExecute(dir)) {
                checkedPath.append(dirname);
                checkedPath.append(":");
            }
        }
        return checkedPath.substring(0, checkedPath.length()-1);
    }

    @Override
    public void initializeEmulator(int columns, int rows) {
        if (VTTEST_MODE) {
            columns = 80;
            rows = 24;
        }
        super.initializeEmulator(columns, rows);

        Exec.setPtyUTF8Mode(mTermFd, getUTF8Mode());
        setUTF8ModeUpdateCallback(mUTF8ModeNotify);

        mWatcherThread.start();
        sendInitialCommand(mInitialCommand);
    }

    private void sendInitialCommand(String initialCommand) {
    	Log.d("TERM", "sendInitialCommand:"+initialCommand);
        if (initialCommand.length() > 0) {
            write(initialCommand + '\r');
        }
    }

     private void createSubprocess(int[] processId, String shell, String[] env) {
        ArrayList<String> argList = parse(shell);
        String arg0;
        String[] args;

        try {
            arg0 = argList.get(0);
            File file = new File(arg0);
            if (!file.exists()) {
                Log.e(TermDebug.LOG_TAG, "Shell " + arg0 + " not found!");
                throw new FileNotFoundException(arg0);
            } else if (!FileCompat.canExecute(file)) {
                Log.e(TermDebug.LOG_TAG, "Shell " + arg0 + " not executable!");
                throw new FileNotFoundException(arg0);
            }
            args = argList.toArray(new String[1]);
        } catch (Exception e) {
            argList = parse(mSettings.getFailsafeShell());
            arg0 = argList.get(0);
            args = argList.toArray(new String[1]);
        }

        mTermFd = Exec.createSubprocess(arg0, args, env, processId);
    }

    private ArrayList<String> parse(String cmd) {
        final int PLAIN = 0;
        final int WHITESPACE = 1;
        final int INQUOTE = 2;
        int state = WHITESPACE;
        ArrayList<String> result =  new ArrayList<String>();
        int cmdLen = cmd.length();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cmdLen; i++) {
            char c = cmd.charAt(i);
            if (state == PLAIN) {
                if (Character.isWhitespace(c)) {
                    result.add(builder.toString());
                    builder.delete(0,builder.length());
                    state = WHITESPACE;
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    builder.append(c);
                }
            } else if (state == WHITESPACE) {
                if (Character.isWhitespace(c)) {
                    // do nothing
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    state = PLAIN;
                    builder.append(c);
                }
            } else if (state == INQUOTE) {
                if (c == '\\') {
                    if (i + 1 < cmdLen) {
                        i += 1;
                        builder.append(cmd.charAt(i));
                    }
                } else if (c == '"') {
                    state = PLAIN;
                } else {
                    builder.append(c);
                }
            }
        }
        if (builder.length() > 0) {
            result.add(builder.toString());
        }
        return result;
    }

    @Override
    public void updateSize(int columns, int rows) {
        if (VTTEST_MODE) {
            columns = 80;
            rows = 24;
        }
        // Inform the attached pty of our new size:
        Exec.setPtyWindowSize(mTermFd, rows, columns, 0, 0);
        super.updateSize(columns, rows);
    }

    /* XXX We should really get this ourselves from the resource bundle, but
       we cannot hold a context */
    public void setProcessExitMessage(String message) {
        mProcessExitMessage = message;
    }

    private void onProcessExit(int result) {
        if (mSettings.closeWindowOnProcessExit()) {
            finish();
        } else if (mProcessExitMessage != null) {
            try {
                byte[] msg = ("\r\n[" + mProcessExitMessage + "]").getBytes("UTF-8");
                appendToEmulator(msg, 0, msg.length);
                notifyUpdate();
            } catch (UnsupportedEncodingException e) {
                // Never happens
            }
        }
    }

    @Override
    public void finish() {
    	Log.d("ShellTermSession", "finish");
        Exec.hangupProcessGroup(mProcId);
        Exec.close(mTermFd);
        super.finish();
    }

    /**
     * Gets the terminal session's title.  Unlike the superclass's getTitle(),
     * if the title is null or an empty string, the provided default title will
     * be returned instead.
     *
     * @param defaultTitle The default title to use if this session's title is
     *     unset or an empty string.
     */
    public String getTitle(String defaultTitle) {
        String title = super.getTitle();
        if (title != null && title.length() > 0) {
            return title;
        } else {
            return defaultTitle;
        }
    }

    public void setHandle(String handle) {
        if (mHandle != null) {
            throw new IllegalStateException("Cannot change handle once set");
        }
        mHandle = handle;
    }

    public String getHandle() {
        return mHandle;
    }
}
