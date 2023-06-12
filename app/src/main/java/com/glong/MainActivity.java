package com.glong;

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

import com.glong.databinding.ActivityFullscreenBinding;

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
                actionBar.show();
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
                    Toast.makeText(MainActivity.this, str, Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothService.BluetoothConstants.MESSAGE_SENT:
                    break;
                case BluetoothService.BluetoothConstants.MESSAGE_TOAST:
                    break;
                case BluetoothService.BluetoothConstants.ENABLED:
                    binding.sendButton.setEnabled(false);
                    binding.connectButton.setText("Connect");
                    binding.connectButton.setEnabled(true);
                    binding.connectButton.setVisibility(View.VISIBLE);
                    binding.connectButtonLoading.setVisibility(View.GONE);
                    break;
                case BluetoothService.BluetoothConstants.DISABLED:
                    binding.sendButton.setEnabled(false);
                    binding.connectButton.setText("Enable Bluetooth");
                    binding.connectButton.setEnabled(true);
                    binding.connectButton.setVisibility(View.VISIBLE);
                    binding.connectButtonLoading.setVisibility(View.GONE);
                    break;
                case BluetoothService.BluetoothConstants.CONNECTED:
                    binding.sendButton.setEnabled(true);
                    binding.connectButton.setText("Disconnect");
                    binding.connectButton.setEnabled(true);
                    binding.connectButton.setVisibility(View.VISIBLE);
                    binding.connectButtonLoading.setVisibility(View.GONE);
                    break;
                case BluetoothService.BluetoothConstants.DISCONNECTED:
                    binding.sendButton.setEnabled(false);
                    binding.connectButton.setText("Connect");
                    binding.connectButton.setEnabled(true);
                    binding.connectButton.setVisibility(View.VISIBLE);
                    binding.connectButtonLoading.setVisibility(View.GONE);
                    break;
                default:
                    super.handleMessage(msg);
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

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.

        //binding.fullscreenContent.setText(getResources().getString(R.string.bt_service_ready));

        /**
         * Touch listener to use for in-layout UI controls to delay hiding the
         * system UI. This is to prevent the jarring behavior of controls going away
         * while interacting with activity UI.
         */
        binding.connectButton.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    binding.connectButton.setEnabled(false);

                    new Handler(getMainLooper()).postDelayed(() -> {
                        if (binding.connectButton.isEnabled()) return;
                        binding.connectButton.setVisibility(View.GONE);
                        binding.connectButtonLoading.setVisibility(View.VISIBLE);
                    }, 200);

                    //INFO custom actions
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
            return false;
        });
        binding.sendButton.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();

                    //INFO custom actions
                    if (mBluetoothService.isConnected()) {
                        mBluetoothService.send("data values");
                    } else {
                        Toast.makeText(MainActivity.this, "Not connected", Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    break;
            }
            return false;
        });
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
     * App exiting method using back button (twice)
     */
    @Override
    public void onBackPressed() {
        if (firstTimeBackPressed + EXIT_TIME_THRESHOLD > System.currentTimeMillis()) {
            super.onBackPressed();
            return;
        } else {
            Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT).show();
        }

        firstTimeBackPressed = System.currentTimeMillis();
    }
}