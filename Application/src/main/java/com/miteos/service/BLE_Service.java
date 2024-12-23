package com.miteos.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.miteos.activity.MainActivity;
import com.miteos.activity.R;
import com.miteos.service.gatt.SampleGattAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLE_Service extends Service {

    private final static String TAG = BLE_Service.class.getSimpleName();

    public static final String CHANNEL_ID = "com.miteos.UPDATE_SERVICE";

    public static Boolean isRunning = false;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private static final int MAX_RETRY_COUNT = 5;
    private int mRetryCount = 0;
    private static final long RETRY_DELAY_MS = 5000; // 5 seconds

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.miteos.service.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_CONNECTING =
            "com.miteos.service.le.ACTION_GATT_CONNECTING";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.miteos.service.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.miteos.service.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.miteos.service.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.miteos.service.le.EXTRA_DATA";
    public final static String ACTION_MTU_CHANGED =
            "com.miteos.service.le.ACTION_MTU_CHANGED";
    public final static String EXTRA_MTU_SIZE =
            "com.miteos.service.le.EXTRA_MTU_SIZE";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.MY_UUID);

    public final static UUID UUID_NUS_SERVICE =
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");

    public final static UUID UUID_READ_FROM_ESP =
            UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    public final static UUID UUID_WRITE_TO_ESP =
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    intentAction = ACTION_GATT_CONNECTED;
                    mConnectionState = STATE_CONNECTED;
                    mRetryCount = 0; // Reset retry count on successful connection

                    broadcastUpdate(intentAction);
                    Log.i(TAG, "Connected to GATT server.");

                    if (ActivityCompat.checkSelfPermission(MainService.instance, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
                        return;
                    }

                    // Request larger MTU size first
                    Log.d(TAG, "Requesting MTU size of 512");
                    boolean mtuRequested = mBluetoothGatt.requestMtu(512);
                    Log.d(TAG, "MTU request initiated: " + mtuRequested);

                    // Add a small delay before discovering services
                    mHandler.postDelayed(() -> {
                        if (ActivityCompat.checkSelfPermission(MainService.instance, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for service discovery");
                            return;
                        }
                        Log.i(TAG, "Starting service discovery...");
                        boolean discoveryStarted = mBluetoothGatt.discoverServices();
                        Log.i(TAG, "Service discovery started: " + discoveryStarted);
                        if (!discoveryStarted) {
                            Log.e(TAG, "Failed to start service discovery");
                            // Try to reconnect
                            disconnect();
                            connect(mBluetoothDeviceAddress);
                        }
                    }, 1000); // Increased delay to 1 second
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    intentAction = ACTION_GATT_DISCONNECTED;
                    mConnectionState = STATE_DISCONNECTED;
                    Log.i(TAG, "Disconnected from GATT server.");
                    broadcastUpdate(intentAction);

                    // Try to reconnect with exponential backoff
                    if (mRetryCount < MAX_RETRY_COUNT) {
                        mRetryCount++;
                        long delay = RETRY_DELAY_MS * (long) Math.pow(2, mRetryCount - 1);
                        Log.d(TAG, "Scheduling reconnection attempt " + mRetryCount + " in " + delay + "ms");
                        mHandler.postDelayed(() -> {
                            if (mConnectionState == STATE_DISCONNECTED) {
                                connect(mBluetoothDeviceAddress);
                            }
                        }, delay);
                    } else {
                        Log.e(TAG, "Max retry count reached. Stopping reconnection attempts.");
                    }
                }
            } else {
                Log.e(TAG, "Connection state change with status: " + status);
                // Connection failed, try to reconnect
                mConnectionState = STATE_DISCONNECTED;
                if (mRetryCount < MAX_RETRY_COUNT) {
                    mRetryCount++;
                    long delay = RETRY_DELAY_MS * (long) Math.pow(2, mRetryCount - 1);
                    Log.d(TAG, "Connection failed. Scheduling reconnection attempt " + mRetryCount + " in " + delay + "ms");
                    mHandler.postDelayed(() -> {
                        if (mConnectionState == STATE_DISCONNECTED) {
                            connect(mBluetoothDeviceAddress);
                        }
                    }, delay);
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d(TAG, "MTU Changed - New MTU: " + mtu + " Status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Notify MainService about the new MTU size
                Intent intent = new Intent(ACTION_MTU_CHANGED);
                intent.putExtra(EXTRA_MTU_SIZE, mtu);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful: " + descriptor.getUuid().toString());
                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
                if (characteristic != null && 
                    characteristic.getUuid().toString().equalsIgnoreCase("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")) {
                    Log.d(TAG, "Notifications enabled for RX characteristic");
                }
            } else {
                Log.e(TAG, "Descriptor write failed: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful: " + characteristic.getUuid().toString());
            } else {
                Log.e(TAG, "Characteristic write failed: " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully");
                if (ActivityCompat.checkSelfPermission(MainService.instance, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for listing services");
                    return;
                }
                List<BluetoothGattService> services = gatt.getServices();
                Log.d(TAG, "Found " + services.size() + " services:");
                for (BluetoothGattService service : services) {
                    Log.d(TAG, "Service: " + service.getUuid().toString());
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        Log.d(TAG, "  Characteristic: " + characteristic.getUuid().toString() + 
                              " Properties: 0x" + Integer.toHexString(characteristic.getProperties()));
                    }
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
                // If service discovery failed, try to discover again
                if (mBluetoothGatt != null) {
                    Log.d(TAG, "Retrying service discovery...");
                    mHandler.postDelayed(() -> {
                        if (ActivityCompat.checkSelfPermission(MainService.instance, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            mBluetoothGatt.discoverServices();
                        }
                    }, 1000);
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                Log.d(TAG, "Characteristic read successfully: " + characteristic.getUuid().toString());
            } else {
                Log.e(TAG, "Characteristic read failed: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            String uuid = characteristic.getUuid().toString();
            byte[] value = characteristic.getValue();
            Log.d(TAG, "Characteristic changed notification received for: " + uuid + 
                  " value: " + (value != null ? new String(value) : "null"));
            
            // For Nordic UART RX characteristic
            if (uuid.equalsIgnoreCase("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")) {
                if (value != null && value.length > 0) {
                    String data = new String(value);
                    Log.d(TAG, "Broadcasting data from RX characteristic: " + data);
                    final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
                    intent.putExtra(EXTRA_DATA, data);
                    intent.setPackage(getPackageName());
                    sendBroadcast(intent);
                }
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        intent.setPackage(getPackageName());

        // Special handling for the Nordic UART RX characteristic
        if (characteristic.getUuid().toString().equalsIgnoreCase("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                String value = new String(data);
                Log.d(TAG, "Received data from RX characteristic: " + value);
                intent.putExtra(EXTRA_DATA, value);
            } else {
                Log.w(TAG, "Received empty data from RX characteristic");
            }
        } else {
            Log.d(TAG, "Received data from characteristic: " + characteristic.getUuid().toString());
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BLE_Service getService() {
            return BLE_Service.this;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Intent notificationIntent = new Intent(this.getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(this.getApplicationContext(), 300, notificationIntent, PendingIntent.FLAG_MUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(this.getApplicationContext(), 300, notificationIntent, PendingIntent.FLAG_ONE_SHOT);
        }

        Notification notification = new NotificationCompat.Builder(this.getApplicationContext(), CHANNEL_ID)
                .setContentTitle("MiteOS Companion Service")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (mBluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
            }
            mBluetoothGatt = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        isRunning = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(String address) {
        Log.i(TAG, "Connecting to:" + address);
        if (address.equalsIgnoreCase("00:00:00:00:00")) {
            Log.e(TAG, "Invalid address");
            return false;
        }

        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device. Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
                return false;
            }
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                Log.d(TAG, "Re-connection started");
                return true;
            } else {
                Log.e(TAG, "Re-connection failed");
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found. Unable to connect.");
            return false;
        }

        // Close existing GATT if any
        if (mBluetoothGatt != null) {
            Log.d(TAG, "Closing existing GATT connection");
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        // We want to directly connect to the device, so we are setting the autoConnect parameter to false.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
            return false;
        }
        Log.d(TAG, "Creating new GATT connection");
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        if (mBluetoothGatt == null) {
            Log.e(TAG, "Failed to create GATT connection");
            return false;
        }
        
        Log.d(TAG, "New GATT connection created");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) return;
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Request a write of a given characteristic.
     *
     * @param characteristic The characteristic to write.
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
            return;
        }

        // For Nordic UART TX characteristic, we should use WRITE_TYPE_NO_RESPONSE
        if (characteristic.getUuid().toString().equalsIgnoreCase("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")) {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        }

        byte[] value = characteristic.getValue();
        Log.d(TAG, "Writing characteristic: " + characteristic.getUuid().toString() + 
              " value: " + (value != null ? new String(value) : "null") +
              " write type: " + characteristic.getWriteType());
        
        boolean success = mBluetoothGatt.writeCharacteristic(characteristic);
        if (!success) {
            Log.e(TAG, "Failed to write characteristic");
        }
    }

    /**
     * Request a write of a given descriptor.
     *
     * @param descriptor The descriptor to write.
     */
    public void writeDescriptor(BluetoothGattDescriptor descriptor) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Log.d(TAG, "Writing descriptor: " + descriptor.getUuid().toString());
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification. False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                            boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
            return;
        }

        // Log the characteristic properties
        int properties = characteristic.getProperties();
        Log.d(TAG, "Setting characteristic notification: " + characteristic.getUuid().toString() 
              + " enabled: " + enabled 
              + " properties: 0x" + Integer.toHexString(properties));

        // For Nordic UART RX characteristic, ensure it supports notifications
        if (characteristic.getUuid().toString().equalsIgnoreCase("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")) {
            if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                Log.e(TAG, "RX characteristic does not support NOTIFY");
                return;
            }
        }

        // Enable notification at the GATT level
        boolean success = mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        if (!success) {
            Log.e(TAG, "Failed to set characteristic notification at GATT level");
            return;
        }
        Log.d(TAG, "Successfully set characteristic notification at GATT level");

        // Get the Client Characteristic Configuration Descriptor
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if (descriptor != null) {
            byte[] value = enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE :
                                   BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            success = descriptor.setValue(value);
            if (!success) {
                Log.e(TAG, "Failed to set descriptor value");
                return;
            }
            
            success = mBluetoothGatt.writeDescriptor(descriptor);
            if (!success) {
                Log.e(TAG, "Failed to write descriptor");
            } else {
                Log.d(TAG, "Successfully initiated descriptor write");
            }
        } else {
            Log.e(TAG, "Could not get CCC descriptor for characteristic: " + characteristic.getUuid().toString());
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return null;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        return mBluetoothGatt.getServices();
    }
}
