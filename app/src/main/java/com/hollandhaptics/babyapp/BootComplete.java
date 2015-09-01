/**************************************************************************
 *
 * BabyApp              BootComplete
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

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;

public class BootComplete extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
            Intent serviceIntent = new Intent(context, BabyService.class);
            context.startService(serviceIntent);
        }
    }
}