package com.example.airpodspopup;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST = 100;
    private static final int APPLE_ID = 0x004C;
    private Button btnScan, btnStop;
    private TextView txtStatus, txtResults, txtDeviceCount;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;

    private boolean scanning = false;
    private final Set<String> detectDevices = new HashSet<>();
    private final StringBuilder packetLog = new StringBuilder();
    private long lastUiUpdate=0;

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord record = result.getScanRecord();
            if(record==null) return;
            byte[] appleData = record.getManufacturerSpecificData(APPLE_ID);
            if(appleData==null) return;
            String address = result.getDevice().getAddress();
            int rssi = result.getRssi();
            String hex = bytesToHex(appleData);
            runOnUiThread(()->{
                detectDevices.add(address);
                txtDeviceCount.setText(detectDevices.size()+"found");
                packetLog.insert(0, "APPLE BLE DETECTED\n"+"MAC: "+address+"\n"+"RSSI: "+rssi+" dBm\n"+"Manufacturer: 0x004C\n"+"DATA: "+hex+"\n"+"------------------------\n\n");
            });
            if (packetLog.length()>12000){
                packetLog.setLength(12000);
            }
            txtResults.setText(packetLog.toString());
            long currentTime = android.os.SystemClock.elapsedRealtime();
            if (currentTime-lastUiUpdate>=500){
                lastUiUpdate=currentTime;
                txtResults.setText(packetLog.toString());
            }
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(()->{
                scanning=false;
                updateButtons();
                txtStatus.setText("Scan error: "+errorCode);
            });
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        btnScan=findViewById(R.id.btnScan);
        btnStop=findViewById(R.id.btnStop);
        txtStatus=findViewById(R.id.txtStatus);
        txtResults=findViewById(R.id.txtResults);
        txtDeviceCount=findViewById(R.id.txtDeviceCount);

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter=manager.getAdapter();
        btnScan.setOnClickListener(v-> checkPermissionsAndScan());
        btnStop.setOnClickListener(v-> stopScanning());

        // Service Calling
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},200);
        }
        if (Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},201);
        } else {
            Intent serviceIntent = new Intent(this, AirPodsService.class);
//            android.util.Log.d("AirPodsService", "STARTING SERVICE FROM ACTIVITY");
            startForegroundService(serviceIntent);
        }
    }

    private void checkPermissionsAndScan(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST);
                return;
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST);
            return;
        }
        startScanning();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode==PERMISSION_REQUEST){
            checkPermissionsAndScan();
        }
    }

    @SuppressLint("SetTextI18n")
    private void startScanning(){
        if (bluetoothAdapter==null){
            txtStatus.setText("Bluetooth unavailable");
            return;
        }
        if (!bluetoothAdapter.isEnabled()){
            txtStatus.setText("Enable Bluetooth");
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        if (scanning) return;
        bleScanner=bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner==null){
            txtStatus.setText("BLE scanner unavailable");
            return;
        }
        detectDevices.clear();
        packetLog.setLength(0);
        txtDeviceCount.setText("0 found");
        txtResults.setText("Scanning for Apple BLE packets...");

        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        try {
            bleScanner.startScan(null, settings, scanCallback);
            scanning = true;
            txtStatus.setText(" Scanning");
            updateButtons();
        } catch (SecurityException e){
            txtStatus.setText("Bluetooth permission missing");
        }
    }

    @SuppressLint("SetTextI18n")
    private void stopScanning(){
        if (!scanning) return;
        try {
            if (bleScanner!=null){
                bleScanner.stopScan(scanCallback);
            }
        } catch (SecurityException ignored){
        }
        scanning = false;
        txtStatus.setText(" Stopped");
        updateButtons();
    }

    private void updateButtons(){
        btnScan.setEnabled(!scanning);
        btnStop.setEnabled(scanning);
    }

    private String bytesToHex(byte[] bytes){
        StringBuilder builder=new StringBuilder();
        for (byte b:bytes){
            builder.append(String.format(Locale.US, "%02X", b & 0xFF));
        }
        return builder.toString().trim();
    }

    protected void onDestroy(){
        stopScanning();
        super.onDestroy();
    }
}