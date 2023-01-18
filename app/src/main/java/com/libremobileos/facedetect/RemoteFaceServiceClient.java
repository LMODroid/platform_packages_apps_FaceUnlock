package com.libremobileos.facedetect;

import android.content.Context;
import android.content.SharedPreferences;

import com.libremobileos.yifan.face.DirectoryFaceStorageBackend;
import com.libremobileos.yifan.face.FaceDataEncoder;
import com.libremobileos.yifan.face.FaceStorageBackend;
import com.libremobileos.yifan.face.SharedPreferencesFaceStorageBackend;

import java.util.function.Consumer;

public abstract class RemoteFaceServiceClient {
	public static void connect(Context ctx, Consumer<RemoteFaceServiceClient> callback) {
		new Thread(() -> {
			//TODO replace with remote thing
			SharedPreferences prefs2 = ctx.getSharedPreferences("faces2", 0);
			FaceStorageBackend s = new DirectoryFaceStorageBackend(ctx.getFilesDir());;
			callback.accept(new RemoteFaceServiceClient() {
				private static final String FACE = "Face";
				private static final String SECURE = "secure";

				@Override
				public boolean isEnrolled() {
					return s.getNames().contains(FACE);
				}

				@Override
				public boolean isSecure() {
					return prefs2.getBoolean(SECURE, false);
				}

				@Override
				public void setSecure(boolean secure) {
					prefs2.edit().putBoolean(SECURE, secure).apply();
				}

				@Override
				public boolean unenroll() {
					return s.delete(FACE);
				}

				@Override
				public boolean enroll(String data) {
					return s.register(FACE, FaceDataEncoder.decode(data), true);
				}
			});
		}).start();
	}

	public abstract boolean isEnrolled();
	public abstract boolean isSecure();
	public abstract void setSecure(boolean secure);
	public abstract boolean unenroll();
	public abstract boolean enroll(String data);

	public boolean enroll(float[][] data) {
		return enroll(FaceDataEncoder.encode(data));
	}
}
