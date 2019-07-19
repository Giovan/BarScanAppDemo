package com.gravemind.dev.barscanappdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.util.Log;


public class MyBroadCastReceiver extends BroadcastReceiver {
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) {
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if( accessory != null ) {
                        //call method to set up accessory communication
                        Log.d("USB detected", "permission denied for accessory " + accessory);
                    }
                } else {
                    Log.d("NO USB detected", "permission denied for accessory " + accessory);
                }
            }
        }
    }
}