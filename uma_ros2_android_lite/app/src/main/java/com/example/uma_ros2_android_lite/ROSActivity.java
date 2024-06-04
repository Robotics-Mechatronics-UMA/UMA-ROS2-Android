package com.example.uma_ros2_android_lite;

// Author: Esteve Fernandez <esteve@apache.org>
// Edited by Manuel Cordoba Ramos <manuelcordoba123@gmail.com>
// Implementa ROS 2 y define sus características.
// Implement ROS 2 and define its features.

import java.util.Timer;
import java.util.TimerTask;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.ros2.rcljava.RCLJava;
import org.ros2.rcljava.executors.Executor;
import org.ros2.rcljava.executors.SingleThreadedExecutor;

public class ROSActivity extends AppCompatActivity {
    private Executor rosExecutor;
    private Timer timer;
    private Handler handler;

    private static String logtag = ROSActivity.class.getName();

    private static long SPINNER_DELAY = 0;
    private static long SPINNER_PERIOD_MS = 200;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.handler = new Handler(getMainLooper());
        RCLJava.rclJavaInit();
        this.rosExecutor = this.createExecutor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                Runnable runnable = new Runnable() {
                    public void run() {
                        rosExecutor.spinSome();
                    }
                };
                handler.post(runnable);
            }
        }, this.getDelay(), this.getPeriod());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timer != null) {
            timer.cancel();
        }
    }

    public void run() {
        rosExecutor.spinSome();
    }

    public Executor getExecutor() {
        return this.rosExecutor;
    }

    protected Executor createExecutor() {
        return new SingleThreadedExecutor();
    }

    protected long getDelay() {
        return SPINNER_DELAY;
    }

    protected long getPeriod() {
        return SPINNER_PERIOD_MS;
    }
}
