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
public class ScaleRectangleDrawable extends MultiTouchDrawable {

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

	public ScaleRectangleDrawable(Context context, MultiTouchDrawable superDrawable, OkCallback okCallback) {
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
			
			float midX = Math.min(slider1.getRelativeX(), slider2.getRelativeX()) + (Math.abs(slider1.getRelativeX() - slider2.getRelativeX()) / 2);
			float midY = Math.min(slider1.getRelativeY(), slider2.getRelativeY()) + (Math.abs(slider1.getRelativeY() - slider2.getRelativeY()) / 2);
			okDrawable.setRelativePosition(midX, midY);
		}
	}

	//punto iniziale (piu basso) del rettangolo
	public Location getBeginPoint() {
		float minX = Math.min(slider1.getRelativeX()/superDrawable.getScaleX(), slider2.getRelativeX()/superDrawable.getScaleX());
		float minY = Math.min(slider1.getRelativeY()/superDrawable.getScaleY(), slider2.getRelativeY()/superDrawable.getScaleY());
		return new Location(minX, minY);
	}
	
	//punto finale (piu alto) del rettangolo
	public Location getEndPoint() {
		float maxX = Math.max(slider1.getRelativeX()/superDrawable.getScaleX(), slider2.getRelativeX()/superDrawable.getScaleX());
		float maxY = Math.max(slider1.getRelativeY()/superDrawable.getScaleY(), slider2.getRelativeY()/superDrawable.getScaleY());
		return new Location(maxX, maxY);
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

		canvas.drawRect(slider1.getRelativeX() * scaleX, slider1.getRelativeY() * scaleY, slider2.getRelativeX() * scaleX, slider2.getRelativeY() * scaleY, paint);
		
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
