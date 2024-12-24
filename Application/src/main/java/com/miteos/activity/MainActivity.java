package com.miteos.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;

import com.miteos.activity.R;
import com.miteos.service.BLE_Service;
import com.miteos.service.MainService;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Constants
    private final static String TAG = MainActivity.class.getSimpleName();

    private static final String DEVICE_NAME = "device_name";
    private static final String DEVICE_ADDRESS = "device_address";

    private static final int REQUEST = 111;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000; // Stops scanning after 10 seconds.

    // Global variables
    private boolean mScanning;
    private String mDeviceAddress;
    private String mDeviceName;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;

    // User interface
    private Toolbar toolbar;
    private FloatingActionButton fabScan;
    private MaterialButton btnStartService;
    private MaterialButton btnStopService;
    private MaterialCardView listContainer;
    private ListView listView;

    ////////////////////////
    // Activity functions //
    ////////////////////////
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST: {
                if ((checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                        (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                    recreate();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogCustom));
                    builder.setTitle(getString(R.string.app_name));
                    builder.setMessage(getString(R.string.permissions_denied));
                    builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                            finish();
                            dialog.dismiss();

                        }
                    });
                    builder.show();
                }
                break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Check if Bluetooth adapter is initialized
        if (mBluetoothAdapter == null) return;
        
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
            }
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            return;
        }

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLE_Service.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLE_Service.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLE_Service.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLE_Service.ACTION_DATA_AVAILABLE);
        registerReceiver(mGattUpdateReceiver, intentFilter);

        // Only update adapter if it's null
        if (mLeDeviceListAdapter == null) {
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            setupListView();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mGattUpdateReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
            Log.w(TAG, "Receiver was not registered");
        }
        scanLeDevice(false);
        if (mLeDeviceListAdapter != null) {
            mLeDeviceListAdapter.clear();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components first
        initializeUI();

        // Initialize toolbar after UI components
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Pair Watch");
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
        
        // Use this check to determine whether BLE is supported on the device
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mHandler = new Handler(Looper.getMainLooper());

        // Initialize remaining components
        initializeListeners();
        checkPermissions();

        if (!isNotificationServiceRunning()) {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mDeviceAddress = sharedPreferences.getString(DEVICE_ADDRESS, "00:00:00:00:00");
        mDeviceName = sharedPreferences.getString(DEVICE_NAME, "Unknown device");

        updateConnectionState(R.string.disconnected);
        startMyService();
    }

    ///////////////////////
    // Private functions //
    ///////////////////////
    private void initializeUI() {
        try {
            toolbar = findViewById(R.id.toolbar);
            btnStartService = findViewById(R.id.btn_start_service);
            btnStopService = findViewById(R.id.btn_stop_service);
            listContainer = findViewById(R.id.list_container);
            fabScan = findViewById(R.id.fab_scan);
            listView = findViewById(R.id.device_list);

            // Hide list container initially
            if (listContainer != null) {
                listContainer.setVisibility(View.GONE);
            }

            // Initialize adapter
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            
            setupListView();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI components", e);
            Toast.makeText(this, "Error initializing UI components", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupListView() {
        if (listView != null) {
            Log.d(TAG, "Setting up ListView and click listener");
            listView.setAdapter(mLeDeviceListAdapter);
            
            listView.setOnItemClickListener((parent, view, position, id) -> {
                Log.d(TAG, "Device clicked at position: " + position);
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device != null) {
                    Log.d(TAG, "Selected device: " + device.getAddress());
                    scanLeDevice(false);  // Stop scanning before connecting
                    connectTo(device);
                }
            });
        } else {
            Log.e(TAG, "ListView is null!");
        }
    }

    private void initializeListeners() {
        try {
            if (btnStartService != null) {
                btnStartService.setOnClickListener(v -> startMyService());
            }
            if (btnStopService != null) {
                btnStopService.setOnClickListener(v -> stopMyService());
            }
            if (fabScan != null) {
                fabScan.setOnClickListener(v -> {
                    if (mScanning) {
                        scanLeDevice(false);
                        listContainer.setVisibility(View.GONE);
                    } else {
                        if (mLeDeviceListAdapter != null) {
                            mLeDeviceListAdapter.clear();
                        }
                        scanLeDevice(true);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing listeners", e);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if ((checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                    (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                    (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) ||
                    (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                     checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {

                AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogCustom));
                builder.setTitle(getString(R.string.app_name));
                builder.setMessage(getString(R.string.permission_request));
                builder.setPositiveButton(getString(android.R.string.ok), (dialog, which) -> {
                    String[] permissions;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions = new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS
                        };
                    } else {
                        permissions = new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        };
                    }
                    requestPermissions(permissions, REQUEST);
                });
                builder.show();
            }
        } else {
            if ((checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                    (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {

                AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogCustom));
                builder.setTitle(getString(R.string.app_name));
                builder.setMessage(getString(R.string.permission_request));
                builder.setPositiveButton(getString(android.R.string.ok), (dialog, which) -> {
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                            REQUEST);
                });
                builder.show();
            }
        }
    }

    private void startMyService() {
        Intent intent = new Intent(this, MainService.class);
        intent.putExtra(DEVICE_ADDRESS, mDeviceAddress);
        intent.putExtra(DEVICE_NAME, mDeviceName);
        startService(intent);
    }

    private void stopMyService() {
        stopService(new Intent(MainActivity.this, MainService.class));
    }

    public boolean isNotificationServiceRunning() {
        ContentResolver contentResolver = getContentResolver();
        String enabledNotificationListeners =
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
        String packageName = getPackageName();
        return enabledNotificationListeners != null && enabledNotificationListeners.contains(packageName);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(() -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(String.format(Locale.US, "%s (%s)", mDeviceName, mDeviceAddress));
                getSupportActionBar().setSubtitle(getString(resourceId));
            }
        });
    }

    private void scanLeDevice(final boolean enable) {
        if (mBluetoothAdapter == null || listContainer == null || fabScan == null) {
            Log.e(TAG, "Required components not initialized");
            return;
        }

        if (enable) {
            // Stops scanning after a predefined scan period.
            mHandler.postDelayed(() -> {
                mScanning = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "checkSelfPermission failed: BLUETOOTH_SCAN");
                        return;
                    }
                }
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                fabScan.setImageResource(android.R.drawable.ic_search_category_default);
            }, SCAN_PERIOD);

            mScanning = true;
            listContainer.setVisibility(View.VISIBLE);
            fabScan.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "checkSelfPermission failed: BLUETOOTH_SCAN");
                    return;
                }
            }
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            fabScan.setImageResource(android.R.drawable.ic_search_category_default);
            // Remove any pending scan timeout
            mHandler.removeCallbacksAndMessages(null);
        }
        invalidateOptionsMenu();
    }

    private void connectTo(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "Cannot connect to null device");
            return;
        }
        
        Log.d(TAG, "Connecting to device: " + device.getAddress() + " (Name: " + 
              (device.getName() != null ? device.getName() : getString(R.string.unknown_device)) + ")");
        
        // Update local variables
        mDeviceAddress = device.getAddress();
        mDeviceName = device.getName() != null ? device.getName() : getString(R.string.unknown_device);
        
        // Save the new device address and name to preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(DEVICE_ADDRESS, mDeviceAddress);
        editor.putString(DEVICE_NAME, mDeviceName);
        editor.apply();
        
        Log.d(TAG, "Saved new device info to preferences - Address: " + mDeviceAddress + ", Name: " + mDeviceName);

        // Update UI
        if (listContainer != null) {
            listContainer.setVisibility(View.GONE);
        }

        // Stop scanning
        scanLeDevice(false);

        // First unbind and stop existing services
        try {
            Log.d(TAG, "Stopping existing services");
            stopService(new Intent(this, BLE_Service.class));
            stopService(new Intent(this, MainService.class));
        } catch (Exception e) {
            Log.e(TAG, "Error stopping services", e);
        }
        
        // Wait a bit before starting the services again
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Log.d(TAG, "Starting services for new device");
                
                // Start BLE service first
                Intent bleIntent = new Intent(this, BLE_Service.class);
                startService(bleIntent);
                
                // Then start main service
                Intent mainIntent = new Intent(this, MainService.class);
                mainIntent.putExtra(DEVICE_ADDRESS, mDeviceAddress);
                mainIntent.putExtra(DEVICE_NAME, mDeviceName);
                startService(mainIntent);
                
                Log.d(TAG, "Services started, finishing activity");
                finish();
            } catch (Exception e) {
                Log.e(TAG, "Error starting services", e);
                Toast.makeText(this, "Error connecting to device", Toast.LENGTH_SHORT).show();
            }
        }, 1000); // Wait 1 second before restarting services
    }

    ///////////////
    // Callbacks //
    ///////////////
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();
            if (BLE_Service.ACTION_GATT_CONNECTED.equals(action)) {
                updateConnectionState(R.string.connected);
            } else if (BLE_Service.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateConnectionState(R.string.disconnected);
            } else if (BLE_Service.ACTION_DATA_AVAILABLE.equals(action)) {

                String s = intent.getStringExtra(BLE_Service.EXTRA_DATA);
                Log.i(TAG, "ESP says: " + s);

                if (s.startsWith("ESP32=")) {

                    s = s.replace("ESP32=", "");
                    Log.d(TAG, "Command: ESP32");
                    Log.d(TAG, "Value: " + s);

                } else { //GET_NOTIFICATION_LIST
                    Log.e(TAG, "Unknown command");
                }
            } /*else {
                Log.e(TAG, "Action=" + action);
            }*/
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(false); // Hide menu scan button since we use FAB
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
            case 16908332:
                this.finish();
                break;
        }
        return true;
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.d(TAG, "Device found: " + device.getAddress() + 
                          " Name: " + (device.getName() != null ? device.getName() : "Unknown"));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mLeDeviceListAdapter != null) {
                                mLeDeviceListAdapter.addDevice(device);
                                mLeDeviceListAdapter.notifyDataSetChanged();
                                Log.d(TAG, "Device added to adapter, total devices: " + mLeDeviceListAdapter.getCount());
                            } else {
                                Log.e(TAG, "Adapter is null in onLeScan!");
                            }
                        }
                    });
                }
            };

    //////////////////////
    // Internal Classes //
    //////////////////////
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    /**
    * Adapter for holding devices found through scanning.
    */
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = MainActivity.this.getLayoutInflater();
            Log.d(TAG, "LeDeviceListAdapter initialized");
        }

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                Log.d(TAG, "Adding device: " + device.getAddress());
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            Log.d(TAG, "Getting device at position: " + position);
            return mLeDevices.get(position);
        }

        public void clear() {
            Log.d(TAG, "Clearing device list");
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @SuppressLint("MissingPermission")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view.findViewById(R.id.device_address);
                viewHolder.deviceName = view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            String deviceName;
            try {
                deviceName = device.getName();
            } catch (Exception e) {
                deviceName = null;
            }
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

}
