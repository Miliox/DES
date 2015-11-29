package br.ufpe.ppgee.emilianofirmino.des.suite;

import android.content.ContentResolver;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import br.ufpe.ppgee.emilianofirmino.des.R;

public class DisplayStressActivity extends AppCompatActivity {
    public static final String TAG = "DisplayStress";

    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED   = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE  = 0xFF0000FF;

    private Timer clock;
    private OutputStreamWriter loggerStream = null;
    private ContentResolver resolver;

    private boolean buttonVisible = false;
    private int bright = -1;
    private View root;

    private int colorIndex = 0;
    private int colorSetup[] = {BLACK, WHITE, RED, GREEN, BLUE};
    private String colorName[] = {"BLACK", "WHITE", "RED", "GREEN", "BLUE"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        setContentView(R.layout.activity_display_stress);
        getSupportActionBar().hide();

        try {
            Date now = new Date(System.currentTimeMillis());
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

            String filename = "display_stress_" + formatter.format(now) + ".log";
            File loggerFile = new File(Environment.getExternalStorageDirectory(), filename);

            loggerStream = new OutputStreamWriter(new FileOutputStream(loggerFile));
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("DisplayStress", "failed to open file");
        }

        resolver = getContentResolver();
        clock = new Timer();
        clock.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (bright < 255) {
                            bright++;
                            setDisplayBrightness(bright);
                        } else {
                            bright = 0;
                            colorIndex++;
                            if (colorIndex < colorSetup.length) {
                                setBackgroundColor(colorSetup[colorIndex]);
                                setDisplayBrightness(bright);
                            } else {
                                if (loggerStream != null) {
                                    try {
                                        loggerStream.flush();
                                        loggerStream.close();
                                        loggerStream = null;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                onBackPressed();
                            }
                        }
                        if (colorIndex < colorSetup.length) {
                            String message = colorName[colorIndex] + " " + bright;
                            Log.v(TAG, message);
                            long timestamp = System.currentTimeMillis();
                            if (loggerStream != null) {
                                try {
                                    loggerStream.write(timestamp + " " + message + "\n");
                                } catch (Exception e) {}
                            }
                        }
                    }
                });
            }
        }, 10 * 1000, 5 * 1000);

        root = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!buttonVisible)
                    showSystemButtons();
                else
                    hideSystemButtons();

                buttonVisible = !buttonVisible;
                return false;
            }
        });

        hideSystemButtons();
        setBackgroundColor(colorSetup[0]);
        setDisplayBrightness(0);
    }

    private void setBackgroundColor(int color) {
        root.setBackgroundColor(color);
    }

    private void setDisplayBrightness(int brightness) {
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
    }

    private void hideSystemButtons() {
        root.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    private void showSystemButtons() {
        root.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setDisplayBrightness(255);
    }
}
