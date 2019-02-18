package com.megster.cordova;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import java.io.IOException;

public class BluetoothSerialConnection {

    // Debugging
    private static final String TAG = "BluetoothSerial";
    private static final boolean D = true;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Message types sent from the BluetoothSerialService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_READ_RAW = 6;

    // callbacks
    private CallbackContext connectCallback;
    private CallbackContext dataAvailableCallback;
    private CallbackContext rawDataAvailableCallback;

    private BluetoothSerialService bluetoothSerialService;

    private StringBuffer buffer = new StringBuffer();
    private String delimiter;

    // The Handler that gets information back from the BluetoothSerialService
    // Original code used handler for the because it was talking to the UI.
    // Consider replacing with normal callbacks
    private final Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    buffer.append((String)msg.obj);

                    if (dataAvailableCallback != null) {
                        sendDataToSubscriber();
                    }

                    break;
                case MESSAGE_READ_RAW:
                    if (rawDataAvailableCallback != null) {
                        byte[] bytes = (byte[]) msg.obj;
                        sendRawDataToSubscriber(bytes);
                    }
                    break;
                case MESSAGE_STATE_CHANGE:

                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_CONNECTED:
                            Log.i(TAG, "BluetoothSerialService.STATE_CONNECTED");
                            notifyConnectionSuccess();
                            break;
                        case BluetoothSerialService.STATE_CONNECTING:
                            Log.i(TAG, "BluetoothSerialService.STATE_CONNECTING");
                            break;
                        case BluetoothSerialService.STATE_LISTEN:
                            Log.i(TAG, "BluetoothSerialService.STATE_LISTEN");
                            break;
                        case BluetoothSerialService.STATE_NONE:
                            Log.i(TAG, "BluetoothSerialService.STATE_NONE");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    //  byte[] writeBuf = (byte[]) msg.obj;
                    //  String writeMessage = new String(writeBuf);
                    //  Log.i(TAG, "Wrote: " + writeMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    Log.i(TAG, msg.getData().getString(DEVICE_NAME));
                    break;
                case MESSAGE_TOAST:
                    String message = msg.getData().getString(TOAST);
                    notifyConnectionLost(message);
                    break;
            }
        }
    };

    BluetoothSerialConnection() {
        bluetoothSerialService = new BluetoothSerialService(mHandler);
    }

    private void notifyConnectionLost(String error) {
        if (connectCallback != null) {
            connectCallback.error(error);
            connectCallback = null;
        }
    }

    private void notifyConnectionSuccess() {
        if (connectCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(true);
            connectCallback.sendPluginResult(result);
        }
    }

    private void sendRawDataToSubscriber(byte[] data) {
        if (data != null && data.length > 0) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
            result.setKeepCallback(true);
            rawDataAvailableCallback.sendPluginResult(result);
        }
    }

    private void sendDataToSubscriber() {
        String data = readUntil(delimiter);
        if (data != null && data.length() > 0) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
            result.setKeepCallback(true);
            dataAvailableCallback.sendPluginResult(result);

            sendDataToSubscriber();
        }
    }

    public boolean isConnected() {
        return bluetoothSerialService.getState() == BluetoothSerialService.STATE_CONNECTED;
    }

    public void connect(BluetoothDevice device, boolean secure, CallbackContext callbackContext) {
        connectCallback = callbackContext;
        clear();

        if (!isConnected()) {
            bluetoothSerialService.connect(device, secure);
        }
    }

    public void write(byte[] data) throws IOException {
        bluetoothSerialService.write(data);
    }

    public void subscribe(String newDelimiter, CallbackContext callbackContext) {
        delimiter = newDelimiter;
        dataAvailableCallback = callbackContext;
    }

    public void unsubscribe() {
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        dataAvailableCallback.sendPluginResult(result);
        dataAvailableCallback = null;
        delimiter = null;
    }

    public void subscribeRaw(CallbackContext callbackContext) {
        rawDataAvailableCallback = callbackContext;
    }

    public void unsubscribeRaw() {
        rawDataAvailableCallback = null;
    }

    public int available() {
        return buffer.length();
    }

    public String read() {
        int length = buffer.length();
        String data = buffer.substring(0, length);
        buffer.delete(0, length);
        return data;
    }

    public String readUntil(String c) {
        String data = "";
        int index = buffer.indexOf(c, 0);
        if (index > -1) {
            data = buffer.substring(0, index + c.length());
            buffer.delete(0, index + c.length());
        }
        return data;
    }

    public void clear() {
        buffer.setLength(0);
    }

    public void stop() {
        if (bluetoothSerialService != null) {
            bluetoothSerialService.stop();
        }
    }
}
