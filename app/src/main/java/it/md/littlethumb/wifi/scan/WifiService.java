package it.md.littlethumb.wifi.scan;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import it.md.littlethumb.Logger;
import it.md.littlethumb.R;
import it.md.littlethumb.activities.UserTrackerActivity;
import it.md.littlethumb.model.AccessPoint;
import it.md.littlethumb.model.BssidResult;
import it.md.littlethumb.model.Location;
import it.md.littlethumb.model.ProjectSite;
import it.md.littlethumb.model.WifiScanResult;
import it.md.littlethumb.model.helper.DatabaseHelper;
import it.md.littlethumb.algorithm.FingerprintLocator;
import it.md.littlethumb.algorithm.UserPointTrilateration;
import it.md.littlethumb.algorithm.model.AccessPointResult;
import it.md.littlethumb.userlocation.LocationServiceFactory;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class WifiService extends Service {
    
	protected Logger log = new Logger(WifiService.class);
	
	//notification manager
	private NotificationManager notificationManager;
	
	//shared preferences
	public static SharedPreferences preferences;
	
    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION_ID = 1;
    String title = "Fingerprint";
    String text = "servizio di scansione wifi";
    String textStop = "stop del servizio di scansione wifi";

	protected int schedulerTime = 10;

    // WiFi manager
 	private WifiHandler wifi;

 	// WiFi Receiver
 	private WifiReceiver receiverWifi;
 	
 	// Flag to show if there is an ongoing progress
 	private Boolean inProgress = false;

 	protected ProjectSite site;
	protected DatabaseHelper databaseHelper = null;
	protected Dao<ProjectSite, Integer> projectSiteDao = null;
	
	private Map<String, AccessPoint> knownAccessPointList = new HashMap<String, AccessPoint>();
	private Map<String, AccessPointResult> discoveredAccessPointList = new HashMap<String, AccessPointResult>();
	private FingerprintLocator locator;

    @Override
    public void onCreate() {
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
        
        // WiFi manager to manage scans
 		wifi = new WifiHandler(getApplicationContext());
 		
 		// Create new receiver to get broadcasts
 		receiverWifi = new MainWifiReceiver();
 		
 		// Preferences
 		preferences = getSharedPreferences(UserTrackerActivity.SHARED_PREFS_INDOOR, MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        
        int siteId = intent.getExtras().getInt(UserTrackerActivity.SITE_KEY, -1);
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
		
        //start della scansione wifi
        inProgress = false;
        int scanInterval = preferences.getInt(UserTrackerActivity.SCAN_INTERVAL_USER_TRACKER, schedulerTime) * 1000;
        wifi.startScan(receiverWifi, scanInterval);
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
    	
    	//stop della scansione wifi
    	wifi.stopScan(receiverWifi);
    	
    	// Cancel the persistent notification.
        notificationManager.cancel(NOTIFICATION_ID);

        // Tell the user we stopped.
        Toast.makeText(this, textStop, Toast.LENGTH_SHORT).show();
    }

    //resume dello scan
    public void resumeScan() {
    	if(!wifi.getIsScanning()) {
    		wifi.setIsScanning(true);
    	}
    }
    
    //suspend dello scan
    public void suspendScan() {
    	if(wifi.getIsScanning()) {
    		wifi.setIsScanning(false);
    	}
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
	
    public class LocalBinder extends Binder {
    	public WifiService getService() {
            return WifiService.this;
        }
    }
    
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

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
    
    //receiver del wifi scan
  	public class MainWifiReceiver extends WifiReceiver {

  		public void onReceive(Context c, Intent intent) {
  			log.debug("receive wifi..");

  			//se non e' in scansione esce dal metodo
  			if(!wifi.getIsScanning().booleanValue()) return;
  			
  			//message("inizio scansione.. ", Toast.LENGTH_SHORT);
  			
			try {
				if (intent == null || c == null || intent.getAction() == null) return;
				String action = intent.getAction();
				if (!action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
					return;

				// Set in progress (true)
				synchronized (inProgress) {
					if (inProgress == true) return;
					inProgress = true;
				}

				log.debug("wifi scanning..");
				message("wifi scanning.. ", Toast.LENGTH_SHORT);
				List<ScanResult> wifiList = wifi.getScanResults();

				Location curLocation=LocationServiceFactory.getLocationService().getLocation();
				WifiScanResult wifiScanResult = new WifiScanResult(new Date().getTime(),curLocation,null);
				for (ScanResult sr : wifiList) {
					BssidResult bssid=new BssidResult(sr,wifiScanResult);
					wifiScanResult.addTempBssid(bssid);
				}
				
				message("user position updating.. ", Toast.LENGTH_SHORT);
				
				wifi.setIsScanning(false);
				
				int algorithmSelection = Integer.parseInt(preferences.getString(UserTrackerActivity.ALGORITHM_CHOOSE_USER_TRACKER, "4"));
				switch(algorithmSelection) {
					case 1:
						updateUserPositionByTrilateration(wifiScanResult, 1);
						break;
					case 2:
						updateUserPositionByTrilateration(wifiScanResult, 2);
						break;
					case 3:
						updateUserPositionByTrilateration(wifiScanResult, 3);
						break;
					case 4:
						updateUserPositionByFingerprint(wifiScanResult, 1);
						break;
					case 5:
						updateUserPositionByFingerprint(wifiScanResult, 2);
						break;
					case 6:
						updateUserPositionByFingerprint(wifiScanResult, 3);
						break;
					case 7:
						updateUserPositionByFingerprint(wifiScanResult, 4);
						break;
					case 8:
						updateUserPositionByFingerprint(wifiScanResult, 5);
						break;
				}
				
				// Unset in progress (false)
				synchronized (inProgress) {
					inProgress = false;
				}

				message("", Toast.LENGTH_SHORT);
				
				wifi.setIsScanning(true);
				
				//message("fine scansione.. ", Toast.LENGTH_SHORT);
				log.debug("wifi scan finish..");

			} catch (RuntimeException e) {
				return;
			}
  		}
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
  			if(knownAccessPointList.size() < 3) return;
  			
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
  				if(result.getLevel() > -90) {
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
  			geofencesManager.notifyChangeUserPosition(
  						new Location(location.getX(), location.getY()));
  			
  			//LOG posizione dell'utente
  			message(String.format("device position:%.2f/%.2fm", location.getX()/MultiTouchDrawable.getGridSpacingX(), location.getY()/MultiTouchDrawable.getGridSpacingY()), Toast.LENGTH_SHORT);
  			*/
  		}
  	}
}