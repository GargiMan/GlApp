package com.glong;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class BluetoothService extends Service {

    private static final String TAG = BluetoothService.class.getSimpleName();

    private final IBinder binder = new BluetoothBinder();

    public class BluetoothBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final int REQUEST_ENABLE_BT = 1;
    private final int REQUEST_DISABLE_BT = 2;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattCallback mGattCallback;
    private BluetoothGattCharacteristic mCharacteristic;
    private BluetoothGattService mBluetoothGattService;
    private final List<BluetoothDevice> mBluetoothDevices = new ArrayList<>();

    private Context mContext;

    public void setup(Context context) {
        mContext = context;
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
        } else {
            Toast.makeText(mContext, "Service not supported", Toast.LENGTH_SHORT).show();
        }
    }

    public void requestPermission(int requestCode) {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(mContext, "Bluetooth permission is required", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        ActivityCompat.requestPermissions((Activity) mContext, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, requestCode);
    }

    public boolean isAvailable() {
        return mBluetoothAdapter != null;
    }

    public boolean isEnabled() {
        if (!isAvailable()) throw new IllegalStateException("Bluetooth is not available");
        return mBluetoothAdapter.isEnabled();
    }

    @SuppressLint("MissingPermission")
    public void enable() {
        if (isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(REQUEST_ENABLE_BT);
            return;
        }

        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_STATE_CHANGED);
        mBluetoothAdapter.enable();
    }

    public void disable() {
        if (!isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(REQUEST_DISABLE_BT);
            return;
        }

        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_STATE_CHANGED);
        mBluetoothAdapter.disable();
    }

    private String requestedAction;
    private BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action != null && action.equals(requestedAction)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (action) {
                    case BluetoothAdapter.ACTION_STATE_CHANGED:

                        switch (state) {
                            case BluetoothAdapter.STATE_TURNING_ON:
                                //Toast.makeText(mContext, "Bluetooth turning on", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothAdapter.STATE_ON:
                                //Toast.makeText(mContext, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothAdapter.STATE_TURNING_OFF:
                                //Toast.makeText(mContext, "Bluetooth turning off", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothAdapter.STATE_OFF:
                                //Toast.makeText(mContext, "Bluetooth disabled", Toast.LENGTH_SHORT).show();
                                break;
                        }
                        break;

                    case BluetoothAdapter.ACTION_REQUEST_ENABLE:
                        switch (state) {
                            case Activity.RESULT_OK:
                                Toast.makeText(mContext, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                                break;
                            case Activity.RESULT_CANCELED:
                                Toast.makeText(mContext, "Bluetooth enabling canceled", Toast.LENGTH_SHORT).show();
                                break;
                        }
                        break;
                }
            }
        }
    };

    public void registerBluetoothStateReceiver(String action) {
        requestedAction = action;
        IntentFilter filter = new IntentFilter(requestedAction);
        mContext.registerReceiver(mBluetoothStateReceiver, filter);
    }

    public void unregisterBluetoothStateReceiver() {
        mContext.unregisterReceiver(mBluetoothStateReceiver);
    }


    @Override
    protected void finalize() {
        unregisterBluetoothStateReceiver();
    }
}
