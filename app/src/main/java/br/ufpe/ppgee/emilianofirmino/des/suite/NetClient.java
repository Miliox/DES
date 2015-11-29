package br.ufpe.ppgee.emilianofirmino.des.suite;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.Semaphore;

public class NetClient {
    final private StressMode mode;
    final private String     url;
    final private int        port;
    final private int        transferByteSize;
    final private long       packetDueTime;

    private PlungeClientObserver observer;

    private Thread controller;
    private Thread reader;
    private Thread writer;

    public NetClient(
            StressMode stressMode,
            String     serverUrl,
            int        serverPort,
            int        transferByteSize,
            long       packetDueTimeInMilliseconds) {

        this.mode = stressMode;
        this.url  = serverUrl;
        this.port = serverPort;

        this.transferByteSize = transferByteSize;
        this.packetDueTime    = packetDueTimeInMilliseconds;
    }

    public void setObserver(PlungeClientObserver observer) {
        this.observer = observer;
    }

    public void start() throws IOException, IllegalStateException {
        if (this.controller != null) {
            throw new IllegalStateException("Session in progress, abort or try later");
        }
        connectAndTransferAsync();
    }

    private void connectAndTransferAsync() throws IOException {
        this.controller = new Thread(new Runnable() {
            @Override
            public void run() {
                controllerLoop();
            }
        });
        this.controller.start();
    }

    public void abort() {
    }

    private void controllerLoop() {
        Semaphore semaphore = new Semaphore(2);

        for (int i = 0; i < 10; i++) {
            Log.d("PlungeClient", "start iteration " + i);
            Socket conn;
            InputStream in;
            OutputStream out;

            try {
                Thread.sleep(30 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                conn = new Socket(url, port);
                in = conn.getInputStream();
                out = conn.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            this.reader = new ReaderThread(semaphore, in, this.transferByteSize);
            this.writer = new WriterThread(semaphore, out, this.transferByteSize);

            try {
                semaphore.acquire(2);
            } catch (Exception e) { }

            this.reader.start();
            this.writer.start();

            Log.d("PlungeClient", "wait transfer finish");
            try {
                semaphore.acquire();
                semaphore.acquire();
                semaphore.release(2);
            } catch (Exception e) { }
            Log.d("PlungeClient", "transfer finished");

            Log.d("PlungeClient", "close connection and sleep");
            try {
                in.close();
                out.close();
                conn.close();
                Thread.sleep(this.packetDueTime);
            } catch (Exception e) { }
            Log.d("PlungeClient", "finish iteration");
        }

        Log.d("PlungeClient", "notify observer");
        if (observer != null) {
            observer.connectionFinished(this);
        }
        this.controller = null;
    }

    private class ReaderThread extends Thread {
        private Semaphore   semaphore;
        private InputStream input;
        private int         transferBytes;

        public ReaderThread(Semaphore semaphore, InputStream input, int transferBytes) {
            this.input = input;
            this.semaphore = semaphore;
            this.transferBytes = transferBytes;
        }

        @Override
        public void run() {
            Log.d("PlungeClient", "reader thread start transfer " + transferBytes);
            if (mode == StressMode.SIMPLEX_TX) {
                Log.d("PlungeClient", "reader thread finish because simplex_tx");
                this.semaphore.release();
                return;
            }

            byte[] payload = new byte[1024];

            int count = 0;
            while (count < this.transferBytes) {
                try {
                    count += this.input.read(payload);
                } catch (Exception e) {
                    Log.d("PlungeClient", "reader thread interrupted by exception");
                    break;
                }
            }

            try {
                semaphore.release();
            } catch (Exception e) { }
            Log.d("PlungeClient", "reader thread finish");
        }
    }

    private class WriterThread extends Thread {
        private OutputStream output;
        private Semaphore    semaphore;
        private int          transferBytes;

        public WriterThread(Semaphore semaphore, OutputStream output, int transferBytes) {
            this.output = output;
            this.semaphore = semaphore;
            this.transferBytes = transferBytes;
        }

        @Override
        public void run() {
            Log.d("PlungeClient", "writer thread start transfer " + transferBytes);
            if (mode == StressMode.SIMPLEX_RX) {
                Log.d("PlungeClient", "writer thread finish because simplex_rx");
                this.semaphore.release();
                return;
            }

            byte[] payload = new byte[1024];
            Random r = new Random();
            r.nextBytes(payload);

            int n = this.transferBytes / 1024;
            for(int i = 0; i < n; i++) {
                try {
                    this.output.write(payload);
                    this.output.flush();
                } catch (Exception e) {
                    Log.d("PlungeClient", "writer thread interrupted by exception");
                    break;
                }
            }

            try {
                this.semaphore.release();
            } catch (Exception e) { }
            Log.d("PlungeClient", "writer thread finished");
        }
    }

    public enum StressMode {
        SIMPLEX_RX, SIMPLEX_TX, HALF_DUPLEX, FULL_DUPLEX
    }

    public interface PlungeClientObserver {
        public void connectionFinished(NetClient source);
    }
}
