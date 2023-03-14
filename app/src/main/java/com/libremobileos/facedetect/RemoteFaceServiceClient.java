package com.libremobileos.facedetect;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.libremobileos.yifan.face.DirectoryFaceStorageBackend;
import com.libremobileos.yifan.face.FaceDataEncoder;
import com.libremobileos.yifan.face.FaceStorageBackend;

import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;

public abstract class RemoteFaceServiceClient {
	public static final String FACE = "Face";
	public static final String SECURE = "secure";

	public static void connect(Context ctx, File dir, Consumer<RemoteFaceServiceClient> callback) {
		new Thread(() -> {
			//TODO replace with remote thing
			SharedPreferences prefs2 = ctx.getSharedPreferences("faces2", 0);
			FaceStorageBackend s = new DirectoryFaceStorageBackend(dir);
			callback.accept(new RemoteFaceServiceClient() {

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
				public boolean enroll(String data, byte[] hat) {
					boolean result = s.register(FACE, FaceDataEncoder.decode(data), true);
					if (result) {
						prefs2.edit().putString(FACE, new String(Base64.encode(hat, Base64.URL_SAFE))).apply();
					}
					return result;
				}
			});
		}).start();
	}

	public abstract boolean isEnrolled();
	public abstract boolean isSecure();
	public abstract void setSecure(boolean secure);
	public abstract boolean unenroll();
	public abstract boolean enroll(String data, byte[] hat);

	public boolean enroll(float[][] data, byte[] hat) {
		return enroll(FaceDataEncoder.encode(data), hat);
	}
}
