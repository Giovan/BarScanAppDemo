package com.gravemind.dev.barscanappdemo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class MainActivity extends Activity implements Runnable{
    protected static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "com.examples.accessory.controller.action.USB_PERMISSION";

    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    UsbAccessory mAccessory;
    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;

    private static final int MESSAGE_SWITCH = 1;
    private static final int MESSAGE_JOY = 4;
    private static final int MESSAGE_VIBE = 5;

    TextView mDeviceText, mDisplayText;
    Button mConnectButton;
    UsbDevice mDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        setContentView(R.layout.activity_main); */
        mDeviceText = (TextView) findViewById(R.id.text_status);
        mDisplayText = (TextView) findViewById(R.id.text_data);
        mConnectButton = (Button) findViewById(R.id.button_connect);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        /* UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        final UsbAccessory[] accessoryList = manager.getAccessoryList();
        final UsbAccessory accessory = (accessoryList == null ? null : accessoryList[0]);
        if (accessory != null) {
            Log.d("accessory", accessory.getModel());
            Toast.makeText(this, accessory.getModel(), Toast.LENGTH_LONG).show();
        } */
    }

    @Override
    public void onResume() {
        super.onResume();

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        //Check currently connected devices
        updateDeviceList();

        Intent intent = getIntent();
        if (mInputStream != null && mOutputStream != null) {
            return;
        }

        /* UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                Log.d(TAG, "----");
                Log.d(TAG, "----");
                Log.d(TAG, "----");
                Log.d(TAG, accessory.getSerial());
                Log.d(TAG, accessory.getModel());
                Log.d(TAG, accessory.getDescription());
                Log.d(TAG, "----");
                Log.d(TAG, "----");
                Log.d(TAG, "----");
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
        } */
    }

    @Override
    public void onPause() {
        super.onPause();
        // closeAccessory();
        unregisterReceiver(mUsbReceiver);
    }

    /*
     * Receiver to catch user permission responses, which are required in order to actual
     * interact with a connected device.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        && device != null) {
                    //Query the device's descriptor
                    getDeviceStatus(device);
                } else {
                    Log.d(TAG, "permission denied for device " + device);
                }
            }
        }
    };

    /*
     * Initiate a control transfer to request the first configuration
     * descriptor of the device.
     */
    //Type: Indicates whether this is a read or write
    // Matches USB_ENDPOINT_DIR_MASK for either IN or OUT
    private static final int REQUEST_TYPE = 0x80;
    //Request: GET_CONFIGURATION_DESCRIPTOR = 0x06
    private static final int REQUEST = 0x06;
    //Value: Descriptor Type (High) and Index (Low)
    // Configuration Descriptor = 0x2
    // Index = 0x0 (First configuration)
    private static final int REQ_VALUE = 0x200;
    private static final int REQ_INDEX = 0x00;
    private static final int LENGTH = 64;
    private void getDeviceStatus(UsbDevice device) {
        UsbDeviceConnection connection = mUsbManager.openDevice(device);
        //Create a sufficiently large buffer for incoming data
        byte[] buffer = new byte[LENGTH];
        connection.controlTransfer(REQUEST_TYPE, REQUEST, REQ_VALUE, REQ_INDEX,
                buffer, LENGTH, 2000);
        //Parse received data into a description
        String description = parseConfigDescriptor(buffer);

        mDisplayText.setText(description);
        connection.close();
    }

    /*
     * Parse the USB configuration descriptor response per the
     * USB Specification.  Return a printable description of
     * the connected device.
     */
    private static final int DESC_SIZE_CONFIG = 9;
    private String parseConfigDescriptor(byte[] buffer) {
        StringBuilder sb = new StringBuilder();
        //Parse configuration descriptor header
        int totalLength = (buffer[3] &0xFF) << 8;
        totalLength += (buffer[2] & 0xFF);
        //Interface count
        int numInterfaces = (buffer[5] & 0xFF);
        //Configuration attributes
        int attributes = (buffer[7] & 0xFF);
        //Power is given in 2mA increments
        int maxPower = (buffer[8] & 0xFF) * 2;

        sb.append("Configuration Descriptor:\n");
        sb.append("Length: " + totalLength + " bytes\n");
        sb.append(numInterfaces + " Interfaces\n");
        sb.append(String.format("Attributes:%s%s%s\n",
                (attributes & 0x80) == 0x80 ? " BusPowered" : "",
                (attributes & 0x40) == 0x40 ? " SelfPowered" : "",
                (attributes & 0x20) == 0x20 ? " RemoteWakeup" : ""));
        sb.append("Max Power: " + maxPower + "mA\n");

        //The rest of the descriptor is interfaces and endpoints
        int index = DESC_SIZE_CONFIG;
        while (index < totalLength) {
            //Read length and type
            int len = (buffer[index] & 0xFF);
            int type = (buffer[index+1] & 0xFF);
            switch (type) {
                case 0x04: //Interface Descriptor
                    int intfNumber = (buffer[index+2] & 0xFF);
                    int numEndpoints = (buffer[index+4] & 0xFF);
                    int intfClass = (buffer[index+5] & 0xFF);

                    sb.append(String.format("- Interface %d, %s, %d Endpoints\n",
                            intfNumber, nameForClass(intfClass), numEndpoints));
                    break;
                case 0x05: //Endpoint Descriptor
                    int endpointAddr = ((buffer[index+2] & 0xFF));
                    //Number is lower 4 bits
                    int endpointNum = (endpointAddr & 0x0F);
                    //Direction is high bit
                    int direction = (endpointAddr & 0x80);

                    int endpointAttrs = (buffer[index+3] & 0xFF);
                    //Type is the lower two bits
                    int endpointType = (endpointAttrs & 0x3);

                    sb.append(String.format("-- Endpoint %d, %s %s\n",
                            endpointNum,
                            nameForEndpointType(endpointType),
                            nameForDirection(direction) ));
                    break;
            }
            //Advance to next descriptor
            index += len;
        }

        return sb.toString();
    }

    private void updateDeviceList() {
        HashMap<String, UsbDevice> connectedDevices = mUsbManager
                .getDeviceList();
        if (connectedDevices.isEmpty()) {
            mDevice = null;
            mDeviceText.setText("No Devices Currently Connected");
            mConnectButton.setEnabled(false);
        } else {
            StringBuilder builder = new StringBuilder();
            for (UsbDevice device : connectedDevices.values()) {
                //Use the last device detected (if multiple) to open
                mDevice = device;
                builder.append(readDevice(device));
                builder.append("\n\n");
            }
            mDeviceText.setText(builder.toString());
            mConnectButton.setEnabled(true);
        }
    }

    /*
     * Enumerate the endpoints and interfaces on the connected device.
     * We do not need permission to do anything here, it is all "publicly available"
     * until we try to connect to an actual device.
     */
    private String readDevice(UsbDevice device) {
        StringBuilder sb = new StringBuilder();
        sb.append("Device Name: " + device.getDeviceName() + "\n");
        sb.append(String.format(
                "Device Class: %s -> Subclass: 0x%02x -> Protocol: 0x%02x\n",
                nameForClass(device.getDeviceClass()),
                device.getDeviceSubclass(), device.getDeviceProtocol()));

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            sb.append(String
                    .format("+--Interface %d Class: %s -> Subclass: 0x%02x -> Protocol: 0x%02x\n",
                            intf.getId(),
                            nameForClass(intf.getInterfaceClass()),
                            intf.getInterfaceSubclass(),
                            intf.getInterfaceProtocol()));

            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint endpoint = intf.getEndpoint(j);
                sb.append(String.format("  +---Endpoint %d: %s %s\n",
                        endpoint.getEndpointNumber(),
                        nameForEndpointType(endpoint.getType()),
                        nameForDirection(endpoint.getDirection())));
            }
        }

        return sb.toString();
    }

    /* Helper Methods to Provide Readable Names for USB Constants */

    private String nameForClass(int classType) {
        switch (classType) {
            case UsbConstants.USB_CLASS_APP_SPEC:
                return String.format("Application Specific 0x%02x", classType);
            case UsbConstants.USB_CLASS_AUDIO:
                return "Audio";
            case UsbConstants.USB_CLASS_CDC_DATA:
                return "CDC Control";
            case UsbConstants.USB_CLASS_COMM:
                return "Communications";
            case UsbConstants.USB_CLASS_CONTENT_SEC:
                return "Content Security";
            case UsbConstants.USB_CLASS_CSCID:
                return "Content Smart Card";
            case UsbConstants.USB_CLASS_HID:
                return "Human Interface Device";
            case UsbConstants.USB_CLASS_HUB:
                return "Hub";
            case UsbConstants.USB_CLASS_MASS_STORAGE:
                return "Mass Storage";
            case UsbConstants.USB_CLASS_MISC:
                return "Wireless Miscellaneous";
            case UsbConstants.USB_CLASS_PER_INTERFACE:
                return "(Defined Per Interface)";
            case UsbConstants.USB_CLASS_PHYSICA:
                return "Physical";
            case UsbConstants.USB_CLASS_PRINTER:
                return "Printer";
            case UsbConstants.USB_CLASS_STILL_IMAGE:
                return "Still Image";
            case UsbConstants.USB_CLASS_VENDOR_SPEC:
                return String.format("Vendor Specific 0x%02x", classType);
            case UsbConstants.USB_CLASS_VIDEO:
                return "Video";
            case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:
                return "Wireless Controller";
            default:
                return String.format("0x%02x", classType);
        }
    }

    private String nameForEndpointType(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_BULK:
                return "Bulk";
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                return "Control";
            case UsbConstants.USB_ENDPOINT_XFER_INT:
                return "Interrupt";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                return "Isochronous";
            default:
                return "Unknown Type";
        }
    }

    private String nameForDirection(int direction) {
        switch (direction) {
            case UsbConstants.USB_DIR_IN:
                return "IN";
            case UsbConstants.USB_DIR_OUT:
                return "OUT";
            default:
                return "Unknown Direction";
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    /* @Override
    protected boolean isControllerConnected() {
        return (mAccessory != null);
    }

    @Override
    protected void hideControls() {
        // setContentView(R.layout.no_device);
        super.hideControls();
    } */

    /* @Override
    protected void sendVibeControl(boolean longDuration) {
        byte[] command = {0x02,
                longDuration ? (byte)0x64 : (byte)0x32,
                0x00};
        Message msg = Message.obtain(null, MESSAGE_VIBE, command);
        // mHandler.sendMessage(msg);
    }*/

    private void openAccessory(UsbAccessory accessory) {
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            Thread thread = new Thread(null, this, "AccessoryController");
            thread.start();
            Log.d(TAG, "accessory opened");
            // enableControls(true);
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

    private void closeAccessory() {
        // enableControls(false);

        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    /*
     * This receiver monitors for the event of a user granting permission to use
     * the attached accessory.  If the user has checked to always allow, this will
     * be generated following attachment without further user interaction.
     */
    /* private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory "+ accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null && accessory.equals(mAccessory)) {
                    closeAccessory();
                }
            }
        }
    }; */

    /*
     * Runnable block that will poll the accessory data stream
     * for regular updates, posting each message it finds to a
     * Handler.  This is run on a spawned background thread.
     */
    public void run() {
        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;

        while (ret >= 0) {
            try {
                ret = mInputStream.read(buffer);
            } catch (IOException e) {
                break;
            }

            i = 0;
            while (i < ret) {
                int len = ret - i;

                switch (buffer[i]) {
                    case 0x1:
                        if (len >= 3) {
                            Message m = Message.obtain(mHandler, MESSAGE_SWITCH);
                            m.obj = new SwitchMsg(buffer[i + 1], buffer[i + 2]);
                            mHandler.sendMessage(m);
                        }
                        i += 3;
                        break;

                    case 0x6:
                        if (len >= 3) {
                            Message m = Message.obtain(mHandler, MESSAGE_JOY);
                            m.obj = new JoyMsg(buffer[i + 1], buffer[i + 2]);
                            mHandler.sendMessage(m);
                        }
                        i += 3;
                        break;

                    default:
                        Log.d(TAG, "unknown msg: " + buffer[i]);
                        i = len;
                        break;
                }
            }

        }
    }

    /*
     * This Handler receives messages from the polling thread and
     * injects them into the GameActivity methods on the main thread.
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SWITCH:
                    SwitchMsg o = (SwitchMsg) msg.obj;
                    // handleSwitchMessage(o);
                    break;

                case MESSAGE_JOY:
                    JoyMsg j = (JoyMsg) msg.obj;
                    // handleJoyMessage(j);
                    break;

                case MESSAGE_VIBE:
                    try {
                        byte[] v = (byte[]) msg.obj;
                        mOutputStream.write(v);
                        mOutputStream.flush();
                    } catch (IOException e) {
                        Log.w("AccessoryController", "Error writing vibe output");
                    }
                    break;
            }
        }
    };

    /* private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    }; */
}

/*
 * The following are message types defined for each input event that
 * the accessory devices will send
 */

class SwitchMsg {
    private byte sw;
    private byte state;

    public SwitchMsg(byte sw, byte state) {
        this.sw = sw;
        this.state = state;
    }

    public byte getSw() {
        return sw;
    }

    public byte getState() {
        return state;
    }
}

class JoyMsg {
    private int x;
    private int y;

    public JoyMsg(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
