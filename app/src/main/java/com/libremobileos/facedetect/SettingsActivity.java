package com.libremobileos.facedetect;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings_activity);
		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.settings, new SettingsFragment())
					.commit();
		}
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	public static void applyMaterial3(Preference p) {
		if (p instanceof PreferenceGroup) {
			PreferenceGroup pg = (PreferenceGroup) p;
			for (int i = 0; i < pg.getPreferenceCount(); i++) {
				applyMaterial3(pg.getPreference(i));
			}
		}
		if (p instanceof SwitchPreferenceCompat) {
			p.setWidgetLayoutResource(R.layout.preference_material_switch);
		}
	}

	public static class SettingsFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.root_preferences, rootKey);
			applyMaterial3(getPreferenceScreen());
			SwitchPreferenceCompat sp = findPreference("enable");
			SwitchPreferenceCompat se = findPreference("secure");
			Preference rescan = findPreference("rescan");
			assert sp != null; assert se != null; assert rescan != null;
			sp.setEnabled(false);
			se.setEnabled(false);
			rescan.setEnabled(false);
			RemoteFaceServiceClient.connect(getActivity(), faced -> {
				boolean isEnrolled = faced.isEnrolled();
				boolean isSecure = faced.isSecure();
				requireActivity().runOnUiThread(() -> {
					rescan.setEnabled(isEnrolled);
					se.setEnabled(isEnrolled);
					se.setChecked(isEnrolled && isSecure);
					sp.setChecked(isEnrolled);
					sp.setEnabled(true);
					sp.setOnPreferenceChangeListener((preference, newValue) -> {
						if ((Boolean) newValue) {
							startActivity(new Intent(getActivity(), EnrollActivity.class));
							requireActivity().finish();
						} else {
							new Thread(() -> {
								if (!faced.unenroll()) {
									requireActivity().runOnUiThread(() -> {
										Toast.makeText(getActivity(), R.string.internal_err, Toast.LENGTH_LONG).show();
										requireActivity().finish();
									});
								}
							}).start();
							rescan.setEnabled(false);
							se.setEnabled(false);
						}
						return false;
					});
					se.setOnPreferenceChangeListener((preference, newValue) -> {
						faced.setSecure((Boolean) newValue);
						return false;
					});
				});
			});
		}
	}
}