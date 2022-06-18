package com.glong.bluetooth;

import android.bluetooth.BluetoothDevice;

public interface BluetoothListener {

    void onStartDiscovery();
    void onFinishDiscovery();
    void onEnabledBluetooth();
    void onDisabledBluetooth();
    void getBluetoothDeviceList(BluetoothDevice device);
}
