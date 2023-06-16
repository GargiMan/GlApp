package com.glapp;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.glapp.databinding.ActivityFullscreenBinding;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = false;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;

    private final Handler mHideHandler = new Handler(Looper.myLooper());

    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar , API 30+
            mContentView.getWindowInsetsController().hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        }
    };

    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                //actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = this::hide;
    private ActivityFullscreenBinding binding;


    public final Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case BluetoothService.BluetoothConstants.MESSAGE_RECEIVED:
                    String str = new String((byte[]) msg.obj);
                    //Toast.makeText(MainActivity.this, str, Toast.LENGTH_SHORT).show();
                    if (str.charAt(0) != BluetoothService.MessageStructure.NODE_MASTER) {
                        Log.e(TAG, "Received message from unknown node: " + str);
                        break;
                    }
                    binding.fullscreenInfo.setText(str.substring(2, 2 + str.charAt(1)) + " V");
                    break;
                case BluetoothService.BluetoothConstants.MESSAGE_SENT:
                    break;
                case BluetoothService.BluetoothConstants.MESSAGE_TOAST:
                    break;
                case BluetoothService.BluetoothConstants.ENABLED:
                case BluetoothService.BluetoothConstants.DISCONNECTED:
                    updateDisplayText(getResources().getString(R.string.welcome_message));
                    binding.connectButton.setText("Connect");
                    binding.connectButton.setEnabled(true);
                    binding.connectButton.setVisibility(View.VISIBLE);
                    binding.connectButtonLoading.setVisibility(View.GONE);
                    binding.fullscreenContent.setOnTouchListener(null);
                    binding.fullscreenInfo.setText("");
                    break;
                case BluetoothService.BluetoothConstants.DISABLED:
                    updateDisplayText(getResources().getString(R.string.welcome_message));
                    binding.connectButton.setText("Enable Bluetooth");
                    binding.connectButton.setEnabled(true);
                    binding.connectButton.setVisibility(View.VISIBLE);
                    binding.connectButtonLoading.setVisibility(View.GONE);
                    break;
                case BluetoothService.BluetoothConstants.CONNECTED:
                    updateDisplayText(getResources().getString(R.string.ready));
                    binding.connectButton.setText("Disconnect");
                    binding.connectButton.setEnabled(true);
                    binding.connectButton.setVisibility(View.VISIBLE);
                    binding.connectButtonLoading.setVisibility(View.GONE);
                    binding.fullscreenContent.setOnTouchListener(driveController);
                    break;
                default:
                    Log.e(TAG, "Unknown message type received"+msg.what+" "+msg.obj+" "+msg.arg1+" "+msg.arg2);
                    break;
            }
        }
    };

    private static final String BT_DEVICE_NAME = "GlBoard";
    private BluetoothService mBluetoothService;

    /**
     * Bluetooth service connection
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            mBluetoothService = binder.getService();
            mBluetoothService.init(MainActivity.this, mHandler);
            //Toast.makeText(MainActivity.this, "Service connected", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //Toast.makeText(MainActivity.this, "Service disconnected", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Service disconnected");
        }
    };

    private static final int CLICK_TIME_INTERVAL = 500; // # milliseconds, desired time passed between two clicks
    private long clickedFirst; // last time clicked

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mVisible = true;
        mControlsView = binding.fullscreenContentControls;
        mContentView = binding.fullscreenContent;

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(view -> {
            if (clickedFirst + CLICK_TIME_INTERVAL > System.currentTimeMillis()) {
                toggle();
                return;
            }
            clickedFirst = System.currentTimeMillis();
        });

        //Bluetooth service setup
        Intent bluetoothServiceIntent = new Intent(MainActivity.this, BluetoothService.class);
        startService(bluetoothServiceIntent);
        bindService(bluetoothServiceIntent, serviceConnection, BIND_AUTO_CREATE);

        //Controllers setup
        binding.connectButton.setOnTouchListener(connectController);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        //delayedHide(100);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar , API 30+
        mContentView.getWindowInsetsController()
                .show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public static final long EXIT_TIME_THRESHOLD = 2000; //time delta between two back presses
    private long firstTimeBackPressed = 0;              //time of first back press

    /**
     * Double back press to exit
     */
    @Override
    public void onBackPressed() {
        if (firstTimeBackPressed + EXIT_TIME_THRESHOLD > System.currentTimeMillis()) {
            if (mBluetoothService != null) {
                mBluetoothService.disconnect();
            }
            super.onBackPressed();
            return;
        } else {
            Toast.makeText(this, getResources().getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show();
        }

        firstTimeBackPressed = System.currentTimeMillis();
    }

    private void updateDisplayText(String text) {
        binding.fullscreenContent.setText(text);
    }

    //////////////////////////////// CONTROLS ////////////////////////////////

    private static final double MAX_CONTROL_RANGE = 0.4; // 40% height of screen is control size

    private static final double START_DEAD_ZONE = 0.1; // 10% of power is start dead zone (power 10 and lower is 0)

    private final View.OnTouchListener driveController = new View.OnTouchListener() {

        int power;
        private boolean started = false;
        private float touchPosStart, touchPosNow;
        private final static int SEND_INTERVAL = 100; //ms
        private final Handler sendingHandler = new Handler(Looper.myLooper());
        private final Runnable sendingLoop = new Runnable() {
            @Override
            public void run() {
                mBluetoothService.send(new byte[]{(byte)power});

                sendingHandler.postDelayed(this, SEND_INTERVAL); // Run the task again after 100 milliseconds
            }
        };

        private double getScreenHeight() {
            return getWindowManager().getCurrentWindowMetrics().getBounds().height();
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {

            if (!mBluetoothService.isConnected()) {
                view.performClick();
                Toast.makeText(MainActivity.this, "Not connected", Toast.LENGTH_SHORT).show();
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    //set start touch position
                    touchPosStart = event.getY();
                    Log.d("","start -> y: " + touchPosStart);
                    return false;
                case MotionEvent.ACTION_UP:
                    if (!started) return false;

                    //reset power and dead zone
                    power = 0;
                    started = false;
                    Log.d("","start -> " + touchPosStart + " now -> " + touchPosNow + " power -> " + power);

                    //send power and reset display text
                    updateDisplayText(getResources().getString(R.string.ready));
                    mBluetoothService.send(new byte[]{(byte)power});

                    //stop sending loop
                    sendingHandler.removeCallbacks(sendingLoop);
                    break;
                case MotionEvent.ACTION_MOVE:
                    //set current touch position
                    touchPosNow = event.getY();

                    //calculate power
                    power = (int)((touchPosStart - touchPosNow) / (getScreenHeight() * MAX_CONTROL_RANGE / 2f) * 100f);

                    //limit power and move zone
                    if (power > 100) {
                        power = 100;
                        touchPosStart = (float) (touchPosNow + (getScreenHeight() * MAX_CONTROL_RANGE / 2f));
                    } else if (power < -100) {
                        power = -100;
                        touchPosStart = (float) (touchPosNow - (getScreenHeight() * MAX_CONTROL_RANGE / 2f));
                    }
                    Log.d("","start -> " + touchPosStart + " now -> " + touchPosNow + " power -> " + power);

                    //dead zone
                    if (power < START_DEAD_ZONE * 100 && power > -START_DEAD_ZONE * 100) {
                        if (!started) break;
                    } else {
                        started = true;
                    }

                    //INFO temp
                    //round power (reduce power wobble)
                    power = (int)(Math.round(power / 10.0) * 10);

                    //send power and update display
                    updateDisplayText(String.valueOf(power));
                    mBluetoothService.send(new byte[]{(byte)power});

                    //start/reset sending loop
                    sendingHandler.removeCallbacks(sendingLoop);
                    sendingHandler.postDelayed(sendingLoop, SEND_INTERVAL);
                    break;
                default:
                    break;
            }
            return true;
        }
    };

    private final View.OnTouchListener connectController = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    binding.connectButton.setEnabled(false);

                    new Handler(getMainLooper()).postDelayed(() -> {
                        if (binding.connectButton.isEnabled() || mBluetoothService.isConnected()) return;
                        binding.connectButton.setVisibility(View.GONE);
                        binding.connectButtonLoading.setVisibility(View.VISIBLE);
                    }, 200);

                    if (mBluetoothService.isEnabled()) {
                        if (mBluetoothService.isConnected()) {
                            mBluetoothService.disconnect();
                        } else {
                            mBluetoothService.connect(BT_DEVICE_NAME);
                        }
                    } else {
                        mBluetoothService.enable();
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    };
}