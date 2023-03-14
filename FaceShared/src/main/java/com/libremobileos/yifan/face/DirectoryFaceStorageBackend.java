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

package com.libremobileos.yifan.face;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * {@link FaceStorageBackend} to store data in a directory. Directory must not contain files other than these created by this class!
 */
public class DirectoryFaceStorageBackend extends FaceStorageBackend {
	private final File dir;

	public DirectoryFaceStorageBackend(File dir) {
		this.dir = dir;
		if (!dir.exists()) {
			throw new IllegalArgumentException("directory.exists() == false");
		}
		if (!dir.isDirectory()) {
			throw new IllegalArgumentException("directory.isDirectory() == false");
		}
		if (!dir.canRead()) {
			throw new IllegalArgumentException("directory.canRead() == false");
		}
		if (!dir.canWrite()) {
			throw new IllegalArgumentException("directory.canWrite() == false");
		}
	}

	@Override
	protected Set<String> getNamesInternal() {
		// Java...
		return new HashSet<>(Arrays.asList(Objects.requireNonNull(dir.list())));
	}

	@Override
	protected boolean registerInternal(String name, String data, boolean duplicate) {
		File f = new File(dir, name);
		try {
			if (f.exists()) {
				if (!duplicate)
					throw new IOException("f.exists() && !duplicate == true");
			} else {
				if (!f.createNewFile())
					throw new IOException("f.createNewFile() failed");
			}
			OutputStreamWriter faceOSW = new OutputStreamWriter(new FileOutputStream(f));
			faceOSW.write(data);
			faceOSW.close();
			return true;
		} catch (IOException e) {
			Log.e("DirectoryFaceStorageBackend", Log.getStackTraceString(e));
		}
		return false;
	}

	@Override
	protected String getInternal(String name) {
		File f = new File(dir, name);
		try {
			if (!f.exists()) {
				throw new IOException("f.exists() == false");
			}
			if (!f.canRead()) {
				throw new IOException("f.canRead() == false");
			}
			try (InputStream inputStream = new FileInputStream(f)) {
				// https://stackoverflow.com/a/35446009
				ByteArrayOutputStream result = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				for (int length; (length = inputStream.read(buffer)) != -1; ) {
					result.write(buffer, 0, length);
				}
				// ignore the warning, api 33-only stuff right there :D
				return result.toString(StandardCharsets.UTF_8.name());
			}
		} catch (IOException e) {
			Log.e("DirectoryFaceStorageBackend", Log.getStackTraceString(e));
		}
		return null;
	}

	@Override
	protected boolean deleteInternal(String name) {
		File f = new File(dir, name);
		try {
			if (!f.exists()) {
				throw new IOException("f.exists() == false");
			}
			if (!f.canWrite()) {
				throw new IOException("f.canWrite() == false");
			}
			return f.delete();
		} catch (IOException e) {
			Log.e("DirectoryFaceStorageBackend", Log.getStackTraceString(e));
		}
		return false;
	}
}
