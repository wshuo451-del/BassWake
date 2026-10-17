package com.example.basswake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class BassWakeService extends Service {

    private static final String CHANNEL_ID = "BassWakeChannel_V2";
    public static volatile boolean isRunning = false;
    private Thread audioThread;
    private AudioTrack audioTrack;
    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        acquireWakeLock();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("低音扬声器全局唤醒中 (澎湃OS 4)")
                .setContentText("已锁定 6 扬声器音乐分频通道")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, notification);
        }

        startSubBassTrack();
        return START_STICKY;
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BassWake::AudioKeepAliveLock");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void startSubBassTrack() {
        if (isRunning) return;
        isRunning = true;

        audioThread = new Thread(() -> {
            final int sampleRate = 44100;
            int minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();

            int bufferSize = Math.max(minBufferSize, sampleRate * 2);

            // 使用 Android 17 推荐的 AudioTrack.Builder
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            // 配合 18Hz 亚音速微幅信号，既让澎湃 OS 4 判定为活跃音乐，又保证人耳完全无声
            audioTrack.setVolume(0.03f);

            // 生成 1 秒的立体声 18Hz 微幅正弦波（幅值设为 16，避免被判定为静音流）
            short[] subBassBuffer = new short[sampleRate * 2];
            double freq = 18.0;
            for (int i = 0; i < sampleRate; i++) {
                short sample = (short) (Math.sin(2.0 * Math.PI * i * freq / sampleRate) * 16);
                subBassBuffer[i * 2] = sample;     // 左声道
                subBassBuffer[i * 2 + 1] = sample; // 右声道
            }

            audioTrack.play();

            while (isRunning) {
                int written = audioTrack.write(subBassBuffer, 0, subBassBuffer.length);
                if (written < 0) {
                    break;
                }
            }
        }, "BassWakeAudioThread_V2");

        audioThread.setPriority(Thread.NORM_PRIORITY);
        audioThread.start();
    }

    private void stopSubBassTrack() {
        isRunning = false;
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {
            }
            audioTrack = null;
        }
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
        releaseWakeLock();
    }

    @Override
    public void onDestroy() {
        stopSubBassTrack();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "低音唤醒常驻服务",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("防止澎湃OS 4回收音乐分频通道");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
