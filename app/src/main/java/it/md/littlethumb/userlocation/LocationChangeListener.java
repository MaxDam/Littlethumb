/*
 * Created on Mar 31, 2012
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package it.md.littlethumb.userlocation;

import it.md.littlethumb.model.Location;

public interface LocationChangeListener {
	public void onLocationChange(Location loc);
}
