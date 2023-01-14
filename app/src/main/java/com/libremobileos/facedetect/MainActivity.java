package com.libremobileos.facedetect;

import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Pair;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.libremobileos.yifan.face.scan.FaceFinder;
import com.libremobileos.yifan.face.scan.FaceScanner;
import com.libremobileos.yifan.face.shared.FaceDetector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	private PreviewView previewView;
	private FaceFinder faceFinder;
	private FaceBoundsOverlayView overlayView;
	private final Size desiredInputSize = new Size(640, 480);
	private final int selectedCamera = CameraSelector.LENS_FACING_FRONT;
	private int previewWidth, previewHeight;
	private HashMap<String, float[][]> knownFaces = new HashMap<>();
	private boolean addPending = false;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		previewView = findViewById(R.id.viewFinder);
		previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
		overlayView = findViewById(R.id.overlay);
		overlayView.setOnClickListener(v -> addPending = true);
		setTitle("Tap anywhere to add face");

		cameraProviderFuture = ProcessCameraProvider.getInstance(this);
		cameraProviderFuture.addListener(() -> {
			try {
				ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
				bindPreview(cameraProvider);
			} catch (ExecutionException | InterruptedException e) {
				// No errors need to be handled for this Future.
				// This should never be reached.
			}
		}, getMainExecutor());

	}

	@OptIn(markerClass = ExperimentalGetImage.class)
	private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
		Preview preview = new Preview.Builder()
				.build();

		CameraSelector cameraSelector = new CameraSelector.Builder()
				.requireLensFacing(selectedCamera)
				.build();

		preview.setSurfaceProvider(previewView.getSurfaceProvider());

		/* cameras are landscape */
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			previewWidth = desiredInputSize.getHeight();
			previewHeight = desiredInputSize.getWidth();
		} else {
			previewWidth = desiredInputSize.getWidth();
			previewHeight = desiredInputSize.getHeight();
		}

		ImageAnalysis imageAnalysis =
				new ImageAnalysis.Builder()
						.setTargetResolution(new Size(previewWidth, previewHeight))
						.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
						.build();

		imageAnalysis.setAnalyzer(getMainExecutor(), imageProxy -> {
			Pair<List<Pair<FaceDetector.Face, FaceScanner.Face>>, Long> data = faceFinder.process(BitmapUtils.getBitmap(imageProxy));
			ArrayList<Pair<RectF, String>> bounds = new ArrayList<>();
			for (Pair<FaceDetector.Face, FaceScanner.Face> faceFacePair : data.first) {
				RectF boundingBox = new RectF(faceFacePair.first.getLocation());
				if (selectedCamera == CameraSelector.LENS_FACING_FRONT) {
					// camera is frontal so the image is flipped horizontally
					// flips horizontally
					Matrix flip = new Matrix();
					flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
					flip.mapRect(boundingBox);
				}
				FaceScanner.Face detected = null;
				for (Map.Entry<String, float[][]> possible : knownFaces.entrySet()) {
					float newdistance = faceFacePair.second.compare(possible.getValue());
					if (newdistance <= FaceScanner.MAXIMUM_DISTANCE_TF_OD_API && (detected == null || newdistance < detected.getDistance())) {
						detected = new FaceScanner.Face(faceFacePair.first.getId(), possible.getKey(), newdistance, faceFacePair.first.getLocation(), faceFacePair.second.getCrop(), possible.getValue());
					}
				}
				bounds.add(new Pair<>(boundingBox, detected == null ? faceFacePair.first.getTitle() + " " + faceFacePair.first.getConfidence() : detected.getTitle() + " " + detected.getDistance()));
				if (addPending) {
					runOnUiThread(() ->
						showAddFaceDialog(faceFacePair.second));
					addPending = false;
				}
			}
			overlayView.updateBounds(bounds, previewWidth, previewHeight);
			imageProxy.close();
		});

		/* Camera camera = */ cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis, preview);

		faceFinder = FaceFinder.create(this, previewWidth, previewHeight, 0);
	}

	private void showAddFaceDialog(FaceScanner.Face rec) {
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

}
