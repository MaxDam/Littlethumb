package it.md.littlethumb.common;

import android.bluetooth.BluetoothAdapter;

public class BluetoothUtil {

    //gestisce lo stato del bluetooth
    public static boolean setActivation(boolean enable) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean isEnabled = bluetoothAdapter.isEnabled();
        if (enable && !isEnabled) {
            return bluetoothAdapter.enable();
        }
        else if(!enable && isEnabled) {
            return bluetoothAdapter.disable();
        }
        return true;
    }
}
