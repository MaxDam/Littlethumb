/*
 * Created on Mar 9, 2012
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package it.md.littlethumb.userlocation;

import java.util.Date;

import android.content.Context;
import it.md.littlethumb.model.Location;

public class ManualLocationProvider extends LocationProviderImpl {

	public ManualLocationProvider(Context ctx) {
		this(ctx, LocationServiceFactory.getLocationService());
	}

	public ManualLocationProvider(Context ctx, LocationService locationService) {
		super(ctx, locationService);

	}

	public void updateCurrentPosition(float x, float y) {
		loc = new Location(getProviderName(), x, y, 0, new Date());
		if (locationService != null)
			locationService.updateLocation(loc);

		if (listener != null) {
			listener.onLocationChange(loc);
		}
	}

}
