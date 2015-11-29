package br.ufpe.ppgee.emilianofirmino.des.suite;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.IOException;

import br.ufpe.ppgee.emilianofirmino.des.R;

public class NetworkStressActivity extends AppCompatActivity implements NetClient.PlungeClientObserver {
    private final String DEFAULT_URL  = "192.168.0.25";
    private final int    DEFAULT_PORT = 12345;
    private final int    DEFAULT_PACKET_SIZE = 1024;

    private EditText urlInput;
    private EditText portInput;
    private EditText packetSizeInput;
    private EditText timeBetweenSession;
    private Spinner  transferModeOption;
    private Spinner  transferSizeOption;

    private Button   runButton;

    private NetClient client;
    private Thread launcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_stress);
        initializeUiComponents();
    }

    private void initializeUiComponents() {
        this.urlInput           = (EditText) findViewById(R.id.input_url);
        this.portInput          = (EditText) findViewById(R.id.input_port);
        this.packetSizeInput    = (EditText) findViewById(R.id.input_packet_size);
        this.timeBetweenSession = (EditText) findViewById(R.id.input_intermission);
        this.transferModeOption = (Spinner)  findViewById(R.id.input_test_mode);
        this.transferSizeOption = (Spinner)  findViewById(R.id.input_transfer_size);
        this.runButton          = (Button)   findViewById(R.id.button_run);

        this.urlInput.setHint(DEFAULT_URL);
        this.portInput.setHint(Integer.toString(DEFAULT_PORT));
        this.packetSizeInput.setHint(Integer.toString(DEFAULT_PACKET_SIZE));

        this.urlInput.setText(DEFAULT_URL);
        this.portInput.setText("12345");
        this.packetSizeInput.setText("1");

        ArrayAdapter<CharSequence> testOptions = ArrayAdapter.createFromResource(
            this, R.array.stress_mode_array, android.R.layout.simple_spinner_item);
        ArrayAdapter<CharSequence> sizeOptions = ArrayAdapter.createFromResource(
            this, R.array.data_unit_array, android.R.layout.simple_spinner_item);

        this.transferModeOption.setAdapter(testOptions);
        this.transferSizeOption.setAdapter(sizeOptions);

        this.transferModeOption.setSelection(1);
        this.transferSizeOption.setSelection(1);

        this.runButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            String run = getString(R.string.execute_test);
            String end = getString(R.string.stop_test);

            if (run.equals(runButton.getText())) {
                final String url = (urlInput.length() > 0)
                    ? urlInput.getText().toString()
                    : DEFAULT_URL;

                final int port = (portInput.length() > 0)
                    ? Integer.parseInt(portInput.getText().toString())
                    : DEFAULT_PORT;

                final int dueTimeInSeconds = (timeBetweenSession.length() > 0)
                    ? Integer.parseInt(timeBetweenSession.getText().toString()) * 1000
                    : 1000;

                int totalDataToTransfer = (packetSizeInput.length() > 0)
                    ? Integer.parseInt(packetSizeInput.getText().toString())
                    : DEFAULT_PACKET_SIZE;

                int dataSizeOption = transferSizeOption.getSelectedItemPosition();

                if (dataSizeOption == 1) {
                    totalDataToTransfer *= 1024;
                }
                else if (dataSizeOption == 2) {
                    totalDataToTransfer *= 1024 * 1024;
                }

                int connType = transferModeOption.getSelectedItemPosition();

                String msg = "url:" + url;
                msg += ",conn:" + connType;

                setUiComponentsEnabled(false);
                runButton.setText(end);

                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                launcher = new Thread(
                    new ClientLauncher(url, port, totalDataToTransfer, dueTimeInSeconds));
                launcher.start();
            }
            else if (end.equals(runButton.getText())) {
                if (launcher != null) {
                    synchronized (launcher) {
                        launcher.notify();
                    }
                }
            }
            }
        });
    }

    private void setUiComponentsEnabled(boolean enabled) {
        View views[] = {
            urlInput,
            portInput,
            packetSizeInput,
            transferModeOption,
            transferSizeOption
        };

        for (View v : views) {
            v.setEnabled(enabled);
        }
    }

    @Override
    public void connectionFinished(NetClient source) {
        runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    runButton.setText("Run");
                    setUiComponentsEnabled(true);
                    String message = "Finished";
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    private class ClientLauncher implements Runnable {
        private final String url;
        private final int port;
        private final int packet;
        private final long duration;

        public ClientLauncher(String url, int port, int packet, long duration) {
            this.url = url;
            this.port = port;
            this.packet = packet;
            this.duration = duration;
        }

        @Override
        public void run() {
            client = new NetClient(
                NetClient.StressMode.FULL_DUPLEX, url, port, packet, duration);
            client.setObserver(NetworkStressActivity.this);

            boolean connected = false;
            try {
                client.start();
                connected = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            final boolean isConnected = connected;
            runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                    if (!isConnected) {
                        setUiComponentsEnabled(true);
                        runButton.setText(getString(R.string.execute_test));
                    }
                    String message = isConnected ? "Connection Success" : "Connection Failed";
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    }
                }
            );

            synchronized (launcher) {
                try {
                    launcher.wait(duration * 1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                    return;
                }
            }

            if (client != null) {
                client.abort();
                runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            setUiComponentsEnabled(true);
                            runButton.setText("Run");
                            String message = "Finished";
                            Toast.makeText(
                                getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        }
                    }
                );
            }
            launcher = null;
        }
    };
}
