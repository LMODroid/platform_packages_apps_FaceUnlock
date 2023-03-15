package com.libremobileos.faceunlock;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.libremobileos.yifan.face.DirectoryFaceStorageBackend;
import com.libremobileos.yifan.face.FaceDataEncoder;
import com.libremobileos.yifan.face.FaceStorageBackend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.Consumer;

public abstract class RemoteFaceServiceClient {
	public static final String FACE = "Face";
	public static final String SECURE = "secure";

	public static void connect(String dir, Consumer<RemoteFaceServiceClient> callback) {
		new Thread(() -> {
			FaceStorageBackend s = new DirectoryFaceStorageBackend(new File(dir + "/faces"));
			callback.accept(new RemoteFaceServiceClient() {

				@Override
				public boolean isEnrolled() {
					return s.getNames().contains(FACE);
				}

				@Override
				public boolean isSecure() {
					return false;
				}

				@Override
				public void setSecure(boolean secure) {
				}

				@Override
				public boolean unenroll() {
					boolean result = s.delete(FACE);
					if (result) {
						File f = new File(dir, ".FACE_HAT");
						if (f.exists()) {
							f.delete();
						}
					}
					return result;
				}

				@Override
				public boolean enroll(String data, byte[] hat) {
					boolean result = s.register(FACE, FaceDataEncoder.decode(data), true);
					if (result) {
						File f = new File(dir, ".FACE_HAT");
						try {
							if (f.exists()) {
								f.delete();
							} else {
								if (!f.createNewFile())
									throw new IOException("f.createNewFile() failed");
							}
							OutputStreamWriter hatOSW = new OutputStreamWriter(new FileOutputStream(f));
							hatOSW.write(new String(Base64.encode(hat, Base64.URL_SAFE)));
							hatOSW.close();
						} catch (IOException e) {
							Log.e("RemoteFaceServiceClient", "Failed to write HAT", e);
							return false;
						}
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
