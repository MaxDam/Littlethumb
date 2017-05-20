package it.md.littlethumb.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

import java.util.ArrayList;
import java.util.List;

import it.md.littlethumb.Logger;
import it.md.littlethumb.R;
import it.md.littlethumb.bluetooth.BeaconSession;
import it.md.littlethumb.bluetooth.BeaconsMonitoringService;
import it.md.littlethumb.exceptions.SiteNotFoundException;
import it.md.littlethumb.geofences.GeoFencesListener;
import it.md.littlethumb.geofences.GeoFencesManager;
import it.md.littlethumb.model.Location;
import it.md.littlethumb.model.ProjectSite;
import it.md.littlethumb.model.helper.DatabaseHelper;
import it.md.littlethumb.userlocation.LocationServiceFactory;
import it.md.littlethumb.view.MultiTouchDrawable;
import it.md.littlethumb.view.MultiTouchView;
import it.md.littlethumb.view.NorthDrawable;
import it.md.littlethumb.view.OkCallback;
import it.md.littlethumb.view.RefreshableView;
import it.md.littlethumb.view.ScaleCircleDrawable;
import it.md.littlethumb.view.ScaleRectangleDrawable;
import it.md.littlethumb.view.SiteMapDrawable;
import it.md.littlethumb.view.UserTrack;
import it.md.littlethumb.wifi.scan.WifiService;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class UserTrackerActivity extends Activity implements OnClickListener, RefreshableView, GeoFencesListener {

	protected Logger log = new Logger(UserTrackerActivity.class);

	public static Context ctx;
	
	public static final String SITE_KEY = "SITE";

	public static final String PROJECT_KEY = "PROJECT";

	protected static final int MESSAGE_REFRESH = 1, MESSAGE_START_WIFISCAN = 2;

	protected int schedulerTime = 10;

	protected MultiTouchView multiTouchView;

	protected SiteMapDrawable map;

	protected ProjectSite site;

	protected DatabaseHelper databaseHelper = null;

	protected Dao<ProjectSite, Integer> projectSiteDao = null;

	protected AlertDialog scanAlertDialog;

	protected ImageView scanningImageView;

	protected BroadcastReceiver wifiBroadcastReceiver;

	protected UserTrack user;

	protected final Context context = this;

	protected TextView backgroundPathTextView;

	protected float scalerDistance;

	protected NorthDrawable northDrawable = null;

	protected Handler messageHandler;

	protected boolean freshSite = false;
	
	protected boolean trackSteps= true; 

	public static final String SCAN_INTERVAL_USER_TRACKER = "scan_interval_user_tracker";

	public static final String ALGORITHM_CHOOSE_USER_TRACKER = "algorithm_choose_user_tracker";

	protected static final int DIALOG_CHANGE_SCAN_INTERVAL = 1, DIALOG_CHOOSE_ALGORITHM = 2, DIALOG_SET_RECTANGLE_FENCE = 3, DIALOG_SET_CIRCLE_FENCE = 4;
	
	final double factor = 0.96;

	// Preferences name for indoor and outdoor
	public static final String SHARED_PREFS_INDOOR = "Indoor_Preferences";

	//geofences manager
	private GeoFencesManager geofencesManager;
	
	//alert dialog
	AlertDialog alertDialog = null;
	
	//fences grafiche
	private List<ScaleRectangleDrawable> scaleRectangleDrawableList = new ArrayList<ScaleRectangleDrawable>();
	private List<ScaleCircleDrawable> scaleCircleDrawableList = new ArrayList<ScaleCircleDrawable>();
	
	// Set When broadcast event will fire.
	private IntentFilter filter = new IntentFilter("android.intent.action.UPDATE_USER_POSITION");

	//servizio di scansione wifi
	private static WifiService wifiService = null;

    //servizio di scansione ibeacon
    private static BeaconsMonitoringService beaconsMonitoringService = null;

    //shared preferences
    public static SharedPreferences preferences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			ctx = this.context;
			
			this.setContentView(R.layout.user_tracker);
			super.onCreate(savedInstanceState);
			Intent intent = this.getIntent();

			int siteId = intent.getExtras().getInt(SITE_KEY, -1);
			if (siteId == -1) {
				throw new SiteNotFoundException("UserTackerActivity called without a correct site ID!");
			}

			databaseHelper = OpenHelperManager.getHelper(this, DatabaseHelper.class);
			projectSiteDao = databaseHelper.getDao(ProjectSite.class);
			site = projectSiteDao.queryForId(siteId);

			if (site == null) {
				throw new SiteNotFoundException("The ProjectSite Id could not be found in the database!");
			}

			MultiTouchDrawable.setGridSpacing(site.getGridSpacingX(), site.getGridSpacingY());

			map = new SiteMapDrawable(this, this);
			map.setAngleAdjustment(site.getNorth());

			if (site.getWidth() == 0 || site.getHeight() == 0) {
				// the site has never been loaded
				freshSite = true;
				site.setSize(map.getWidth(), map.getHeight());
			} else {
				map.setSize(site.getWidth(), site.getHeight());
			}
			if (site.getBackgroundBitmap() != null) {
				map.setBackgroundImage(site.getBackgroundBitmap());
			}

			/*
			for (AccessPoint ap : site.getAccessPoints()) {
				new AccessPointDrawable(this, map, ap);
			}

			for (WifiScanResult wsr : site.getScanResults()) {
				new MeasuringPointDrawable(this, map, wsr);
			}
			*/

            // Preferences
            preferences = getSharedPreferences(UserTrackerActivity.SHARED_PREFS_INDOOR, MODE_PRIVATE);

			user = new UserTrack(this, map);

			if (site.getLastLocation() != null) {
				user.setRelativePosition(site.getLastLocation().getX(), site.getLastLocation().getY());
			} else {
				user.setRelativePosition(map.getWidth() / 2, map.getHeight() / 2);
			}

			LocationServiceFactory.getLocationService().setRelativeNorth(site.getNorth());
			LocationServiceFactory.getLocationService().setGridSpacing(site.getGridSpacingX(), site.getGridSpacingY());

			messageHandler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					switch (msg.what) {
					case MESSAGE_REFRESH:
						/* Refresh UI */
						if (multiTouchView != null)
							multiTouchView.invalidate();
						break;
					}
				}
			};

			//inizializza il geofences manager
			geofencesManager = GeoFencesManager.getInstance(UserTrackerActivity.this);
			
			//inizializza la UI
			initUI();
			
			//registra il receiver
			this.registerReceiver(updateUserPositionReceiver, filter);

            /*
			//start wifi service
	 		if(!isWifiServiceRunning()) {
	 			Intent wifiServiceIntent = new Intent(this, WifiService.class);
	 			bindService(wifiServiceIntent, wifiServiceConnection, Context.BIND_AUTO_CREATE);
		 		wifiServiceIntent.putExtra(SITE_KEY, siteId);
		 		this.startService(wifiServiceIntent); 
	 		}
	 		*/

            //start beacon scan service update position
            BeaconsMonitoringService.action.set(BeaconSession.UPDATE_USER_POSITION);

		} catch (Exception ex) {
			log.error("Failed to create UserTrackerActivity: " + ex.getMessage(), ex);
			Toast.makeText(this, R.string.project_site_load_failed, Toast.LENGTH_LONG).show();
			this.finish();
		}
	}

    /*
	//service connection event class
    private ServiceConnection wifiServiceConnection = new ServiceConnection() {
  	    public void onServiceConnected(ComponentName className, IBinder service) {
  	        log.debug("onWifiServiceConnected");
  	    	LocalBinder binder = (LocalBinder) service;
  	        wifiService = binder.getService();
  	    }

  	    public void onServiceDisconnected(ComponentName className) {
  	    	log.debug("onWifiServiceDisconnected");
  	    }
  	};
  	
  	//controlla se il servizio e' gia' in running
  	private boolean isWifiServiceRunning() {
  	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
  	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
  	        if (WifiService.class.getName().equals(service.service.getClassName())) {
  	            return true;
  	        }
  	    }
  	    return false;
  	}
  	*/
  	
	protected void initUI() {

		//((ToggleButton) findViewById(R.id.project_site_toggle_autorotate)).setOnClickListener(this);

		((ToggleButton) findViewById(R.id.project_site_walking)).setOnClickListener(this);
		
		//textlog = (TextView)findViewById(R.id.textlog);
		
		multiTouchView = ((MultiTouchView) findViewById(R.id.project_site_resultview));
		multiTouchView.setRearrangable(false);

		multiTouchView.addDrawable(map);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		if (databaseHelper != null) {
			OpenHelperManager.releaseHelper();
			databaseHelper = null;
		}
		
		//sospende lo scan
		//if(wifiService != null) wifiService.suspendScan();
        BeaconsMonitoringService.action.set(BeaconSession.SCANNING_OFF);
    }

	@Override
	protected void onResume() {
		
		//registra il receiver
		this.registerReceiver(updateUserPositionReceiver, filter);

		super.onResume();
		log.debug("setting context");

		multiTouchView.loadImages(this);
		map.load();
		
		//elimina le fences
		geofencesManager.deleteAllFences();
		
		//riattiva lo scan
		//if(wifiService != null) wifiService.resumeScan();
        BeaconsMonitoringService.action.set(BeaconSession.UPDATE_USER_POSITION);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {

			case R.id.project_site_walking: {

				ToggleButton button = (ToggleButton) findViewById(R.id.project_site_walking);
				
				if(button.isChecked()) {
					//if(wifiService != null) wifiService.resumeScan();
                    BeaconsMonitoringService.action.set(BeaconSession.UPDATE_USER_POSITION);
				}
				else {
					//if(wifiService != null) wifiService.suspendScan();
                    BeaconsMonitoringService.action.set(BeaconSession.SCANNING_OFF);
                }
				break;
			}
			
			/*
			case R.id.project_site_toggle_autorotate: {
	
				ToggleButton button = (ToggleButton) findViewById(R.id.project_site_toggle_autorotate);
	
				if (button.isChecked()) {
					map.startAutoRotate();
					Logger.d("Started autorotate.");
				} else {
					map.stopAutoRotate();
					Logger.d("Stopped autorotate.");
				}
				break;
			}*/
		}
	}

	@Override
	protected void onPause() {

		//unregister del receiver
		this.unregisterReceiver(updateUserPositionReceiver);

		super.onPause();
		
		multiTouchView.unloadImages();
		map.unload();
		
		//elimina le fences
		geofencesManager.deleteAllFences();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		this.setContentView(R.layout.project_site);
		initUI();
	}

	@Override
	public void invalidate() {
		if (multiTouchView != null) {
			multiTouchView.invalidate();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.user_tracker, menu);
		return true;
	}

	private ScaleRectangleDrawable scalerRectangleArea = null;
	private ScaleCircleDrawable scalerCircleArea = null;
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.chooseAlgorithm: {
				showDialog(DIALOG_CHOOSE_ALGORITHM);
				return true;
			}	
			case R.id.project_site_menu_change_scan_interval: {
				showDialog(DIALOG_CHANGE_SCAN_INTERVAL);
				return false;
			}
			case R.id.project_site_menu_rectangle_area: {
				if(scalerRectangleArea == null) {
					scalerRectangleArea = new ScaleRectangleDrawable(context, map, new OkCallback() {
						@Override
						public void onOk() {
							showDialog(DIALOG_SET_RECTANGLE_FENCE);
						}
					});
					scalerRectangleArea.getSlider(1).setRelativePosition(user.getRelativeX() - 80, user.getRelativeY() - 80);
					scalerRectangleArea.getSlider(2).setRelativePosition(user.getRelativeX() + 80, user.getRelativeY() + 80);
					multiTouchView.invalidate();
				}
				return false;
			}
			case R.id.project_site_menu_circle_area: {
				if(scalerCircleArea == null) {
					scalerCircleArea = new ScaleCircleDrawable(context, map, new OkCallback() {
						@Override
						public void onOk() {
							showDialog(DIALOG_SET_CIRCLE_FENCE);
						}
					});
					scalerCircleArea.getSlider(1).setRelativePosition(user.getRelativeX() - 80, user.getRelativeY());
					scalerCircleArea.getSlider(2).setRelativePosition(user.getRelativeX() + 80, user.getRelativeY());
					multiTouchView.invalidate();
				}
				return false;
			}
			case R.id.project_site_menu_delete_fences: {
				
				//elimina le fences
				geofencesManager.deleteAllFences();
				
				//elimina i componenti grafici dei fences
				for(ScaleRectangleDrawable srd : scaleRectangleDrawableList) map.removeSubDrawable(srd);
				scaleRectangleDrawableList.clear();
				for(ScaleCircleDrawable scd : scaleCircleDrawableList) map.removeSubDrawable(scd);
				scaleCircleDrawableList.clear();
				invalidate();
				
				return false;
			}
			case R.id.project_site_menu_check_fences: {
				//controlla i fences in base alla posizione utente attuale
				geofencesManager.notifyChangeUserPosition(
						new Location(user.getRelativeX(), user.getRelativeY()));
				return false;
			}
			default:
				return false;
		}
	}
	
	private int algorithmTempSelection = 3;

	@Override
	protected Dialog onCreateDialog(final int id) {
		switch (id) {
			case DIALOG_CHOOSE_ALGORITHM: {

                int algorithmSelection = Integer.parseInt(preferences.getString(ALGORITHM_CHOOSE_USER_TRACKER, "4"));
                algorithmTempSelection = algorithmSelection - 1;
                
				AlertDialog.Builder selectChooseAlgorithmDialog = new AlertDialog.Builder(this);
	
				final String[] algorithmsArray = getResources().getStringArray(R.array.AlgorithmsArray);
				final String[] algorithmsValues = getResources().getStringArray(R.array.AlgorithmsValues);
				
				selectChooseAlgorithmDialog.setSingleChoiceItems(
						algorithmsArray, 
						algorithmTempSelection, 
		                new DialogInterface.OnClickListener() {
		             
		            @Override
		            public void onClick(DialogInterface dialog, int which) {
		            	algorithmTempSelection = which;
		            }
		        })
		        .setCancelable(false)
			    .setPositiveButton(getString(R.string.button_ok), 
			        new DialogInterface.OnClickListener() 
			        {
			            @Override
			            public void onClick(DialogInterface dialog,  int which) {
                            preferences.edit().putString(ALGORITHM_CHOOSE_USER_TRACKER, algorithmsValues[algorithmTempSelection]).commit();
			            }
			        }
			    )
			    .setNegativeButton(getString(R.string.button_cancel), 
			        new DialogInterface.OnClickListener() 
			        {
			            @Override
			            public void onClick(DialogInterface dialog, int which) {
			            	// Canceled.
			            }
			        }
			    );
				
		        AlertDialog dialog = selectChooseAlgorithmDialog.create();
		        dialog.show();
				return dialog;
			}
			case DIALOG_CHANGE_SCAN_INTERVAL: {
				AlertDialog.Builder changeScanIntervalBuilder = new Builder(context);
				changeScanIntervalBuilder.setTitle(R.string.project_site_dialog_change_scan_interval_title);
				changeScanIntervalBuilder.setMessage(getString(R.string.project_site_dialog_change_scan_interval_message, schedulerTime));
	
				final SeekBar sb = new SeekBar(this);
				sb.setMax(60);
				sb.setProgress(schedulerTime);
	
				changeScanIntervalBuilder.setView(sb);
	
				changeScanIntervalBuilder.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						schedulerTime = sb.getProgress();
						if(schedulerTime==0) schedulerTime=1; // schedulerTime must not be 0
                        preferences.edit().putInt(SCAN_INTERVAL_USER_TRACKER, schedulerTime).commit();
					}
	
				});
	
				changeScanIntervalBuilder.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Canceled.
	
					}
				});
	
				final AlertDialog changeScanIntervalDialog = changeScanIntervalBuilder.create();
	
				sb.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
	
					@Override
					public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
						changeScanIntervalDialog.setMessage(context.getString(R.string.project_site_dialog_change_scan_interval_message, progress));
					}
	
					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
					}
	
					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
					}
	
				});
	
				return changeScanIntervalDialog;
			}
			case DIALOG_SET_RECTANGLE_FENCE: 
			case DIALOG_SET_CIRCLE_FENCE: {
				AlertDialog.Builder scaleOfMapDialog = new AlertDialog.Builder(this);

				scaleOfMapDialog.setTitle(R.string.project_site_dialog_fence_title);
				scaleOfMapDialog.setMessage(R.string.project_site_dialog_fence_message);

				// Set an EditText view to get user input
				final EditText scaleInput = new EditText(this);
				scaleInput.setSingleLine(true);
				scaleOfMapDialog.setView(scaleInput);
				scaleOfMapDialog.setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String message = scaleInput.getText().toString();
						switch (id) {
							case DIALOG_SET_RECTANGLE_FENCE: 
								Location beginPoint = scalerRectangleArea.getBeginPoint();
								Location endPoint = scalerRectangleArea.getEndPoint();
								geofencesManager.addRectangleFence(beginPoint, endPoint, message);
								scalerRectangleArea.setReadOnly();
								scaleRectangleDrawableList.add(scalerRectangleArea);
								scalerRectangleArea = null;
								invalidate();
								scaleInput.setText("");
								break;
							case DIALOG_SET_CIRCLE_FENCE:
								Location circleCenter = scalerCircleArea.getCenter();
								float radius = scalerCircleArea.getRadius();
								geofencesManager.addCircleFence(circleCenter, radius, message);
								scalerCircleArea.setReadOnly();
								scaleCircleDrawableList.add(scalerCircleArea);
								scalerCircleArea = null;
								invalidate();
								scaleInput.setText("");
								break;
						}
					}
				});

				scaleOfMapDialog.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Canceled.
					}
				});

				return scaleOfMapDialog.create();
			}
			default: {
				return super.onCreateDialog(id);
			}
		}
	}
	
	//visualizza un messagio
	private void message(final String str, final int duration) {
		if(str.trim().equals("")) return;
		Toast.makeText(UserTrackerActivity.this.getApplicationContext(), str, duration).show();
	}
	
	public BroadcastReceiver updateUserPositionReceiver = new BroadcastReceiver(){
        
        @Override
        public void onReceive(Context c, Intent i) {
        	float x = i.getFloatExtra("x", 0);
        	float y = i.getFloatExtra("y", 0);
        	
        	user.setRelativePosition(x, y);
  			//messageHandler.sendEmptyMessage(MESSAGE_REFRESH);
  			invalidate();
  			
  			//notifica ai geofences il cambio di posizione utente
  			geofencesManager.notifyChangeUserPosition(new Location(x, y));
  			
  			//LOG posizione dell'utente
  			//message(String.format("device position:%.2f/%.2fm", x/MultiTouchDrawable.getGridSpacingX(), y/MultiTouchDrawable.getGridSpacingY()), Toast.LENGTH_SHORT);
        }
	};
	
	@Override
	public void onGeoFencesNotify(String message) {
		//visualizza una finestra di dialogo notificando l'allarme
		alertDialog = new AlertDialog.Builder(UserTrackerActivity.ctx).create();
		alertDialog.setTitle("Fences alert");
		alertDialog.setMessage(message);
		alertDialog.setIcon(R.drawable.tick);
		alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int which) {
	        	dialog.cancel();
	        }
		});
		alertDialog.show();
	}
}
