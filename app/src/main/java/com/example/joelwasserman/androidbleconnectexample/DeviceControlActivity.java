package com.example.joelwasserman.androidbleconnectexample;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static android.app.PendingIntent.getActivity;

public class DeviceControlActivity extends AppCompatActivity {

    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    TextView connectTextView;
    TextView peripheralTextView;

    private String mDeviceAddress;
    private String mDeviceName;
    private BluetoothLeService mBluetoothLeService;

    private final ServiceConnection  mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            printString("Service called ");
            if (!mBluetoothLeService.initialize()) {
                //Log.e(TAG, "Unable to initialize Bluetooth");
                printString("Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                //mConnected = true;
                //updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                //mConnected = false;
                //updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                printString(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("MESSAGE");
            if (message != null) {
                peripheralTextView.append(message + "\n");
                // auto scroll for text view
                final int scrollAmount = peripheralTextView.getLayout().getLineTop(peripheralTextView.getLineCount()) - peripheralTextView.getHeight();
                // if there is no need to scroll, scrollAmount will be <=0
                if (scrollAmount > 0) {
                    peripheralTextView.scrollTo(0, scrollAmount);
                }
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
//        mBluetoothLeService = null;
    }

    public void printString(String str){
        Toast toast = Toast.makeText(getApplicationContext(),
                str,
                Toast.LENGTH_SHORT);

        toast.show();

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }



    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //mConnectionState.setText(resourceId);
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            // Log.d(TAG, "Connect request result=" + result);

        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter("sendDataToActivity"));
        setContentView(R.layout.activity_device_control);
        peripheralTextView = (TextView) findViewById(R.id.PeripheralDeviceTextView);
        peripheralTextView.setMovementMethod(new ScrollingMovementMethod());
        peripheralTextView.setText("");
        peripheralTextView.append("Data will flow here\n");

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        connectTextView = (TextView) findViewById(R.id.connectedTo);
        connectTextView.setText("Connected to: "+mDeviceName);
        printString("address of device"+mDeviceAddress);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    public void disconnectDevice(View view) {
        printString("Disconnecting device: " +mDeviceAddress);
        if(mBluetoothLeService!=null) {
            mBluetoothLeService.dontRestart();
        }
        finish();
    }







}


