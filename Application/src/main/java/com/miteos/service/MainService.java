package com.miteos.service;

import static com.miteos.service.BLE_Service.UUID_READ_FROM_ESP;
import static com.miteos.service.BLE_Service.UUID_WRITE_TO_ESP;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.miteos.activity.R;
import com.google.gson.Gson;
import com.miteos.service.data.NotificationBundle;
import com.miteos.service.gatt.SampleGattAttributes;
import com.miteos.service.handlers.MediaHandler;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainService extends Service {

    // Constants
    private final static String TAG = MainService.class.getSimpleName();
    private static final int ICON_PIXELS = 24;
    private static final int ICON_COMPRESSION_QUALITY = 80;
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    public static final String DEVICE_ADDRESS = "device_address";

    // Commands from BT device
    private static final String GET_PACKAGE_ICON = "GET_PKG_ICON=";
    private static final String GET_NOTIFICATION_LIST = "GET_NOTIF_LIST=";
    private static final String GET_PLAYBACK_INFO = "GET_PLAYBACK_INFO=";
    private static final String TOGGLE_PLAYBACK = "TOGGLE_PLAYBACK=";
    private static final String NEXT_PLAYBACK = "NEXT_PLAYBACK=";
    private static final String PREVIOUS_PLAYBACK = "PREVIOUS_PLAYBACK=";
    private static final String GET_CONFIGURATION = "GET_CONFIGURATION=";

    // Actions
    public final static String NOTIFICATION_ACTION = "com.miteos.NOTIFICATION_LISTENER_EXAMPLE";
    public final static String GET_NOTIFICATION_INTENT = "com.miteos.NOTIFICATION_LISTENER_SERVICE_EXAMPLE";

    // Global variables
    private boolean mConnected = false;
    private String mDeviceAddress;
    private BLE_Service mBLEService;
    private NotificationReceiver nReceiver = new NotificationReceiver();
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic mWriteCharacteristic;

    public static MainService instance;

    private int mMtuSize = 20; // Default MTU size

    public MainService() {
        instance = this;
    }

    ///////////////////////
    // Service functions //
    ///////////////////////
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            mDeviceAddress = intent.getStringExtra(DEVICE_ADDRESS);
        } else {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            mDeviceAddress = sharedPreferences.getString(DEVICE_ADDRESS, "00:00:00:00:00");
        }

        // Start BLE service and bind to it
        Intent gattServiceIntent = new Intent(this, BLE_Service.class);
        startService(gattServiceIntent);

        // Register receivers with package-specific intents
        IntentFilter gattFilter = makeGattUpdateIntentFilter();
        Log.d(TAG, "Registering GATT update receiver with filter: " + gattFilter.toString());
        registerReceiver(mGattUpdateReceiver, gattFilter);

        IntentFilter nlFilter = makeNLServiceIntentFilter();
        registerReceiver(nReceiver, nlFilter);

        // Bind to BLE service after registering receivers
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mGattUpdateReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "GATT receiver not registered", e);
        }
        try {
            unregisterReceiver(nReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Notification receiver not registered", e);
        }
        if (mBLEService != null) {
            unbindService(mServiceConnection);
            mBLEService = null;
        }
        stopService(new Intent(this, BLE_Service.class));
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    ///////////////////////
    // Private functions //
    ///////////////////////
    private String getApplicationName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            return (String) pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown app";
        }
    }

    private Drawable getApplicationIcon(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationIcon(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBLEService = ((BLE_Service.LocalBinder) service).getService();
            if (!mBLEService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                mBLEService.disconnect();
                return;
            }
            
            // Delay the connection attempt slightly to ensure proper initialization
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // Automatically connects to the device upon successful start-up initialization.
                if (mBLEService != null) {
                    final boolean result = mBLEService.connect(mDeviceAddress);
                    Log.d(TAG, "Connect request result=" + result);
                }
            }, 1000);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBLEService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "MainService received action: " + action);

            if (BLE_Service.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                Log.i(TAG, getString(R.string.connected));
            } else if (BLE_Service.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                Log.i(TAG, getString(R.string.disconnected));
            } else if (BLE_Service.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "Services discovered");
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBLEService.getSupportedGattServices());
            } else if (BLE_Service.ACTION_MTU_CHANGED.equals(action)) {
                mMtuSize = intent.getIntExtra(BLE_Service.EXTRA_MTU_SIZE, 20);
                Log.d(TAG, "MTU size updated to: " + mMtuSize);
            } else if (BLE_Service.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BLE_Service.EXTRA_DATA);
                Log.i(TAG, "Received data from ESP: " + (data != null ? data : "null"));

                if (data != null) {
                    if (data.startsWith(GET_PACKAGE_ICON)) {
                        data = data.replace(GET_PACKAGE_ICON, "");
                        sendApplicationIcon(data);
                    } else if (data.startsWith(GET_NOTIFICATION_LIST)) {
                        data = data.replace(GET_NOTIFICATION_LIST, "");
                        Log.d(TAG, "Command: Get Notification List");
                        Log.d(TAG, "Value: " + data);
                        Intent i = new Intent(GET_NOTIFICATION_INTENT);
                        i.putExtra("command", "list");
                        sendBroadcast(i);
                    } else if(data.startsWith(GET_PLAYBACK_INFO)) {
                        Log.d(TAG, "Command: Get Playback Info");
                        sendData(new Gson().toJson(MediaHandler.getPlaybackInfos()).replaceAll("[^\\x00-\\x7F]", ""));
                    } else if(data.startsWith(TOGGLE_PLAYBACK)) {
                        Log.d(TAG, "Command: Toggle Playback");
                        MediaHandler.toggle();
                        sendData("DONE");
                    } else if(data.startsWith(NEXT_PLAYBACK)) {
                        Log.d(TAG, "Command: Next Playback");
                        MediaHandler.next();
                        sendData("DONE");
                    } else if(data.startsWith(PREVIOUS_PLAYBACK)) {
                        Log.d(TAG, "Command: Previous Playback");
                        MediaHandler.previous();
                        sendData("DONE");
                    } else if(data.startsWith(GET_CONFIGURATION)) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        String haasUrl = prefs.getString("hassUrl", "http://127.0.0.1");
                        String haasToken = prefs.getString("hass_token", "");
                        String entities = prefs.getString("hass_list_items", "[]");
                        sendData("{ \"hassUrl\": \"" + haasUrl + "\", \"hassTkn\": \"" + haasToken + "\", \"entities\": " + entities + " }");
                    } else {
                        Log.e(TAG, "Unknown command: " + data);
                    }
                }
            }
        }
    };

    private void sendApplicationIcon(String packageName) {
        Log.d(TAG, "Get package icon: " + packageName);
        // Get Application icon
        Drawable drawable = getApplicationIcon(packageName);
        if (drawable != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // Create a bitmap from drawable
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            // Resize to 24x24
            bitmap = getResizedBitmap(bitmap, ICON_PIXELS, ICON_PIXELS);
            // Compress and convert it to PNG
            bitmap.compress(Bitmap.CompressFormat.JPEG, ICON_COMPRESSION_QUALITY, byteArrayOutputStream);
            // Get the bytes
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            // Convert it to Base64
            String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);
            // Send it via BT
            Log.d(TAG, "Sending " + encoded.length() + " bytes...");
            sendData("ICON=" + encoded);
        } else {
            Log.e(TAG, "Error loading application icon from package: " + packageName);
        }
    }

    private Bitmap getResizedBitmap(Bitmap bm, int newHeight, int newWidth) {
        // Get the original bitmap dimensions
        int width = bm.getWidth();
        int height = bm.getHeight();
        Matrix matrix = new Matrix();
        // Resize the bitmap
        matrix.postScale(((float) newWidth) / width, ((float) newHeight) / height);
        // Create the new bitmap
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
    }

    private void sendData(String data) {
        if (!mConnected) {
            Log.e(TAG, "Not connected to device");
            return;
        }

        if (mWriteCharacteristic == null) {
            Log.e(TAG, "Write characteristic not available");
            doDisconnect(null);
            return;
        }

        // Use the negotiated MTU size, accounting for ATT overhead
        int maxChunkSize = mMtuSize - 3; // ATT overhead is 3 bytes
        int length = data.length();
        int offset = 0;

        Log.d(TAG, "Sending data with length " + length + " using MTU size " + mMtuSize);

        // Create a handler for delayed writes
        Handler writeHandler = new Handler(Looper.getMainLooper());
        final int WRITE_DELAY_MS = 50; // 50ms delay between writes

        while (offset < length) {
            final int currentOffset = offset;
            int chunkSize = Math.min(maxChunkSize, length - offset);
            String chunk = data.substring(currentOffset, currentOffset + chunkSize);
            
            // Schedule the write with a delay
            writeHandler.postDelayed(() -> {
                byte[] bytes = chunk.getBytes();
                mWriteCharacteristic.setValue(bytes);
                
                Log.d(TAG, "Writing chunk " + (currentOffset/maxChunkSize + 1) + " of " + 
                      ((length + maxChunkSize - 1)/maxChunkSize) + " size: " + bytes.length);
                
                // Set write type to no response for faster transmission
                mWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                mBLEService.writeCharacteristic(mWriteCharacteristic);
            }, (currentOffset/maxChunkSize) * WRITE_DELAY_MS);

            offset += chunkSize;
        }
    }

    private static IntentFilter makeNLServiceIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NOTIFICATION_ACTION);
        return intentFilter;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLE_Service.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLE_Service.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLE_Service.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLE_Service.ACTION_MTU_CHANGED);
        intentFilter.addAction(BLE_Service.ACTION_DATA_AVAILABLE);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        return intentFilter;
    }

    private void doDisconnect(View view) {
        if (mBLEService != null) {
            mBLEService.disconnect();
        }
    }

    /**
    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    */
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {
            Log.e(TAG, "No GATT services available");
            return;
        }
        
        Log.d(TAG, "Displaying GATT services");
        mNotifyCharacteristic = null;
        mWriteCharacteristic = null;

        // First find the Nordic UART Service
        BluetoothGattService uartService = null;
        for (BluetoothGattService service : gattServices) {
            UUID serviceUuid = service.getUuid();
            Log.d(TAG, "Service discovered: " + serviceUuid.toString());
            
            // Compare UUIDs directly
            if (serviceUuid.toString().equalsIgnoreCase("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")) {
                Log.d(TAG, "Found Nordic UART Service");
                uartService = service;
                break;
            }
        }

        if (uartService != null) {
            // Now get the characteristics from the UART service
            List<BluetoothGattCharacteristic> characteristics = uartService.getCharacteristics();
            Log.d(TAG, "Found " + characteristics.size() + " characteristics in UART service");
            
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                UUID uuid = characteristic.getUuid();
                int properties = characteristic.getProperties();
                Log.d(TAG, "Characteristic discovered: " + uuid.toString() + " with properties: 0x" + Integer.toHexString(properties));

                // Compare UUIDs directly
                if (uuid.toString().equalsIgnoreCase("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")) {
                    Log.d(TAG, "Found RX characteristic");
                    if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        Log.d(TAG, "RX characteristic supports NOTIFY");
                        mNotifyCharacteristic = characteristic;
                        // Set up notifications immediately
                        setCharacteristicNotification(mNotifyCharacteristic, true);
                    } else {
                        Log.e(TAG, "RX characteristic does not support NOTIFY");
                    }
                }

                if (uuid.toString().equalsIgnoreCase("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")) {
                    Log.d(TAG, "Found TX characteristic");
                    if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        Log.d(TAG, "TX characteristic supports WRITE_NO_RESPONSE");
                        mWriteCharacteristic = characteristic;
                        // Set write type to no response
                        mWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    } else if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                        Log.d(TAG, "TX characteristic supports WRITE");
                        mWriteCharacteristic = characteristic;
                    } else {
                        Log.e(TAG, "TX characteristic does not support WRITE");
                    }
                }
            }

            if (mNotifyCharacteristic == null) {
                Log.e(TAG, "RX characteristic not found or does not support NOTIFY");
            }

            if (mWriteCharacteristic == null) {
                Log.e(TAG, "TX characteristic not found or does not support WRITE");
            }
        } else {
            Log.e(TAG, "Nordic UART Service not found");
            // Log all available services for debugging
            Log.d(TAG, "Available services:");
            for (BluetoothGattService service : gattServices) {
                Log.d(TAG, "  Service: " + service.getUuid().toString());
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    Log.d(TAG, "    Characteristic: " + characteristic.getUuid().toString() + 
                          " Properties: 0x" + Integer.toHexString(characteristic.getProperties()));
                }
            }
        }
    }

    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBLEService == null || characteristic == null) {
            Log.w(TAG, "BluetoothGattService or characteristic not initialized");
            return;
        }

        Log.d(TAG, "Setting up notifications for characteristic: " + characteristic.getUuid().toString());
        
        // First, set the local notification
        mBLEService.setCharacteristicNotification(characteristic, enabled);

        // Then, write the descriptor to enable notifications on the remote device
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")); // Client Characteristic Configuration
        if (descriptor != null) {
            Log.d(TAG, "Found notification descriptor, setting value");
            
            // Check if the characteristic has the NOTIFY property
            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                byte[] value = enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : 
                                     BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                boolean success = descriptor.setValue(value);
                if (success) {
                    // Add a small delay before writing the descriptor
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        mBLEService.writeDescriptor(descriptor);
                        Log.d(TAG, "Descriptor write initiated for NOTIFY");
                        
                        // Add a retry mechanism if the write fails
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (!characteristic.getDescriptor(descriptor.getUuid()).getValue().equals(value)) {
                                Log.d(TAG, "Retrying descriptor write for NOTIFY");
                                mBLEService.writeDescriptor(descriptor);
                            }
                        }, 1000); // Check after 1 second
                    }, 100); // 100ms delay before first write
                } else {
                    Log.e(TAG, "Failed to set descriptor value for NOTIFY");
                }
            }
            // Check if the characteristic has the INDICATE property
            else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                byte[] value = enabled ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : 
                                     BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                boolean success = descriptor.setValue(value);
                if (success) {
                    // Add a small delay before writing the descriptor
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        mBLEService.writeDescriptor(descriptor);
                        Log.d(TAG, "Descriptor write initiated for INDICATE");
                        
                        // Add a retry mechanism if the write fails
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (!characteristic.getDescriptor(descriptor.getUuid()).getValue().equals(value)) {
                                Log.d(TAG, "Retrying descriptor write for INDICATE");
                                mBLEService.writeDescriptor(descriptor);
                            }
                        }, 1000); // Check after 1 second
                    }, 100); // 100ms delay before first write
                } else {
                    Log.e(TAG, "Failed to set descriptor value for INDICATE");
                }
            } else {
                Log.e(TAG, "Characteristic does not support NOTIFY or INDICATE");
            }
        } else {
            Log.e(TAG, "No notification descriptor found for characteristic: " + characteristic.getUuid().toString());
        }
    }

    //////////////////////
    // Internal classes //
    //////////////////////
    class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w(TAG, "Intent");
            if (intent.hasExtra("type")) {
                Bundle extras = intent.getExtras();
                String type = extras.getString("type");
                NotificationBundle notificationBundle = new NotificationBundle();
                Log.e(TAG, type);

                if (type.equalsIgnoreCase("new_notification")) {

                    notificationBundle.id = extras.getInt("id", 0);
                    notificationBundle.pName = extras.getString("package", "");
                    notificationBundle.appName = getApplicationName(notificationBundle.pName);
                    notificationBundle.category = extras.getString("category", "");
                    notificationBundle.title = extras.getString("title", "");
                    notificationBundle.text = extras.getString("text", "");
                    if (notificationBundle.category.equals("email")) {
                        notificationBundle.subText = extras.getString("sub_text", "");
                    }
                    // Currently not needed as the clock is in pulling mode to save energy
                    //sendData("NEW_NOTIFICATION=" + new Gson().toJson(notificationBundle));

                } else if (type.equalsIgnoreCase("list_notification")) {

                    // Currently not needed as the clock is in pulling mode to save energy
                    sendData("NOTIFICATION_LIST=" + extras.getString("data", ""));

                } else {
                    Log.e(TAG, "Unknown type:" + type);
                }
            }
        }
    }
}