package com.gravemind.dev.barscanappdemo;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class MyService extends Service {

    final static String TAG = "MyService";
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("TAG", "onCreate");
        Toast.makeText(this, "Service created...", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("TAG", "onDestroy");
        Toast.makeText(this, "Service destroyed...", Toast.LENGTH_LONG).show();
    }
    @Override
    public IBinder onBind(Intent intent) {
        Log.i("TAG", "onBind");
        // TODO Auto-generated method stub
        return null;
    }
}