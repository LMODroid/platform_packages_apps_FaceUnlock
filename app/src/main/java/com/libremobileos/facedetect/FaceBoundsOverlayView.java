package com.libremobileos.facedetect;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.libremobileos.yifan.face.shared.ImageUtils;

import java.util.Arrays;

public class FaceBoundsOverlayView extends View {

	private RectF[] bounds = null;
	private Paint paint = null;
	private Matrix transform = null;
	private int extra = 0;

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
		if (bounds == null || transform == null) return;
		if (paint == null) {
			paint = new Paint();
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(10f);
			paint.setColor(Color.RED);
		}
 		for (RectF bound : bounds) {
			@SuppressLint("DrawAllocation") RectF rect = new RectF(bound);
			transform.mapRect(rect);
			rect.offset(0, extra);
			canvas.drawRect(rect, paint);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		int newh = (w / 3) * 4;
		extra = (h - newh) / 2;
		transform = ImageUtils.getTransformationMatrix(480, 640, w, newh, 0, false);
		transform.preScale(-1, 1, 480 / 2, 640 / 2); // swap x axis
	}

	public void updateBounds(RectF[] bounds) {
		this.bounds = bounds;
		invalidate();
	}
}
