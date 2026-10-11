package com.example.airpodspopup;

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

import java.util.Locale;

public class AirPodsService extends Service {
    private static final String CHANNEL_ID="airpods_service";
    private static final int NOTIFICATION_ID=101;
    private static final String TAG="AirPodsService";

    private boolean receiverRegistered = false;
    private final BroadcastReceiver bluetoothReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("AirPodsService", "Bluetooth Event: "+action);
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                handleDeviceConnected(intent);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                Log.d(TAG, "Bluetooth device disconnected");
            }
        }
    };

    @Override
    public void onCreate(){
        super.onCreate();
        createNotificationChannel();
        registerBluetoothReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d("AirPodsService", "SERVICE STARTED SUCCESSFULLY");
        startForeground(NOTIFICATION_ID, createNotification());
        Log.d("AirpodsService", "FOREGROUND SERVICE ACTIVE");
        return START_STICKY;
    }

    private void registerBluetoothReceiver(){
        IntentFilter filter=new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(filter);
        receiverRegistered=true;
    }

    private void registerReceiver(IntentFilter filter) {
    }

    private void handleDeviceConnected(Intent intent){
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!= PackageManager.PERMISSION_GRANTED){
            return;
        }
        BluetoothDevice device;
        if (Build.VERSION.SDK_INT>=33){
            device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
        if (device==null) return;
        String deviceName=device.getName();
        Log.d("AirPodsService", "Connected Device: " + deviceName);
        if (deviceName==null) return;
        String lowerName=deviceName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("air pods")){
            Log.d(TAG, "AirPods Connected: "+deviceName);
            Intent popupIntent=new Intent("com.airpods.popup.DEVICE_CONNECTED");
            popupIntent.setPackage(getPackageName());
            popupIntent.putExtra("device_name", deviceName);
            sendBroadcast(popupIntent);
        }
    }

    private void createNotificationChannel(){
        NotificationChannel channel= null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(CHANNEL_ID, "AirPods Background Detection", NotificationManager.IMPORTANCE_LOW);
        }
        NotificationManager manager=getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID).setContentTitle("AirPods Popup").setContentText("Monitoring Bluetooth connections").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOngoing(true).build();
        }
        return null;
    }

    @Override
    public void onDestroy(){
        if (receiverRegistered){
            unregisterReceiver(bluetoothReceiver);
            receiverRegistered=false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent){
        return null;
    }
}
