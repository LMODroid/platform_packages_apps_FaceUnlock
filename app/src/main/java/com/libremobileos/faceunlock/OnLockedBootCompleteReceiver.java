package com.libremobileos.faceunlock;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;

import android.util.Log;

public class OnLockedBootCompleteReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "FaceUnlockBootReceiverService";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.i(LOG_TAG, "onBoot");

        Intent sIntent = new Intent(context, FaceUnlockService.class);
        context.startService(sIntent);
    }
}
