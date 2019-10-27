package com.example.joelwasserman.androidbleconnectexample;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class DeviceAdapter extends ArrayAdapter<MainActivity.Device> {

    public DeviceAdapter(Context context, ArrayList<MainActivity.Device> users) {
        super(context, 0, users);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        MainActivity.Device device = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.device_list, parent, false);
        }
        // Lookup view for data population
        TextView tvName = (TextView) convertView.findViewById(R.id.deviceName);
//        TextView tvHome = (TextView) convertView.findViewById(R.id.deviceAddress);
        // Populate the data into the template view using the data object
        tvName.setText("Click to Connect to: " + device.name);
//        tvHome.setText(device.address);

        // Return the completed view to render on screen
        return convertView;
    }
}
