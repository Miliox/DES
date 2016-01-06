package br.ufpe.ppgee.emilianofirmino.des.suite;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.DataOutputStream;

import br.ufpe.ppgee.emilianofirmino.des.R;

public class CPULoadActivity extends AppCompatActivity {

    String TAG = "CPULoadActivity";

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
                String cmd = "cd /data/local/tmp\n" + "source cpu_stress.sh\n";
                runPrivilegedCommand(cmd);
            }
        });
        t.start();
    }

    private void runPrivilegedCommand(String cmd) {
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

        Toast.makeText(getApplicationContext(),
                rooted ? "SUCESS" : "FAILED", Toast.LENGTH_SHORT).show();
        finish();
    }

}
