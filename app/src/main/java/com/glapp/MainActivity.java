package com.glapp;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;

import com.glapp.data.ControlData;
import com.glapp.data.MetricsData;
import com.glapp.data.Packet;
import com.glapp.data.Packet.*;
import com.glapp.databinding.MainActivityBinding;

import java.util.Set;

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

    private final Handler mHideHandler = new Handler(Looper.getMainLooper());

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
    private MainActivityBinding binding;


    public final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case BluetoothService.MSG_WHAT.MESSAGE_RECEIVED:

                    Packet packet = (Packet) msg.obj;

                    // Send the obtained bytes to the UI activity.
                    switch (packet.getCommand()) {
                        case METRICS:
                            if (packet.getType() != Packet.Type.RESPONSE) {
                                Log.e(TAG, "Invalid metrics packet received (wrong type)");
                                return;
                            }
                            if (packet.getPayloadLength() != 52) {
                                Log.e(TAG, "Invalid metrics packet received (payload length mismatch)");
                                return;
                            }
                            MetricsData metricData = new MetricsData(packet.getPayload());
                            Set<String> dataToShow = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getStringSet("board_data", null);
                            String str = metricData.getConcatenatedData(dataToShow);
                            binding.boardData.setText(str);
                            break;
                        case CONTROL:
                            break;
                        case CONFIG:
                            break;
                        case TEST:
                            break;
                    }
                    break;

                case BluetoothService.MSG_WHAT.MESSAGE_SENT:
                    break;

                case BluetoothService.MSG_WHAT.MESSAGE_TOAST:
                    if (msg.getData() != null) {
                        Toast.makeText(MainActivity.this, msg.getData().getString("toast"), Toast.LENGTH_SHORT).show();
                    }
                    break;

                case BluetoothService.MSG_WHAT.STATUS:
                    switch ((BluetoothService.State) msg.obj) {
                        case ENABLED:
                        case DISCONNECTED:
                        case DISABLED:
                            binding.connectButton.setText(R.string.connect);
                            binding.connectButton.setEnabled(true);
                            binding.driveScreenLoading.setVisibility(View.GONE);
                            binding.driveScreen.setText(R.string.tap_to_connect);
                            binding.driveScreen.setVisibility(View.VISIBLE);
                            binding.driveScreenControlsNeutral.setVisibility(View.GONE);
                            binding.boardData.setVisibility(View.GONE);
                            binding.boardData.setText("");
                            getMenu().findItem(R.id.app_bar_bluetooth).setEnabled(true);
                            getMenu().findItem(R.id.app_bar_bluetooth).setIcon(R.drawable.ic_bluetooth_disconnected);
                            getMenu().findItem(R.id.app_bar_bluetooth).setTooltipText(getString(R.string.connect));
                            getMenu().findItem(R.id.app_bar_direction).setVisible(false);
                            getMenu().findItem(R.id.app_bar_lights).setVisible(false);
                            break;
                        case CONNECTED:
                            binding.connectButton.setText(R.string.disconnect);
                            binding.connectButton.setEnabled(true);
                            binding.driveScreenLoading.setVisibility(View.GONE);
                            binding.driveScreen.setVisibility(View.VISIBLE);
                            binding.driveScreen.setText("");
                            binding.driveScreenControlsNeutral.setText(R.string.drag_to_drive);
                            binding.driveScreenControlsNeutral.setVisibility(View.VISIBLE);
                            binding.driveScreenControlsNeutral.setY(getWindowManager().getCurrentWindowMetrics().getBounds().height() / 2f - binding.driveScreenControlsNeutral.getHeight() * 3f/2f);
                            binding.driveScreenControlsNeutral.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (getWindowManager().getCurrentWindowMetrics().getBounds().height() * MAX_CONTROL_RANGE * START_DEAD_ZONE)));
                            binding.boardData.setVisibility(View.VISIBLE);
                            obtainMessage(BluetoothService.MSG_WHAT.MESSAGE_RECEIVED, new Packet(Type.RESPONSE, Source.NODE_MASTER, Command.METRICS, new MetricsData())).sendToTarget();
                            getMenu().findItem(R.id.app_bar_bluetooth).setEnabled(true);
                            getMenu().findItem(R.id.app_bar_bluetooth).setIcon(R.drawable.ic_bluetooth_connected);
                            getMenu().findItem(R.id.app_bar_bluetooth).setTooltipText(getString(R.string.disconnect));
                            getMenu().findItem(R.id.app_bar_direction).setVisible(true);
                            getMenu().findItem(R.id.app_bar_lights).setVisible(true);
                            break;
                        case ENABLING:
                            binding.connectButton.setEnabled(false);
                            getMenu().findItem(R.id.app_bar_bluetooth).setEnabled(false);
                            new Handler(getMainLooper()).postDelayed(() -> {
                                if (mBluetoothService.isEnabled()) return;
                                binding.driveScreenLoading.setVisibility(View.VISIBLE);
                                binding.driveScreen.setVisibility(View.GONE);
                            }, 200);
                        case CONNECTING:
                            binding.connectButton.setEnabled(false);
                            getMenu().findItem(R.id.app_bar_bluetooth).setEnabled(false);
                            new Handler(getMainLooper()).postDelayed(() -> {
                                if (mBluetoothService.isConnected()) return;
                                binding.driveScreenLoading.setVisibility(View.VISIBLE);
                                binding.driveScreen.setVisibility(View.GONE);
                            }, 200);
                            break;
                        default:
                            Log.e(TAG, "Bluetooth status received: "+((BluetoothService.State) msg.obj).name());
                            break;
                    }
                    break;
            }
        }
    };

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
            Log.i(TAG, "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBluetoothService = null;
            Log.i(TAG, "Service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = MainActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mVisible = true;
        mControlsView = binding.driveScreenControlsButtons;
        mContentView = binding.driveScreen;

        //Bluetooth service setup
        Intent bluetoothServiceIntent = new Intent(MainActivity.this, BluetoothService.class);
        //bluetoothServiceIntent.putExtra("com.glapp.Messenger", new Messenger(mHandler));
        startService(bluetoothServiceIntent);
        bindService(bluetoothServiceIntent, serviceConnection, BIND_AUTO_CREATE);

        //Controllers setup
        binding.connectButton.setOnTouchListener(connectController);
        binding.driveScreen.setOnTouchListener(driveController);
    }

    //Required to update the UI when the activity is resumed (theme change)
    @Override
    protected void onResume() {
        super.onResume();

        if (mBluetoothService != null) {
            if (mBluetoothService.isConnected()) {
                mHandler.obtainMessage(BluetoothService.MSG_WHAT.STATUS, BluetoothService.State.CONNECTED).sendToTarget();
            } else {
                mHandler.obtainMessage(BluetoothService.MSG_WHAT.STATUS, BluetoothService.State.DISCONNECTED).sendToTarget();
            }
        }
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
            Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
        }

        firstTimeBackPressed = System.currentTimeMillis();
    }

    private Menu menu = null;

    public Menu getMenu() {
        return menu;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // change icon for bluetooth menu item
        if (mBluetoothService != null && mBluetoothService.isConnected()) {
            menu.findItem(R.id.app_bar_bluetooth).setIcon(R.drawable.ic_bluetooth_connected);
        }

        MenuItem item = menu.findItem(R.id.app_bar_direction);
        item.getActionView().setOnClickListener(v -> onOptionsItemSelected(menu.findItem(R.id.app_bar_direction)));
        ((ImageView) item.getActionView()).setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_direction));
        item.getActionView().setPadding(10, 10, 10, 10);
        return true;
    }

    //true = forward, false = backward
    private boolean direction = true;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.app_bar_lights:

                return true;

            case R.id.app_bar_direction:

                if (item.getActionView().getRotation() % 180 != 0) return true;

                item.setEnabled(false);
                ObjectAnimator rotationAnimator = ObjectAnimator.ofFloat(item.getActionView(), "rotation", item.getActionView().getRotation(), item.getActionView().getRotation()+180f);
                rotationAnimator.setDuration(300);
                rotationAnimator.setInterpolator(new LinearInterpolator());
                rotationAnimator.start();
                new Handler(Looper.getMainLooper()).postDelayed(() -> item.setEnabled(true), 300);

                direction = !direction;

                return true;

            case R.id.app_bar_bluetooth:

                item.setEnabled(false);

                connectionController();

                return true;

            case R.id.app_bar_settings:

                startActivity(new Intent(this, SettingsActivity.class));

                return true;
        }
        return(super.onOptionsItemSelected(item));
    }

    //////////////////////////////// BLUETOOTH ////////////////////////////////

    /**
     * Bluetooth service connection flow controller
     */
    void connectionController() {
        if (mBluetoothService == null) {
            return;
        }

        if (mBluetoothService.isEnabled() && mBluetoothService.isConnected()) {
            mBluetoothService.disconnect();
        } else {
            mBluetoothService.connect(true);
        }
    }

    //////////////////////////////// CONTROLS ////////////////////////////////

    private static final double MAX_CONTROL_RANGE = 0.4; // 40% height of screen is control size

    private static final double START_DEAD_ZONE = 0.1; // 10% of power is start dead zone (power 10 and lower is 0)

    private final View.OnTouchListener driveController = new View.OnTouchListener() {

        int power;
        private boolean started = false;
        private float touchPosStart, touchPosNow;
        private long touchTimeStart, touchTimeFirst;
        private static final int DOUBLE_CLICK_TIME_THRESHOLD = 250; // ms
        private final static long CLICK_TIME_THRESHOLD = 200; //ms
        private final static int SEND_INTERVAL = 100; //ms
        private final Handler sendingHandler = new Handler(Looper.getMainLooper());
        private final Runnable sendingLoop = new Runnable() {
            @Override
            public void run() {
                mBluetoothService.send(new Packet(Type.REQUEST, Source.NODE_SLAVE, Command.CONTROL, new ControlData(direction, power)));
                sendingHandler.postDelayed(this, SEND_INTERVAL); // Run the task again after 100 milliseconds
            }
        };

        private final Handler clickHandler = new Handler(Looper.getMainLooper());
        private final Runnable connectDevice = () -> connectionController();

        @Override
        public boolean onTouch(View view, MotionEvent event) {

            int windowHeight = getWindowManager().getCurrentWindowMetrics().getBounds().height();
            ObjectAnimator animation;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchTimeStart = System.currentTimeMillis();
                    //set start touch position
                    touchPosStart = event.getY();

                    animation = ObjectAnimator.ofFloat(binding.driveScreenControlsNeutral, "y", binding.driveScreenControlsNeutral.getY(), touchPosStart - binding.driveScreenControlsNeutral.getHeight() / 2f);
                    animation.setInterpolator(new OvershootInterpolator());
                    animation.setAutoCancel(true);
                    animation.start();

                    break;
                case MotionEvent.ACTION_UP:

                    animation = ObjectAnimator.ofFloat(binding.driveScreenControlsNeutral, "y", binding.driveScreenControlsNeutral.getY(), windowHeight / 2f - binding.driveScreenControlsNeutral.getHeight() * 3f/2f);
                    animation.setInterpolator(new OvershootInterpolator());
                    animation.setAutoCancel(true);
                    animation.start();

                    if (!started) {

                        // Set up the user interaction to manually show or hide the system UI.
                        if (touchTimeFirst + DOUBLE_CLICK_TIME_THRESHOLD > System.currentTimeMillis()) {
                            clickHandler.removeCallbacks(connectDevice);
                            toggle();
                            break;
                        }
                        touchTimeFirst = System.currentTimeMillis();

                        if (touchTimeStart + CLICK_TIME_THRESHOLD > System.currentTimeMillis()) {
                            if (mBluetoothService.isConnected()) {
                                break;
                            }
                            view.setPressed(true);
                            clickHandler.postDelayed(connectDevice, DOUBLE_CLICK_TIME_THRESHOLD);
                            view.setPressed(false);
                        }
                        break;
                    }

                    //reset power and dead zone
                    power = 0;
                    started = false;
                    //Log.d(TAG,"start=" + touchPosStart + ", now=" + touchPosNow + ", power=" + power);

                    //send power and reset display text
                    binding.driveScreenControlsGradient.setVisibility(View.GONE);
                    binding.driveScreenControlsNeutral.setText(R.string.drag_to_drive);
                    mBluetoothService.send(new Packet(Type.REQUEST, Source.NODE_SLAVE, Command.CONTROL, new ControlData(direction, power)));

                    //stop sending loop
                    sendingHandler.removeCallbacks(sendingLoop);
                    break;
                case MotionEvent.ACTION_MOVE:

                    if (!mBluetoothService.isConnected()) {
                        //Toast.makeText(MainActivity.this, "Not connected", Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    //set current touch position
                    touchPosNow = event.getY();

                    //calculate power
                    power = (int)((touchPosStart - touchPosNow) / (windowHeight * MAX_CONTROL_RANGE / 2f) * 100f);

                    //limit power and move zone
                    if (power > 100 || power < -100) {
                        if (power > 100) {
                            power = 100;
                            touchPosStart = (float) (touchPosNow + (windowHeight * MAX_CONTROL_RANGE / 2f));
                        } else {
                            power = -100;
                            touchPosStart = (float) (touchPosNow - (windowHeight * MAX_CONTROL_RANGE / 2f));
                        }
                    }
                    //Log.d(TAG,"start=" + touchPosStart + ", now=" + touchPosNow + ", power=" + power);

                    //dead zone
                    if (power < START_DEAD_ZONE * 100 && power > -START_DEAD_ZONE * 100) {
                        if (!started) break;
                    } else {
                        started = true;
                    }

                    if (binding.driveScreenControlsNeutral.getY() != touchPosStart - binding.driveScreenControlsNeutral.getHeight() / 2f) {
                        //binding.driveScreenControlsNeutral.setY(touchPosStart - binding.driveScreenControlsNeutral.getHeight() / 2f);
                        animation = ObjectAnimator.ofFloat(binding.driveScreenControlsNeutral, "y", binding.driveScreenControlsNeutral.getY(), touchPosStart - binding.driveScreenControlsNeutral.getHeight() / 2f);
                        animation.setDuration(0);
                        animation.start();
                    }

                    binding.driveScreenControlsGradient.setVisibility(View.VISIBLE);
                    if (power > 0) {
                        binding.driveScreenControlsGradient.setImageDrawable(getDrawable(R.drawable.gradient_throttle));
                        binding.driveScreenControlsGradient.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (int) (touchPosStart - touchPosNow)));
                        binding.driveScreenControlsGradient.setY(touchPosNow);
                    } else if (power < 0) {
                        binding.driveScreenControlsGradient.setImageDrawable(getDrawable(R.drawable.gradient_brake));
                        binding.driveScreenControlsGradient.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (int) (touchPosNow - touchPosStart)));
                        binding.driveScreenControlsGradient.setY(touchPosStart);
                    } else {
                        binding.driveScreenControlsGradient.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0));
                    }

                    //INFO temp
                    //round power (reduce power wobble)
                    power = (int)(Math.round(power / 10.0) * 10);

                    //send power and update display
                    binding.driveScreenControlsNeutral.setText(String.valueOf(power));
                    mBluetoothService.send(new Packet(Type.REQUEST, Source.NODE_SLAVE, Command.CONTROL, new ControlData(direction, power)));
                    //Integer.toBinaryString(power).replaceFirst("^.*(.{8})$","$1")

                    //start/reset sending loop
                    sendingHandler.removeCallbacks(sendingLoop);
                    sendingHandler.post(sendingLoop);
                    break;
                default:
                    break;
            }
            return true;
        }
    };

    private final View.OnTouchListener connectController = (view, event) -> {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (AUTO_HIDE) {
                    delayedHide(AUTO_HIDE_DELAY_MILLIS);
                }
                break;
            case MotionEvent.ACTION_UP:
                view.performClick();

                connectionController();

                break;
            default:
                break;
        }
        return true;
    };
}