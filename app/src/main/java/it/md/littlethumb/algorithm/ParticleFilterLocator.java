package it.md.littlethumb.algorithm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import it.md.littlethumb.activities.UserTrackerActivity;
import it.md.littlethumb.model.Location;
import it.md.littlethumb.algorithm.model.AccessPointResult;
import it.md.littlethumb.algorithm.model.Particle;

public class ParticleFilterLocator {

    //shared preferences
    public static SharedPreferences preferences;

    //algoritmo selezionato
    private int algorithmSelection = 0;

	private AtomicBoolean isActive = new AtomicBoolean(false);
	private Context context;

	//paramenters
	private static final int FPS = 10;
	private static final int NUM_PARTICLES = 100;
	private static final int R_WALK_MAX = 50;
	private static final int R_WALK_FREQUENCY = 5;
	private static final double JUMP_DISTANCE = 40;
	
	//particles
	private List<Particle> particles = null;
	
	//access point list
	private List<AccessPointResult> accessPointList;
	
	private int curFrame;
	private double maxDist;

	//trilateration position
	private double xPos;
	private double yPos;
	
	//moving delta
	private double movedX;
	private double movedY;
	
	//estimate posizion
	private double estimateX;
	private double estimateY;
	
	//weight estimate position
	private double estimateWX;
	private double estimateWY;
	
	//private constructor
	private ParticleFilterLocator(Context ctx, Map<String, AccessPointResult> accessPointMap, double maxRangeWidth, double maxRangeHeight, int algorithmSelection) {

        //salva il context
        this.context = ctx;

        this.algorithmSelection = algorithmSelection;

        //ottiene le preferences
        preferences = ctx.getSharedPreferences(UserTrackerActivity.SHARED_PREFS_INDOOR, ctx.MODE_PRIVATE);

		//trasforma la mappa in lista
		accessPointList = new ArrayList<AccessPointResult>(accessPointMap.values());
		
		//ordina in base al level
		Collections.sort(accessPointList, new Comparator<AccessPointResult>() {
            @Override
            public int compare(AccessPointResult apr1, AccessPointResult apr2) {
            	return (apr1.getLevel() > apr2.getLevel() ? -1 : (apr1.getLevel() == apr2.getLevel() ? 0 : 1));
            }
        }); 
		
		maxDist = Math.floor(Math.sqrt(maxRangeWidth*maxRangeWidth + maxRangeHeight*maxRangeHeight));
		
		xPos = Math.floor(Math.random()*maxRangeWidth);
		yPos = Math.floor(Math.random()*maxRangeHeight);
		
		movedX = 0;
		movedY = 0;
		estimateX = 0;
		estimateY = 0;
		estimateWX = 0;
		estimateWY = 0;
		curFrame = -1;
		
		// set up particles
		particles = new ArrayList<Particle>();
		for(int i = 0; i < NUM_PARTICLES; i++){
			Particle p = new Particle();
			p.randomize(maxRangeWidth, maxRangeHeight);
			particles.add(p);
		}
	}
	
	//singleton constructor
	private static ParticleFilterLocator instance = null;
	public static ParticleFilterLocator getInstance(Context ctx, Map<String, AccessPointResult> accessPointMap, double maxRangeWidth, double maxRangeHeight, int algorithmSelection) {
		if(instance == null) {
			instance = new ParticleFilterLocator(ctx, accessPointMap, maxRangeWidth, maxRangeHeight, algorithmSelection);
		}
		
		return instance;
	}
	
	/**
	 * update user position witha new trilateratio position
	 * @param xPos
	 * @param yPos
	 */
	public void updatePosition(double xPos, double yPos) {
		
		this.movedX = xPos - this.xPos;
		this.movedY = yPos - this.yPos;
		
		// if mouse moved too much, consider it a jump (a jump will not update the
		//	movement of the particles)
		if(Math.abs(this.movedX) > JUMP_DISTANCE || Math.abs(this.movedY) > JUMP_DISTANCE)
			this.movedX = this.movedY = 0;
		
		this.xPos = xPos;
		this.yPos = yPos;
		
		System.out.println("-> User position: "+this.xPos+" - "+this.yPos);
	}

    /**
     * return if the service is running
     * @return
     */
    public boolean inRunning() {
        return isActive.get();
    }

    /**
	 * start thread update position
	 */
	public void start() {
		if(isActive.get()) return;
		isActive.set(true);

		new Thread(new Runnable() {
            @Override
            public void run() {
				while(isActive.get()) 
				{
                    //applica il filtro ed invia il broadcast
					applyFilter();
					sendBroadcastUserPosition();

                    //controllo di disattivazione servizio ogni 500 frames
                    if((curFrame % 500) == 0) {
                        if(algorithmSelection == Integer.parseInt(preferences.getString(UserTrackerActivity.ALGORITHM_CHOOSE_USER_TRACKER, "0"))) {
                            isActive.set(false);
                        }
                    }

					try {
						Thread.sleep(1000 / FPS);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();

	}

    /**
	 * stop thread update position
	 */
	public void stop() {
		if(!isActive.get()) return;
		isActive.set(false);
	}
	
	/**
	 * Particle Filter: run the actual particle filter algorithm here
	 */
	public void applyFilter() {
		
		//increment frame counter
		this.curFrame += 1;
		
		// 0. approximate robot position using current particles
		double totalX = 0;
		double totalY = 0;
		double totalWX = 0;
		double totalWY = 0;
		double totalW = 0;
		for(int i = 0; i < particles.size(); i++){
			totalX += particles.get(i).x;
			totalY += particles.get(i).y;
			double weight = particles.get(i).getWeight();
			totalWX += (weight * particles.get(i).x);
			totalWY += (weight * particles.get(i).y);
			totalW += weight;
		}
		// direct average location of all particles
		this.estimateX = Math.floor(totalX / particles.size());
		this.estimateY = Math.floor(totalY / particles.size());
		// weighted average of all particles
		this.estimateWX = Math.floor(totalWX / totalW);
		this.estimateWY = Math.floor(totalWY / totalW);
		
		// 1. if mouse moved (i.e. the "agent" moved), update all particles
		//	by the same amount as the mouse movement
		if(this.movedX != 0 || this.movedY != 0) {
			for(int i = 0; i < particles.size(); i++) {
				particles.get(i).x += this.movedX;
				particles.get(i).y += this.movedY;
			}
		}

		// 2. do a random walk if on random walk frame
		if(R_WALK_FREQUENCY != 0 && (this.curFrame % R_WALK_FREQUENCY) == 0) {
			for(int i = 0; i < particles.size(); i++){
				double dX = Math.floor(Math.random() * (R_WALK_MAX+1)) - R_WALK_MAX/2;
				double dY = Math.floor(Math.random() * (R_WALK_MAX+1)) - R_WALK_MAX/2;
				particles.get(i).x += dX;
				particles.get(i).y += dY;
			}
		}

		// 3. estimate weights of every particle
		double maxWeight = 0;
		for(int i = 0; i < particles.size(); i++){
			double weightSum = 0;
			for(int j = 0; j < accessPointList.size(); j++){
				// get distance to beacon of both the particle and the robot
				Location apLocation = accessPointList.get(j).getLocation();
				double userDistToBeacon = distance(this.xPos, this.yPos, apLocation.getX(), apLocation.getY());
				double particleDistToBeacon = distance(particles.get(i).x, particles.get(i).y, apLocation.getX(), apLocation.getY());
				weightSum += getWeight(userDistToBeacon, particleDistToBeacon);
			}
			double weight = weightSum / accessPointList.size();
			particles.get(i).degrade(weight);
			if(weight > maxWeight)
				maxWeight = weight;
		}

		// 4. normalize weights
		double weightSum = 0;
		for(int i = 0; i < particles.size(); i++){
			particles.get(i).normalize(maxWeight);
			weightSum += particles.get(i).getWeight();
		}

		// 5. resample: pick each particle based on probability
		List<Particle> newParticles = new ArrayList<Particle>();
		int numParticles = particles.size();
		for(int i = 0; i < numParticles; i++){
			double choice = Math.random() * weightSum;
			int index = -1;
			do {
				index++;
				choice -= particles.get(index).getWeight();
			} while(choice > 0);
			newParticles.add(particles.get(index).clone());
		}
		particles = newParticles;

		// clear any movedX, movedY values
		this.movedX = 0;
		this.movedY = 0;
	}
	
	/**
	 * Distance Formula: returns the distance between two given points
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return
	 */
	private double distance(double x1, double y1, double x2, double y2){
		double distX = x1-x2;
		double distY = y1-y2;
		return Math.floor(Math.sqrt(distX*distX + distY*distY));
	}

	/**
	 * computes distance weight between a
	 * @param robotDistToBeacon
	 * @param particleDistToBeacon
	 * @return
	 */
	private double getWeight(double robotDistToBeacon, double particleDistToBeacon){
		double diff = Math.abs(robotDistToBeacon - particleDistToBeacon);
		return (maxDist - diff) / maxDist;
	}

	/**
	 * send a broadcast with a new user position
	 */
	public void sendBroadcastUserPosition() {
		Intent broadcastReceiverIntent = new Intent();
        broadcastReceiverIntent.putExtra("x", (float)estimateWX);
        broadcastReceiverIntent.putExtra("y", (float)estimateWY);
        broadcastReceiverIntent.setAction("android.intent.action.UPDATE_USER_POSITION");
        context.sendBroadcast(broadcastReceiverIntent);
		
		System.out.println(String.format("posizione: %s - %s", estimateWX, estimateWY));
	}
	
	public static void main(String[] args) {
		
		double frameWidth  = 860;
		double frameHeight = 540;
		
		Map<String, AccessPointResult> accessPointMap = new HashMap<String, AccessPointResult>();
		AccessPointResult apr = new AccessPointResult();
		apr.setLocation(new Location());
		apr.getLocation().setX(20);
		apr.getLocation().setY(20);
		accessPointMap.put("BEACON1", apr);
		apr = new AccessPointResult();
		apr.setLocation(new Location());
		apr.getLocation().setX((float)frameWidth/2);
		apr.getLocation().setY(20);
		accessPointMap.put("BEACON2", apr);
		apr = new AccessPointResult();
		apr.setLocation(new Location());
		apr.getLocation().setX((float)frameWidth-20);
		apr.getLocation().setY(20);
		accessPointMap.put("BEACON3", apr);
		apr = new AccessPointResult();
		apr.setLocation(new Location());
		apr.getLocation().setX(20);
		apr.getLocation().setY((float)frameHeight-20);
		accessPointMap.put("BEACON4", apr);
		apr = new AccessPointResult();
		apr.setLocation(new Location());
		apr.getLocation().setX((float)frameWidth/2);
		apr.getLocation().setY((float)frameHeight-20);
		accessPointMap.put("BEACON5", apr);
		apr = new AccessPointResult();
		apr.setLocation(new Location());
		apr.getLocation().setX((float)frameWidth-20);
		apr.getLocation().setY((float)frameHeight-20);
		accessPointMap.put("BEACON6", apr);

		//crea il filtro
		ParticleFilterLocator pfl = ParticleFilterLocator.getInstance(null, accessPointMap, frameWidth, frameHeight, 9);
		
		//posizione trilaterata dell'utente
		double xPos = 100;
		double yPos = 100;
		
		//start del filtro
		pfl.updatePosition(xPos, yPos);
		pfl.start();
		try {
			Thread.sleep(1000 * 20);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		xPos += 50;
		pfl.updatePosition(xPos, yPos);
		try {
			Thread.sleep(1000 * 20);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		xPos += 50;
		yPos += 80;
		pfl.updatePosition(xPos, yPos);
		try {
			Thread.sleep(1000 * 20);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		pfl.stop();
	}
}
