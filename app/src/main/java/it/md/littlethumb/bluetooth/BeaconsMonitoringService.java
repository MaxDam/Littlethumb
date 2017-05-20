package it.md.littlethumb.bluetooth;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import it.md.littlethumb.Logger;
import it.md.littlethumb.MyApplication;
import it.md.littlethumb.R;
import it.md.littlethumb.activities.UserTrackerActivity;
import it.md.littlethumb.algorithm.KalmanFilterLocator;
import it.md.littlethumb.model.AccessPoint;
import it.md.littlethumb.model.BssidResult;
import it.md.littlethumb.model.Location;
import it.md.littlethumb.model.ProjectSite;
import it.md.littlethumb.model.WifiScanResult;
import it.md.littlethumb.model.helper.DatabaseHelper;
import it.md.littlethumb.algorithm.FingerprintLocator;
import it.md.littlethumb.algorithm.ParticleFilterLocator;
import it.md.littlethumb.algorithm.UserPointTrilateration;
import it.md.littlethumb.algorithm.model.AccessPointResult;
import it.md.littlethumb.userlocation.LocationServiceFactory;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class BeaconsMonitoringService extends Service implements BeaconConsumer {

    public static final String INTENT_GET_DETECTED_BEACONS = "BeaconsMonitoringService.INTENT_GET_DETECTED_BEACONS";

    protected Logger log = new Logger(BeaconsMonitoringService.class);

    //stato della scansione dei fari
    public static AtomicInteger action = new AtomicInteger(BeaconSession.SCANNING_OFF);

    //notification manager
    private NotificationManager notificationManager;

    //shared preferences
    public static SharedPreferences preferences;

    private int NOTIFICATION_ID = 1;
    String title = "Littlethumb";
    String text = "servizio di scansione ibeacon";
    String textStop = "stop del servizio di scansione ibeacon";

    private static final String TAG = "altbeacon";
    private BeaconManager beaconManager;

    private static int advertisingId = 1;

    //semaforo che indica esserci una sincronizzazione in atto
    public static final AtomicBoolean inProgress = new AtomicBoolean(false);

    protected ProjectSite site;
    protected DatabaseHelper databaseHelper = null;
    protected Dao<ProjectSite, Integer> projectSiteDao = null;

    private Map<String, AccessPoint> knownAccessPointList = new HashMap<String, AccessPoint>();
    private Map<String, AccessPointResult> discoveredAccessPointList = new HashMap<String, AccessPointResult>();
    private FingerprintLocator locator;

    private BeaconSession sessionData = null;

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onCreate() {
        // Configure BeaconManager.
        Log.d(TAG, "Beacons monitoring service created");
        Toast.makeText(this, "Beacons monitoring service created", Toast.LENGTH_SHORT).show();

        //get beacon session data instance
        sessionData = BeaconSession.getInstance();

        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        // Preferences
        preferences = getSharedPreferences(UserTrackerActivity.SHARED_PREFS_INDOOR, MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Beacons monitoring service destroyed");
        Toast.makeText(this, "Beacons monitoring service destroyed", Toast.LENGTH_SHORT).show();
        /*Notification noti = new Notification.Builder(AltBeaconsMonitoringService.this)
                .setContentTitle("Stopped")
                .setContentText("See you!")
                .setSmallIcon(R.drawable.notificationbeacon)
                .build();*/
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        MyApplication app = (MyApplication)getApplication();
        beaconManager = app.getBeaconManager();
        beaconManager.bind(this);

        Log.d(TAG, "Beacons monitoring service starting");
        Toast.makeText(this, "Beacons monitoring service starting", Toast.LENGTH_SHORT).show();

        //attiva il bluethoot se non e' attivo
        //BluetoothUtil.setActivation(true);

        /*
        Notification noti = new Notification.Builder(this)
                .setContentTitle("Started")
                .setContentText("Here we go")
                .setSmallIcon(R.drawable.notificationbeacon)
                .build();

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(2);
        mNotificationManager.notify(1, noti);
        */

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    //set del site
    private void setSite(int siteId) {

        if (siteId == -1) {
            log.error("UserTackerActivity called without a correct site ID!");
        }

        databaseHelper = OpenHelperManager.getHelper(this, DatabaseHelper.class);
        try {
            projectSiteDao = databaseHelper.getDao(ProjectSite.class);
            site = projectSiteDao.queryForId(siteId);
        } catch (SQLException e) {
            log.error("ProjectSite DAO Error", e);
        }

        if (site == null) {
            log.error("The ProjectSite Id could not be found in the database!");
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.setMonitorNotifier(new MonitorNotifier() {

            @Override
            public void didEnterRegion(Region region) {
                /*
                Log.d(TAG, "entered");
                Toast.makeText(AltBeaconsMonitoringService.this, "Entered", Toast.LENGTH_LONG).show();

                Intent notificationIntent = new Intent(AltBeaconsMonitoringService.this, MyActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent intent = PendingIntent.getActivity(AltBeaconsMonitoringService.this, 0,
                        notificationIntent, 0);

                Notification noti = new Notification.Builder(AltBeaconsMonitoringService.this)
                        .setContentTitle("Entered")
                        .setContentText("You're home!")
                        .setSmallIcon(R.drawable.notificationbeacon)
                        .setContentIntent(intent)
                        .build();


                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(2);
                mNotificationManager.notify(1, noti);
                */
            }

            @Override
            public void didExitRegion(Region region) {
                /*
                Log.d(TAG, "exited");
                Toast.makeText(AltBeaconsMonitoringService.this, "Exited", Toast.LENGTH_LONG).show();
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(1);

                Notification noti = new Notification.Builder(AltBeaconsMonitoringService.this)
                        .setContentTitle("Exited")
                        .setContentText("See you!")
                        .setSmallIcon(R.drawable.notificationbeacon)
                        .build();

                mNotificationManager.notify(2, noti);
                */
            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                //Log.i(TAG, "I have just switched from seeing/not seeing beacons: "+state);
            }
        });

        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if(beacons.size() == 0) return;

                //set del site
                if(site == null && sessionData.getSiteId() != null) {
                    setSite(sessionData.getSiteId());
                }

                //execute action
                switch(action.get()) {
                    case BeaconSession.SCANNING_OFF: {
                        return;
                    }
                    case BeaconSession.GET_DETECTED_BEACONS: {
                        //invia il broadcast per notificare l'aggiornamento
                        Intent broadcastIntent = new Intent();
                        broadcastIntent.putExtra("beacons", new Gson().toJson(beacons.toArray(new Beacon[beacons.size()]), Beacon[].class));
                        broadcastIntent.setAction(INTENT_GET_DETECTED_BEACONS);
                        BeaconsMonitoringService.this.sendBroadcast(broadcastIntent);
                        break;
                    }
                    case BeaconSession.UPDATE_USER_POSITION: {
                        if(inProgress.get()) return;
                        if(site == null) return;

                        try {
                            inProgress.set(true);

                            Location curLocation = LocationServiceFactory.getLocationService().getLocation();
                            WifiScanResult scanResult = new WifiScanResult(new Date().getTime(), curLocation, null);
                            for (Beacon beacon : beacons) {
                                BssidResult bssid = new BssidResult(beacon, scanResult);
                                scanResult.addTempBssid(bssid);
                            }

                            message("user position updating.. ", Toast.LENGTH_SHORT);

                            int algorithmSelection = Integer.parseInt(preferences.getString(UserTrackerActivity.ALGORITHM_CHOOSE_USER_TRACKER, "4"));
                            switch (algorithmSelection) {
                                case 1:
                                    updateUserPositionByTrilateration(scanResult, 1);
                                    break;
                                case 2:
                                    updateUserPositionByTrilateration(scanResult, 2);
                                    break;
                                case 3:
                                    updateUserPositionByTrilateration(scanResult, 3);
                                    break;
                                case 4:
                                    updateUserPositionByTrilateration(scanResult, 4);
                                    break;
                                case 5:
                                    updateUserPositionByTrilateration(scanResult, 5);
                                    break;
                                case 6:
                                    updateUserPositionByFingerprint(scanResult, 1);
                                    break;
                                case 7:
                                    updateUserPositionByFingerprint(scanResult, 2);
                                    break;
                                case 8:
                                    updateUserPositionByFingerprint(scanResult, 3);
                                    break;
                                case 9:
                                    updateUserPositionByFingerprint(scanResult, 4);
                                    break;
                                case 10:
                                    updateUserPositionByFingerprint(scanResult, 5);
                                    break;
                                case 11:
                                    updateUserPositionByParticleFilter(scanResult, algorithmSelection);
                                    break;
                                case 12:
                                    updateUserPositionByParticleFilter(scanResult, algorithmSelection);
                                    break;
                                case 13:
                                    updateUserPositionByParticleFilter(scanResult, algorithmSelection);
                                    break;
                                case 14:
                                    updateUserPositionByParticleFilter(scanResult, algorithmSelection);
                                    break;
                                case 15:
                                    updateUserPositionByParticleFilter(scanResult, algorithmSelection);
                                    break;
                            }

                            message("", Toast.LENGTH_SHORT);

                            //message("fine scansione.. ", Toast.LENGTH_SHORT);
                            log.debug("wifi scan finish..");
                        } finally {
                            inProgress.set(false);
                        }
                        break;
                    }
                }
            }
        });

        try {
            //beaconManager.startMonitoringBeaconsInRegion(new Region("altBeaconMonitoring", null, null, null));
            beaconManager.startRangingBeaconsInRegion(new Region("altBeaconRanging", null, null, null));
        }
        catch (RemoteException e) {
        }
    }

    /*
    //chiama il server
    private boolean callServer(Collection<Beacon> beacons) {

        //se c'è una sincronizzazione in atto.. esce
        if (inProgress.get()) return false;

        try {
            //imposta ad on il semaforo
            inProgress.set(true);

            //chiama il server
            try {
                List<String> beaconUuidList = new ArrayList<String>();
                StringBuffer messageText = new StringBuffer();
                for(final Beacon beacon : beacons) {
                    String selectedUuid = beacon.getId1() + "-" + beacon.getId2() + "-" + beacon.getId3();
                    if(messageText.length() > 0) messageText.append(",");
                    messageText.append(selectedUuid).append("\n");
                    beaconUuidList.add(selectedUuid);
                }

                JsonRestClient jsonClient = JsonRestClient.newInstance(CommonStuff.SERVER_PATH)
                        .addPath("server/getAdvertising.php")
                        .setBasicAuth(CommonStuff.SERVER_USER, CommonStuff.SERVER_PASSWORD)
                        .setRequestBody(new Gson().toJson(beaconUuidList.toArray(new String[beaconUuidList.size()]), String[].class));
                Log.d(TAG, "request: "+jsonClient);
                String out = jsonClient.post().getOutputString();
                Log.d(TAG, "response: "+out);

                //ottiene il risultato
                List<String> messageList = Arrays.asList(new Gson().fromJson(out, String[].class));

                //crea l'intent da chiamare alla notifica
                Intent notificationIntent = new Intent(AltBeaconsMonitoringService.this, AdvertisingActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                notificationIntent.putExtra("messageList", messageList.toArray(new String[messageList.size()]));
                PendingIntent intent = PendingIntent.getActivity(AltBeaconsMonitoringService.this, advertisingId++, notificationIntent, 0);

                //crea la notifica
                Notification notification = new Notification.Builder(AltBeaconsMonitoringService.this)
                        .setContentTitle(beacons.size() + " beacon Found..")
                        .setContentText(messageText.toString())
                        .setSmallIcon(R.drawable.notificationbeacon)
                        .setContentIntent(intent)
                        .build();

                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(2);
                mNotificationManager.notify(1, notification);

                return true;
            }
            catch (Exception e) {
                Log.e(TAG, "errore nella comunicazione con il server", e);
            }
        }
        finally {
            //reimposta a off il semaforo
            inProgress.set(false);
            return false;
        }
    }
    */

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.icon, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, UserTrackerActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, title,
                text, contentIntent);

        // Send the notification.
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    //visualizza un messagio
    private void message(final String str, final int duration) {
        if(str.trim().equals("")) return;
        Toast.makeText(getBaseContext(), str, duration).show();
    }

    //effettua l'update della posizione utente
    private void updateUserPositionByTrilateration(WifiScanResult wr, int algorithmSelection) {

        if(wr.getBssids() != null) {

            //scorre gli access point selezionati e popola la lista
            for (AccessPoint ap : site.getAccessPoints()) {
                knownAccessPointList.put(ap.getBssid(), ap);
            }
            if(knownAccessPointList.size() < 3) {
                message("not enough access point found for trilateration algorithm", Toast.LENGTH_SHORT);
                return;
            }

            //LOG dichiara la struttura contenente gli access points da loggare
            List<AccessPointResult> foundLocations = new ArrayList<AccessPointResult>();

            //scorre gli ssid
            for (BssidResult result : wr.getBssids()) {

                //se l'AP non e' tra quelli selezionati lo salta
                if(!knownAccessPointList.containsKey(result.getBssid())) continue;

                //setta l'access point individuato nella lista
                AccessPointResult accessPoint = new AccessPointResult(result);
                accessPoint.setTimestamp(new Date());

                //se gia' esiste l'AP ricalcola il livello di segnale (in modo da attenuare il rumore)
                if (discoveredAccessPointList.containsKey(result.getBssid())) {
                    //TODO da decommentare
                    //accessPoint.setLevel(factor * accessPoint.getLevel() + (1.0-factor) * accessPoint.getLevel());
                }

                //preleva le coordinate dell'access point
                AccessPoint knownAccessPoint = knownAccessPointList.get(result.getBssid());
                accessPoint.setLocation(new Location(knownAccessPoint.getLocation().getX(), knownAccessPoint.getLocation().getY()));
                discoveredAccessPointList.put(result.getBssid(), accessPoint);

                //LOG riempie la lista degli AP da loggare
                //if(result.getLevel() > -90) {
                if(Math.abs(result.getLevel()) < 90) {
                    //message("found Access Point: " + accessPoint, Toast.LENGTH_SHORT);
                    foundLocations.add(accessPoint);
                }
            }

            //effettua la trilaterazione
            UserPointTrilateration upt = new UserPointTrilateration(discoveredAccessPointList);
            PointF point = null;
            switch(algorithmSelection) {
                case 1:
                    point = upt.calculateUserPosition();
                    break;
                case 2:
                    point = upt.calculateGradientUserPosition();
                    break;
                case 3:
                    point = upt.calculateTriangulationUserPosition();
                    break;
                case 4:
                    point = upt.calculateNLLSUserPosition();
                    break;
                case 5:
                    point = upt.calculateNLLSUserPosition();

                    //applica il filtro di kalman
                    KalmanFilterLocator kfl = KalmanFilterLocator.getInstance();
                    point = kfl.filterPoint(point.x, point.y);
                    break;
            }

            if(point != null) {
                //invia in broadcast la posizione dell'utente
                Intent broadcastReceiverIntent = new Intent();
                broadcastReceiverIntent.putExtra("x", point.x);
                broadcastReceiverIntent.putExtra("y", point.y);
                broadcastReceiverIntent.setAction("android.intent.action.UPDATE_USER_POSITION");
                sendBroadcast(broadcastReceiverIntent);

                /*
  				//LOG ultima posizione
  				String locations = "";
  				locations = String.format("Old position %.2f/%.2fm\n", user.getRelativeX()/MultiTouchDrawable.getGridSpacingX(), user.getRelativeY()/MultiTouchDrawable.getGridSpacingY());

  				//LOG posizione access points
  				Collections.sort(foundLocations, new Comparator<AccessPointResult>() {
  		            @Override
  		            public int compare(AccessPointResult apr1, AccessPointResult apr2) {
  		            	return (apr1.getLevel() > apr2.getLevel() ? -1 : (apr1.getLevel() == apr2.getLevel() ? 0 : 1));
  		            }
  		        });
  				for(AccessPointResult entry : foundLocations) {
  					locations += String.format("%s %.0fdbm %.2f/%.2fm\n", entry.getSsid(), entry.getLevel(), entry.getLocation().getX()/MultiTouchDrawable.getGridSpacingX(), entry.getLocation().getY()/MultiTouchDrawable.getGridSpacingY());
  				}

  				//posiziona l'utente
  				user.setRelativePosition(point.x, point.y);
  				//messageHandler.sendEmptyMessage(MESSAGE_REFRESH);
  				invalidate();

  				//notifica ai geofences il cambio di posizione utente
  				geofencesManager.notifyChangeUserPosition(
  						new Location(point.x, point.y));

  				//LOG posizione dell'utente
  				message(String.format("%sdevice position:%.2f/%.2fm", locations, point.x/MultiTouchDrawable.getGridSpacingX(), point.y/MultiTouchDrawable.getGridSpacingY()), Toast.LENGTH_SHORT);
  				*/
            } else {
                message("is not been possible to acquire user position by trilateration algorith", Toast.LENGTH_SHORT);
                return;
            }
        }
    }

    //effettua l'update della posizione utente
    private void updateUserPositionByFingerprint(WifiScanResult wr, int algorithmSelection) {
        if(wr.getBssids() == null) return;

        //inizializza il locator
        locator = new FingerprintLocator(site);

        //ottiene la posizione dell'utente
        Location location = null;
        switch(algorithmSelection) {
            case 1:
                location = locator.locate(wr);
                break;
            case 2:
                location = locator.locateWithAlgorithms(wr, 1);
                break;
            case 3:
                location = locator.locateWithAlgorithms(wr, 2);
                break;
            case 4:
                location = locator.locateWithAlgorithms(wr, 3);
                break;
            case 5:
                location = locator.locateWithAlgorithms(wr, 4);
                break;
        }

        //posiziona l'utente
        if(location != null) {
            //invia in broadcast la posizione dell'utente
            Intent broadcastReceiverIntent = new Intent();
            broadcastReceiverIntent.putExtra("x", location.getX());
            broadcastReceiverIntent.putExtra("y", location.getY());
            broadcastReceiverIntent.setAction("android.intent.action.UPDATE_USER_POSITION");
            sendBroadcast(broadcastReceiverIntent);

            /*
  			user.setRelativePosition(location.getX(), location.getY());
  			//messageHandler.sendEmptyMessage(MESSAGE_REFRESH);
  			invalidate();

  			//notifica ai geofences il cambio di posizione utente
  			geofencesManager.notifyChangeUserPosition(new Location(location.getX(), location.getY()));

  			//LOG posizione dell'utente
  			message(String.format("device position:%.2f/%.2fm", location.getX()/MultiTouchDrawable.getGridSpacingX(), location.getY()/MultiTouchDrawable.getGridSpacingY()), Toast.LENGTH_SHORT);
  			*/
        }
    }

    private void updateUserPositionByParticleFilter(WifiScanResult wr, int algorithmSelection) {

        if (wr.getBssids() != null) {

            //scorre gli access point selezionati e popola la lista
            for (AccessPoint ap : site.getAccessPoints()) {
                knownAccessPointList.put(ap.getBssid(), ap);
            }
            if (knownAccessPointList.size() < 3) {
                message("not enough access point found for trilateration algorithm", Toast.LENGTH_SHORT);
                return;
            }

            //LOG dichiara la struttura contenente gli access points da loggare
            List<AccessPointResult> foundLocations = new ArrayList<AccessPointResult>();

            //scorre gli ssid
            for (BssidResult result : wr.getBssids()) {

                //se l'AP non e' tra quelli selezionati lo salta
                if (!knownAccessPointList.containsKey(result.getBssid())) continue;

                //setta l'access point individuato nella lista
                AccessPointResult accessPoint = new AccessPointResult(result);
                accessPoint.setTimestamp(new Date());

                //se gia' esiste l'AP ricalcola il livello di segnale (in modo da attenuare il rumore)
                if (discoveredAccessPointList.containsKey(result.getBssid())) {
                    //TODO da decommentare
                    //accessPoint.setLevel(factor * accessPoint.getLevel() + (1.0-factor) * accessPoint.getLevel());
                }

                //preleva le coordinate dell'access point
                AccessPoint knownAccessPoint = knownAccessPointList.get(result.getBssid());
                accessPoint.setLocation(new Location(knownAccessPoint.getLocation().getX(), knownAccessPoint.getLocation().getY()));
                discoveredAccessPointList.put(result.getBssid(), accessPoint);

                //LOG riempie la lista degli AP da loggare
                //if(result.getLevel() > -90) {
                if (Math.abs(result.getLevel()) < 90) {
                    //message("found Access Point: " + accessPoint, Toast.LENGTH_SHORT);
                    foundLocations.add(accessPoint);
                }
            }

            //effettua la trilaterazione
            UserPointTrilateration upt = new UserPointTrilateration(discoveredAccessPointList);
            PointF point = null;
            switch(algorithmSelection) {
                case 10:
                    point = upt.calculateUserPosition();
                    break;
                case 11:
                    point = upt.calculateGradientUserPosition();
                    break;
                case 12:
                    point = upt.calculateTriangulationUserPosition();
                    break;
                case 13:
                case 14:
                    point = upt.calculateNLLSUserPosition();
                    break;
            }

            //se non e' stato ottenuto un punto esce
            if(point == null) {
                message("is not been possible to acquire user position by trilateration algorith", Toast.LENGTH_SHORT);
                return;
            }

            switch(algorithmSelection) {
                case 14:
                    //applica il filtro di kalman
                    KalmanFilterLocator kfl = KalmanFilterLocator.getInstance();
                    point = kfl.filterPoint(point.x, point.y);
                    break;
            }

            //applica il filtro antiparticolato
            ParticleFilterLocator pfl = ParticleFilterLocator.getInstance(this, discoveredAccessPointList, site.getWidth(), site.getHeight(), algorithmSelection);
            pfl.updatePosition(point.x, point.y);
            if(!pfl.inRunning()) pfl.start();

            //se non siamo nel servizio di update -> stop del filtro
            if(action.get() != BeaconSession.UPDATE_USER_POSITION) {
                pfl.stop();
                return;
            }
        }
    }
}
