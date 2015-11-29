package br.ufpe.ppgee.emilianofirmino.des.service;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

@SuppressLint("Wakelock")
public class DESService extends IntentService {
    public static final String ACTION_IS_ACTIVE = "isActive";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_START = "start";

    public static final String EXTRA_ACTIVE = "active";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_PREVENT_SLEEP = "prevent-sleep";

    public static final String STATUS_IGNORED = "ignored";
    public static final String STATUS_INTERRUPTED = "interrupted";
    public static final String STATUS_PROCESSED = "processed";

    private static WakeLock wakelock = null;
    private static SystemEventReceiver systemEventReceiver = null;

    public DESService() {
        super("DESService");
        Log.d("DESService", "DESService Created");
        systemEventReceiver = new SystemEventReceiver(this);
    }

    protected void onHandleIntent(Intent request) {
        String action = request.getAction();
        Intent response = new Intent(action);

        if (action.equals(ACTION_START)) {
            processStart(request, response);
        } else if (action.equals(ACTION_STOP)) {
            processStop(response);
        } else if (action.equals(ACTION_IS_ACTIVE)) {
            processIsActive(response);
        } else {
            response.putExtra(EXTRA_STATUS, STATUS_IGNORED);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(response);
    }

    private void processStart(Intent request, Intent response) {
        boolean preventSleep = request.getBooleanExtra(EXTRA_PREVENT_SLEEP, false);
        try {
            if (preventSleep) {
                if (wakelock == null)
                {
                    wakelock = ((PowerManager) getApplicationContext()
                        .getSystemService(Context.POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DESService");
                }
                wakelock.acquire();
            }

            systemEventReceiver.startSession();
            SystemMonitor.startProfiler();

            response.putExtra(EXTRA_STATUS, STATUS_PROCESSED);
            response.putExtra(EXTRA_PREVENT_SLEEP, preventSleep);
        } catch (Exception e) {
            response.putExtra(EXTRA_STATUS, STATUS_INTERRUPTED);
        }
    }

    private void processStop(Intent response) {
        try {
            boolean preventSleep = wakelock != null;
            if (preventSleep) {
                wakelock.release();
                wakelock = null;
            }
            systemEventReceiver.stopSession();
            SystemMonitor.stopProfiler();

            response.putExtra(EXTRA_STATUS, STATUS_PROCESSED);
            response.putExtra(EXTRA_PREVENT_SLEEP, preventSleep);
        } catch (Exception e) {
            response.putExtra(EXTRA_STATUS, STATUS_INTERRUPTED);
        }
    }

    private void processIsActive(Intent response) {
        response.putExtra(EXTRA_STATUS, STATUS_PROCESSED);
        response.putExtra(EXTRA_ACTIVE, SystemMonitor.isProfilerActive());
        response.putExtra(EXTRA_PREVENT_SLEEP, wakelock != null);
    }
}
