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

import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link FaceStorageBackend} storing data in {@link SharedPreferences}
 */
public class SharedPreferencesFaceStorageBackend extends FaceStorageBackend {
	private final SharedPreferences prefs;

	/**
	 * Create/load {@link SharedPreferencesFaceStorageBackend}
	 * @param prefs {@link SharedPreferences} to use
	 */
	public SharedPreferencesFaceStorageBackend(SharedPreferences prefs) {
		this.prefs = prefs;
	}

	@Override
	protected Set<String> getNamesInternal() {
		Set<String> allkeys = prefs.getAll().keySet();
		Set<String> faceNames = new HashSet<>();
		for (String key : allkeys) {
			if (!key.endsWith("_hat")) {
				faceNames.add(key);
			}
		}
		return faceNames;
	}

	@Override
	protected boolean registerInternal(String name, String data, String hat, boolean replace) {
		return prefs.edit().putString(name, data).commit() &&
				prefs.edit().putString(name + "_hat", hat).commit();
	}

	@Override
	protected String getFaceInternal(String name) {
		return prefs.getString(name, null);
	}

	@Override
	protected String getFaceHatInternal(String name) {
		return prefs.getString(name + "_hat", null);
	}

	@Override
	protected boolean deleteInternal(String name) {
		return prefs.edit().remove(name).commit();
	}
}
