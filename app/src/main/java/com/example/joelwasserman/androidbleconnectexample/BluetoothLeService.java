package com.example.joelwasserman.androidbleconnectexample;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.codepath.asynchttpclient.AsyncHttpClient;
import com.codepath.asynchttpclient.RequestHeaders;
import com.codepath.asynchttpclient.RequestParams;
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
    static List<ServiceCharacteristic> serviceCharacteristicList = new ArrayList<>();
    static Map<String, ReadingType> characteristicUuidReadingTypeMap = new HashMap<>();
    private int serviceCharacteristicMapCounter = 0;
    public int counter=0;

    public static final String SERVER_URL = "https://olacabs.dashboard.enliteresearch.com/dashboard/upsert/external/data";

    class ServiceCharacteristic {
        private String serviceId;
        private String characterticId;

        public ServiceCharacteristic(String serviceId, String characterticId) {
            this.serviceId = serviceId;
            this.characterticId = characterticId;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getCharacterticId() {
            return characterticId;
        }

        public void setCharacterticId(String characterticId) {
            this.characterticId = characterticId;
        }
    }

    private enum ReadingType {
        temp(100, BluetoothGattCharacteristic.FORMAT_UINT16),
        rh(100, BluetoothGattCharacteristic.FORMAT_UINT16),
        air_quality(100, BluetoothGattCharacteristic.FORMAT_UINT16),
        data_accuracy(1, BluetoothGattCharacteristic.FORMAT_SINT8),
        dust(100, BluetoothGattCharacteristic.FORMAT_UINT16);

        int multiplyingFactor;
        int valueFormatType;
        ReadingType(Integer multiplyingFactor, int valueFormatType) {
            this.multiplyingFactor = multiplyingFactor;
            this.valueFormatType = valueFormatType;
        }
    }

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

    /*****************************/


    public void printString(final String str) {
//        Toast toast = Toast.makeText(getApplicationContext(),
//                str,
//                Toast.LENGTH_SHORT);
//        toast.show();
    }

    public void sendApiRequestToServer(JSONObject json) {
        AsyncHttpClient client = new AsyncHttpClient();

        RequestParams params = new RequestParams();
        RequestHeaders requestHeaders = new RequestHeaders();
        requestHeaders.put("x-auth-token", "w7S8NsSyZUKbKGwNb6v0Dw2xMxJdTc8e");
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString());

        client.post(SERVER_URL, requestHeaders, params, body, new JsonHttpResponseHandler() {
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
            ReadingType readingType = characteristicUuidReadingTypeMap.get(characteristic.getUuid().toString().toUpperCase());
            Integer value = characteristic.getIntValue(readingType.valueFormatType, 0);
            System.out.println("Characteristic " + characteristic.getUuid() + " discovered for service: " + value);
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
                fields.put(readingType.toString(), Double.valueOf(value)/readingType.multiplyingFactor);
                JSONObject tags = new JSONObject();
                tags.put("sensorID", mBluetoothGatt.getDevice().getName());
//                tags.put("equipment", "110");
                jsonObject.put("fields", fields);
                jsonObject.put("tags", tags);
                jsonObject.put("time", System.currentTimeMillis());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            sendApiRequestToServer(jsonObject);
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            System.out.println(newState);
            switch (newState) {
                case 0:
                    printString("device disconnected");
                    break;
                case 2:
                    printString("Device Connected");

                    // discover services and characteristics for this device
                    mBluetoothGatt.discoverServices();
                    break;
                default:
                    printString("we encounterned an unknown state, uh oh\\n");
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            createDescriptorsFromList();
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


    private synchronized void createDescriptorsFromList() {
        if (serviceCharacteristicList.size() == 0) {
            return;
        }
        int index = 0;
        for(ServiceCharacteristic serviceCharacteristic: serviceCharacteristicList) {
            if(index==serviceCharacteristicMapCounter) {
                System.out.println(createDescriptorsForCharacteristics(serviceCharacteristic.getServiceId(), serviceCharacteristic.getCharacterticId()));
                serviceCharacteristicMapCounter = serviceCharacteristicMapCounter >= serviceCharacteristicList.size()-1 ? 0 : serviceCharacteristicMapCounter+1;
                break;
            }
            index++;
        }
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
        Log.i("onUnbind called", "onUnBind called for our BLE service");
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, Restarter.class);
        this.sendBroadcast(broadcastIntent);
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startTimer();
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stoptimertask();
        Log.i("onDestroy called", "onDestroy called for out BLE service");
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, Restarter.class);
        this.sendBroadcast(broadcastIntent);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void startMyOwnForeground()
    {
        String NOTIFICATION_CHANNEL_ID = "example.permanence";
        String channelName = "Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("Ola app running in background")
                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                .setSmallIcon(R.drawable.common_full_open_on_phone)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    private Timer timer;
    private TimerTask timerTask;
    public void startTimer() {
        timer = new Timer();
        timerTask = new TimerTask() {
            public void run() {
                Log.i("Count", "=========  "+ (counter++));
            }
        };
        timer.schedule(timerTask, 1000, 1000); //
    }

    public void stoptimertask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public boolean initialize() {
        serviceCharacteristicList.add(new ServiceCharacteristic("0000181A-0000-1000-8000-00805F9B34FB", "00002A6E-0000-1000-8000-00805F9B34FB"));
        serviceCharacteristicList.add(new ServiceCharacteristic("0000181A-0000-1000-8000-00805F9B34FB", "00002A6F-0000-1000-8000-00805F9B34FB"));
        serviceCharacteristicList.add(new ServiceCharacteristic("1BF8D02D-D56F-4D38-B068-D60D38637B46", "059621E8-B53C-40A7-A006-EF9AFCCFF870"));
        serviceCharacteristicList.add(new ServiceCharacteristic("1BF8D02D-D56F-4D38-B068-D60D38637B46", "4170C175-006F-4C07-8E3F-2AEB7312A788"));
        serviceCharacteristicList.add(new ServiceCharacteristic("1BF8D02D-D56F-4D38-B068-D60D38637B46", "698427F4-410D-4DAB-A896-9806A8DFFC3B"));
        characteristicUuidReadingTypeMap.put("00002A6E-0000-1000-8000-00805F9B34FB", ReadingType.temp);
        characteristicUuidReadingTypeMap.put("00002A6F-0000-1000-8000-00805F9B34FB", ReadingType.rh);
        characteristicUuidReadingTypeMap.put("059621E8-B53C-40A7-A006-EF9AFCCFF870", ReadingType.air_quality);
        characteristicUuidReadingTypeMap.put("4170C175-006F-4C07-8E3F-2AEB7312A788", ReadingType.data_accuracy);
        characteristicUuidReadingTypeMap.put("698427F4-410D-4DAB-A896-9806A8DFFC3B", ReadingType.dust);

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
        mBluetoothGatt = device.connectGatt(this, true, btleGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        printString("Trying to create a new connection ");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

}
