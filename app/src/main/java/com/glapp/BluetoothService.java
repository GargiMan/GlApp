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

    private BluetoothState mState, mNewState;

    enum BluetoothState {
        STATE_NONE,
        STATE_ENABLED,
        STATE_CONNECTING,
        STATE_CONNECTED,
        STATE_DISCONNECTED,
        STATE_ERROR
    }

    /**
     * Initialize BluetoothService with context (Activity),
     * Needs to be called before any other method
     * @param context Activity context
     */
    public void init(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;

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
        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_STATE_CHANGED, true);

        if (isEnabled()) {
            mHandler.obtainMessage(BluetoothConstants.ENABLED).sendToTarget();
        } else {
            mHandler.obtainMessage(BluetoothConstants.DISABLED).sendToTarget();
        }
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

    public boolean isConnected() {
        return mConnectedThread != null && mConnectedThread.isAlive();
    }

    public void enable() {
        if (isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

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
            mHandler.obtainMessage(BluetoothConstants.DISCONNECTED).sendToTarget();
            return;
        }

        BluetoothDevice device = getPairedDevice(deviceName);
        if (device == null) {
            mHandler.obtainMessage(BluetoothConstants.DISCONNECTED).sendToTarget();
            return;
        }

        if (isConnected() && mConnectThread.getDevice().equals(device)) {
            mHandler.obtainMessage(BluetoothConstants.CONNECTED).sendToTarget();
            return;
        }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    public void send(byte[] message) {
        if (!isEnabled()) return;

        if (mConnectedThread == null) {
            Log.e(TAG, "No connection");
            return;
        }

        if (message.length > 255) {
            Log.e(TAG, "Message too long");
            return;
        }

        //INFO message structure: <node type><message length><message>
        byte[] bytes = new byte[message.length + 2];
        bytes[0] = MessageStructure.NODE_SLAVE;
        bytes[1] = (byte) message.length;
        System.arraycopy(message, 0, bytes, 2, message.length);

        mConnectedThread.write(bytes);
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
                                mHandler.obtainMessage(BluetoothConstants.ENABLED).sendToTarget();
                                break;
                            case BluetoothAdapter.STATE_TURNING_OFF:
                                Log.i(TAG, "Bluetooth turning off");
                                //Toast.makeText(mContext, "Bluetooth turning off", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothAdapter.STATE_OFF:
                                Log.i(TAG, "Bluetooth disabled");
                                //Toast.makeText(mContext, "Bluetooth disabled", Toast.LENGTH_SHORT).show();
                                mHandler.obtainMessage(BluetoothConstants.DISABLED).sendToTarget();
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
        disconnect();
        unregisterBluetoothStateReceiver();
        super.onDestroy();
    }

    //stop bluetooth
    public void disconnect() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mHandler != null) {
            mHandler.obtainMessage(BluetoothConstants.DISCONNECTED).sendToTarget();
        }
    }

    private Handler mHandler;

    public interface MessageStructure {
        char NODE_MASTER = 0x01;
        char NODE_SLAVE = 0x02;
    }

    public interface BluetoothConstants {
        int ERROR = -1;
        int NOT_SUPPORTED = 0;
        int ENABLED = 1;
        int DISABLED = 2;
        int CONNECTED = 3;
        int DISCONNECTED = 4;
        int MESSAGE_RECEIVED = 5;
        int MESSAGE_SENT = 6;
        int MESSAGE_TOAST = 7;
        int MESSAGE_ERROR = 8;
        int DISCOVERABLE_REQUEST = 9;
        int PERMISSION_REQUEST = 10;
    }

    // INFO website to example
    //  https://developer.android.com/guide/topics/connectivity/bluetooth/connect-bluetooth-devices#connect-client
    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        /**
         * Create socket to remote device
         * @param device remote device
         */
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
                mHandler.obtainMessage(BluetoothConstants.DISCONNECTED).sendToTarget();
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
                mHandler.obtainMessage(BluetoothConstants.DISCONNECTED).sendToTarget();
                return;
            }

            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();

            mHandler.obtainMessage(BluetoothConstants.CONNECTED).sendToTarget();
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

        public BluetoothDevice getDevice() {
            return mmDevice;
        }
    }

    // INFO website to example
    //  https://developer.android.com/guide/topics/connectivity/bluetooth/transfer-data#example
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        /**
         * Get input and output stream
         * @param socket socket to remote device
         */
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

        /**
         * Listen to input stream in loop
         */
        public void run() {
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    byte[] mmBuffer = new byte[1024];
                    int numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    mHandler.obtainMessage(BluetoothConstants.MESSAGE_RECEIVED, numBytes, -1, mmBuffer).sendToTarget();
                } catch (IOException e) {
                    mHandler.obtainMessage(BluetoothConstants.DISCONNECTED).sendToTarget();
                    Log.d("", "Input stream was disconnected", e);
                    break;
                }
            }
        }

        /**
         * Write to output stream,
         * Called to send data to the remote device
         * @param bytes bytes to write
         */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                // Share the sent message with the UI activity.
                mHandler.obtainMessage(BluetoothConstants.MESSAGE_SENT, -1, -1, bytes).sendToTarget();
            } catch (IOException e) {
                Log.e("", "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        mHandler.obtainMessage(BluetoothConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);
            }
        }

        /**
         * Close connected socket
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("", "Could not close the connect socket", e);
            }
        }
    }
}
