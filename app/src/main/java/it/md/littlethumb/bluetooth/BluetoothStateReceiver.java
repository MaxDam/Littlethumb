package it.md.littlethumb.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class BluetoothStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            switch(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1))
            {
                case BluetoothAdapter.STATE_OFF:
                    context.stopService(new Intent(context, BeaconsMonitoringService.class));
                    break;

                case BluetoothAdapter.STATE_ON:
                    context.startService(new Intent(context, BeaconsMonitoringService.class));
                    break;
            }
        }
    }
}
