package com.libremobileos.yifan.face;

import android.content.SharedPreferences;

import java.util.Set;

public class SharedPreferencesFaceStorageBackend extends FaceStorageBackend {
	private final SharedPreferences prefs;

	public SharedPreferencesFaceStorageBackend(SharedPreferences prefs) {
		this.prefs = prefs;
	}

	@Override
	protected Set<String> getNamesInternal() {
		return prefs.getAll().keySet();
	}

	@Override
	protected boolean registerInternal(String name, String data) {
		return prefs.edit().putString(name, data).commit();
	}

	@Override
	protected String getInternal(String name) {
		return prefs.getString(name, null);
	}
}
