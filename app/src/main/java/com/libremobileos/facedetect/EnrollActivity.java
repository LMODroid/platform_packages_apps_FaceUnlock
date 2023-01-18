package com.libremobileos.facedetect;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.libremobileos.yifan.face.FaceDataEncoder;

public class EnrollActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_enroll);
		FrameLayout f = findViewById(R.id.frameLayout);
		getLayoutInflater().inflate(R.layout.enroll_finish, f);
		TextView t = f.findViewById(R.id.textView2);
		if (getIntent() == null || !getIntent().hasExtra("faces")) {
			t.setText(R.string.welcome_text);
			findViewById(R.id.button).setOnClickListener(v -> {
				startActivity(new Intent(this, ScanActivity.class));
				finish();
			});
			findViewById(R.id.button2).setOnClickListener(v -> {
				startActivity(new Intent(this, SettingsActivity.class));
				finish();
			});
			return;
		}
		findViewById(R.id.button2).setVisibility(View.GONE);
		findViewById(R.id.button).setOnClickListener(v -> {
			startActivity(new Intent(this, SettingsActivity.class));
			finish();
		});
		RemoteFaceServiceClient.connect(this, faced -> {
			if (!faced.enroll(getIntent().getStringExtra("faces"), new byte[0])) {
				runOnUiThread(() -> t.setText(R.string.register_failed));
			} else {
				runOnUiThread(() -> t.setText(
						R.string.finish_msg));
			}
		});
	}
}
