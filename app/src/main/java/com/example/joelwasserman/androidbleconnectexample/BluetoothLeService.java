package com.example.joelwasserman.androidbleconnectexample;

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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.codepath.asynchttpclient.AsyncHttpClient;
import com.codepath.asynchttpclient.RequestHeaders;
import com.codepath.asynchttpclient.RequestParams;
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler;
import com.google.android.gms.common.api.GoogleApiClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    Boolean btScanning = false;
    int deviceIndex = 0;
    ArrayList<BluetoothDevice> devicesDiscovered = new ArrayList<BluetoothDevice>();
    static Map<String, String> serviceCharacteristicMap = new HashMap<>();

    static {
        serviceCharacteristicMap.put("0000181A-0000-1000-8000-00805F9B34FB", "00002A6E-0000-1000-8000-00805F9B34FB");
        serviceCharacteristicMap.put("1bf8d02d-d56f-4d38-b068-d60d38637b46", "059621e8-b53c-40a7-a006-ef9afccff870");
    }

    public Map<String, String> uuids = new HashMap<String, String>();

    // Stops scanning after 5 seconds.
    private Handler mHandler = new Handler();
    private static final long SCAN_PERIOD = 5000;
    private GoogleApiClient client;


    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID MY_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");


    /*****************************/


    public void printString(String str) {
        Toast toast = Toast.makeText(getApplicationContext(),
                str,
                Toast.LENGTH_SHORT);

        toast.show();

    }

    public void sendApiRequestToServer(JSONObject json) {
        AsyncHttpClient client = new AsyncHttpClient();

        RequestParams params = new RequestParams();
        RequestHeaders requestHeaders = new RequestHeaders();
        requestHeaders.put("x-auth-token", "token");
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString());

        client.post("https://demo.dashboard.enliteresearch.com/dashboard/upsert/external/data", requestHeaders, params, body, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Headers headers, JSON json) {
                // peripheralTextView.append("Data send successfully\n");
                System.out.println(json.jsonArray.toString());

                printString("DATA send Successfully" + json.jsonArray.toString());
            }

            @Override
            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                System.out.println(response);
                printString(response);
            }
        });

    }

    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation
            Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            System.out.println("Characteristic discovered for service: " + value);
            JSONObject jsonObject = new JSONObject();
            try {
                /**
                 * {
                 *    "fields" : {
                 *     "by" : 1.0
                 *    },
                 *    "tags" : {
                 *        "sensorID" : "sensorId",
                 *        "equipment" :"110"
                 *    },
                 *    "time": 1571208265000
                 * }
                 */
                JSONObject fields = new JSONObject();
                fields.put("temp", Double.valueOf(value) / 100);
                JSONObject tags = new JSONObject();
                fields.put("sensorID", "111111");
                fields.put("equipment", "110");
                jsonObject.put("fields", fields);
                jsonObject.put("tags", tags);
                jsonObject.put("time", System.currentTimeMillis());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            sendApiRequestToServer(jsonObject);
//            MainActivity.this.runOnUiThread(new Runnable() {
//                public void run() {
//                   // peripheralTextView.append("device read or wrote to\n" + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,0));
//                }
//            });
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            System.out.println(newState);
            switch (newState) {
                case 0:
//                    MainActivity.this.runOnUiThread(new Runnable() {
//                        public void run() {
//                            peripheralTextView.append("device disconnected\n");
//                            connectToDevice.setVisibility(View.VISIBLE);
//                            disconnectDevice.setVisibility(View.INVISIBLE);
//                        }
//                    });
                    printString("device disconnected");
                    break;
                case 2:
//                    MainActivity.this.runOnUiThread(new Runnable() {
//                        public void run() {
//                            peripheralTextView.append("device connected\n");
//                            connectToDevice.setVisibility(View.INVISIBLE);
//                            disconnectDevice.setVisibility(View.VISIBLE);
//                        }
//                    });
                    printString("Device Connected");

                    // discover services and characteristics for this device
                    mBluetoothGatt.discoverServices();

                    break;
                default:
//                    MainActivity.this.runOnUiThread(new Runnable() {
//                        public void run() {
//                            peripheralTextView.append("we encounterned an unknown state, uh oh\n");
//                        }
//                    });
                    printString("we encounterned an unknown state, uh oh\\n");
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            // this will get called after the client initiates a 			BluetoothGatt.discoverServices() call
//            MainActivity.this.runOnUiThread(new Runnable() {
//                public void run() {
//                    //peripheralTextView.append("device services have been discovered\n");
//                }
//            });

            createDescriptorsFromList();
            // displayGattServices(bluetoothGatt.getService(UUID.fromString("0000181A-0000-1000-8000-00805F9B34FB")));
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        private void broadcastUpdate(final String action,
                                     final BluetoothGattCharacteristic characteristic) {

            System.out.println(characteristic.getUuid());
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            createDescriptorsFromList();
        }
    };


// Device connect call back


    private void createDescriptorsFromList() {
        if (serviceCharacteristicMap.size() == 0) {
            return;
        }
        Map.Entry<String, String> entry = serviceCharacteristicMap.entrySet().iterator().next();
        String service = entry.getKey();
        String characteristic = entry.getValue();
        System.out.println(createDescriptorsForCharacteristics(service, characteristic));
        serviceCharacteristicMap.remove(service);
    }


    private boolean createDescriptorsForCharacteristics(String serviceUUID, String characteristicUUID) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BluetoothGattCharacteristic characteristic = mBluetoothGatt.getService(UUID.fromString(serviceUUID))
                .getCharacteristic(UUID.fromString(characteristicUUID));
        mBluetoothGatt.setCharacteristicNotification(characteristic, Boolean.TRUE);
        BluetoothGattDescriptor bluetoothGattDescriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"));
        bluetoothGattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return mBluetoothGatt.writeDescriptor(bluetoothGattDescriptor);
    }

    //
    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
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

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
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

    public boolean connect(final String address) {
         printString("Connect");
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected devi ce.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);


        //final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            printString("Device not Found \n ");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        printString("new connection ");
        mBluetoothGatt = device.connectGatt(this, false, btleGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        printString("Trying to create a new connection ");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

}
