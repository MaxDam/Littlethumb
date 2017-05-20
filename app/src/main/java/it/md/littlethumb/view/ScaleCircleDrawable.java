package it.md.littlethumb.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import it.md.littlethumb.model.Location;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class ScaleCircleDrawable extends MultiTouchDrawable {

	/**
	 * @uml.property  name="slider1"
	 * @uml.associationEnd  
	 */
	protected ScaleSliderDrawable slider1;

	/**
	 * @uml.property  name="slider2"
	 * @uml.associationEnd  
	 */
	protected ScaleSliderDrawable slider2;

	/**
	 * @uml.property  name="okDrawable"
	 * @uml.associationEnd  
	 */
	protected OkDrawable okDrawable;

	public ScaleCircleDrawable(Context context, MultiTouchDrawable superDrawable, OkCallback okCallback) {
		super(context, superDrawable);
		slider1 = new ScaleSliderDrawable(ctx, this, 1);
		slider2 = new ScaleSliderDrawable(ctx, this, 2);

		okDrawable = new OkDrawable(ctx, this, okCallback);
		this.setRelativePosition(0, 0);
		this.setAngle(superDrawable.getAngle());
	}

	protected void init() {

	}

	public void onSliderMove(int id) {

		if (okDrawable != null) {
			okDrawable.setRelativePosition(slider1.getRelativeX(), slider1.getRelativeY());
		}
	}

	//ritorna il raggio del cerchio
	public float getRadius() {
		return (float) Math.sqrt(Math.pow(slider2.getRelativeX()/superDrawable.getScaleX() - slider1.getRelativeX()/superDrawable.getScaleX(), 2)
				+ Math.pow(slider2.getRelativeY()/superDrawable.getScaleY() - slider1.getRelativeY()/superDrawable.getScaleY(), 2));
	}
	
	//ritorna il centro del cerchio
	public Location getCenter() {
		return new Location(slider1.getRelativeX()/superDrawable.getScaleX(), slider1.getRelativeY()/superDrawable.getScaleY());
	}
	
	public void removeScaleSliders() {
		this.removeSubDrawable(slider1);
		this.removeSubDrawable(slider2);
	}

	@Override
	public Drawable getDrawable() {
		return null;
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.save();
		float dx = (maxX + minX) / 2;
		float dy = (maxY + minY) / 2;

		canvas.translate(dx, dy);
		canvas.rotate((float) Math.toDegrees(this.getAngle()));

		Paint paint = new Paint();
		paint.setStyle(Paint.Style.FILL);
		paint.setStrokeWidth(3);
		paint.setColor(Color.argb(70, 255, 0, 0));
		

		float scaleX = this.getScaleX();
		float scaleY = this.getScaleY();

		float radius = (float) Math.sqrt(Math.pow(slider2.getRelativeX() * scaleX - slider1.getRelativeX() * scaleX, 2)
				     + Math.pow(slider2.getRelativeY() * scaleY - slider1.getRelativeY() * scaleY, 2));
		
		canvas.drawCircle(slider1.getRelativeX() * scaleX, slider1.getRelativeY() * scaleY, radius, paint);
		
		canvas.restore();

		this.drawSubdrawables(canvas);
	}

	@Override
	public boolean isScalable() {
		return true;
	}

	@Override
	public boolean isRotatable() {
		return true;
	}

	@Override
	public boolean isDragable() {
		return true;
	}

	@Override
	public boolean isOnlyInSuper() {
		return true;
	}

	public ScaleSliderDrawable getSlider(int id) {
		switch (id) {
		case 1:
			return slider1;
		case 2:
			return slider2;
		default:
			return null;
		}
	}
	
	public void setReadOnly() {
		this.removeSubDrawable(slider1);
		this.removeSubDrawable(slider2);
		this.removeSubDrawable(okDrawable);
	}
}
