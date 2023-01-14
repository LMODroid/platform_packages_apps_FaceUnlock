package com.libremobileos.facedetect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.libremobileos.yifan.face.shared.ImageUtils;

public class FaceBoundsOverlayView extends View {

	private RectF[] bounds = null;
	private Paint paint = null;
	private Matrix transform = null;
	private int extraw, extrah, viewraww, viewrawh, sensorWidth, sensorHeight;

	public FaceBoundsOverlayView(Context context) {
		this(context, null);
	}

	public FaceBoundsOverlayView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public FaceBoundsOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public FaceBoundsOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (bounds == null || transform == null || paint == null)
			return; // am I ready yet?
 		for (RectF bound : bounds) {
			canvas.drawRect(bound, paint);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		viewraww = w;
		viewrawh = h;
		transform = null;
	}

	// please give me RectF's that wont be used otherwise as I modify them
	public void updateBounds(RectF[] bounds, int sensorWidth, int sensorHeight) {
		this.bounds = bounds;
		// if we have no paint yet, make one
		if (paint == null) {
			paint = new Paint();
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(10f);
			paint.setColor(Color.RED);
		}
		// if camera size or view size changed, recalculate it
		if (this.sensorWidth != sensorWidth || this.sensorHeight != sensorHeight || (viewraww + viewrawh) > 0) {
			this.sensorWidth = sensorWidth;
			this.sensorHeight = sensorHeight;
			int oldw = viewraww;
			int oldh = viewrawh;
			extraw = 0;
			extrah = 0;
			// calculate scaling keeping aspect ratio
			int newh = (oldw / sensorWidth) * sensorHeight;
			int neww = (oldh / sensorHeight) * sensorWidth;
			// calculate out black bars
			if (neww > oldw) {
				extrah = (oldh - newh) / 2;
				viewrawh = newh;
			} else {
				extraw = (oldw - neww) / 2;
				viewraww = neww;
			}
			// scale from image size to view size
			transform = ImageUtils.getTransformationMatrix(sensorWidth, sensorHeight, viewraww, viewrawh, 0, false);
			viewraww = 0; viewrawh = 0;
		}
		// map bounds to view size
		for (RectF bound : bounds) {
			transform.mapRect(bound);
			bound.offset(extraw, extrah);
		}
		invalidate();
	}
}
