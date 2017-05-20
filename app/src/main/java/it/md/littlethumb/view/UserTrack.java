package it.md.littlethumb.view;

import org.metalev.multitouch.controller.MultiTouchController.PointInfo;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import it.md.littlethumb.R;
import it.md.littlethumb.userlocation.ManualLocationProvider;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class UserTrack extends MultiTouchDrawable {

	protected static BitmapDrawable icon;
	
	/**
	 * @uml.property  name="locProvider"
	 * @uml.associationEnd  
	 */
	protected ManualLocationProvider locProvider;
	
	/**
	 * @uml.property  name="compassIcon"
	 * @uml.associationEnd  
	 */
	protected UserCompassDrawable compassIcon;

	
	public UserTrack(Context ctx,MultiTouchDrawable superDrawable) {
		super(ctx,superDrawable);
		init();
	}

	protected void init() {
		icon = (BitmapDrawable) ctx.getResources().getDrawable(R.drawable.user_icon_track);
		this.setPivot(0.5f,66f/69f);
		
		this.width = icon.getBitmap().getWidth();
		this.height = icon.getBitmap().getHeight();
		
		locProvider=new ManualLocationProvider(ctx);
	}
	
	@Override
	public Drawable getDrawable() {
		return icon;
	}

	@Override
	public boolean isScalable() {
		return false;
	}

	@Override
	public boolean isRotatable() {
		return false;
	}

	@Override
	public boolean isDragable() {
		return true;
	}

	@Override
	public boolean isOnlyInSuper() {
		return true;
	}

	

	/* (non-Javadoc)
	 * @see at.fhstp.wificompass.view.MultiTouchDrawable#onSingleTouch(org.metalev.multitouch.controller.MultiTouchController.PointInfo)
	 */
	@Override
	public boolean onSingleTouch(PointInfo pointinfo) {
		bringToFront();
		return true;
	}

	/* (non-Javadoc)
	 * @see at.fhstp.wificompass.view.MultiTouchDrawable#onRelativePositionUpdate()
	 */
	@Override
	protected void onRelativePositionUpdate() {
		super.onRelativePositionUpdate();
		locProvider.updateCurrentPosition(relX, relY);
	}
}
