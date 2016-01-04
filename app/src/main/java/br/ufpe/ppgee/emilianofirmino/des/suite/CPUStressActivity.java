package br.ufpe.ppgee.emilianofirmino.des.suite;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import br.ufpe.ppgee.emilianofirmino.des.R;

public class CPUStressActivity extends AppCompatActivity {
    private int numberOfCPU = 0;
    private int stressCores = -1;
    private Worker workers[] = null;
    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cpustress);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        numberOfCPU = Runtime.getRuntime().availableProcessors();

        Log.i("CPUStress", "number of cpus: " + numberOfCPU);

        workers = new Worker[numberOfCPU];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Worker();
        }
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                stressCores += 1;
                if (stressCores < numberOfCPU) {
                    workers[stressCores].start();
                } else {
                    for (int i = 0; i < workers.length; i++) {
                        workers[i].shutdown();
                    }
                    timer.cancel();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
                }
            }
        }, 60 * 1000, 5 * 60 * 1000);
    }

    private class Worker extends Thread {
        private boolean requestStop = false;

        public void shutdown() {
            requestStop = true;
        }

        public void run() {
            Random r = new Random();
            while (!requestStop) {
                Math.sqrt(r.nextDouble());
            }
        }
    }
}
