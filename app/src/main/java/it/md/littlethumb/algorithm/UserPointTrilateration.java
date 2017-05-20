package it.md.littlethumb.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import Jama.Matrix;
import android.graphics.PointF;

import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;

import it.md.littlethumb.ToolBox;
import it.md.littlethumb.algorithm.helper.NonLinearLeastSquaresSolver;
import it.md.littlethumb.algorithm.helper.TrilaterationFunction;
import it.md.littlethumb.algorithm.model.AccessPointResult;
import it.md.littlethumb.algorithm.model.MeasurementDataSet;
import it.md.littlethumb.view.MultiTouchDrawable;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class UserPointTrilateration {

	private List<AccessPointResult> accessPointList;
	protected static float windowSize = 2;
	protected static float g = 1.3f;

	//private Map<String, AccessPointResult> accessPointList;
	//public UserPointTrilateration(Map<String, AccessPointResult> accessPointList) {
	//	this.accessPointList = accessPointList;
	//}
	
	public UserPointTrilateration(Map<String, AccessPointResult> accessPointMap) {

		//trasforma la mappa in lista
		accessPointList = new ArrayList<AccessPointResult>(accessPointMap.values());
		
		//ordina in base al level
		Collections.sort(accessPointList, new Comparator<AccessPointResult>() {
            @Override
            public int compare(AccessPointResult apr1, AccessPointResult apr2) {
            	return (apr1.getLevel() > apr2.getLevel() ? -1 : (apr1.getLevel() == apr2.getLevel() ? 0 : 1));
            }
        }); 
	}
	
	//PRIMO METODO
	
	public PointF calculateUserPosition() {
		if (accessPointList.size() >= 3) {
			Vector<MeasurementDataSet> data = new Vector<MeasurementDataSet>();

			float sumRssi = 0;
			
			//for(AccessPointResult accessPoint: accessPointList.values()) {
			for(AccessPointResult accessPoint: accessPointList) {

				float newRssi = (float) Math.pow(Math.pow(10, accessPoint.getLevel() / 20), g);
				sumRssi += newRssi;
				
				data.add(new MeasurementDataSet(accessPoint.getLocation().getX(), accessPoint.getLocation().getY(), newRssi));
			}

			float x = 0;
			float y = 0;
			
			for (Iterator<MeasurementDataSet> itd = data.iterator(); itd.hasNext();) {
				MeasurementDataSet dataSet = itd.next();
				
				float weight = dataSet.getRssi() / sumRssi;
				x += dataSet.getX() * weight;
				y += dataSet.getY() * weight;
			}
			
			return new PointF(x, y);
		} else {
			return null;
		}
	}

	//SECONDO METODO
	
	public PointF calculateGradientUserPosition() {

		Vector<GradientArrow> arrows = new Vector<GradientArrow>();
		Vector<MeasurementDataSet> data = new Vector<MeasurementDataSet>();

		if (accessPointList.size() >= 3) {

			float sumRssi = 0;

			//for(AccessPointResult accessPoint: accessPointList.values()) {
			for(AccessPointResult accessPoint: accessPointList) {
			//for (Iterator<MeasurementDataSet> it = originalData.iterator(); it
			//		.hasNext();) {
			//	MeasurementDataSet dataSet = it.next();

				float newRssi = (float) Math.pow(Math.pow(10, accessPoint.getLevel() / 20), g);
				sumRssi += newRssi;

				data.add(new MeasurementDataSet(accessPoint.getLocation().getX(), accessPoint.getLocation().getY(),
						newRssi));
			}

			for (Iterator<MeasurementDataSet> it = data.iterator(); it
					.hasNext();) {
				MeasurementDataSet currentDataSet = it.next();

				Vector<MeasurementDataSet> dataSetsWithinWindow = this
						.getMeasurementDataWithinWindow(data, currentDataSet,
								windowSize);

				// Make sure we can fit a plane, so we need at least 3 points.
				// And make sure the measurement points are not collinear
				if (dataSetsWithinWindow.size() >= 3
						&& !areAllMeasurementsCollinear(dataSetsWithinWindow)) {

					Matrix matrixA = new Matrix(dataSetsWithinWindow.size(), 3);
					Matrix matrixZ = new Matrix(dataSetsWithinWindow.size(), 1);

					for (int i = 0; i < dataSetsWithinWindow.size(); i++) {
						MeasurementDataSet dataSet = dataSetsWithinWindow
								.get(i);
						matrixA.set(i, 0, dataSet.getX());
						matrixA.set(i, 1, dataSet.getY());
						matrixA.set(i, 2, 1);

						matrixZ.set(i, 0, dataSet.getRssi());
					}

					// Least squares solution
					Matrix matrixC = matrixA.solve(matrixZ);

					float weight = currentDataSet.rssi / sumRssi;
					// Logger.d("Weight: " + weight + " (current RSSI: "
					// + currentDataSet.rssi + ", sum: " + sumRssi + ")");

					GradientArrow arrow = new GradientArrow(currentDataSet.x,
							currentDataSet.y, (float) matrixC.get(0, 0),
							(float) matrixC.get(1, 0), weight);
					arrows.add(arrow);
				}
			}

			// Steps (in meters) by which the area is divided in order to
			// calculate the heat map
			float step = 1.0f * MultiTouchDrawable.getGridSpacingX();
			float areaFactor = 2.0f; // Factor by which the area is expanded

			float minX = roundToGridSpacing(MeasurementDataSet.getMinimumValue(
					data, MeasurementDataSet.VALUE_X));
			float maxX = roundToGridSpacing(MeasurementDataSet.getMaximumValue(
					data, MeasurementDataSet.VALUE_X));
			float minY = roundToGridSpacing(MeasurementDataSet.getMinimumValue(
					data, MeasurementDataSet.VALUE_Y));
			float maxY = roundToGridSpacing(MeasurementDataSet.getMaximumValue(
					data, MeasurementDataSet.VALUE_Y));

			float centerX = (maxX - minX) / 2.0f + minX;
			float dX = Math.abs(maxX - centerX);
			float centerY = (maxY - minY) / 2.0f + minY;
			float dY = Math.abs(maxY - centerY);

			float areaMinX = centerX - dX * areaFactor;
			float areaMaxX = centerX + dX * areaFactor;
			float areaMinY = centerY - dY * areaFactor;
			float areaMaxY = centerY + dY * areaFactor;

			float sumP = 0;
			Vector<MeasurementDataSet> probabilities = new Vector<MeasurementDataSet>();

			for (float x = areaMinX; x <= areaMaxX; x += step) {
				for (float y = areaMinY; y <= areaMaxY; y += step) {
					for (GradientArrow arrow : arrows) {
						float arrowAngle = ToolBox.normalizeAngle((float) Math
								.atan2(arrow.directionY, arrow.directionX));
						float pointAngle = ToolBox.normalizeAngle((float) Math
								.atan2(y - arrow.y, x - arrow.x));

						float angleDifference = Math.abs(arrowAngle
								- pointAngle);

						// Always use the minimum difference of the two angles
						// (190 degrees would be 170 then)
						if (angleDifference > Math.PI)
							angleDifference = (float) (2 * Math.PI - angleDifference);

						// Logger.d("Angle difference: " + x + ", " + y + ", " +
						// angleDifference);

						sumP += (float) Math.pow(angleDifference, 2)
								* arrow.weight;
					}

					probabilities.add(new MeasurementDataSet(x, y, sumP));
					sumP = 0.0f;
				}
				// Logger.d("Done with " + (x - areaMinX) / (areaMaxX -
				// areaMinX) * 100 + " %");
			}

			float minProb = 0.0f;
			float minProbX = 0.0f;
			float minProbY = 0.0f;

			for (MeasurementDataSet probability : probabilities) {

				// Logger.d("Probability: x(" + probability.getX() + "), y(" +
				// probability.getY() + "), prob(" + probability.getRssi() +
				// ")");

				if ((probabilities.indexOf(probability) == 0)
						|| (probability.getRssi() < minProb)) {
					minProb = probability.getRssi();
					minProbX = probability.getX();
					minProbY = probability.getY();
				}
			}

			return new PointF(minProbX, minProbY);
		} else {
			return null;
		}
	}
	
	protected float roundToGridSpacing(float value) {
		return Math.round(value / MultiTouchDrawable.getGridSpacingX())
				* MultiTouchDrawable.getGridSpacingX();
	}
	
	/**
	 * Returns whether a set of points is collinear, or in other words, in one
	 * line.
	 * 
	 * @return <b>true</b> if the points are collinear, <b>false</b> if not
	 */
	protected boolean areAllMeasurementsCollinear(
			Vector<MeasurementDataSet> measurements) {

		if (measurements.size() < 3) {
			return true;
		} else {
			PointF p1 = measurements.get(0).getPointF();
			PointF p2 = measurements.get(1).getPointF();

			for (int i = 2; i < measurements.size(); i++)
				if (!arePointsCollinear(p1, p2, measurements.get(i).getPointF()))
					return false;

			return true;
		}
	}
	
	/**
	 * Returns a vector of all points that are located within the statically
	 * configured window.
	 * 
	 * @param data
	 *            The data to search
	 * @param dataSet
	 *            The center point
	 * @param windowSize
	 *            Half the size of the square to search
	 * @return A vector of all points that are inside the window
	 */
	protected Vector<MeasurementDataSet> getMeasurementDataWithinWindow(
			Vector<MeasurementDataSet> data, MeasurementDataSet dataSet,
			float windowSize) {

		Vector<MeasurementDataSet> result = new Vector<MeasurementDataSet>();

		// We walk through all the measuring points given and return only those
		// which are inside the window
		for (MeasurementDataSet currentDataSet : data) {
			// Let's see if the current point is located within the window
			if (currentDataSet.getX() >= dataSet.getX() - windowSize
					* MultiTouchDrawable.getGridSpacingX()
					&& currentDataSet.getX() <= dataSet.getX() + windowSize
							* MultiTouchDrawable.getGridSpacingX()
					&& currentDataSet.getY() >= dataSet.getY() - windowSize
							* MultiTouchDrawable.getGridSpacingY()
					&& currentDataSet.getY() <= dataSet.getY() + windowSize
							* MultiTouchDrawable.getGridSpacingY())
				// If so, add it to the results
				result.add(currentDataSet);
		}

		return result;
	}
	
	/**
	 * Checks whether three points are collinear, or in other words, in one
	 * line. Mathematically, it calculates the area of the triangle of the three
	 * points and checks whether this value lies within a certain threshold.
	 * 
	 * @param p1
	 *            The first point
	 * @param p2
	 *            The second point
	 * @param p3
	 *            The third point
	 * 
	 * @return Whether or not the three points are in one line
	 */
	protected boolean arePointsCollinear(PointF p1, PointF p2, PointF p3) {

		float threshold = 0.1f;
		float area = Math
				.abs((p1.x * (p2.y - p3.y) + p2.x * (p3.y - p1.y) + p3.x
						* (p1.y - p2.y)) / 2.0f);

		return (area <= threshold) ? true : false;
	}
	
	/**
	 * This class holds an arrow needed for the Local Signal Strength Gradient
	 * algorithm
	 * 
	 * @author tom
	 * 
	 */
	protected class GradientArrow {
		public float x;
		public float y;
		public float directionX;
		public float directionY;
		public float weight;

		public GradientArrow() {

		}

		public GradientArrow(float x, float y, float directionX,
				float directionY, float weight) {
			this.x = x;
			this.y = y;
			this.directionX = directionX;
			this.directionY = directionY;
			this.weight = weight;
		}
	}
	
	//TERZO METODO
	
	public PointF calculateTriangulationUserPosition() {
		if (accessPointList.size() >= 3) {
			
			AccessPointResult accessPoint1 = accessPointList.get(0);
			AccessPointResult accessPoint2 = accessPointList.get(1);
			AccessPointResult accessPoint3 = accessPointList.get(2);
			
			double[] loc = MyTrilateration(
					accessPoint1.getLocation().getX(), accessPoint1.getLocation().getY(), accessPoint1.getLevel(),
					accessPoint2.getLocation().getX(), accessPoint2.getLocation().getY(), accessPoint2.getLevel(),
					accessPoint3.getLocation().getX(), accessPoint3.getLocation().getY(), accessPoint3.getLevel());
			
			return new PointF((float)loc[0], (float)loc[1]);
		} else {
			return null;
		}
	}
	
	private double calcDistance(double rssi) {
        double base = 10;
        double exponent = -(rssi + 51.504)/16.532;
        //double distance = Math.pow(base, exponent);
        //104.09004338 + 13.26842562x + 0.57250833x^2 + 0.00986120x^3 + 0.00006099x^4
        
        // SI NORTH THIRD FLOOR (room 3250)
//      double distance = 104.09004338 + 13.26842562 * rssi + 0.57250833* Math.pow(rssi,2)
//            + 0.00986120*Math.pow(rssi, 3) + 0.00006099 * Math.pow(rssi,4);
        
        // SI NORTH FIRST FLOOR 
        // 0 degree
        //double distance = 3324.4981666 + 234.0366524 * rssi + 6.0593624* Math.pow(rssi,2)
    //  + 0.0683264*Math.pow(rssi, 3) + 0.0002843 * Math.pow(rssi,4);
        
        double distance = 730.24198315 + 52.33325511*rssi + 1.35152407*Math.pow(rssi, 2) 
                + 0.01481265*Math.pow(rssi, 3) + 0.00005900*Math.pow(rssi, 4)+0.00541703*180;
        
        
        //return (distance>0)?distance:rssi;
        return distance;
	}
	
	// Convert Feet into Meter
	private double calFeetToMeter(double rssi) {             
	       return rssi*0.3048;
	}

	double calDistToDeg(double dist) {
        double result;
        double DistToDeg;

        final int lat = 42;
        final double EarthRadius = 6367449;
        final double a = 6378137;
        final double b = 6356752.3;
        final double ang = lat*(Math.PI/180);
        
        // This function will calculate the longitude distance based on the latitude
        // More information is 
        // http://en.wikipedia.org/wiki/Geographic_coordinate_system#Expressing_latitude_and_longitude_as_linear_units
        
//      result = Math.cos(ang)*Math.sqrt((Math.pow(a,4)*(Math.pow(Math.cos(ang),2))
//                      + (Math.pow(b,4)*(Math.pow(Math.sin(ang),2)))) 
//                      / (Math.pow((a*Math.cos(ang)),2)+Math.pow((b*Math.sin(ang)),2)))
//                      * Math.PI/180;
        
        DistToDeg = 82602.89223259855;  // unit (meter), based on 42degree.
        result = dist/DistToDeg;                // convert distance to lat,long degree.
        return result;
	}

    private double[] myRotation(double x, double y, double dist, double deg) {
            
            double tmpX, tmpY;
            //ArrayList<Double> myLocation = null;
            double[]  myLocation = new double[3];
            
            tmpX = x*Math.cos((Math.PI/180)*deg)-y*Math.sin((Math.PI/180)*deg);
            tmpY = x*Math.sin((Math.PI/180)*deg)+y*Math.cos((Math.PI/180)*deg);
            
//          myLocation.add(tmpX);
//          myLocation.add(tmpY);
            myLocation[0] = tmpX;
            myLocation[1] = tmpY;
            myLocation[2] = dist;
            
            return myLocation;
    }
    
	public double[] MyTrilateration(
		double Lat1, double Long1, double rssi1, 
        double Lat2, double Long2, double rssi2,
        double Lat3, double Long3, double rssi3) {

		//ArrayList<Double> tmpWAP1, tmpWAP2, tmpWAP3;
		double[] tmpWAP1 = new double[3];
		double[] tmpWAP2 = new double[3]; 
		double[] tmpWAP3 = new double[3]; 
		
		double dist1, dist2, dist3;            
		double tmpLat2, tmpLong2, tmpLat3, tmpLong3;
		double tmpSlide, deg;
		double MyLat, MyLong;
		
		double[] MyLocation = new double[2];
		
		//dist1 = calDistToDeg(5);       //calDistToDeg(calcDistance(rssi1));
		//dist2 = calDistToDeg(6);       //calDistToDeg(calcDistance(rssi2));
		//dist3 = calDistToDeg(7);       //calDistToDeg(calcDistance(rssi3));
		
		dist1 = calDistToDeg(calFeetToMeter(calcDistance(rssi1)));
		dist2 = calDistToDeg(calFeetToMeter(calcDistance(rssi2)));
		dist3 = calDistToDeg(calFeetToMeter(calcDistance(rssi3)));
		
		//test
		//dist1 = calDistToDeg(calFeetToMeter(53));
		//dist2 = calDistToDeg(calFeetToMeter(24));
		//dist3 = calDistToDeg(calFeetToMeter(51));
		             
		tmpLat2        = Lat2-Lat1;
		tmpLong2       = Long2 - Long1;
		tmpLat3        = Lat3-Lat1;
		tmpLong3       = Long3 - Long1;
		
		tmpSlide = Math.sqrt(Math.pow(tmpLat2,2)+Math.pow(tmpLong2,2));
		
		//deg = (180/Math.PI)*Math.acos( ((Math.pow(tmpLat2,2) + Math.pow(tmpSlide,2) - Math.pow(tmpLong2, 2)) / (2*tmpLat2*tmpSlide)) );
		deg = (180/Math.PI)*Math.acos( Math.abs(tmpLat2)/Math.abs(tmpSlide));
		
		// 1 quadrant
		if( (tmpLat2>0 && tmpLong2>0) ) {
		     deg = 360 - deg;
		}
		else if( (tmpLat2<0 && tmpLong2>0) ) {
		     deg = 180 + deg;
		}
		// 3 quadrant
		else if( (tmpLat2<0 && tmpLong2<0)){                   
		     deg = 180 - deg;
		}
		// 4 quadrant
		else if( (tmpLat2>0 && tmpLong2<0)) {
		     deg = deg;
		}
		
		tmpWAP1[0] = 0.0;
		tmpWAP1[1] = 0.0;
		tmpWAP1[2] = dist1;             
		tmpWAP2 = myRotation(tmpLat2, tmpLong2, dist2, deg);
		tmpWAP3 = myRotation(tmpLat3, tmpLong3, dist3, deg);
		
		
		MyLat = (Math.pow(tmpWAP1[2],2)-Math.pow(tmpWAP2[2],2)+Math.pow(tmpWAP2[0],2))/(2*tmpWAP2[0]);
		
		MyLong = (Math.pow(tmpWAP1[2],2)-Math.pow(tmpWAP3[2],2)-Math.pow(MyLat,2)
		             +Math.pow(MyLat-tmpWAP3[0],2)+Math.pow(tmpWAP3[1], 2))/(2*tmpWAP3[1]);
		
		MyLocation = myRotation(MyLat, MyLong, 0, -deg);
		
		MyLocation[0] = MyLocation[0] + Lat1;
		MyLocation[1] = MyLocation[1] + Long1; 
		
		return MyLocation;
	}

    //QUARTO METODO

    //NonLinearLeastSquares user position calculate
    public PointF calculateNLLSUserPosition() {

        double[][] positions = new double[accessPointList.size()][2];
        double[] distances = new double[accessPointList.size()];

        for(int i = 0; i < accessPointList.size(); i++) {
            AccessPointResult accessPoint = accessPointList.get(i);
            positions[i][0] = accessPoint.getLocation().getX();
            positions[i][1] = accessPoint.getLocation().getY();
            distances[i]    = accessPoint.getLevel();
        }

        TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
        NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

        Optimum optimum = solver.solve();
        double[] calculatedPosition = optimum.getPoint().toArray();

        return new PointF((float)calculatedPosition[0], (float)calculatedPosition[1]);
    }
}
