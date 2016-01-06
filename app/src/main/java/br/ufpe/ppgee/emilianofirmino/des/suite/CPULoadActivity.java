package br.ufpe.ppgee.emilianofirmino.des.suite;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import br.ufpe.ppgee.emilianofirmino.des.R;

public class CPULoadActivity extends AppCompatActivity {

    private static final String TAG = "CPULoadActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cpuload);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int cpuCount = Runtime.getRuntime().availableProcessors();

                StringBuilder command = new StringBuilder();
                command.append("cd /data/local/tmp\n");
                command.append("stop mpdecision\n");
                for (int i = 1; i < cpuCount; i++) {
                    command.append("echo 0 > /sys/devices/system/cpu/cpu" + i + "/online\n");
                }

                String scalingFrequencies = "";
                try {
                    FileReader fileReader = new FileReader(
                        new File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies"));
                    BufferedReader br = new BufferedReader(fileReader);
                    scalingFrequencies = br.readLine().replace("\n","");
                } catch (FileNotFoundException fnfe) {

                } catch (IOException ioe) {

                }
                command.append("for freq in " + scalingFrequencies + "; do\n");
                command.append("echo userspace > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor\n");
                command.append("echo $freq > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq");
                command.append("echo $freq > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
                command.append("echo $freq > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed");
                command.append("./cpuload &\n");
                command.append("cpuload_pid=$!\n");

                command.append("for load in 5 10 15 20 25 30 35 40 45 50 55 60 65 70 75 80 85 90 95 100; do\n");

                command.append("./cpulimit --pid=$cpuload_pid --limit=$load &\n");
                command.append("cpulimit_pid=$!\n");
                command.append("sleep 20\n");
                command.append("kill -9 $cpulimit_pid\n");

                command.append("done\n");

                command.append("kill -9 $cpuload_pid\n");
                command.append("sleep 20\n");

                command.append("done\n");

                command.append("start mpdecision\n");

                runPrivilegedCommand(command.toString());
            }
        });
        t.start();
    }


    private void runPrivilegedCommand(String cmd) {
        Log.v(TAG, "Running Command:\n" + cmd);

        boolean rooted = false;
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd);
            os.writeBytes("\nexit\n");
            os.flush();
            p.waitFor();
            rooted = p.exitValue() != 255;
        } catch (Exception e) {
            Log.e(TAG, "Root launch an exception", e);
        }

        final String result = rooted ? "SUCESS" : "FAILED";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CPULoadActivity.this, result, Toast.LENGTH_SHORT).show();
            }
        });

        finish();
    }

}
