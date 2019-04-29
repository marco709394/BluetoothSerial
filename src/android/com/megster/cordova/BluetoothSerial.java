package com.megster.cordova;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PhoneGap Plugin for Serial Communication over Bluetooth
 */
public class BluetoothSerial extends CordovaPlugin {

    // actions
    private static final String LIST = "list";
    private static final String CONNECT = "connect";
    private static final String CONNECT_INSECURE = "connectInsecure";
    private static final String DISCONNECT = "disconnect";
    private static final String WRITE = "write";
    private static final String WRITE_STRING = "writeString";
    private static final String AVAILABLE = "available";
    private static final String READ = "read";
    private static final String READ_UNTIL = "readUntil";
    private static final String SUBSCRIBE = "subscribe";
    private static final String UNSUBSCRIBE = "unsubscribe";
    private static final String SUBSCRIBE_RAW = "subscribeRaw";
    private static final String UNSUBSCRIBE_RAW = "unsubscribeRaw";
    private static final String IS_ENABLED = "isEnabled";
    private static final String IS_CONNECTED = "isConnected";
    private static final String CLEAR = "clear";
    private static final String SETTINGS = "showBluetoothSettings";
    private static final String ENABLE = "enable";
    private static final String DISCOVER_UNPAIRED = "discoverUnpaired";
    private static final String SET_DEVICE_DISCOVERED_LISTENER = "setDeviceDiscoveredListener";
    private static final String CLEAR_DEVICE_DISCOVERED_LISTENER = "clearDeviceDiscoveredListener";
    private static final String SET_NAME = "setName";
    private static final String SET_DISCOVERABLE = "setDiscoverable";

    // callbacks
    private CallbackContext enableBluetoothCallback;
    private CallbackContext deviceDiscoveredCallback;

    private BluetoothAdapter bluetoothAdapter;
    private Map<String, BluetoothSerialConnection> bluetoothConnections = new ConcurrentHashMap<>();

    // Debugging
    private static final String TAG = "BluetoothSerial";
    private static final boolean D = true;


    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    // Android 23 requires user to explicitly grant permission for location to discover unpaired
    private static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int CHECK_PERMISSIONS_REQ_CODE = 2;
    private CallbackContext permissionCallback;

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {

        LOG.d(TAG, "action = " + action);

        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        boolean validAction = true;

        if (action.equals(LIST)) {

            listBondedDevices(callbackContext);

        } else if (action.equals(CONNECT)) {

            boolean secure = true;
            connect(args, secure, callbackContext);

        } else if (action.equals(CONNECT_INSECURE)) {

            // see Android docs about Insecure RFCOMM http://goo.gl/1mFjZY
            boolean secure = false;
            connect(args, secure, callbackContext);

        } else if (action.equals(DISCONNECT)) {

            String id = args.getString(0);
            if (id == null || id.isEmpty()) {
                for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                    entry.getValue().stop();
                }
                bluetoothConnections.clear();
            } else {
                BluetoothSerialConnection conn = bluetoothConnections.get(id);
                if (conn != null) {
                    conn.stop();
                    bluetoothConnections.remove(id);
                }
            }
            callbackContext.success();

        } else if (action.equals(WRITE)) {

            byte[] data = args.getArrayBuffer(0);
            try {
                String macAddress = args.getString(1);
                if (macAddress == null || macAddress.isEmpty()) {
                    for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                        entry.getValue().write(data);
                    }
                } else {
                    BluetoothSerialConnection conn = bluetoothConnections.get(macAddress);
                    if (conn != null) {
                        conn.write(data);
                    } else {
                        throw new Exception("device not connected");
                    }
                }
                callbackContext.success();
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }

        } else if (action.equals(WRITE_STRING)) {
            String msg = args.getString(0);
            try {
                byte[] data = msg.getBytes("gbk");

                String macAddress = args.getString(1);
                if (macAddress == null || macAddress.isEmpty()) {
                    for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                        entry.getValue().write(data);
                    }
                } else {
                    BluetoothSerialConnection conn = bluetoothConnections.get(macAddress);
                    if (conn != null) {
                        conn.write(data);
                    } else {
                        throw new Exception("device not connected");
                    }
                }
                callbackContext.success(data);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }

        } else if (action.equals(AVAILABLE)) {

            int[] results = new int[bluetoothConnections.size()];
            int i = 0;
            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                results[i++] = entry.getValue().available();
            }
            callbackContext.success(new JSONArray(results));

        } else if (action.equals(READ)) {

            List<String> results = new ArrayList<>();
            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                results.add(entry.getValue().read());
            }
            callbackContext.success(new JSONArray(results));

        } else if (action.equals(READ_UNTIL)) {

            String interesting = args.getString(0);
            List<String> results = new ArrayList<>();
            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                results.add(entry.getValue().readUntil(interesting));
            }
            callbackContext.success(new JSONArray(results));

        } else if (action.equals(SUBSCRIBE)) {

            String delimiter = args.getString(0);
            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                entry.getValue().subscribe(delimiter, callbackContext);
            }

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else if (action.equals(UNSUBSCRIBE)) {

            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                entry.getValue().unsubscribe();
            }
            callbackContext.success();

        } else if (action.equals(SUBSCRIBE_RAW)) {

            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                entry.getValue().subscribeRaw(callbackContext);
            }

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else if (action.equals(UNSUBSCRIBE_RAW)) {

            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                entry.getValue().unsubscribeRaw();
            }
            callbackContext.success();

        } else if (action.equals(IS_ENABLED)) {

            if (bluetoothAdapter.isEnabled()) {
                callbackContext.success();
            } else {
                callbackContext.error("Bluetooth is disabled.");
            }

        } else if (action.equals(IS_CONNECTED)) {

            List<String> results = new ArrayList<>();
            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                if (entry.getValue().isConnected()) {
                    results.add(entry.getKey());
                }
            }
            callbackContext.success(new JSONArray(results));

        } else if (action.equals(CLEAR)) {

            for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
                entry.getValue().clear();
            }
            callbackContext.success();

        } else if (action.equals(SETTINGS)) {

            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            cordova.getActivity().startActivity(intent);
            callbackContext.success();

        } else if (action.equals(ENABLE)) {

            enableBluetoothCallback = callbackContext;
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH);

        } else if (action.equals(DISCOVER_UNPAIRED)) {

            if (cordova.hasPermission(ACCESS_COARSE_LOCATION)) {
                discoverUnpairedDevices(callbackContext);
            } else {
                permissionCallback = callbackContext;
                cordova.requestPermission(this, CHECK_PERMISSIONS_REQ_CODE, ACCESS_COARSE_LOCATION);
            }

        } else if (action.equals(SET_DEVICE_DISCOVERED_LISTENER)) {

            this.deviceDiscoveredCallback = callbackContext;

        } else if (action.equals(CLEAR_DEVICE_DISCOVERED_LISTENER)) {

            this.deviceDiscoveredCallback = null;

        } else if (action.equals(SET_NAME)) {

            String newName = args.getString(0);
            bluetoothAdapter.setName(newName);
            callbackContext.success();

        } else if (action.equals(SET_DISCOVERABLE)) {

            int discoverableDuration = args.getInt(0);
            Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableDuration);
            cordova.getActivity().startActivity(discoverIntent);

        } else {
            validAction = false;

        }

        return validAction;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {

            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "User enabled Bluetooth");
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.success();
                }
            } else {
                Log.d(TAG, "User did *NOT* enable Bluetooth");
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.error("User did not enable Bluetooth");
                }
            }

            enableBluetoothCallback = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        for (Map.Entry<String, BluetoothSerialConnection> entry : bluetoothConnections.entrySet()) {
            entry.getValue().stop();
        }
    }

    private void listBondedDevices(CallbackContext callbackContext) throws JSONException {
        JSONArray deviceList = new JSONArray();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : bondedDevices) {
            deviceList.put(deviceToJSON(device));
        }
        callbackContext.success(deviceList);
    }

    private void discoverUnpairedDevices(final CallbackContext callbackContext) throws JSONException {

        final CallbackContext ddc = deviceDiscoveredCallback;

        final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {

            private JSONArray unpairedDevices = new JSONArray();

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    try {
                    	JSONObject o = deviceToJSON(device);
                        unpairedDevices.put(o);
                        if (ddc != null) {
                            PluginResult res = new PluginResult(PluginResult.Status.OK, o);
                            res.setKeepCallback(true);
                            ddc.sendPluginResult(res);
                        }
                    } catch (JSONException e) {
                        // This shouldn't happen, log and ignore
                        Log.e(TAG, "Problem converting device to JSON", e);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    callbackContext.success(unpairedDevices);
                    cordova.getActivity().unregisterReceiver(this);
                }
            }
        };

        Activity activity = cordova.getActivity();
        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        bluetoothAdapter.startDiscovery();
    }

    private JSONObject deviceToJSON(BluetoothDevice device) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", device.getName());
        json.put("address", device.getAddress());
        json.put("id", device.getAddress());
        if (device.getBluetoothClass() != null) {
            json.put("class", device.getBluetoothClass().getDeviceClass());
        }
        return json;
    }

    private void connect(CordovaArgs args, boolean secure, CallbackContext callbackContext) throws JSONException {
        String macAddress = args.getString(0);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);

        if (device != null) {
            BluetoothSerialConnection conn = bluetoothConnections.get(macAddress);
            if (conn == null) {
                conn = new BluetoothSerialConnection();
                bluetoothConnections.put(macAddress, conn);
            }
            conn.connect(device, secure, callbackContext);

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else {
            callbackContext.error("Could not connect to " + macAddress);
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {

        for(int result:grantResults) {
            if(result == PackageManager.PERMISSION_DENIED) {
                LOG.d(TAG, "User *rejected* location permission");
                this.permissionCallback.sendPluginResult(new PluginResult(
                        PluginResult.Status.ERROR,
                        "Location permission is required to discover unpaired devices.")
                    );
                return;
            }
        }

        switch(requestCode) {
            case CHECK_PERMISSIONS_REQ_CODE:
                LOG.d(TAG, "User granted location permission");
                discoverUnpairedDevices(permissionCallback);
                break;
        }
    }
}
