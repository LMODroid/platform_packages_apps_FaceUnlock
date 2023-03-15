/*
 * Copyright 2019 The TensorFlow Authors
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

package com.libremobileos.yifan.face;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Generic interface for interacting with different recognition engines. */
/* package-private */ abstract class SimilarityClassifier {

  /* package-private */ static SimilarityClassifier create(
          final AssetManager assetManager,
          final String modelFilename,
          final String labelFilename,
          final int inputSize,
          final boolean isQuantized,
          final boolean hwAcceleration,
          final boolean useEnhancedAcceleration, // if hwAcceleration==true, setting this uses NNAPI instead of GPU. if false, it toggles XNNPACK
          final int numThreads) throws IOException {
    return TFLiteObjectDetectionAPIModel.create(assetManager, modelFilename, labelFilename, inputSize, isQuantized, hwAcceleration, useEnhancedAcceleration, numThreads);
  }

  /* package-private */ abstract List<Recognition> recognizeImage(Bitmap bitmap);

  /** An immutable result returned by a Classifier describing what was recognized. */
  /* package-private */ static class Recognition {
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
    private final RectF location;

    /* package-private */ Recognition(
            final String id, final String title, final Float distance, final RectF location) {
      this.id = id;
      this.title = title;
      this.distance = distance;
      this.location = location;
      this.extra = null;
    }

    public void setExtra(float[][] extra) {
        this.extra = extra;
    }
    public float[][] getExtra() {
        return this.extra;
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
  }
}
