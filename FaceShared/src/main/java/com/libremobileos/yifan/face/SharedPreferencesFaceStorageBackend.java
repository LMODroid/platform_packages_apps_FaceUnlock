package com.libremobileos.yifan.face;

import android.content.SharedPreferences;

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
		return prefs.getAll().keySet();
	}

	@Override
	protected boolean registerInternal(String name, String data, boolean replace) {
		return prefs.edit().putString(name, data).commit();
	}

	@Override
	protected String getInternal(String name) {
		return prefs.getString(name, null);
	}

	@Override
	protected boolean deleteInternal(String name) {
		return prefs.edit().remove(name).commit();
	}
}
