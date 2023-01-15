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
			t.setText("Click Next to start!");
			findViewById(R.id.button).setOnClickListener(v -> {
				startActivity(new Intent(this, ScanActivity.class));
				finish();
			});
			findViewById(R.id.button2).setOnClickListener(v -> {
				startActivity(new Intent(this, MainActivity.class));
				finish();
			});
			return;
		}
		findViewById(R.id.button2).setVisibility(View.GONE);
		findViewById(R.id.button).setOnClickListener(v -> {
			startActivity(new Intent(this, MainActivity.class));
			finish();
		});
		RemoteFaceServiceClient.connect(this, faced -> {
			if (!faced.enroll(getIntent().getStringExtra("faces"))) {
				runOnUiThread(() -> t.setText("oops something's wrong"));
			} else {
				runOnUiThread(() -> t.setText(
						"Face Unlock will unlock your phone even if it's not your face. If you don't want that, stop reading and go earn some money to buy an iPhone. Thank you."));
			}
		});
	}
}
