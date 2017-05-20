package it.md.littlethumb.algorithm;

import java.util.Iterator;
import java.util.Vector;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.PointF;

import it.md.littlethumb.algorithm.model.MeasurementDataSet;
import it.md.littlethumb.model.AccessPoint;
import it.md.littlethumb.model.ProjectSite;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class WeightedCentroidTrilateration extends AccessPointTrilateration {

	protected static float g = 1.3f;

	public WeightedCentroidTrilateration(Context context, ProjectSite projectSite) {
		super(context, projectSite);
	}
	
	public WeightedCentroidTrilateration(Context context,
			ProjectSite projectSite, ProgressDialog progressDialog) {
		super(context, projectSite, progressDialog);
	}

	@Override
	public PointF calculateAccessPointPosition(AccessPoint ap) {
		Vector<MeasurementDataSet> originalData = this.measurementData.get(ap);
		
		if (originalData.size() > 3) {
			Vector<MeasurementDataSet> data = new Vector<MeasurementDataSet>();

			float sumRssi = 0;
			
			for (Iterator<MeasurementDataSet> it = originalData.iterator(); it.hasNext();) {
				MeasurementDataSet dataSet = it.next();
				
				float newRssi = (float) Math.pow(Math.pow(10, dataSet.getRssi() / 20), g);
				sumRssi += newRssi;
				
				data.add(new MeasurementDataSet(dataSet.getX(), dataSet.getY(), newRssi));
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
}
