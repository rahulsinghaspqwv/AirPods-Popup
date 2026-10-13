package com.example.airpodspopup;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Locale;

public class AirPodsService extends Service {

    private static final String CHANNEL_ID="airpods_service";
    private static final int NOTIFICATION_ID = 101;
    private static final String TAG="AirPodsService";
    private boolean receiverRegistered = false;
    private final BroadcastReceiver bluetoothReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("AirPodsService", "Bluetooth event: " + intent.getAction());
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                handleDeviceConnected(intent);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                Log.d(TAG, "Bluetooth device disconnected");
            }
        }
    };
    private static final String BATTERY_ACTION =
            "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    private static final String BATTERY_EXTRA =
            "android.bluetooth.device.extra.BATTERY_LEVEL";

    private boolean batteryReceiverRegistered = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
             if (intent == null) return;
             if (!BATTERY_ACTION.equals(intent.getAction())) {
                 return;
             }
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                 if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!= PackageManager.PERMISSION_GRANTED) {
                        Log.e("AirPodsBattery", "BLUETOOTH_CONNECT permission missing");
                            return;
                 }
             }
             BluetoothDevice device;
             if (Build.VERSION.SDK_INT >= 33) {
                 device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
             } else {
                 device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
             }
             if (device == null) {
                 Log.d("AirPodsBattery", "Device is null");
                 return;
             }
             String deviceName = device.getName();
             if (deviceName == null) {
                 Log.d("AirPodsBattery", "Device name unavailable");
                 return;
             }
             Log.d("AirPodsBattery", "Battery event from: " + deviceName);
             if (!deviceName.equalsIgnoreCase("Rahul’s AirPods Pro")) {
                 return;
             }
             int batteryLevel = intent.getIntExtra(BATTERY_EXTRA, -1);
             if (batteryLevel < 0 || batteryLevel > 100) {
                 Log.d("AirPodsBattery", "Valid battery level not available");
                 return;
             }
             getSharedPreferences("airpods", MODE_PRIVATE).edit().putInt("battery", batteryLevel).putString("battery_device", deviceName).putLong("battery_updated_at", System.currentTimeMillis()).apply();
             Log.d("AirPodsBattery", "Rahul's AirPods battery: " + batteryLevel + "%");
        }
    };

    private void registerBatteryReceiver() {
        if (batteryReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(BATTERY_ACTION);
        registerReceiver(batteryReceiver, filter);
        batteryReceiverRegistered = true;
        Log.d("AirPodsBattery", "Battery receiver registered");
    }

    private void unregisterBatteryReceiver() {
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver);
            batteryReceiverRegistered = false;
            Log.d("AirPodsBattery", "Battery receiver unregistered");
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerBluetoothReceiver();
        registerBatteryReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("AirPodsService", "SERVICE STARTED SUCCESSFULLY");
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }

    private void registerBluetoothReceiver(){
        IntentFilter filter=new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);
        receiverRegistered=true;
    }

    private void handleDeviceConnected(Intent intent){
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            return;
        }
        BluetoothDevice device;
        if (Build.VERSION.SDK_INT>=33){
            device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
        if (device==null){
            return;
        }
        String deviceName=device.getName();

        Log.d("AirPodsService", "Detected device: " + deviceName);

        if (deviceName==null)return;
        String lowerName=deviceName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("airpods")){
            Log.d(TAG, "AirPods Connected: "+deviceName);
            showAirPodsPopup(deviceName);
            Intent popupIntent=new Intent("com.example.airpodspopup.DEVICE_CONNECTED");
            popupIntent.setPackage(getPackageName());
            popupIntent.putExtra("device_name", deviceName);
            sendBroadcast(popupIntent);
        }
    }

    private void createNotificationChannel(){
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID, "Airpods Background Detection", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification createNotification(){
        return new Notification.Builder(this, CHANNEL_ID).setContentTitle("AirPods Pro").setContentText("Monitoring Bluetooth connections").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOngoing(true).build();
    }

    private void showAirPodsPopup(String deviceName){
        if (!android.provider.Settings.canDrawOverlays(this)){
            Log.e(TAG, "Overlay permission not granted");
            return;
        }
        Intent intent=new Intent(this, AirPodsPopupService.class);
        intent.putExtra("device_name", deviceName);
        startService(intent);
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered){
            unregisterReceiver(bluetoothReceiver);
            receiverRegistered=false;
        }
        unregisterBatteryReceiver();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
