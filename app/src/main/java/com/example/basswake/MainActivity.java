package com.example.basswake;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(60, 60, 60, 60);

        statusText = new TextView(this);
        statusText.setTextSize(20);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 0, 0, 60);

        Button batteryBtn = new Button(this);
        batteryBtn.setText("第一步：解除澎湃OS 4后台冻结限制");
        batteryBtn.setTextSize(16);
        batteryBtn.setOnClickListener(v -> requestIgnoreBatteryOptimizations());

        Button startBtn = new Button(this);
        startBtn.setText("第二步：开启全局低音唤醒");
        startBtn.setTextSize(18);
        startBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, BassWakeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            updateStatus(true);
            Toast.makeText(this, "澎湃OS 4 低音分频通道已锁定", Toast.LENGTH_SHORT).show();
        });

        Button stopBtn = new Button(this);
        stopBtn.setText("关闭低音唤醒服务");
        stopBtn.setTextSize(16);
        stopBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, BassWakeService.class);
            stopService(intent);
            updateStatus(false);
            Toast.makeText(this, "低音唤醒服务已停止", Toast.LENGTH_SHORT).show();
        });

        layout.addView(statusText);
        layout.addView(batteryBtn);
        layout.addView(startBtn);
        layout.addView(stopBtn);
        setContentView(layout);

        updateStatus(BassWakeService.isRunning);
    }

    private void requestIgnoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "已处于澎湃OS 4后台无限制白名单中", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus(boolean running) {
        if (running) {
            statusText.setText("当前状态：运行中 (澎湃OS 4 满血分频已锁定)");
            statusText.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            statusText.setText("当前状态：未开启");
            statusText.setTextColor(Color.parseColor("#C62828"));
        }
    }
}
