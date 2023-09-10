package com.glapp;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import java.util.Objects;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private BluetoothService mBluetoothService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            mBluetoothService = binder.getService();

            updateSelectableDevices();
        }

        @Override
        public void onServiceDisconnected(android.content.ComponentName name) {
            mBluetoothService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, BluetoothService.class), mServiceConnection, BIND_AUTO_CREATE);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        updateView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mServiceConnection);
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return (super.onOptionsItemSelected(item));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null) return;

        switch (key) {
            case "board_data":
                getFragmentPreference(key).setSummary(sharedPreferences.getStringSet(key, null).toString());

                // Update board data displayed in offline mode
                if (sharedPreferences.getBoolean("offline_mode", false)) {
                    mBluetoothService.connect(true);
                }

                break;

            case "debug_mode":
                updateView();
                break;

            case "offline_mode":
                mBluetoothService.disconnect();
                break;
        }
    }

    private void updateView() {
        boolean inDebugMode = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("debug_mode", false);

        getFragmentPreference("offline_mode").setVisible(inDebugMode);
        getFragmentPreference("bluetooth_board_ssid_filter").setVisible(inDebugMode);

        if (!inDebugMode && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("offline_mode", false)) {
            ((SwitchPreference)getFragmentPreference("offline_mode")).setChecked(false);
        }
    }

    private void updateSelectableDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothService.getPairedDevices();
        String editText = ((EditTextPreference) Objects.requireNonNull(getFragmentPreference("bluetooth_board_ssid_filter"))).getText();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            ((ListPreference) Objects.requireNonNull(getFragmentPreference("bluetooth_board_select"))).setEntries(pairedDevices.stream().filter(device -> editText == null || device.getName().contains(editText)).map(device -> device.getName() + "\n" + device.getAddress()).toArray(CharSequence[]::new));
            ((ListPreference) Objects.requireNonNull(getFragmentPreference("bluetooth_board_select"))).setEntryValues(pairedDevices.stream().filter(device -> editText == null || device.getName().contains(editText)).map(device -> device.getName() + "\n" + device.getAddress()).toArray(CharSequence[]::new));
        }

        if (((ListPreference) Objects.requireNonNull(getFragmentPreference("bluetooth_board_select"))).getEntry() == null) {
            if (((ListPreference) Objects.requireNonNull(getFragmentPreference("bluetooth_board_select"))).getEntries().length != 0) {
                ((ListPreference) Objects.requireNonNull(getFragmentPreference("bluetooth_board_select"))).setValueIndex(0);
            }
        }
    }

    private Preference getFragmentPreference(String key) {
        SettingsFragment settingsFragment = ((SettingsFragment) getSupportFragmentManager().findFragmentById(R.id.settings));
        return settingsFragment != null ? settingsFragment.findPreference(key) : null;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            ((MultiSelectListPreference) Objects.requireNonNull(getPreferenceManager().findPreference("board_data"))).setSummary(((MultiSelectListPreference) Objects.requireNonNull(getPreferenceManager().findPreference("board_data"))).getValues().toString());
        }

        @Override
        public boolean onPreferenceTreeClick(@NonNull Preference preference) {

            switch (preference.getKey()) {
                case "reset":
                    // Reset preferences
                    SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
                    if (preferences != null) {
                        try {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.reset_preferences)
                                    .setMessage(R.string.do_you_really_want_to_reset_preferences_to_default_values)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                                        if (preferences.getBoolean("offline_mode", false)) {
                                            ((SettingsActivity) requireActivity()).mBluetoothService.disconnect();
                                        }
                                        preferences.edit().clear().apply();
                                        requireActivity().recreate();
                                    })
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case "bluetooth_board_select":
                    // Refresh paired devices
                    ((SettingsActivity) requireActivity()).updateSelectableDevices();
                    break;
            }

            return super.onPreferenceTreeClick(preference);
        }
    }
}