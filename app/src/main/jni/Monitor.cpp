#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <time.h>
#include <linux/limits.h>
#include <android/log.h>
#include <arpa/inet.h>

static const char* kModuleName = "SystemMonitor";

static const char* kCapacityFile =
        "/sys/class/power_supply/battery/capacity";
static const char* kCurrentFile =
        "/sys/class/power_supply/battery/current_now";
static const char* kVoltageFile =
        "/sys/class/power_supply/battery/voltage_now";

static const char* kMaxVoltageFile =
        "/sys/class/power_supply/battery/voltage_max_design";
static const char* kMinVoltageFile =
        "/sys/class/power_supply/battery/voltage_min_design";

static const char* kTemperatureFile =
        "/sys/class/power_supply/battery/temp";

static const char* kWlanStatsRecvBytes =
        "/sys/class/net/wlan0/statistics/rx_bytes";
static const char* kWlanStatsRecvPackets =
        "/sys/class/net/wlan0/statistics/rx_packets";
static const char* kWlanStatsRecvDropped =
        "/sys/class/net/wlan0/statistics/rx_dropped";
static const char* kWlanStatsRecvErrors =
        "/sys/class/net/wlan0/statistics/rx_errors";

static const char* kWlanStatsSentBytes =
        "/sys/class/net/wlan0/statistics/tx_bytes";
static const char* kWlanStatsSentPackets =
        "/sys/class/net/wlan0/statistics/tx_packets";
static const char* kWlanStatsSentDropped =
        "/sys/class/net/wlan0/statistics/tx_dropped";
static const char* kWlanStatsSentErrors =
        "/sys/class/net/wlan0/statistics/tx_errors";

static const char* kMobileStatsRecvBytes =
        "/sys/class/net/rmnet0/statistics/rx_bytes";
static const char* kMobileStatsRecvPackets =
        "/sys/class/net/rment0/statistics/rx_packets";
static const char* kMobileStatsRecvDropped =
        "/sys/class/net/rment0/statistics/rx_dropped";
static const char* kMobileStatsRecvErrors =
        "/sys/class/net/rmnet0/statistics/rx_errors";

static const char* kMobileStatsSentBytes =
        "/sys/class/net/rmnet0/statistics/tx_bytes";
static const char* kMobileStatsSentPackets =
        "/sys/class/net/rmnet0/statistics/tx_packets";
static const char* kMobileStatsSentDropped =
        "/sys/class/net/rmnet0/statistics/tx_dropped";
static const char* kMobileStatsSentErrors =
        "/sys/class/net/rmnet0/statistics/tx_errors";


static const char* kCpuUsageStats = "/proc/stat";

static const char* kCpuIsOnline[4] = {
        "/sys/devices/system/cpu/cpu0/online",
        "/sys/devices/system/cpu/cpu1/online",
        "/sys/devices/system/cpu/cpu2/online",
        "/sys/devices/system/cpu/cpu3/online"};

static const char* kCpuFrequency[4] = {
        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
        "/sys/devices/system/cpu/cpu1/cpufreq/scaling_cur_freq",
        "/sys/devices/system/cpu/cpu2/cpufreq/scaling_cur_freq",
        "/sys/devices/system/cpu/cpu3/cpufreq/scaling_cur_freq"};

static const char* kCpuFreqInState[4] = {
        "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state",
        "/sys/devices/system/cpu/cpu1/cpufreq/stats/time_in_state",
        "/sys/devices/system/cpu/cpu2/cpufreq/stats/time_in_state",
        "/sys/devices/system/cpu/cpu3/cpufreq/stats/time_in_state"};

#define readIntFromFile(filename, buffer, length) \
  (int) readLongFromFile(filename, buffer, length)

#define DDMMService(functionName) \
    Java_br_ufpe_ppgee_emilianofirmino_des_service_SystemMonitor_##functionName

struct ProfileContext {
    bool isActive;
    bool isBinary;

    char battery_logname[PATH_MAX];
    char network_logname[PATH_MAX];
    char processor_logname[PATH_MAX];

    ProfileContext() : isActive(false), isBinary(false) {
      memset(battery_logname,   0, sizeof(char) * PATH_MAX);
      memset(network_logname,   0, sizeof(char) * PATH_MAX);
      memset(processor_logname, 0, sizeof(char) * PATH_MAX);
    }
};

union V {
    float    f;
    long     l;
    int      i;
    uint32_t u;
};

int readLongFromFile(const char* filename, char* buffer, size_t length) {
    long value = -1;
    FILE* file = fopen(filename, "r");

    if (file != NULL && fgets(buffer, length, file) != NULL) {
        value = strtol(buffer, (char**) NULL, 10);
        fclose(file);
    }
    return value;
}

void* battery_monitor(void* ptr) {
    ProfileContext* ctx = (ProfileContext*)ptr;

    const int maxlen = 512;

    FILE* logfile = fopen(ctx->battery_logname, ctx->isBinary ? "wb" : "w");
    char* logline = new char[maxlen + 1];

    if (logfile != NULL) {
        struct timeval begin;
        struct timeval now;
        struct timeval delta;

        struct tm datetime;

        V maxVoltage, minVoltage;

        maxVoltage.i = readIntFromFile(kMaxVoltageFile, logline, maxlen) / 1000000.0f;
        minVoltage.i = readIntFromFile(kMinVoltageFile, logline, maxlen) / 1000000.0f;

        gettimeofday(&begin, NULL);
        for (;;) {
            gettimeofday(&now, NULL);

            V charge, mcurrent, voltage, mpower, temperature;

            charge.i      = readIntFromFile(kCapacityFile, logline, maxlen);
            mcurrent.f    = readIntFromFile(kCurrentFile, logline, maxlen) / 1000.0f;
            voltage.f     = readIntFromFile(kVoltageFile, logline, maxlen) / 1000000.0f;
            mpower.f      = voltage.f * mcurrent.f;
            temperature.f = readIntFromFile(kTemperatureFile, logline, maxlen) / 10.0f;

            if (!ctx->isBinary) {
                timersub(&now, &begin, &delta);

                datetime = *localtime(&now.tv_sec);
                datetime.tm_year += 1900;
                datetime.tm_mon += 1;

                int len = snprintf(logline, maxlen,
                                   "%d/%d/%d-%d:%d:%d.%06ld %ld.%06ld %d %f %f %f %f %f %f\n",
                                   datetime.tm_year, datetime.tm_mon, datetime.tm_mday,
                                   datetime.tm_hour, datetime.tm_min, datetime.tm_sec,
                                   now.tv_usec, delta.tv_sec, delta.tv_usec,
                                   charge.i,
                                   mcurrent.f,
                                   voltage.f,
                                   maxVoltage.f,
                                   minVoltage.f,
                                   mpower.f,
                                   temperature.f);
                if (len > 0) {
                    #ifdef DEBUG
                    __android_log_print(ANDROID_LOG_VERBOSE, kModuleName, "%s", logline);
                    #endif
                    fwrite(logline, len, 1, logfile);
                }
            } else {
                *((uint32_t*) (logline + 0)) = htonl((uint32_t)  now.tv_sec);
                *((uint32_t*) (logline + 4)) = htonl((uint32_t)  now.tv_usec);
                *((uint32_t*) (logline + 8)) = htonl(charge.u);
                *((uint32_t*) (logline + 12)) = htonl(mcurrent.u);
                *((uint32_t*) (logline + 16)) = htonl(voltage.u);
                *((uint32_t*) (logline + 20)) = htonl(mpower.u);
                *((uint32_t*) (logline + 24)) = htonl(temperature.u);

                int len = 28;
                fwrite(logline, len, 1, logfile);
            }

            if (ctx->isActive) {
                usleep(100 * 1000);
            } else {
                break;
            }
        }

        fclose(logfile);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, kModuleName, "fail to open %s", ctx->battery_logname);
    }

    delete[] logline;
    return ctx;
}

void* network_monitor(void* ptr) {
    ProfileContext* ctx = (ProfileContext*)ptr;
    const int maxlen = 512;

    FILE* logfile = fopen(ctx->network_logname, ctx->isBinary ? "wb" : "w");
    char* logline = new char[maxlen + 1];

    if (logfile != NULL) {
        struct timeval begin;
        struct timeval now;
        struct timeval delta;

        struct tm datetime;

        gettimeofday(&begin, NULL);
        for (;;) {
            gettimeofday(&now, NULL);

            long mrx_bytes = readLongFromFile(kMobileStatsRecvBytes, logline, maxlen);
            long mrx_packets = readLongFromFile(kMobileStatsRecvPackets, logline, maxlen);
            long mrx_dropped = readLongFromFile(kMobileStatsRecvDropped, logline, maxlen);
            long mrx_errors = readLongFromFile(kMobileStatsRecvErrors, logline, maxlen);

            long mtx_bytes = readLongFromFile(kMobileStatsSentBytes, logline, maxlen);
            long mtx_packets = readLongFromFile(kMobileStatsSentPackets, logline, maxlen);
            long mtx_dropped = readLongFromFile(kMobileStatsSentDropped, logline, maxlen);
            long mtx_errors = readLongFromFile(kMobileStatsSentErrors, logline, maxlen);

            long wrx_bytes = readLongFromFile(kWlanStatsRecvBytes, logline, maxlen);
            long wrx_packets = readLongFromFile(kWlanStatsRecvPackets, logline, maxlen);
            long wrx_dropped = readLongFromFile(kWlanStatsRecvDropped, logline, maxlen);
            long wrx_errors = readLongFromFile(kWlanStatsRecvErrors, logline, maxlen);

            long wtx_bytes = readLongFromFile(kWlanStatsSentBytes, logline, maxlen);
            long wtx_packets = readLongFromFile(kWlanStatsSentPackets, logline, maxlen);
            long wtx_dropped = readLongFromFile(kWlanStatsSentDropped, logline, maxlen);
            long wtx_errors = readLongFromFile(kWlanStatsSentErrors, logline, maxlen);

            if (!ctx->isBinary) {
                timersub(&now, &begin, &delta);

                datetime = *localtime(&now.tv_sec);
                datetime.tm_year += 1900;
                datetime.tm_mon += 1;

                int len = snprintf(logline, maxlen,
                    "%d/%d/%d-%d:%d:%d.%06ld "
                    "%ld.%06ld "
                    "%ld %ld %ld %ld "
                    "%ld %ld %ld %ld "
                    "%ld %ld %ld %ld "
                    "%ld %ld %ld %ld\n",
                    datetime.tm_year, datetime.tm_mon, datetime.tm_mday,
                    datetime.tm_hour, datetime.tm_min, datetime.tm_sec,
                    now.tv_usec, delta.tv_sec, delta.tv_usec,
                    mrx_bytes, mrx_packets, mrx_dropped, mrx_errors,
                    mtx_bytes, mtx_packets, mtx_dropped, mtx_errors,
                    wrx_bytes, wrx_packets, wrx_dropped, wrx_errors,
                    wtx_bytes, wtx_packets, wtx_dropped, wtx_errors);

                if (len > 0) {
                    #ifdef DEBUG
                    __android_log_print(ANDROID_LOG_VERBOSE, kModuleName, "%s", logline);
                    #endif
                    fwrite(logline, len, 1, logfile);
                }
            } else {
                *((uint32_t*) (logline + 0))  = htonl((uint32_t) now.tv_sec);
                *((uint32_t*) (logline + 4))  = htonl((uint32_t) now.tv_usec);

                *((uint32_t*) (logline + 8))  = htonl((uint32_t) mrx_bytes);
                *((uint32_t*) (logline + 12)) = htonl((uint32_t) mrx_packets);
                *((uint32_t*) (logline + 16)) = htonl((uint32_t) mrx_dropped);
                *((uint32_t*) (logline + 20)) = htonl((uint32_t) mrx_errors);

                *((uint32_t*) (logline + 24)) = htonl((uint32_t) mtx_bytes);
                *((uint32_t*) (logline + 28)) = htonl((uint32_t) mtx_packets);
                *((uint32_t*) (logline + 32)) = htonl((uint32_t) mtx_dropped);
                *((uint32_t*) (logline + 36)) = htonl((uint32_t) mtx_errors);

                *((uint32_t*) (logline + 40)) = htonl((uint32_t) wrx_bytes);
                *((uint32_t*) (logline + 44)) = htonl((uint32_t) wrx_packets);
                *((uint32_t*) (logline + 48)) = htonl((uint32_t) wrx_dropped);
                *((uint32_t*) (logline + 52)) = htonl((uint32_t) wrx_errors);

                *((uint32_t*) (logline + 56)) = htonl((uint32_t) wtx_bytes);
                *((uint32_t*) (logline + 60)) = htonl((uint32_t) wtx_packets);
                *((uint32_t*) (logline + 64)) = htonl((uint32_t) wtx_dropped);
                *((uint32_t*) (logline + 68)) = htonl((uint32_t) wtx_errors);

                int len = 72;
                fwrite(logline, len, 1, logfile);
            }

            if (ctx->isActive) {
                usleep(100 * 1000);
            } else {
                break;
            }
        }
        fclose(logfile);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, kModuleName, "fail to open %s", ctx->network_logname);
    }
    delete[] logline;
    return ctx;
}

void* processor_monitor(void* ptr) {
    ProfileContext* ctx = (ProfileContext*)ptr;
    size_t maxlen = 1025;

    FILE* logfile = fopen(ctx->processor_logname, ctx->isBinary ? "wb" : "w");
    char* logline = new char[maxlen];

    if (logfile != NULL) {
        struct timeval begin;
        struct timeval now;
        struct timeval delta;

        struct tm datetime;
        gettimeofday(&begin, NULL);

        for (;;) {
            gettimeofday(&now, NULL);

            long cpu_freq[4]  = {0, 0, 0, 0};
            long cpu_is_on[4] = {0, 0, 0, 0};
            long cpu_stat[16];

            FILE* stat = fopen(kCpuUsageStats, "r");
            getline(&logline, &maxlen, stat);

            for (int i = 0; i < 4; i++) {
                FILE* cpu_is_on_file = fopen(kCpuIsOnline[i], "r");
                char c = 0;
                if (cpu_is_on_file) {
                    c = fgetc(cpu_is_on_file);
                    fclose(cpu_is_on_file);
                }

                cpu_is_on[i] = c == '1';
                if (cpu_is_on[i]) {
                    FILE* cpu = fopen(kCpuFreqInState[i], "r");
                    if (cpu) {
                        cpu_freq[i] = readLongFromFile(kCpuFrequency[i], logline, maxlen);
                        fclose(cpu);
                    }
                }

                getline(&logline, &maxlen, stat);
                sscanf(logline, "%*s %ld %ld %ld %ld",
                       &cpu_stat[4 * i],     &cpu_stat[4 * i + 1],
                       &cpu_stat[4 * i + 2], &cpu_stat[4 * i + 3]);
            }

            fclose(stat);

            if (!ctx->isBinary) {
                timersub(&now, &begin, &delta);

                datetime = *localtime(&now.tv_sec);
                datetime.tm_year += 1900;
                datetime.tm_mon  += 1;

                int len = snprintf(logline, maxlen,
                                   "%d/%d/%d-%d:%d:%d.%06ld %ld.%06ld ",
                                   datetime.tm_year, datetime.tm_mon, datetime.tm_mday,
                                   datetime.tm_hour, datetime.tm_min, datetime.tm_sec,
                                   now.tv_usec, delta.tv_sec, delta.tv_usec);

                len += snprintf(logline + len, maxlen - len, "%ld %ld %ld %ld ",
                                cpu_is_on[0] , cpu_is_on[1], cpu_is_on[2], cpu_is_on[3]);

                len += snprintf(logline + len, maxlen - len, "%ld %ld %ld %ld ",
                                cpu_freq[0], cpu_freq[1], cpu_freq[2], cpu_freq[3]);

                for (int i = 0; i < 4; i++) {
                    len += snprintf(logline + len, maxlen - len, "%ld %ld %ld %ld ",
                                    cpu_stat[4 * i], cpu_stat[4 * i + 1], cpu_stat[4 * i + 2],
                                    cpu_stat[4 * i + 3]);
                }

                logline[len - 1] = '\n';
                logline[len] = '\0';
                if (len > 0) {
                    #ifdef DEBUG
                    __android_log_print(ANDROID_LOG_VERBOSE, kModuleName, "%s", logline);
                    #endif
                    fwrite(logline, len, 1, logfile);
                }
            } else {
                *((uint32_t*) (logline + 0))  = htonl((uint32_t) now.tv_sec);
                *((uint32_t*) (logline + 4))  = htonl((uint32_t) now.tv_usec);

                *((uint32_t*) (logline + 8))  = htonl((uint32_t) cpu_is_on[0]);
                *((uint32_t*) (logline + 12)) = htonl((uint32_t) cpu_is_on[1]);
                *((uint32_t*) (logline + 16)) = htonl((uint32_t) cpu_is_on[2]);
                *((uint32_t*) (logline + 20)) = htonl((uint32_t) cpu_is_on[3]);

                *((uint32_t*) (logline + 24)) = htonl((uint32_t) cpu_freq[0]);
                *((uint32_t*) (logline + 28)) = htonl((uint32_t) cpu_freq[1]);
                *((uint32_t*) (logline + 32)) = htonl((uint32_t) cpu_freq[2]);
                *((uint32_t*) (logline + 36)) = htonl((uint32_t) cpu_freq[3]);

                *((uint32_t*) (logline + 40)) = htonl((uint32_t) cpu_stat[0]);
                *((uint32_t*) (logline + 44)) = htonl((uint32_t) cpu_stat[1]);
                *((uint32_t*) (logline + 48)) = htonl((uint32_t) cpu_stat[2]);
                *((uint32_t*) (logline + 52)) = htonl((uint32_t) cpu_stat[3]);

                *((uint32_t*) (logline + 56)) = htonl((uint32_t) cpu_stat[4]);
                *((uint32_t*) (logline + 60)) = htonl((uint32_t) cpu_stat[5]);
                *((uint32_t*) (logline + 64)) = htonl((uint32_t) cpu_stat[6]);
                *((uint32_t*) (logline + 68)) = htonl((uint32_t) cpu_stat[7]);

                *((uint32_t*) (logline + 72)) = htonl((uint32_t) cpu_stat[8]);
                *((uint32_t*) (logline + 76)) = htonl((uint32_t) cpu_stat[9]);
                *((uint32_t*) (logline + 80)) = htonl((uint32_t) cpu_stat[10]);
                *((uint32_t*) (logline + 84)) = htonl((uint32_t) cpu_stat[11]);

                *((uint32_t*) (logline + 88)) = htonl((uint32_t) cpu_stat[12]);
                *((uint32_t*) (logline + 92)) = htonl((uint32_t) cpu_stat[13]);
                *((uint32_t*) (logline + 96)) = htonl((uint32_t) cpu_stat[14]);
                *((uint32_t*) (logline + 100)) = htonl((uint32_t) cpu_stat[15]);

                int len = 104;
                fwrite(logline, len, 1, logfile);
            }

            if (ctx->isActive) {
                usleep(100 * 1000);
            } else {
                break;
            }
        }
        fclose(logfile);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, kModuleName, "fail to open %s", ctx->network_logname);
    }

    delete[] logline;
    return ctx;
}

static ProfileContext profile_context;
static pthread_t battery_monitor_thread = 0;
static pthread_t network_monitor_thread = 0;
static pthread_t processor_monitor_thread = 0;

extern "C" JNIEXPORT void JNICALL
DDMMService(startProfiler)(JNIEnv* env, jclass klass,
    jboolean batteryMonitor, jboolean processorMonitor, jboolean networkMonitor, jboolean isBinary) {
  if (battery_monitor_thread == 0 &&
      network_monitor_thread == 0 &&
      processor_monitor_thread == 0) {
    time_t now = time(NULL);
    struct tm today = *localtime(&now);

    sprintf(profile_context.battery_logname,
            "/sdcard/battmon_%d-%d-%d_%d-%d-%d.log", today.tm_year + 1900,
            today.tm_mon + 1, today.tm_mday, today.tm_hour, today.tm_min,
            today.tm_sec);

    sprintf(profile_context.network_logname,
            "/sdcard/netmon_%d-%d-%d_%d-%d-%d.log", today.tm_year + 1900,
            today.tm_mon + 1, today.tm_mday, today.tm_hour, today.tm_min,
            today.tm_sec);

    sprintf(profile_context.processor_logname,
            "/sdcard/procmon_%d-%d-%d_%d-%d-%d.log", today.tm_year + 1900,
            today.tm_mon + 1, today.tm_mday, today.tm_hour, today.tm_min,
            today.tm_sec);

    profile_context.isActive = true;
    profile_context.isBinary = (bool) isBinary;

    if (batteryMonitor)
        pthread_create(&battery_monitor_thread, NULL, battery_monitor, &profile_context);
    if (networkMonitor)
        pthread_create(&network_monitor_thread, NULL, network_monitor, &profile_context);
    if (processorMonitor)
        pthread_create(&processor_monitor_thread, NULL, processor_monitor, &profile_context);
  } else {
    jclass klass = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(klass, "profile already running");
  }
}

extern "C" JNIEXPORT void JNICALL
DDMMService(stopProfiler)(JNIEnv* env, jclass klass) {
  if (battery_monitor_thread != 0 || network_monitor_thread != 0 || processor_monitor_thread != 0) {
    profile_context.isActive = false;

    pthread_join(battery_monitor_thread, (void**)NULL);
    pthread_join(network_monitor_thread, (void**)NULL);
    pthread_join(processor_monitor_thread, (void**)NULL);

    battery_monitor_thread = 0;
    network_monitor_thread = 0;
    processor_monitor_thread = 0;
  } else {
    jclass klass = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(klass, "profile is not running");
  }
}

extern "C" JNIEXPORT bool JNICALL
DDMMService(isProfilerActive)(JNIEnv* env, jclass klass) {
  return battery_monitor_thread != 0 ||
         network_monitor_thread != 0 ||
         processor_monitor_thread != 0;
}
