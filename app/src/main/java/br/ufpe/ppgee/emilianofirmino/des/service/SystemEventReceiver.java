package br.ufpe.ppgee.emilianofirmino.des.service;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Environment;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by emiliano on 11/19/15.
 */
public class SystemEventReceiver extends BroadcastReceiver {
    private static final String  TAG = "SystemEventReceiver";
    private static final boolean DBG = true;

    private Context context;
    private ContentResolver resolver;
    private Object lock;

    private Timer timer = null;
    private DisplayBrightnessTask task = null;

    private boolean inProgress;

    private BufferedWriter writer = null;

    private int lastBatteryLevel = 0;
    private int lastBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private int lastDisplayBrightness = -1;
    private int lastDisplayBrightnessMode = -1;

    private static final int AIRPLANE_MODE_CHANGED_HASH =
            Intent.ACTION_AIRPLANE_MODE_CHANGED.hashCode();
    private static final int BATTERY_CHANGED_HASH =
            Intent.ACTION_BATTERY_CHANGED.hashCode();
    private final int BATTERY_LOW_HASH =
            Intent.ACTION_BATTERY_LOW.hashCode();
    private final int BATTERY_OKAY_HASH =
            Intent.ACTION_BATTERY_OKAY.hashCode();
    private final int CONNECTIVITY_HASH =
            ConnectivityManager.CONNECTIVITY_ACTION.hashCode();
    private final int POWER_CONNECTED_HASH =
            Intent.ACTION_POWER_CONNECTED.hashCode();
    private final int POWER_DISCONNECTED_HASH =
            Intent.ACTION_POWER_DISCONNECTED.hashCode();
    private final int SCREEN_OFF_HASH =
            Intent.ACTION_SCREEN_OFF.hashCode();
    private final int SCREEN_ON_HASH =
            Intent.ACTION_SCREEN_ON.hashCode();
    private final int SHUTDOWN_HASH =
            Intent.ACTION_SHUTDOWN.hashCode();
    private final int WIFI_STATE_CHANGED_HASH =
            WifiManager.WIFI_STATE_CHANGED_ACTION.hashCode();


    public SystemEventReceiver(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
        this.lock = new Object();
        this.inProgress = false;
    }

    public void startSession() {
        this.inProgress = true;
        try {
            Date now = new Date(System.currentTimeMillis());
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            String filename = Environment.getExternalStorageDirectory().getPath();
            filename += "/system_event_" + formatter.format(now) + ".log";

            writer = new BufferedWriter(new FileWriter(filename));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int state = wm.getDefaultDisplay().getState();
        if (state == Display.STATE_ON) {
            startBrightMonitor();
        }

        try {
            int airplaneModeOn = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON);
            int wifiOn = Settings.Global.getInt(resolver, Settings.Global.WIFI_ON);
            int wifiSleepPolicy = Settings.Global.getInt(resolver, Settings.Global.WIFI_SLEEP_POLICY);
            Log.v(TAG, "AIRPLANE_MODE " + airplaneModeOn);
            Log.v(TAG, "WIFI_ON " + wifiOn);
            Log.v(TAG, "WIFI_SLEEP_POLICY " + wifiSleepPolicy);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        IntentFilter filter = new IntentFilter();

        // Display
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        // Energy
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_SHUTDOWN);

        // Network
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

        context.registerReceiver(this, filter);
    }

    private void startBrightMonitor() {
        stopBrightMonitor();

        task = new DisplayBrightnessTask();
        timer = new Timer();
        timer.schedule(task, 0, 1000);
    }

    private void stopBrightMonitor() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void stopSession() {
        this.inProgress = false;
        context.unregisterReceiver(this);
        stopBrightMonitor();

        try {
            synchronized (lock) {
                if (writer != null) writer.close();
                writer = null;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public boolean sessionInProgress() {
        return inProgress;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int action = intent.getAction().hashCode();

        if (action == CONNECTIVITY_HASH)
            handleConnectivityChanged(intent);
        else if (action == AIRPLANE_MODE_CHANGED_HASH)
            handleAirplaneModeChanged(intent);
        else if (action == BATTERY_CHANGED_HASH)
            handleBatteryChanged(intent);
        else if (action == BATTERY_LOW_HASH)
            handleBatteryLow(intent);
        else if (action == BATTERY_OKAY_HASH)
            handleBatteryOkay(intent);
        else if (action == POWER_CONNECTED_HASH)
            handlePowerConnected(intent);
        else if (action == POWER_DISCONNECTED_HASH)
            handlePowerDisconnected(intent);
        else if (action == SCREEN_OFF_HASH)
            handleScreenOff(intent);
        else if (action == SCREEN_ON_HASH)
            handleScreenOn(intent);
        else if (action == SHUTDOWN_HASH)
            handleShutdown(intent);
        else if (action == WIFI_STATE_CHANGED_HASH)
            handleWifiStateChanged(intent);
    }

    private void handleConnectivityChanged(Intent intent) {
        NetworkInfo networkInfo1 = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        NetworkInfo.DetailedState detailedState = networkInfo1.getDetailedState();
        String networkDetailedState = networkDetailedStateToString(detailedState);

        logEvent(networkTypeToString(networkInfo1.getType()) + "_" + networkDetailedState);
    }


    private void handleAirplaneModeChanged(Intent intent) {
        boolean state = intent.getBooleanExtra("state", false);
        logEvent(state ? "AIRPLANE_MODE_ON" : "AIRPLANE_MODE_OFF");
    }

    private void handleBatteryChanged(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        //int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        //boolean present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false);
        //int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        //int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        //int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

        String batteryStatus;
        switch (status) {
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                batteryStatus = "DISCHARGING";
                break;
            case BatteryManager.BATTERY_STATUS_CHARGING:
                batteryStatus = "CHARGING";
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                batteryStatus = "CHARGE_FULL";
                break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                batteryStatus = "NOT_CHARGING";
                break;
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
            default:
                batteryStatus = "CHARGE_UNKNOWN";
                break;
        }

        if (status != lastBatteryStatus) {
            if (status != BatteryManager.BATTERY_STATUS_CHARGING && status != BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
                logEvent(batteryStatus);
            } else {
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                logEvent(batteryStatus + " " + plugTypeToString(plugged));
            }
        }
        lastBatteryStatus = status;

        if (level != lastBatteryLevel) {
            logEvent("BATTERY_LEVEL " + level);
        }
        lastBatteryLevel = level;
    }

    private void handleBatteryLow(Intent intent) {
        logEvent("BATTERY_LOW");
    }

    private void handleBatteryOkay(Intent intent) {
        logEvent("BATTERY_OKAY");
    }

    private void handlePowerConnected(Intent intent) {
        logEvent("POWER_CONNECTED");
    }

    private void handlePowerDisconnected(Intent intent) {
        logEvent("POWER_DISCONNECTED");
    }

    private void handleScreenOff(Intent intent) {
        logEvent("SCREEN_OFF");
        stopBrightMonitor();

    }

    private void handleScreenOn(Intent intent) {
        logEvent("SCREEN_ON");
        if (timer == null && task == null) {
            startBrightMonitor();
        }
    }

    private void handleShutdown(Intent intent) {
        logEvent("SHUTDOWN");
    }


    private void handleWifiStateChanged(Intent intent) {
        int prev = intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
        int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);

        logEvent(wifiStateToString(prev) + "," + wifiStateToString(state));
    }

    private String networkTypeToString(int type) {
        switch (type) {
            case ConnectivityManager.TYPE_MOBILE:
                return "MOBILE";
            case ConnectivityManager.TYPE_WIFI:
                return "WIFI";
            case ConnectivityManager.TYPE_BLUETOOTH:
                return "BLUETOOTH";
            case ConnectivityManager.TYPE_DUMMY:
                return "DUMMY";
            case ConnectivityManager.TYPE_ETHERNET:
                return "ETHERNET";
            case ConnectivityManager.TYPE_MOBILE_DUN:
                return "MOBILE_DUN";
            case ConnectivityManager.TYPE_WIMAX:
                return "WIMAX";
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                return "MOBILE_HIPRI";
            case ConnectivityManager.TYPE_MOBILE_MMS:
                return "MOBILE_MMS";
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                return "MOBILE_SUPL";
            default:
                return "UNKNOWN";
        }
    }

    private String wifiStateToString(int state) {
        switch(state){
            case WifiManager.WIFI_STATE_DISABLED:
                return "WIFI_DISABLED";
            case WifiManager.WIFI_STATE_DISABLING:
                return "WIFI_DISABLING";
            case WifiManager.WIFI_STATE_ENABLED:
                return "WIFI_ENABLED";
            case WifiManager.WIFI_STATE_ENABLING:
                return "WIFI_ENABLING";
            case WifiManager.WIFI_STATE_UNKNOWN:
            default:
                return "WIFI_UNKNOWN";
        }
    }

    private String plugTypeToString(int plugType) {
        switch (plugType) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "Wireless";
            default:
                return "NO_PLUG";
        }
    }

    private String networkStateToString(NetworkInfo.State state) {
        switch (state) {
            case CONNECTED:
                return "CONNECTED";
            case CONNECTING:
                return "CONNECTING";
            case DISCONNECTED:
                return "DISCONNECTED";
            case DISCONNECTING:
                return "DISCONNECTING";
            case SUSPENDED:
                return "SUSPENDED";
            case UNKNOWN:
            default:
                return "UNKNOWN";
        }
    }

    private String networkDetailedStateToString(NetworkInfo.DetailedState state) {
        switch (state) {
            case AUTHENTICATING:
                return "AUTHENTICATING";
            case BLOCKED:
                return "BLOCKED";
            case CAPTIVE_PORTAL_CHECK:
                return "CAPTIVE_PORTAL_CHECK";
            case CONNECTED:
                return "CONNECTED";
            case CONNECTING:
                return "CONNECTING";
            case DISCONNECTED:
                return "DISCONNECTED";
            case DISCONNECTING:
                return "DISCONNECTING";
            case FAILED:
                return "FAILED";
            case IDLE:
                return "IDLE";
            case OBTAINING_IPADDR:
                return "OBTAINING_IPADDR";
            case SCANNING:
                return "SCANNING";
            case SUSPENDED:
                return "SUSPENDED";
            case VERIFYING_POOR_LINK:
                return "VERIFYING_POOR_LINK";
            default:
                return "UNKNOWN";
        }
    }


    private void logEvent(String event) {
        long timestamp = System.currentTimeMillis();
        String log = timestamp + " " + event;
        if (DBG) {
            Log.v(TAG, log);
        }
        log += "\n";

        try {
            synchronized (lock) {
                if (writer != null) {
                    writer.write(log);
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private class DisplayBrightnessTask extends TimerTask {
        @Override
        public void run() {
            int mode = -1;
            int brightness = -1;

            try {
                mode = Settings.System.getInt(
                    resolver, Settings.System.SCREEN_BRIGHTNESS_MODE);
                brightness = Settings.System.getInt(
                    resolver, Settings.System.SCREEN_BRIGHTNESS);
            } catch (Settings.SettingNotFoundException snfe) {

            }

            if (brightness != lastDisplayBrightness) {
                logEvent("DISPLAY_BRIGHTNESS " + brightness);
            }
            lastDisplayBrightness = brightness;

            if (mode != lastDisplayBrightnessMode) {
                logEvent("DISPLAY_BRIGHTNESS_MODE " +
                    ((mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    ? "AUTOMATIC" : "MANUAL"));
            }
            lastDisplayBrightnessMode = mode;

        }
    }

}
