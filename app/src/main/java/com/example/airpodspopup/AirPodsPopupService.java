package com.example.airpodspopup;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import android.os.Handler;

public class AirPodsPopupService extends Service {
    private WindowManager manager;
    private View popup;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long lastShown=0;
    private String lastName="";
    private final Runnable dismiss = new Runnable() {
        @Override
        public void run() {
            removePopup();
            stopSelf();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String name = intent == null ? null : intent.getStringExtra("device_name");
        if (name == null || name.trim().isEmpty()) name = "AirPods Pro";
        String event = intent == null ? "BOTH" : intent.getStringExtra("bud_event");
        if (event == null) event = "BOTH";
        long now = SystemClock.elapsedRealtime();
        if (popup != null && name.equals(lastName) && now - lastShown < 2500) {
            return START_NOT_STICKY;
        }
        lastShown = now;
        lastName = name;
        int battery=getSharedPreferences("airpods", MODE_PRIVATE).getInt("battery", -1);
        Log.d("AirPodsBattery", "popup battery: "+battery);
        showPopup(name, event);
        return START_NOT_STICKY;
    }

//    @SuppressLint("SetTextI18n")
//    private void showPopup(String name){
//        removePopup();
//        windowManager=(WindowManager) getSystemService(WINDOW_SERVICE);
//        popupView=new LinearLayout(this);
//        popupView.setOrientation(LinearLayout.VERTICAL);
//        popupView.setGravity(Gravity.CENTER);
//        popupView.setPadding(45, 45, 45, 45);
//
//        GradientDrawable background=new GradientDrawable();
//        background.setColor(Color.rgb(30, 30, 35));
//        background.setCornerRadius(50);
//        background.setStroke(2, Color.rgb(85, 85, 95));
//
//        popupView.setBackground(background);
//
//        TextView title=new TextView(this);
//        title.setText(name);
//        title.setTextColor(Color.WHITE);
//        title.setTextSize(22);
//        title.setTypeface(null, Typeface.BOLD);
//        title.setGravity(Gravity.CENTER);
//
//        TextView status = new TextView(this);
//        status.setText("Connected");
//        status.setTextColor(Color.rgb(100, 230, 140));
//        status.setTextSize(16);
//        status.setGravity(Gravity.CENTER);
//        status.setPadding(0, 18, 0, 0);
//
//        popupView.addView(title);
//        popupView.addView(status);
//
//        WindowManager.LayoutParams params=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
//
//        params.gravity=Gravity.BOTTOM;
//        params.x=0;
//        params.y=100;
//
//        windowManager.addView(popupView, params);
//
//        handler.removeCallbacks(dismissRunnable);
//        handler.postDelayed(dismissRunnable, 5000);
//    }

    private void showPopup(String name, String event) {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("AirPodsPopup", "Overlay permission not granted");
            stopSelf();
            return;
        }
        removePopup();
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        popup = LayoutInflater.from(this).inflate(R.layout.popup_airpods, null);
        ((TextView) popup.findViewById(R.id.deviceTitle)).setText(name);
        ImageView left = popup.findViewById(R.id.leftBud);
        ImageView right = popup.findViewById(R.id.rightBud);
        TextView status = popup.findViewById(R.id.statusText);
        if ("LEFT".equals(event)) {
            status.setText("Left AirPod detected");
            animateBud(left);
        } else if ("RIGHT".equals(event)) {
            status.setText("Right AirPod detected");
            animateBud(right);
        } else {
            status.setText("Connected");
            animateBud(left);
            animateBud(right);
        }

        TextView batteryText = popup.findViewById(R.id.batteryText);
        int battery = getSharedPreferences("airpods", MODE_PRIVATE).getInt("battery", -1);
        if (batteryText != null) {
            if (battery >= 0 && battery <= 100) {
                batteryText.setText("Battery  " + battery + "%");
                batteryText.setTextColor(Color.rgb(90, 225, 140));
            } else {
                batteryText.setText("Battery information unavailable");
                batteryText.setTextColor(Color.rgb(184, 193, 208));
            }
        } else {
            Log.e("AirPodsBattery", "batteryText ID not found in popup_airpods.xml");
        }
        Log.d("AirPodsBattery", "Popup battery: " + battery);

        popup.findViewById(R.id.closePopup).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { removePopup(); stopSelf(); }
        });
        popup.findViewById(R.id.donePopup).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { removePopup(); stopSelf(); }
        });
        int width = (int) (330 * getResources().getDisplayMetrics().density + .5f);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        width = Math.min(width, screenWidth - (int) (24 * getResources().getDisplayMetrics().density));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        try {
            manager.addView(popup, params);
        } catch (Exception ex) {
            Log.e("AirPodsPopup", "Cannot display popup", ex);
            popup = null;
            stopSelf();
            return;
        }
        popup.setAlpha(0f);
        popup.setScaleX(.88f);
        popup.setScaleY(.88f);
        popup.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(360).start();
        handler.postDelayed(dismiss, 6500);
    }

    private void animateBud(ImageView bud) {
        bud.setTranslationY(65f * getResources().getDisplayMetrics().density);
        bud.setAlpha(.3f);
        bud.animate().translationY(0f).alpha(1f).setDuration(850).start();
    }

//    private void removePopup(){
//        handler.removeCallbacks(dismissRunnable);
//        if (popupView!=null && windowManager!=null){
//            windowManager.removeView(popupView);
//            popupView=null;
//        }
//    }

    private void removePopup() {
        handler.removeCallbacks(dismiss);
        if (popup != null) {
            popup.animate().cancel();
            if (manager != null) {
                try { manager.removeView(popup); }
                catch (Exception ex) { Log.w("AirPodsPopup", "Remove view failed", ex); }
            }
            popup = null;
        }
    }

    @Override
    public void onDestroy() {
        removePopup();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
