/*
 * Copyright 2023 LibreMobileOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.libremobileos.facedetect;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;
import android.util.Size;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.libremobileos.facedetect.util.BorderedText;
import com.libremobileos.facedetect.util.CameraActivity;
import com.libremobileos.facedetect.util.Logger;
import com.libremobileos.facedetect.util.MultiBoxTracker;
import com.libremobileos.facedetect.util.OverlayView;
import com.libremobileos.yifan.face.scan.FaceFinder;
import com.libremobileos.yifan.face.scan.FaceScanner;
import com.libremobileos.yifan.face.shared.FaceDetector;
import com.libremobileos.yifan.face.shared.SimilarityClassifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class MainActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private FaceFinder detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;

  private boolean computingDetection = false;
  private boolean addPending = false;

  private long timestamp = 0;

  private MultiBoxTracker tracker;

  private HashMap<String, float[][]> knownFaces = new HashMap<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    FloatingActionButton fabAdd = findViewById(R.id.fab_add);
    fabAdd.setOnClickListener(view -> onAddClick());
  }



  private void onAddClick() {

    addPending = true;
    //Toast.makeText(this, "please scan face now", Toast.LENGTH_LONG ).show();

  }

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    BorderedText borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
            canvas -> {
              tracker.draw(canvas);
              if (isDebug()) {
                tracker.drawDebug(canvas);
              }
            });

    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);

    detector = FaceFinder.create(this, previewWidth, previewHeight, sensorOrientation);
  }


  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;

    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    runInBackground(
            () -> {
              onFacesDetected(currTimestamp, rgbFrameBitmap, addPending);
              addPending = false;
            });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.tfe_od_camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(() -> {
      detector.setUseNNAPI(isChecked);
    });
  }

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(() -> {
      detector.setNumThreads(numThreads);
    });
  }

  private void showAddFaceDialog(SimilarityClassifier.Recognition rec) {

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    LayoutInflater inflater = getLayoutInflater();
    View dialogLayout = inflater.inflate(R.layout.image_edit_dialog, null);
    ImageView ivFace = dialogLayout.findViewById(R.id.dlg_image);
    TextView tvTitle = dialogLayout.findViewById(R.id.dlg_title);
    EditText etName = dialogLayout.findViewById(R.id.dlg_input);

    tvTitle.setText("Add Face");
    ivFace.setImageBitmap(rec.getCrop());
    etName.setHint("Input name");

    builder.setPositiveButton("OK", (dlg, i) -> {
        String name = etName.getText().toString();
        if (name.isEmpty()) {
            return;
        }
        //detector.register(name, rec);
        knownFaces.put(name, rec.getExtra());
        dlg.dismiss();
    });
    builder.setView(dialogLayout);
    builder.show();

  }

  private void updateResults(long currTimestamp, final List<SimilarityClassifier.Recognition> mappedRecognitions) {

    tracker.trackResults(mappedRecognitions, currTimestamp);
    trackingOverlay.postInvalidate();
    computingDetection = false;


    if (mappedRecognitions.size() > 0) {
       LOGGER.i("Adding results");
       SimilarityClassifier.Recognition rec = mappedRecognitions.get(0);
       if (rec.getExtra() != null) {
         showAddFaceDialog(rec);
       }

    }

    runOnUiThread(
            () -> {
              showFrameInfo(previewWidth + "x" + previewHeight);
              showInference(lastProcessingTimeMs + "ms");
            });

  }

  private void onFacesDetected(long currTimestamp, Bitmap rgbFrameBitmap, boolean add) {
    // TODO move this to yifan.face package to make this recycle-able code

    List<SimilarityClassifier.Recognition> mappedRecognitions = new ArrayList<>();

    Pair<List<Pair<FaceDetector.Face, FaceScanner.Face>>, Long> faces = detector.process(rgbFrameBitmap);
    lastProcessingTimeMs = faces.second;

    for (Pair<FaceDetector.Face, FaceScanner.Face> faceFacePair : faces.first) {

      FaceDetector.Face face = faceFacePair.first;
      FaceScanner.Face scanned = faceFacePair.second;

      LOGGER.i("FACE" + face.toString());
      LOGGER.i("Running detection on face " + currTimestamp);

      final RectF boundingBox = new RectF(face.getLocation());

      String id = null;
      String label = face.getTitle();
      float distance = -1f;
      int color = Color.BLUE;
      float[][] extra = null;
      Bitmap crop = scanned.getCrop();

      for (Map.Entry<String, float[][]> possible : knownFaces.entrySet()) {
        float newdistance = scanned.compare(possible.getValue());
        if (newdistance <= FaceScanner.MAXIMUM_DISTANCE_TF_OD_API && (id == null || newdistance < distance)) {
          id = scanned.getId();
          label = possible.getKey();
          distance = newdistance;
          extra = possible.getValue();
        }
      }

      if (add) {
        extra = scanned.getExtra();
      } else if (id != null) {
        if (id.equals("0")) {
          color = Color.GREEN;
        } else {
          color = Color.RED;
        }
      }

      if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
        // camera is frontal so the image is flipped horizontally
        // flips horizontally
        Matrix flip = new Matrix();
        if (sensorOrientation == 90 || sensorOrientation == 270) {
          flip.postScale(1, -1, previewWidth / 2.0f, previewHeight / 2.0f);
        } else {
          flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
        }
        flip.mapRect(boundingBox);

      }

      final SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition(
              "0", label, distance, boundingBox);

      result.setColor(color);
      result.setLocation(boundingBox);
      result.setExtra(add ? extra : null); //ui logic excepts extra!=null when opening face dialog only
      result.setCrop(crop);
      mappedRecognitions.add(result);
    }

    updateResults(currTimestamp, mappedRecognitions);


  }


}
