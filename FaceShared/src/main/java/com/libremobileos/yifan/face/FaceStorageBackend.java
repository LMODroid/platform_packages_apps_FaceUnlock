/**
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

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

/** Store Faces on disk (or in memory). This abstract class already performs error checking, caching and data type conversion for users. */
public abstract class FaceStorageBackend {
	private final Base64.Encoder encoder = Base64.getUrlEncoder();
	private final Base64.Decoder decoder = Base64.getUrlDecoder();
	protected Set<String> cachedNames = null;
	protected HashMap<String, float[][]> cachedData = null;

	public FaceStorageBackend() {
		flushCache();
	}

	public Set<String> getNames() {
		Set<String> result = getNamesCached();
		if (result != null) return result;
		return (cachedNames = getNamesInternal().stream().map(v -> new String(decoder.decode(v), StandardCharsets.UTF_8)).collect(Collectors.toSet()));
	}

	public boolean register(String rawname, float[][] alldata) {
		String name = encoder.encodeToString(rawname.getBytes(StandardCharsets.UTF_8));
		if (getNamesInternal().contains(name))
			return false;
		cachedNames.add(rawname);
		cachedData.put(rawname, alldata);
		StringBuilder b = new StringBuilder();
		for (float[] data : alldata) {
			ByteBuffer buff = ByteBuffer.allocate(4 * data.length);
			for (float f : data) {
				buff.putFloat(f);
			}
			b.append(encoder.encodeToString(buff.array())).append(":");
		}
		return registerInternal(name, b.substring(0, b.length() - 1));
	}

	public boolean register(String rawname, float[] alldata) {
		return register(rawname, new float[][] { alldata });
	}

	public float[][] get(String name) {
		float[][] f = getCached(name);
		if (f != null) return f;
		String[] a = getInternal(encoder.encodeToString(name.getBytes(StandardCharsets.UTF_8))).split(":");
		f = new float[a.length][];
		int i = 0;
		for (String s : a) {
			FloatBuffer buf = ByteBuffer.wrap(decoder.decode(s)).asFloatBuffer();
			f[i] = new float[buf.capacity()];
			buf.get(f[i++]);
		}
		cachedData.put(name, f);
		return f;
	}

	protected abstract Set<String> getNamesInternal();
	protected abstract boolean registerInternal(String name, String data);
	protected abstract String getInternal(String name);

	protected @Nullable Set<String> getNamesCached() {
		return cachedNames;
	}
	protected @Nullable float[][] getCached(String name) {
		return cachedData.get(name);
	}
	protected void flushCache() {
		cachedNames = null;
		cachedData = new HashMap<>();
	}
}
