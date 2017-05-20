package it.md.littlethumb.algorithm;

import android.graphics.PointF;

import org.apache.commons.math3.filter.DefaultMeasurementModel;
import org.apache.commons.math3.filter.DefaultProcessModel;
import org.apache.commons.math3.filter.KalmanFilter;
import org.apache.commons.math3.filter.MeasurementModel;
import org.apache.commons.math3.filter.ProcessModel;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

public class KalmanFilterLocator {

    private static KalmanFilterLocator instance = null;

	private KalmanFilterLocator() {
		//RealMatrix P0 = MatrixUtils.createRealIdentityMatrix(4);
		//ProcessModel pm = new DefaultProcessModel(A, B, Q, last_x, P0);
		ProcessModel pm = new DefaultProcessModel(A, B, Q, last_x, null);
		MeasurementModel mm = new DefaultMeasurementModel(H, R);
		filter = new KalmanFilter(pm, mm);
	}

    private KalmanFilter filter = null;

    public static KalmanFilterLocator getInstance() {
        if(instance == null) {
            instance = new KalmanFilterLocator();
        }
        return instance;
    }

	private RealMatrix A = new Array2DRowRealMatrix(new double[][] {
		{1d, 0d, 0.2d, 0d},
		{0d, 1d, 0d,   0.2d},
		{0d, 0d, 1d,   0d},
		{0d, 0d, 0d,   1d}
	});

	private RealMatrix B = new Array2DRowRealMatrix(new double[][] { 
		{1d, 0d, 0d, 0d},
		{0d, 1d, 0d, 0d},
		{0d, 0d, 1d, 0d},
		{0d, 0d, 0d, 1d} 
	});

	private RealMatrix H = new Array2DRowRealMatrix(new double[][] { 	
		{1d, 0d, 0d, 0d},
		{0d, 1d, 0d, 0d},
		{0d, 0d, 1d, 0d},
		{0d, 0d, 0d, 1d}
	});


	private RealMatrix Q = new Array2DRowRealMatrix(new double[][] { 	
		{0.001d, 0d, 	 0d, 0d},
		{0d, 	 0.001d, 0d, 0d},
		{0d, 	 0d, 	 0d, 0d},
		{0d, 	 0d, 	 0d, 0d}
	});

	private RealMatrix R = new Array2DRowRealMatrix(new double[][] { 	
		{0.1d, 	0d, 	0d, 	0d},
		{0d, 	0.1d, 	0d, 	0d},
		{0d, 	0d, 	0.1d, 	0d},
		{0d, 	0d, 	0d, 	0.1d}
	});

	private RealVector last_x = new ArrayRealVector(new double[] { 0, 0, 0, 0 });

	private RealMatrix last_P = new Array2DRowRealMatrix(new double[][] { 	
		{0d, 0d, 0d, 0d},
		{0d, 0d, 0d, 0d},
		{0d, 0d, 0d, 0d},
		{0d, 0d, 0d, 0d} 
	});

	public PointF filterPoint(double cur_xPos, double cur_yPos) {

		double velX = cur_xPos - last_x.getEntry(0);
		double velY = cur_xPos - last_x.getEntry(1);
		RealVector measurement = new ArrayRealVector(new double[] { cur_xPos, cur_yPos, velX, velY });
		RealVector control = new ArrayRealVector(new double[] { 0, 0, 0, 0 }); // TODO - adjust

		// prediction
		RealVector x = (A.operate(last_x)).add(B.operate(control));
		RealMatrix P = ((A.multiply(last_P)).multiply(A.transpose())).add(Q); 

		// correction
		RealMatrix S = ((H.multiply(P)).multiply(H.transpose())).add(R);
		RealMatrix K = (P.multiply(H.transpose())).multiply(new LUDecomposition(S).getSolver().getInverse());
		RealVector y = measurement.subtract(H.operate(x));

		RealVector cur_x = x.add(K.operate(y));
		RealMatrix cur_P = ((MatrixUtils.createRealIdentityMatrix(4)).subtract(K.multiply(H))).multiply(P);

		last_x = cur_x;
		last_P = cur_P;
		
		//System.out.println("x:" + cur_x.getEntry(0) + ", y:" + cur_x.getEntry(1));
        return new PointF((float)cur_x.getEntry(0), (float)cur_x.getEntry(1));
	}

	public PointF filterPoint2(double cur_xPos, double cur_yPos) {

		filter.predict();

		double velX = cur_xPos - last_x.getEntry(0);
		double velY = cur_xPos - last_x.getEntry(1);
		RealVector measurement = new ArrayRealVector(new double[] { cur_xPos, cur_yPos, velX, velY });
	   
		filter.correct(measurement);

        last_x = filter.getStateEstimationVector();

		double[] position = filter.getStateEstimation();

		//System.out.println("x:" + position[0] + ", y:" + position[1]);
        return new PointF((float)position[0], (float)position[1]);
	}
}