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

	private FaceFinder(Context ctx, int inputWidth, int inputHeight, int sensorOrientation, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		this.faceDetector = FaceDetector.create(ctx, hwAccleration, enhancedHwAccleration, numThreads);
		this.faceScanner = FaceScanner.create(ctx, hwAccleration, enhancedHwAccleration, numThreads);
		this.sensorOrientation = sensorOrientation;
		this.detectorInputProc = new FaceDetector.InputImageProcessor(inputWidth, inputHeight, sensorOrientation);
	}

	public static FaceFinder create(Context ctx, int inputWidth, int inputHeight, int sensorOrientation, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		return new FaceFinder(ctx, inputWidth, inputHeight, sensorOrientation, hwAccleration, enhancedHwAccleration, numThreads);
	}

	public static FaceFinder create(Context ctx, int inputWidth, int inputHeight, int sensorOrientation) {
		return create(ctx, inputWidth, inputHeight, sensorOrientation, false, true, 4);
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
