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

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Store Faces on disk (or in memory, or anywhere else, really).
 * This abstract class already performs error checking, caching and data type conversion for both users and implementations.
 * Creating a new implementation only requires any key-value store that can store Base64-encoded strings.
 * An implementation is required to use this class.
 * @see VolatileFaceStorageBackend
 * @see SharedPreferencesFaceStorageBackend
 */
public abstract class FaceStorageBackend {
	private static final Base64.Encoder encoder = Base64.getUrlEncoder();
	private static final Base64.Decoder decoder = Base64.getUrlDecoder();

	/* package-private */ Set<String> cachedNames = null;
	/* package-private */ HashMap<String, float[][]> cachedFaceData = null;
	/* package-private */ HashMap<String, byte[]> cachedHatData = null;

	public FaceStorageBackend() {
		flushCache();
	}

	/**
	 * Get all known faces
	 * @return {@link Set} of all known faces (names only)
	 */
	public Set<String> getNames() {
		Set<String> result = getNamesCached();
		if (result != null) return result;
		return (cachedNames = getNamesInternal().stream().map(v -> new String(decoder.decode(v), StandardCharsets.UTF_8)).collect(Collectors.toSet()));
	}

	/**
	 * Register/store new face.
	 * @param rawname Name of the face, needs to be unique.
	 * @param alldata Face detection model data to store.
	 * @param hat Hardware Authentication Token data to store.
	 * @param replace Allow replacing an already registered face (based on name). If false and it's still attempted, the method returns false and does nothing.
	 * @return If registering was successful.
	 * @see #register(String, float[][], byte[])
	 * @see #register(String, float[], byte[])
	 */
	public boolean register(String rawname, float[][] alldata, byte[] hat, boolean replace) {
		String name = encoder.encodeToString(rawname.getBytes(StandardCharsets.UTF_8));
		boolean duplicate = getNamesInternal().contains(name);
		if (duplicate && !replace) {
			return false;
		}
		String hatString = encoder.encodeToString(hat);
		if (cachedNames != null) {
			cachedNames.add(rawname);
			cachedFaceData.put(rawname, alldata);
			cachedHatData.put(rawname, hat);
		} else {
			flushCache();
		}
		return registerInternal(name, FaceDataEncoder.encode(alldata), hatString, duplicate);
	}

	/**
	 * Register/store new face. Calls {@link #register(String, float[][], byte[], boolean)} and does not allow replacements.
	 * @param rawname Name of the face, needs to be unique.
	 * @param alldata Face detection model data to store.
	 * @param hat Hardware Authentication Token data to store.
	 * @return If registering was successful.
	 * @see #register(String, float[][], byte[], boolean)
	 * @see #register(String, float[], byte[])
	 */
	public boolean register(String rawname, float[][] alldata, byte[] hat) {
		return register(rawname, alldata, hat, false);
	}

	/**
	 * Store 1D face model by converting it to 2D and then calling {@link #register(String, float[][], byte[])}.<br>
	 * Implementation looks like this: <code>return register(rawname, new float[][] { alldata })</code>).<br>
	 * @param rawname Name of the face, needs to be unique.
	 * @param alldata 1D face detection model data to store.
	 * @param hat Hardware Authentication Token data to store.
	 * @return If registering was successful.
	 * @see #register(String, float[][], byte[], boolean)
	 * @see #register(String, float[][], byte[])
	 */
	public boolean register(String rawname, float[] alldata, byte[] hat) {
		return register(rawname, new float[][] { alldata }, hat);
	}

	/**
	 * Adds 1D face model to existing 2D face model to improve accuracy.
	 * @param rawname Name of the face, needs to be unique.
	 * @param alldata 1D face detection model data to store
	 * @param hat Hardware Authentication Token data to store.
	 * @param add If the face doesn't already exist, can we create it?
	 * @return If registering was successful.
	 */
	public boolean extendRegistered(String rawname, float[] alldata, byte[] hat, boolean add) {
		if (!getNames().contains(rawname)) {
			if (!add)
				return false;
			return register(rawname, alldata, hat);
		}
		float[][] array1 = getFace(rawname);
		float[][] combinedArray = new float[array1.length + 1][];
		System.arraycopy(array1, 0, combinedArray, 0, array1.length);
		combinedArray[array1.length] = alldata;
		return register(rawname, combinedArray, hat, true);
	}

	/**
	 * Load 2D face model from storage.
	 * @param name The name of the face to load.
	 * @return The face model.
	 */
	public float[][] getFace(String name) {
		float[][] f = getFaceCached(name);
		if (f != null) return f;
		f = FaceDataEncoder.decode(getFaceInternal(encoder.encodeToString(name.getBytes(StandardCharsets.UTF_8))));
		cachedFaceData.put(name, f);
		return f;
	}

	public byte[] getFaceHat(String name) {
		byte[] f = getFaceHatCached(name);
		if (f != null) return f;
		f = decoder.decode(getFaceHatInternal(encoder.encodeToString(name.getBytes(StandardCharsets.UTF_8))));
		cachedHatData.put(name, f);
		return f;
	}

	/**
	 * Delete all references to a face.
	 * @param name The face to delete.
	 * @return If deletion was successful.
	 */
	@SuppressWarnings("unused")
	public boolean delete(String name) {
		cachedNames.remove(name);
		cachedFaceData.remove(name);
		cachedHatData.remove(name);
		return deleteInternal(encoder.encodeToString(name.getBytes(StandardCharsets.UTF_8)));
	}

	/**
	 * Get all known faces
	 * @return {@link Set} of all known faces (names only)
	 */
	protected abstract Set<String> getNamesInternal();
	/**
	 * Register/store new face.
	 * @param name Name of the face, needs to be unique.
	 * @param data Face detection model data to store.
	 * @param duplicate Only true if we are adding a duplicate and want to replace the saved one.
	 * @return If registering was successful.
	 */
	protected abstract boolean registerInternal(String name, String data, String hat, boolean duplicate);
	/**
	 * Load 2D face model from storage.
	 * @param name The name of the face to load.
	 * @return The face model.
	 */
	protected abstract String getFaceInternal(String name);
	/**
	 * Load Hardware Authentication Token data from storage.
	 * @param name The name of the face to load.
	 * @return The face model.
	 */
	protected abstract String getFaceHatInternal(String name);
	/**
	 * Delete all references to a face.
	 * @param name The face to delete.
	 * @return If deletion was successful.
	 */
	protected abstract boolean deleteInternal(String name);

	/* package-private */ @Nullable Set<String> getNamesCached() {
		return cachedNames;
	}
	/* package-private */ @Nullable float[][] getFaceCached(String name) {
		return cachedFaceData.get(name);
	}
	/* package-private */ @Nullable byte[] getFaceHatCached(String name) {
		return cachedHatData.get(name);
	}
	private void flushCache() {
		cachedNames = null;
		cachedFaceData = new HashMap<>();
		cachedHatData = new HashMap<>();
	}
}
