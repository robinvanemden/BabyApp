/**************************************************************************
 *
 * BabyApp              MainActivity
 *
 * Version:             1.0.1
 *
 * The BabyApp starts an Android background Service which measures
 * the current sound level as registered by the microphone of an Android Smartphone.
 * It will start an audio recording if the sound level exceeds a preset value.
 * Consequently, the app will send or upload the recorded audio files to a
 * predefined LAMP server running a simple PHP script. This PHP script saves
 * the audio file in a directory on the lamp server.
 *
 * Authors:             johny.gorissen@hollandhaptics.com
 *                      r.a.van.emden@vu.nl / robinvanemden@gmail.com
 *
 * Attribution:         Hans IJzerman, https://sites.google.com/site/hijzerman/
 *                      Holland Haptics, http://myfrebble.com/
 *                      Robin van Emden, http://www.pavlov.io/
 *
 * License:             Attribution-ShareAlike 4.0 International
 *                      http://creativecommons.org/licenses/by-sa/4.0/
 *
 **************************************************************************/

package com.hollandhaptics.babyapp;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {

    private TextView MainLB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        MainLB = (TextView) findViewById(R.id.LBmain);

        startbabyService();

        if (GetRunningServices()) {
            MainLB.setText("Service State: Running");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    // Switched to other activity remove external handlers
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    // Start baby service
    private void startbabyService() {
        // We are already running the babyapp service
        if (GetRunningServices()) {
            MainLB.setText("Service State: Running");
            return;
        }
        // Start the service
        startService(new Intent(this, BabyService.class));
        MainLB.setText("Service State: Running");
    }

    private boolean GetRunningServices() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        for (RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals("com.hollandhaptics.babyapp.BabyService")) {
                String TAG = "MainActivity";
                Log.d(TAG, "BabyService is already running");
                return true;
            }
        }
        return false;
    }


}
