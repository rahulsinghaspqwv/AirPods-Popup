package com.example.airpodspopup;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import android.os.Handler;

public class AirPodsPopupService extends Service {
    private WindowManager windowManager;
    private LinearLayout popupView;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable dismissRunnable = new Runnable() {
        @Override
        public void run() {
            removePopup();
            stopSelf();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String name="AirPods";
        if (intent!=null){
            name=intent.getStringExtra("device_name");
            if (name==null)name="AirPods";
        }
        showPopup(name);
        return START_STICKY;
    }

    @SuppressLint("SetTextI18n")
    private void showPopup(String name){
        removePopup();
        windowManager=(WindowManager) getSystemService(WINDOW_SERVICE);
        popupView=new LinearLayout(this);
        popupView.setOrientation(LinearLayout.VERTICAL);
        popupView.setGravity(Gravity.CENTER);
        popupView.setPadding(45, 45, 45, 45);

        GradientDrawable background=new GradientDrawable();
        background.setColor(Color.rgb(30, 30, 35));
        background.setCornerRadius(50);
        background.setStroke(2, Color.rgb(85, 85, 95));

        popupView.setBackground(background);

        TextView title=new TextView(this);
        title.setText(name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView status = new TextView(this);
        status.setText("Connected");
        status.setTextColor(Color.rgb(100, 230, 140));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 18, 0, 0);

        popupView.addView(title);
        popupView.addView(status);

        WindowManager.LayoutParams params=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);

        params.gravity=Gravity.BOTTOM;
        params.x=0;
        params.y=100;

        windowManager.addView(popupView, params);

        handler.removeCallbacks(dismissRunnable);
        handler.postDelayed(dismissRunnable, 5000);
    }

    private void removePopup(){
        handler.removeCallbacks(dismissRunnable);
        if (popupView!=null && windowManager!=null){
            windowManager.removeView(popupView);
            popupView=null;
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
