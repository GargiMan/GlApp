package com.glong;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothService extends Service {

    private static final String TAG = BluetoothService.class.getSimpleName();

    private static final UUID UUID_SERVICE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //SAVED
    //0000110E-0000-1000-8000-00805F9B34FB

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

    private BluetoothManager mBluetoothManager = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket mBluetoothSocket = null;
    private BluetoothGattCallback mGattCallback = null;
    private BluetoothGattCharacteristic mCharacteristic = null;
    private BluetoothGattService mBluetoothGattService = null;
    private Set<BluetoothDevice> mBluetoothDevices = null;

    private Context mContext;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    /**
     * Initialize BluetoothService with context (Activity),
     * Needs to be called before any other method
     * @param context Activity context
     */
    public void start(Context context) {
        mContext = context;
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();

            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }

            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
        } else {
            Log.e(TAG, "Bluetooth not supported");
            Toast.makeText(mContext, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        }

        requestPermissions();
    }

    /**
     * Request all Bluetooth permissions
     */
    private void requestPermissions() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(mContext, "Bluetooth permission is required", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        //INFO probably not needed
        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_REQUEST_ENABLE, true);

        ActivityCompat.requestPermissions((Activity) mContext, new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, hashCode());
    }

    public boolean isAvailable() {
        return mBluetoothAdapter != null;
    }

    public boolean isEnabled() {
        if (!isAvailable()) throw new IllegalStateException("Bluetooth is not available");
        return mBluetoothAdapter.isEnabled();
    }

    public void enable() {
        if (isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        unregisterBluetoothStateReceiver();
        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_STATE_CHANGED, true);
        mBluetoothAdapter.enable();

        if (mBluetoothAdapter.isDiscovering()) mBluetoothAdapter.cancelDiscovery();
    }

    public void disable() {
        if (!isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_STATE_CHANGED, true);
        mBluetoothAdapter.disable();
    }

    private void startDiscovery() {
        if (!isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_DISCOVERY_STARTED, true);
        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_DISCOVERY_FINISHED, false);
        registerBluetoothStateReceiver(BluetoothDevice.ACTION_FOUND, false);
        mBluetoothAdapter.startDiscovery();
    }

    private BluetoothDevice getPairedDevice(String deviceName) {
        if (!isEnabled()) return null;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return null;
        }

        mBluetoothDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : mBluetoothDevices) {
            if (device.getName().equals(deviceName)) {
                Log.i(TAG, "Device " + deviceName + " is paired");
                return device;
            }
        }

        Log.i(TAG, "Device " + deviceName + " is not paired");
        return null;
    }

    public void connect(String deviceName) {
        if (!isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        BluetoothDevice device = getPairedDevice(deviceName);
        if (device == null) return;

        try {
            mBluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_SERVICE);
            mBluetoothSocket.connect();
            mBluetoothSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create socket");
            e.printStackTrace();
        }

        mConnectThread = new ConnectThread(device);
        //TODO needed ?
        //mConnectThread.start();
    }

    private final BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action != null) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (action) {

                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        switch (state) {
                            case BluetoothAdapter.STATE_TURNING_ON:
                                Log.i(TAG, "Bluetooth turning on");
                                //Toast.makeText(mContext, "Bluetooth turning on", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothAdapter.STATE_ON:
                                Log.i(TAG, "Bluetooth enabled");
                                //Toast.makeText(mContext, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothAdapter.STATE_TURNING_OFF:
                                Log.i(TAG, "Bluetooth turning off");
                                //Toast.makeText(mContext, "Bluetooth turning off", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothAdapter.STATE_OFF:
                                Log.i(TAG, "Bluetooth disabled");
                                //Toast.makeText(mContext, "Bluetooth disabled", Toast.LENGTH_SHORT).show();
                                break;
                        }
                        break;

                    //INFO probably not needed
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

    IntentFilter filter = null;

    public void registerBluetoothStateReceiver(String action, boolean clearFilter) {
        if (clearFilter || filter == null) filter = new IntentFilter();
        filter.addAction(action);
        mContext.registerReceiver(mBluetoothStateReceiver, filter);
    }

    public void unregisterBluetoothStateReceiver() {
        if (filter != null) mContext.unregisterReceiver(mBluetoothStateReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBluetoothStateReceiver();
        stop();
    }

    //stop bluetooth
    public void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    // INFO website to example
    //  https://developer.android.com/guide/topics/connectivity/bluetooth/connect-bluetooth-devices#connect-client
    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                //TODO check if this works
                try {
                    Thread permissionThread = new Thread(BluetoothService.this::requestPermissions);
                    permissionThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(UUID_SERVICE);
                //tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_SERVICE);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create socket");
                e.printStackTrace();
            }

            mmSocket = tmp;
        }

        /**
         * Connect to remote device
         */
        public void run() {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
                return;
            }
            mBluetoothAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException connectException) {
                Log.e(TAG, "Failed to connect");
                connectException.printStackTrace();
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Failed to close socket");
                    closeException.printStackTrace();
                }
                return;
            }

            mConnectedThread = new ConnectedThread(mmSocket);
        }

        /**
         * Close client socket
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("", "Could not close the client socket", e);
            }
        }
    }

    private Handler handler;
    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        int MESSAGE_READ = 0;
        int MESSAGE_WRITE = 1;
        int MESSAGE_TOAST = 2;
    }

    // INFO website to example
    //  https://developer.android.com/guide/topics/connectivity/bluetooth/transfer-data#example
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e("", "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e("", "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    Message readMsg = handler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget();
                } catch (IOException e) {
                    Log.d("", "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                Message writtenMsg = handler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e("", "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                handler.sendMessage(writeErrorMsg);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("", "Could not close the connect socket", e);
            }
        }
    }
}
