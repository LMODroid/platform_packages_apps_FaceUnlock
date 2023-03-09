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

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Base64;

public class FaceDataEncoder {
	private static final Base64.Encoder encoder = Base64.getUrlEncoder();
	private static final Base64.Decoder decoder = Base64.getUrlDecoder();

	/**
	 * Encode face model to string.
	 * @param alldata Face model.
	 * @return Encoded face model.
	 */
	public static String encode(float[][] alldata) {
		StringBuilder b = new StringBuilder();
		for (float[] data : alldata) {
			ByteBuffer buff = ByteBuffer.allocate(4 * data.length);
			for (float f : data) {
				buff.putFloat(f);
			}
			b.append(encoder.encodeToString(buff.array())).append(":");
		}
		return b.substring(0, b.length() - 1);
	}

	/**
	 * Decode face model encoded by {@link #encode(float[][])}
	 * @param data Encoded face model.
	 * @return Face model.
	 */
	public static float[][] decode(String data) {
		String[] a = data.split(":");
		float[][] f = new float[a.length][];
		int i = 0;
		for (String s : a) {
			FloatBuffer buf = ByteBuffer.wrap(decoder.decode(s)).asFloatBuffer();
			f[i] = new float[buf.capacity()];
			buf.get(f[i++]);
		}
		return f;
	}
}
