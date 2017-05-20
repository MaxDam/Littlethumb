
package it.md.littlethumb.algorithm.model;

import it.md.littlethumb.model.Location;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class LocDistance {
	private double distance;
	private Location location;

	public LocDistance(double distance, Location location) {
		this.distance = distance;
		this.location = location;
	}

	public double getDistance() {
		return distance;
	}

	public Location getLocation() {
		return location;
	}
}