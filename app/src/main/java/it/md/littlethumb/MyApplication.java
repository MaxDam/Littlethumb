package it.md.littlethumb;

import android.app.Application;
import android.content.Intent;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;

import it.md.littlethumb.bluetooth.BeaconsMonitoringService;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class MyApplication extends Application {

    private BeaconManager beaconManager = null;

    @Override
    public void onCreate() {
        super.onCreate();

        //init & start the service
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        //beaconManager.setBackgroundScanPeriod(2100L);
        //beaconManager.setBackgroundBetweenScanPeriod(5000L);
        //beaconManager.setForegroundScanPeriod(2100L);
        //beaconManager.setForegroundBetweenScanPeriod(5000L);
        beaconManager.setBackgroundScanPeriod(1000L);
        beaconManager.setBackgroundBetweenScanPeriod(2000L);
        beaconManager.setForegroundScanPeriod(1000L);
        beaconManager.setForegroundBetweenScanPeriod(2000L);
        setBeaconManager(beaconManager);
        startService(new Intent(getApplicationContext(), BeaconsMonitoringService.class));
    }

    public void setBeaconManager(BeaconManager beaconManager) {
        this.beaconManager = beaconManager;
    }

    public BeaconManager getBeaconManager() {
        if (beaconManager == null) {
            beaconManager = BeaconManager.getInstanceForApplication(this);
            beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
            return beaconManager;
        }
        return beaconManager;
    }
}