package it.md.littlethumb.algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;

import it.md.littlethumb.Logger;
import it.md.littlethumb.model.BssidResult;
import it.md.littlethumb.model.Location;
import it.md.littlethumb.model.ProjectSite;
import it.md.littlethumb.model.WifiScanResult;
import it.md.littlethumb.algorithm.model.LocDistance;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class FingerprintLocator {

	protected Logger log = new Logger(FingerprintLocator.class);
	
	private static double ID_POS_CONTRIBUTION = 1;
	private static double ID_NEG_CONTRIBUTION = -0.4;
	
	public static double SIGNAL_CONTRIBUTION = 1;
	public static double SIGNAL_PENALTY_THRESHOLD = 10;
	public static double SIGNAL_GRAPH_LEVELING = 0.2;
	
	/* accuracy level */
	public static final int LOCATION_KNOWN = 10;
	public static final int LOCATION_UNKNOWN = 0;
	public static int LOCATION_THRESHOLD = 0; //dovrebbe essere 2
	public static int MIN_LEVEL_THRESHOLD = -85;
	
	public static boolean debug = false;
	
	private Collection<WifiScanResult> list;
	
	public FingerprintLocator(ProjectSite site) {
		list = site.getScanResults();
	}
	
	public Location locate(WifiScanResult currentMeasurement) {
		
		Location loc = null;
		
		//check for similarity
		TreeSet<WifiScanResult> hits = new TreeSet<WifiScanResult>(new MeasurementComparator(currentMeasurement));
		
		for(WifiScanResult m : list) {
			int level = measurementSimilarityLevel(m, currentMeasurement);
			
			if(level > LOCATION_THRESHOLD) {
				hits.add(m);
			}
		}
		
		if (hits.size() > 0) {
			
			WifiScanResult bestMatch = hits.first();
			
			loc = (Location) bestMatch.getLocation();
			loc.setAccurancy(measurementSimilarityLevel(bestMatch, currentMeasurement));
		}
		
		return loc;
	}
	

	private int measurementSimilarityLevel(WifiScanResult t, WifiScanResult o) {

		/* total amount of credit that can be achieved */
		double totalCredit = 0;

		/* account that holds the achieved credit */
		double account = 0;

		/* counts the nr of positive matches of reading ID's */
		int matches;

		/*
		 * holds the max nr of measured readings. max of reference measurement
		 * and current measurement
		 */
		int readings;
		
		Collection<BssidResult> this_vect = (t.getBssids()!=null ? t.getBssids() : t.getTempBssids());
		Collection<BssidResult> other_vect = (o.getBssids()!=null ? o.getBssids() : o.getTempBssids());
		
		matches = 0;
		
		for (BssidResult this_wifi : this_vect) {
			//if(this_wifi.getLevel() < MIN_LEVEL_THRESHOLD) continue;
			
			for (BssidResult other_wifi : other_vect) {
				//if(other_wifi.getLevel() < MIN_LEVEL_THRESHOLD) continue;
				
				/*
				 * bssid match: add ID contribution and signal strength
				 * contribution
				 */
				if (this_wifi != null && this_wifi.getBssid() != null && other_wifi != null && other_wifi.getBssid() != null && this_wifi.getBssid().equals(other_wifi.getBssid())) {
										
					account += ID_POS_CONTRIBUTION;
					account += signalContribution(this_wifi.getLevel(), other_wifi.getLevel());
					matches++;
				}
			}
		}

		/*
		 * penalty if for each net that did not match
		 */
		readings = Math.max(this_vect.size(), other_vect.size());
		account += (readings - matches) * ID_NEG_CONTRIBUTION;
				
		/*
		 * get the total credit for this measurement.
		 */
		totalCredit += this_vect.size() * ID_POS_CONTRIBUTION;
		
		totalCredit += this_vect.size() * SIGNAL_CONTRIBUTION;
		

		/* get accuracy level defined by bounds */
		int factor = LOCATION_KNOWN	- LOCATION_UNKNOWN;

		/* a negative account results immediately in accuracy equals zero */
		int accuracy = 0;
		if (account > 0) {
			/*
			 * compute percentage of account from totalCredit -> [0,1]; stretch
			 * by accuracy span -> [0,MAX]; and in case min accuracy would not
			 * be zero, add this offset
			 */
			double a = (account / totalCredit) * factor + LOCATION_UNKNOWN;

			/* same as Math.round */
			accuracy = (int) Math.floor(a + 0.5d);
		}

		return accuracy;
	}

	private Boolean measurmentAreSimilar(WifiScanResult t, WifiScanResult o) {

		if (measurementSimilarityLevel(t, o) > LOCATION_THRESHOLD) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * computes the credit contributed by the received signal strength of any
	 * wireless scan
	 */
	private double signalContribution(double rssi1, double rssi2) {

		/*
		 * we take the reference value of the rssi as base for further
		 * computations
		 */
		double base = rssi1;

		//LOG
		log.debug("  Base: " + base);

		/*
		 * in order that +20 and -20 dB are treated the same, the penalty
		 * function uses the difference of the rssi's.
		 */
		double diff = Math.abs(rssi1 - rssi2);

		//LOG
		log.debug("  Diff: " + diff);
		
		/* get percentage of error */
		double x = diff / base;

		/* prevent division by zero */
		if (x > 0.0) {
			/*
			 * small error should result in a high contribution, big error in a
			 * small -> reciprocate (1/x) MIN = 1, MAX = infinity
			 */
			double y = 1 / x;

			/*
			 * compute percentage of treshold regarding the current base
			 */
			double t = SIGNAL_PENALTY_THRESHOLD / base;

			/*
			 * shift down the resulting graph. the root (zero) will then be
			 * exactly at x = treshold for every base, e.g. measurement, and
			 * signal differences above the treshold will result in a negative
			 * contribution
			 */
			y -= 1 / t;

			/*
			 * graph increases fast, so that a difference of 15 still results in
			 * a maximal contribution. With this adjustment, the graph gets flat
			 * and this has also an impact on the penalty (difference to big)
			 */
			y = y * SIGNAL_GRAPH_LEVELING;

			if ((-1 * SIGNAL_CONTRIBUTION <= y) && (y <= SIGNAL_CONTRIBUTION)) {

				return y;
			} else {

				/* don't exceed the max possible credits/penalty */
				return SIGNAL_CONTRIBUTION;
			}
		} else {

			return SIGNAL_CONTRIBUTION;
		}
	}
	
	
	//comparator class
	class MeasurementComparator implements Comparator<WifiScanResult> {

		private WifiScanResult basisMeasurement;
		
		public MeasurementComparator(WifiScanResult m) {
			basisMeasurement = m;
		}
		
		public WifiScanResult getBasisMeasurement() {
			return basisMeasurement;
		}

		public void setBasisMeasurement(WifiScanResult basisMeasurement) {
			this.basisMeasurement = basisMeasurement;
		}

		@Override
		public int compare(WifiScanResult arg0, WifiScanResult arg1) {
			int a1 = measurementSimilarityLevel(basisMeasurement, arg0);
			int a2 = measurementSimilarityLevel(basisMeasurement, arg1);
			
			if (a1 == a2) {
				long t1 = arg0.getTimestamp();
				long t2 = arg1.getTimestamp();
				if (t1 == t2) {
					return 0;
				} else {
					if (t1 < t2) {
						return 1;
					} else {
						return -1;
					}
				}
			} else {
				if (a1 < a2) {
					return 1;
				} else {
					return -1;
				}
			}
		}
	}
	
	//Airplace Code
	public Location locateWithAlgorithms(WifiScanResult currentResult, int algorithm_choice) {
		
		int KNN = 1;
		int WKNN = 1;
		float MAP = 1.0f;
		float MMSE = 2.0f;
		
		switch (algorithm_choice) {
			case 1:
				return KNN_WKNN_Algorithm(currentResult, KNN, false);
			case 2:
				return KNN_WKNN_Algorithm(currentResult, WKNN, true);
			case 3:
				return MAP_MMSE_Algorithm(currentResult, MAP, false);
			case 4:
				return MAP_MMSE_Algorithm(currentResult, MMSE, true);
			default:
				return null;
		}
	}
	
	private Location KNN_WKNN_Algorithm(WifiScanResult currentResult, int K, boolean isWeighted) {

		float curResult = 0;
		ArrayList<LocDistance> locDistanceResultsList = new ArrayList<LocDistance>();
		Location myLocation = null;

		// Construct a list with locations-distances pairs for currently
		// observed RSS values
		for (WifiScanResult fingerPrintResult : list) {
				
			curResult = calculateEuclideanDistance(fingerPrintResult, currentResult);
			
			if (curResult == Float.NEGATIVE_INFINITY)
				return null;

			locDistanceResultsList.add(0, new LocDistance(curResult, fingerPrintResult.getLocation()));
		}

		// Sort locations-distances pairs based on minimum distances
		Collections.sort(locDistanceResultsList, new Comparator<LocDistance>() {

			public int compare(LocDistance gd1, LocDistance gd2) {
				return (gd1.getDistance() > gd2.getDistance() ? 1 : (gd1.getDistance() == gd2.getDistance() ? 0 : -1));
			}
		});

		if (!isWeighted) {
			myLocation = calculateAverageKDistanceLocations(locDistanceResultsList, K);
		} else {
			myLocation = calculateWeightedAverageKDistanceLocations(locDistanceResultsList, K);
		}

		return myLocation;

	}

	private Location MAP_MMSE_Algorithm(WifiScanResult currentResult, float sGreek, boolean isWeighted) {

		double curResult = 0.0d;
		Location myLocation = null;
		double highestProbability = Double.NEGATIVE_INFINITY;
		ArrayList<LocDistance> locDistanceResultsList = new ArrayList<LocDistance>();

		// Find the location of user with the highest probability
		for (WifiScanResult fingerPrintResult : list) {
				
			curResult = calculateProbability(fingerPrintResult, currentResult, sGreek);
			
			if (curResult == Double.NEGATIVE_INFINITY)
				return null;
			else if (curResult > highestProbability) {
				highestProbability = curResult;
				myLocation = fingerPrintResult.getLocation();
			}

			if (isWeighted)
				locDistanceResultsList.add(0, new LocDistance(curResult, fingerPrintResult.getLocation()));
		}
		
		if (isWeighted)
			myLocation = calculateWeightedAverageProbabilityLocations(locDistanceResultsList);

		return myLocation;
	}

	private float calculateEuclideanDistance(WifiScanResult fingerPrintResult, WifiScanResult currentResult) {

		float finalResult = 0;
		float v1;
		float v2;
		float temp;

		for(BssidResult curBssidResult : currentResult.getTempBssids()) {
			//if(curBssidResult.getLevel() < MIN_LEVEL_THRESHOLD) continue;
			for(BssidResult fpBssidResult : fingerPrintResult.getBssids()) {
				//if(fpBssidResult.getLevel() < MIN_LEVEL_THRESHOLD) continue;
				if (curBssidResult != null && curBssidResult.getBssid() != null 
				&& fpBssidResult != null && fpBssidResult.getBssid() != null 
				&& curBssidResult.getBssid().equals(fpBssidResult.getBssid())) {
					
					v1 = curBssidResult.getLevel();
					v2 = fpBssidResult.getLevel();
					
					// do the procedure
					temp = v1 - v2;
					temp *= temp;

					// do the procedure
					finalResult += temp;
				}
			}
		}
		
		return ((float) Math.sqrt(finalResult));
	}

	public double calculateProbability(WifiScanResult fingerPrintResult, WifiScanResult currentResult, float sGreek) {

		double finalResult = 1;
		float v1;
		float v2;
		double temp;

		for(BssidResult curBssidResult : currentResult.getTempBssids()) {
			//if(curBssidResult.getLevel() < MIN_LEVEL_THRESHOLD) continue;
			for(BssidResult fpBssidResult : fingerPrintResult.getBssids()) {
				//if(fpBssidResult.getLevel() < MIN_LEVEL_THRESHOLD) continue;
				if (curBssidResult != null && curBssidResult.getBssid() != null 
				&& fpBssidResult != null && fpBssidResult.getBssid() != null 
				&& curBssidResult.getBssid().equals(fpBssidResult.getBssid())) {
					
					v1 = curBssidResult.getLevel();
					v2 = fpBssidResult.getLevel();
					
					temp = v1 - v2;

					temp *= temp;

					temp = -temp;

					temp /= (double) (sGreek * sGreek);
					temp = (double) Math.exp(temp);

					finalResult *= temp;
				}
			}
		}
				
		return finalResult;
	}

	private Location calculateAverageKDistanceLocations(ArrayList<LocDistance> LocDistance_Results_List, int K) {

		float sumX = 0.0f;
		float sumY = 0.0f;
		float x, y;

		int K_Min = K < LocDistance_Results_List.size() ? K : LocDistance_Results_List.size();

		// Calculate the sum of X and Y
		for (int i = 0; i < K_Min; ++i) {
			Location location = LocDistance_Results_List.get(i).getLocation();

			try {
				x = location.getX();
				y = location.getY();
			} catch (Exception e) {
				return null;
			}

			sumX += x;
			sumY += y;
		}

		// Calculate the average
		sumX /= K_Min;
		sumY /= K_Min;

		return new Location(sumX, sumY);
	}

	public Location calculateWeightedAverageKDistanceLocations(ArrayList<LocDistance> LocDistance_Results_List, int K) {

		double LocationWeight = 0.0f;
		double sumWeights = 0.0f;
		double WeightedSumX = 0.0f;
		double WeightedSumY = 0.0f;
		float x, y;

		int K_Min = K < LocDistance_Results_List.size() ? K : LocDistance_Results_List.size();

		// Calculate the weighted sum of X and Y
		for (int i = 0; i < K_Min; ++i) {

			LocationWeight = 1 / LocDistance_Results_List.get(i).getDistance();
			Location location = LocDistance_Results_List.get(i).getLocation();

			try {
				x = location.getX();
				y = location.getY();
			} catch (Exception e) {
				return null;
			}

			sumWeights += LocationWeight;
			WeightedSumX += LocationWeight * x;
			WeightedSumY += LocationWeight * y;

		}

		WeightedSumX /= sumWeights;
		WeightedSumY /= sumWeights;

		return new Location((float)WeightedSumX, (float)WeightedSumY);
	}

	public Location calculateWeightedAverageProbabilityLocations(ArrayList<LocDistance> LocDistance_Results_List) {

		double sumProbabilities = 0.0f;
		double WeightedSumX = 0.0f;
		double WeightedSumY = 0.0f;
		double NP;
		float x, y;

		// Calculate the sum of all probabilities
		for (int i = 0; i < LocDistance_Results_List.size(); ++i)
			sumProbabilities += LocDistance_Results_List.get(i).getDistance();

		// Calculate the weighted (Normalized Probabilities) sum of X and Y
		for (int i = 0; i < LocDistance_Results_List.size(); ++i) {
			Location location = LocDistance_Results_List.get(i).getLocation();

			try {
				x = location.getX();
				y = location.getY();
			} catch (Exception e) {
				return null;
			}

			NP = LocDistance_Results_List.get(i).getDistance() / sumProbabilities;

			WeightedSumX += (x * NP);
			WeightedSumY += (y * NP);

		}
		
		return new Location((float)WeightedSumX, (float)WeightedSumY);
	}
}
