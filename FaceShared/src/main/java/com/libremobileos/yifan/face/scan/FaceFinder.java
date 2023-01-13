package com.libremobileos.yifan.face.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Pair;

import com.libremobileos.yifan.face.shared.FaceDetector;

import java.util.ArrayList;
import java.util.List;

/** lame wrapper around FaceDetector and FaceScanner */
public class FaceFinder {
	private final FaceDetector faceDetector;
	private final FaceDetector.InputImageProcessor detectorInputProc;
	private final FaceScanner faceScanner;
	private final int sensorOrientation;

	private FaceFinder(Context ctx, int inputWidth, int inputHeight, int sensorOrientation) {
		this.faceDetector = FaceDetector.create(ctx);
		this.faceScanner = FaceScanner.create(ctx);
		this.sensorOrientation = sensorOrientation;
		this.detectorInputProc = new FaceDetector.InputImageProcessor(inputWidth, inputHeight, sensorOrientation);
	}

	public static FaceFinder create(Context ctx, int inputWidth, int inputHeight, int sensorOrientation) {
		return new FaceFinder(ctx, inputWidth, inputHeight, sensorOrientation);
	}

	public void setUseNNAPI(boolean useNNAPI) {
		faceScanner.setUseNNAPI(useNNAPI);
		faceDetector.setUseNNAPI(useNNAPI);
	}

	public void setNumThreads(int numThreads) {
		faceScanner.setNumThreads(numThreads);
		faceDetector.setNumThreads(numThreads);
	}

	public Pair<List<Pair<FaceDetector.Face, FaceScanner.Face>> /* detected faces */, Long /* processing time */> process(Bitmap input) {
		FaceDetector.InputImage inputImage = detectorInputProc.process(input);

		final long startTime1 = SystemClock.uptimeMillis();
		final List<FaceDetector.Face> faces = faceDetector.detectFaces(inputImage);
		final List<Pair<FaceDetector.Face, FaceScanner.Face>> results = new ArrayList<>();

		if (faces != null && faces.size() > 0) {
			final FaceScanner.InputImageProcessor scannerInputProc = new FaceScanner.InputImageProcessor(input, sensorOrientation);

			for (FaceDetector.Face face : faces) {
				FaceScanner.InputImage faceBmp = scannerInputProc.process(face.getLocation());
				if (faceBmp == null) continue;

				final FaceScanner.Face scanned = faceScanner.detectFace(faceBmp);
				if (scanned == null) continue;

				results.add(new Pair<>(face, scanned));
			}
		}

		return new Pair<>(results, /* lastProcessingTimeMs */ SystemClock.uptimeMillis() - startTime1);
	}
}
