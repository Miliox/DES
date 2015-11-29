package br.ufpe.ppgee.emilianofirmino.des.service;

/**
 * Created by emiliano on 11/29/15.
 */
public class SystemMonitor {
    static {
        System.loadLibrary("SystemMonitor");
    }

    public static native boolean isProfilerActive();
    public static native void startProfiler();
    public static native void stopProfiler();
}
