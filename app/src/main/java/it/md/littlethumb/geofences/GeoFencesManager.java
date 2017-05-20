package it.md.littlethumb.geofences;

import java.util.ArrayList;
import java.util.List;

import it.md.littlethumb.model.Location;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class GeoFencesManager {
	
	private static GeoFencesManager instance = null;
	private GeoFencesListener listener = null;
	
	private List<RectangleFence> rectengleFencesList = new ArrayList<RectangleFence>();
	private List<CircleFence> circleFencesList = new ArrayList<CircleFence>();
	
	private GeoFencesManager(GeoFencesListener listener) {
		this.listener = listener;
	}

	public static GeoFencesManager getInstance(GeoFencesListener listener) {
		if (instance == null) {
			instance = new GeoFencesManager(listener);
		}

		return instance;
	}
	
	//aggiunge un fence rettangolare
	public void addRectangleFence(Location beginPoint, Location endPoint, String message) {
		rectengleFencesList.add(new RectangleFence(beginPoint, endPoint, message));
	}
	
	//aggiunge un fence circolare
	public void addCircleFence(Location centerPoint, float radius, String message) {
		circleFencesList.add(new CircleFence(centerPoint, radius, message));
	}
	
	//notifica al manager il cambiamento della posizione dell'utente
	public void notifyChangeUserPosition(Location point) {
		
		//scorre e controlla i rectangle fences
		for(RectangleFence rectangleFence : rectengleFencesList) {
			if(rectangleFence.pointIsContained(point)) {
				listener.onGeoFencesNotify(rectangleFence.getMessage());
			}
		}

		//scorre e controlla i circle fences
		for(CircleFence circleFence : circleFencesList) {
			if(circleFence.pointIsContained(point)) {
				listener.onGeoFencesNotify(circleFence.getMessage());
			}
		}
	}

	//cancella tutti i fences creati in precedenza
	public void deleteAllFences() {
		rectengleFencesList.clear();
		circleFencesList.clear();
	}

	//ottiene la distanza fra 2 punti
	private float getDinstance(float x1, float y1, float x2, float y2) {
		return (float)Math.sqrt(Math.pow(Math.abs(x1 - x2), 2) + Math.pow(Math.abs(y1 - y2), 2));
	}
			
	//fence rettangolare
	class RectangleFence {
		private Location beginPoint = null;
		private Location endPoint = null;
		private String message = "";
		public RectangleFence(Location beginPoint, Location endPoint, String message) {
			this.beginPoint = beginPoint;
			this.endPoint = endPoint;
			this.message = message;
		}
		public String getMessage() {
			return message;
		}
		
		//controlla se il punto si trova all'interno del quadrilatero
		public boolean pointIsContained(Location point) {
			return (point.getX() <= Math.max(beginPoint.getX(), endPoint.getX())
				 && point.getX() >= Math.min(beginPoint.getX(), endPoint.getX())
				 && point.getY() <= Math.max(beginPoint.getY(), endPoint.getY())
				 && point.getY() >= Math.min(beginPoint.getY(), endPoint.getY())
			);
			
			/*
			//ottiene la diagonale
			float diagonal = getDinstance(beginPoint.getX(), beginPoint.getY(), endPoint.getX(), endPoint.getY());
			
			//se nessuna delle distanze tra punto e angolo e' maggiore della diagonale -> si trova all'interno dell'area
			return !(getDinstance(point.getX(), point.getY(), beginPoint.getX(), beginPoint.getY()) > diagonal
				|| getDinstance(point.getX(), point.getY(), endPoint.getX(), beginPoint.getY()) > diagonal
				|| getDinstance(point.getX(), point.getY(), beginPoint.getX(), endPoint.getY()) > diagonal
				|| getDinstance(point.getX(), point.getY(), endPoint.getX(), endPoint.getY()) > diagonal);
			*/
		}
		
		
	}
	
	//fence circolare
	class CircleFence {
		private Location centerPoint = null;
		private float radius;
		private String message = "";
		public CircleFence(Location centerPoint, float radius, String message) {
			this.centerPoint = centerPoint;
			this.radius = radius;
			this.message = message;
		}
		public String getMessage() {
			return message;
		}
		
		//controlla se il punto si trova all'interno del cerchio
		public boolean pointIsContained(Location point) {
			//se la distanza tra il punto e il centro del cerchio e' minore del raggio -> si trova all'interno dell'area
			return getDinstance(point.getX(), point.getY(), centerPoint.getX(), centerPoint.getY()) <= radius;
		}
	}
}
