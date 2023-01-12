/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.libremobileos.yifan.face.shared;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Generic interface for interacting with different recognition engines. */
public abstract class SimilarityClassifier {

  public static SimilarityClassifier create(
          final AssetManager assetManager,
          final String modelFilename,
          final String labelFilename,
          final int inputSize,
          final boolean isQuantized) throws IOException {
    return TFLiteObjectDetectionAPIModel.create(assetManager, modelFilename, labelFilename, inputSize, isQuantized);
  }

  public abstract List<Recognition> recognizeImage(Bitmap bitmap);

  public abstract void setNumThreads(int num_threads);

  public abstract void setUseNNAPI(boolean isChecked);

  /** An immutable result returned by a Classifier describing what was recognized. */
  public static class Recognition {
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    private final String id;

    /** Display name for the recognition. */
    private final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Lower should be better.
     */
    private final Float distance;
    private float[][] extra;

    /** Optional location within the source image for the location of the recognized object. */
    private RectF location;
    private Integer color;
    private Bitmap crop;

    public Recognition(
            final String id, final String title, final Float distance, final RectF location) {
      this.id = id;
      this.title = title;
      this.distance = distance;
      this.location = location;
      this.color = null;
      this.extra = null;
      this.crop = null;
    }

    public void setExtra(float[][] extra) {
        this.extra = extra;
    }
    public float[][] getExtra() {
        return this.extra;
    }

    public void setColor(Integer color) {
       this.color = color;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public Float getDistance() {
      return distance;
    }

    public RectF getLocation() {
      return new RectF(location);
    }

    public void setLocation(RectF location) {
      this.location = location;
    }

    @NonNull
    @Override
    public String toString() {
      String resultString = "";
      if (id != null) {
        resultString += "[" + id + "] ";
      }

      if (title != null) {
        resultString += title + " ";
      }

      if (distance != null) {
        resultString += String.format(Locale.US, "(%.1f%%) ", distance * 100.0f);
      }

      if (location != null) {
        resultString += location + " ";
      }

      return resultString.trim();
    }

    public Integer getColor() {
      return this.color;
    }

    public void setCrop(Bitmap crop) {
      this.crop = crop;
    }

    public Bitmap getCrop() {
      return this.crop;
    }

  }
}
