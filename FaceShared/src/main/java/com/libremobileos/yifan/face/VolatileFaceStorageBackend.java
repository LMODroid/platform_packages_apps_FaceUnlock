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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** In-memory FaceStorageBackend, bypassing encoding and storage, relying on cache entirely for performance */
@SuppressWarnings("unused")
public class VolatileFaceStorageBackend extends FaceStorageBackend {

	public VolatileFaceStorageBackend() {
		super();
		cachedNames = new HashSet<>();
		cachedData = new HashMap<>();
	}

	@Override
	protected Set<String> getNamesInternal() {
		throw new RuntimeException("Stub!");
	}

	@Override
	protected boolean registerInternal(String name, String data, boolean duplicate) {
		throw new RuntimeException("Stub!");
	}

	@Override
	protected String getInternal(String name) {
		throw new RuntimeException("Stub!");
	}

	@Override
	protected boolean deleteInternal(String name) {
		return true;
	}

	@Override
	public Set<String> getNames() {
		return getNamesCached();
	}

	@Override
	public boolean register(String rawname, float[][] alldata, boolean replace) {
		cachedNames.add(rawname);
		cachedData.put(rawname, alldata);
		return true;
	}

	@Override
	public float[][] get(String name) {
		return getCached(name);
	}
}
