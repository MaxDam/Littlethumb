package it.md.littlethumb.algorithm.model;

import java.util.Date;

import it.md.littlethumb.model.BssidResult;
import it.md.littlethumb.model.Location;
import it.md.littlethumb.model.ProjectSite;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class AccessPointResult  {
	
	protected String bssid;
	protected String ssid;
	protected String capabilities;
	protected int frequency;
	protected Location location;
	protected ProjectSite projectSite;
	protected boolean calculated = true;
	private double level = 0;
	private double distance = 0;
	private Date timestamp;

	public AccessPointResult() {
		
	}
	
	public AccessPointResult(BssidResult bssidResult) {
		this.bssid = bssidResult.getBssid();
		this.ssid = bssidResult.getSsid();
		this.capabilities = bssidResult.getCapabilities();
		this.frequency = bssidResult.getFrequency();
		this.level = bssidResult.getLevel();
	}

	public String getSsid() {
		return this.ssid;
	}
	
	public String getBssid() {
		return this.bssid;
	}

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public ProjectSite getProjectSite() {
		return projectSite;
	}

	public void setProjectSite(ProjectSite projectSite) {
		this.projectSite = projectSite;
	}

	@Override
	public String toString() {
		return "AccessPointResult: "+ssid+" "+bssid+" "+frequency+" "+capabilities+(location!=null?" "+location.toString():""+" "+level);
	}

	public boolean isCalculated() {
		return calculated;
	}

	public void setCalculated(boolean calculated) {
		this.calculated = calculated;
	}

	public String getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(String capabilities) {
		this.capabilities = capabilities;
	}

	public int getFrequency() {
		return frequency;
	}

	public void setFrequency(int frequency) {
		this.frequency = frequency;
	}

	public void setBssid(String bssid) {
		this.bssid = bssid;
	}

	public void setSsid(String ssid) {
		this.ssid = ssid;
	}
	

	public double getLevel() {
		return level;
	}
	
	public void setLevel(double level) {
		this.level = level;
	}
	
	public double getDistance() {
		return distance;
	}
	
	public void setDistance(double distance) {
		this.distance = distance;
	}
	
	public Date getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
}
