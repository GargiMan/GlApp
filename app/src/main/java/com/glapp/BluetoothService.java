package com.glapp;

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
import android.content.SharedPreferences;
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
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
        //intent.getParcelableExtra("com.glapp.Messenger");
        return binder;
    }

    private BluetoothManager mBluetoothManager = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket mBluetoothSocket = null;
    private BluetoothGattCallback mGattCallback = null;
    private BluetoothGattCharacteristic mCharacteristic = null;
    private BluetoothGattService mBluetoothGattService = null;

    private Context mContext;
    private ConnectThread mConnectThread;
    private CommunicationThread mCommunicationThread;
    private State mState, mNewState;
    private Handler mHandler;

    private interface MessageStructure {
        char NODE_MASTER = 0x01;
        char NODE_SLAVE = 0x02;
    }

    public static class Data {
        float tempMosfet;
        float tempMotor;
        float avgMotorCurrent;
        float avgInputCurrent;
        float dutyCycleNow;
        int rpm;
        float inpVoltage;
        float ampHours;
        float ampHoursCharged;
        float wattHours;
        float wattHoursCharged;
        int tachometer;
        int tachometerAbs;
    }

    public interface MSG_WHAT {
        int STATUS = 0;
        int MESSAGE_RECEIVED = 1;
        int MESSAGE_SENT = 2;
        int MESSAGE_TOAST = 3;
    }

    public enum State {
        NONE,
        NOT_SUPPORTED,
        ENABLED,
        ENABLING,
        DISABLED,
        DISABLING,
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
        DISCONNECTING,
        ERROR;
    }

    @Override
    public void onDestroy() {
        disconnect();
        unregisterBluetoothStateReceiver();
        super.onDestroy();
    }

    /**
     * Initialize BluetoothService with context (Activity),
     * Needs to be called before any other method
     * @param context Activity context
     * @param handler Handler to send messages to
     */
    public void init(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();

            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }

            if (mCommunicationThread != null) {
                mCommunicationThread.cancel();
                mCommunicationThread = null;
            }
        } else {
            Log.e(TAG, "Bluetooth not supported");
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            mHandler.obtainMessage(MSG_WHAT.STATUS, State.NOT_SUPPORTED).sendToTarget();
            return;
        }

        requestPermissions();
        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_STATE_CHANGED, true);

        boolean connect_on_start = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("bluetooth_connect_on_start", false);

        if (connect_on_start) {
            connect(true);
        } else {
            if (isEnabled()) {
                mHandler.obtainMessage(MSG_WHAT.STATUS, State.ENABLED).sendToTarget();
            } else {
                mHandler.obtainMessage(MSG_WHAT.STATUS, State.DISABLED).sendToTarget();
            }
        }

    }

    /**
     * Request all Bluetooth permissions
     */
    private void requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "Bluetooth permission is required", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        ActivityCompat.requestPermissions((Activity) mContext, new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, hashCode());
    }

    public boolean isInitialized() {
        return mBluetoothAdapter != null;
    }

    public boolean isEnabled() {
        if (!isInitialized()) throw new IllegalStateException("BluetoothService is not initialized");
        return mBluetoothAdapter.isEnabled();
    }

    public boolean isConnected() {
        return mCommunicationThread != null && mCommunicationThread.isAlive();
    }

    public void enable() {
        if (isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        mHandler.obtainMessage(MSG_WHAT.STATUS, State.ENABLING).sendToTarget();
        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_STATE_CHANGED, true);
        mBluetoothAdapter.enable();
    }

    public void disable() {
        if (!isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_STATE_CHANGED, true);
        mBluetoothAdapter.disable();
    }

    private void startDiscovery() {
        if (!isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_DISCOVERY_STARTED, true);
        registerBluetoothStateReceiver(BluetoothAdapter.ACTION_DISCOVERY_FINISHED, false);
        registerBluetoothStateReceiver(BluetoothDevice.ACTION_FOUND, false);
        mBluetoothAdapter.startDiscovery();
    }

    private BluetoothDevice getDeviceFromPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String device = preferences.getString("bluetooth_board_select", getResources().getString(R.string.board_name));
        if (device == null) return null;

        return getPairedDevice(device.split("\n")[0], device.split("\n")[1]);
    }

    private BluetoothDevice getPairedDevice(String deviceName, String macAddress) {
        if (!isEnabled()) return null;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return null;
        }

        return mBluetoothAdapter.getBondedDevices().stream().filter(device -> macAddress == null ? device.getName().equals(deviceName) : device.getAddress().equals(macAddress)).findFirst().orElse(null);
    }

    public Set<BluetoothDevice> getPairedDevices() {
        if (!isEnabled()) return null;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return null;
        }

        return mBluetoothAdapter.getBondedDevices();
    }

    /**
     * Connect to the device specified in the preferences
     * @param enable Continue if bluetooth is disabled (will enable it)
     */
    public void connect(boolean enable) {
        if (!isEnabled()) {
            if (enable) {
                enable();

                registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON) {
                            unregisterReceiver(this);
                            connect(true);
                        }
                    }
                }, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            }
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            mHandler.obtainMessage(MSG_WHAT.STATUS, State.DISCONNECTED).sendToTarget();
            return;
        }

        BluetoothDevice device = getDeviceFromPreferences();
        if (device == null) {
            mHandler.obtainMessage(MSG_WHAT.STATUS, State.DISCONNECTED).sendToTarget();
            return;
        }

        if (isConnected() && mConnectThread.getDevice().equals(device)) {
            mHandler.obtainMessage(MSG_WHAT.STATUS, State.CONNECTED).sendToTarget();
            return;
        }

        mHandler.obtainMessage(MSG_WHAT.STATUS, State.CONNECTING).sendToTarget();

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    /**
     * Stop all service threads
     */
    public void disconnect() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mCommunicationThread != null) {
            mCommunicationThread.cancel();
            mCommunicationThread = null;
        }
        if (mHandler != null) {
            mHandler.obtainMessage(MSG_WHAT.STATUS, State.DISCONNECTED).sendToTarget();
        }
    }

    public void send(byte[] message) {
        if (!isEnabled()) return;

        if (mCommunicationThread == null) {
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

        mCommunicationThread.write(bytes);
    }

    IntentFilter filter = null;

    public void registerBluetoothStateReceiver(String action, boolean clearFilter) {

        if (clearFilter || filter == null) {
            filter = new IntentFilter();
        }
        filter.addAction(action);
        registerReceiver(mBluetoothStateReceiver, filter);
    }

    public void unregisterBluetoothStateReceiver() {
        if (filter != null) {
            unregisterReceiver(mBluetoothStateReceiver);
            filter = null;
        }
    }

    private final BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                        switch (state) {
                            case BluetoothAdapter.STATE_ON:
                                Log.i(TAG, "Bluetooth enabled");
                                //Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                                mHandler.obtainMessage(MSG_WHAT.STATUS, State.ENABLED).sendToTarget();

                                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissions();
                                    return;
                                }

                                if (mBluetoothAdapter.isDiscovering()) {
                                    mBluetoothAdapter.cancelDiscovery();
                                }
                                break;
                            case BluetoothAdapter.STATE_OFF:
                                Log.i(TAG, "Bluetooth disabled");
                                //Toast.makeText(this, "Bluetooth disabled", Toast.LENGTH_SHORT).show();
                                mHandler.obtainMessage(MSG_WHAT.STATUS, State.DISABLED).sendToTarget();
                                break;
                        }
                        break;
                }
            }
        }
    };

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
            if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
            }

            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(UUID_SERVICE);
                //tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_SERVICE);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create socket", e);
            }

            mmSocket = tmp;
        }

        /**
         * Connect to remote device
         */
        public void run() {
            if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
                mHandler.obtainMessage(MSG_WHAT.STATUS, BluetoothService.State.DISCONNECTED).sendToTarget();
                return;
            }
            mBluetoothAdapter.cancelDiscovery();

            if (mmSocket == null) {
                Log.e(TAG, "Socket is null");
                mHandler.obtainMessage(MSG_WHAT.STATUS, BluetoothService.State.DISCONNECTED).sendToTarget();
                return;
            }

            try {
                mmSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect", e);
                cancel();
                mHandler.obtainMessage(MSG_WHAT.STATUS, BluetoothService.State.DISCONNECTED).sendToTarget();
                return;
            }

            mCommunicationThread = new CommunicationThread(mmSocket);
            mCommunicationThread.start();

            mHandler.obtainMessage(MSG_WHAT.STATUS, BluetoothService.State.CONNECTED).sendToTarget();
        }

        /**
         * Close client socket
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close socket", e);
            }
        }

        public BluetoothDevice getDevice() {
            return mmDevice;
        }
    }

    // INFO website to example
    //  https://developer.android.com/guide/topics/connectivity/bluetooth/transfer-data#example
    private class CommunicationThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        /**
         * Get input and output stream
         * @param socket socket to remote device
         */
        public CommunicationThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input/output stream", e);
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
                    byte[] mmBuffer = new byte[54];
                    int numBytes = mmInStream.read(mmBuffer);

                    if (numBytes < 2) {
                        Log.e(TAG, "Message too short");
                        continue;
                    }

                    if (mmBuffer[0] != BluetoothService.MessageStructure.NODE_MASTER) {
                        Log.e(TAG, "Unknown message received (wrong node type)");
                        //Toast.makeText(MainActivity.this, str, Toast.LENGTH_SHORT).show();
                        continue;
                    }

                    // Payload length must be 54 bytes (2 bytes for header, 52 bytes for data)
                    if (mmBuffer[1] != numBytes - 2 || numBytes != 54) {
                        Log.e(TAG, "Message payload length mismatch");
                        continue;
                    }

                    int startIndex = 2, dataIndex = 0;

                    ByteBuffer buffer = ByteBuffer.wrap(mmBuffer).order(ByteOrder.LITTLE_ENDIAN);
                    Data data = new Data();
                    data.tempMosfet = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.tempMotor = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.avgMotorCurrent = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.avgInputCurrent = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.dutyCycleNow = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.rpm = buffer.getInt(startIndex + dataIndex++ * 4);
                    data.inpVoltage = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.ampHours = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.ampHoursCharged = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.wattHours = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.wattHoursCharged = buffer.getFloat(startIndex + dataIndex++ * 4);
                    data.tachometer = buffer.getInt(startIndex + dataIndex++ * 4);
                    data.tachometerAbs = buffer.getInt(startIndex + dataIndex++ * 4);

                    // Send the obtained bytes to the UI activity.
                    mHandler.obtainMessage(MSG_WHAT.MESSAGE_RECEIVED, data).sendToTarget();
                } catch (IOException e) {
                    mHandler.obtainMessage(MSG_WHAT.STATUS, BluetoothService.State.DISCONNECTED).sendToTarget();
                    Log.d(TAG, "Input stream was disconnected", e);
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
                mHandler.obtainMessage(MSG_WHAT.MESSAGE_SENT, bytes).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        mHandler.obtainMessage(MSG_WHAT.MESSAGE_TOAST);
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
                Log.e(TAG, "Failed to close socket", e);
            }
        }
    }
}
