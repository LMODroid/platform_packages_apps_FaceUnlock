/*
 * Copyright 2023 LibreMobileOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.libremobileos.facedetect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

import androidx.annotation.Nullable;

import com.libremobileos.yifan.face.ImageUtils;

import java.util.List;

public class FaceBoundsOverlayView extends View {

	private List<Pair<RectF, String>> bounds = null;
	private Paint paint, textPaint;
	private Matrix transform = null;
	private int extraWidth, extraHeight, viewWidth, viewHeight, sensorWidth, sensorHeight;

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
 		for (Pair<RectF, String> bound : bounds) {
			canvas.drawRect(bound.first, paint);
			if (bound.second != null)
				canvas.drawText(bound.second, bound.first.left, bound.first.bottom, textPaint);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
		super.onSizeChanged(w, h, oldWidth, oldHeight);
		viewWidth = w;
		viewHeight = h;
		transform = null;
	}

	// please give me RectF's that wont be used otherwise as I modify them
	public void updateBounds(List<Pair<RectF, String>> inputBounds, int sensorWidth, int sensorHeight) {
		this.bounds = inputBounds;
		// if we have no paint yet, make one
		if (paint == null) {
			paint = new Paint();
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(10f);
			paint.setColor(Color.RED);
		}
		if (textPaint == null) {
			textPaint = new Paint();
			textPaint.setColor(Color.RED);
			textPaint.setTextSize(100);
		}
		// if camera size or view size changed, recalculate it
		if (this.sensorWidth != sensorWidth || this.sensorHeight != sensorHeight || (viewWidth + viewHeight) > 0) {
			this.sensorWidth = sensorWidth;
			this.sensorHeight = sensorHeight;
			int oldWidth = viewWidth;
			int oldHeight = viewHeight;
			extraWidth = 0;
			extraHeight = 0;
			// calculate scaling keeping aspect ratio
			int newHeight = (int)((oldWidth / (float)sensorWidth) * sensorHeight);
			int newWidth = (int)((oldHeight / (float)sensorHeight) * sensorWidth);
			// calculate out black bars
			if (newWidth > oldWidth) {
				extraHeight = (oldHeight - newHeight) / 2;
				viewHeight = newHeight;
			} else {
				extraWidth = (oldWidth - newWidth) / 2;
				viewWidth = newWidth;
			}
			// scale from image size to view size
			transform = ImageUtils.getTransformationMatrix(sensorWidth, sensorHeight, viewWidth, viewHeight, 0, false);
			viewWidth = 0; viewHeight = 0;
		}
		// map bounds to view size
		for (Pair<RectF, String> bound : bounds) {
			transform.mapRect(bound.first);
			bound.first.offset(extraWidth, extraHeight);
		}
		invalidate();
	}
}
