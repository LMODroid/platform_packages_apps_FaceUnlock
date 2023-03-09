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
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CircleOverlayView extends View {
	private Paint paint, paint2;
	private int viewWidth, viewHeight, percentage = 0;

	public CircleOverlayView(Context context) {
		this(context, null);
	}

	public CircleOverlayView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CircleOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public CircleOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (paint == null) {
			paint = new Paint();
			paint.setStrokeWidth(50f);
			paint.setColor(Color.DKGRAY);
			paint.setStyle(Paint.Style.STROKE);
		}
		if (paint2 == null) {
			paint2 = new Paint();
			paint2.setStrokeWidth(50f);
			paint2.setColor(Color.LTGRAY);
			paint2.setStyle(Paint.Style.STROKE);
		}
		canvas.drawArc(0, 0, viewWidth, viewHeight, -90, 360, false, paint);
		canvas.drawArc(0, 0, viewWidth, viewHeight, -90, percentage * 3.6f, false, paint2);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
		super.onSizeChanged(w, h, oldWidth, oldHeight);
		viewWidth = w;
		viewHeight = h;
	}

	public void setPercentage(int percentage) {
		this.percentage = percentage;
		invalidate();
	}
}
