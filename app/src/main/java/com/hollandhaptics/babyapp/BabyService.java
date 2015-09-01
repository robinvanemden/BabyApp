/**************************************************************************
 *
 * BabyApp              BabyService
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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BabyService extends Service {

    private String TAG = "BabyService";                                 // A tag for log output
    private static final int min_free_storagespace = 10;                // min % of free storage space, required for a ftp sync
    private static final double amplitude_threshold = 1000;             // the amplitude threshold (getAmplitude returns unsigned 16-bit integer values (0-32767))
    private static final int number_of_retries = 60;                    // the number of times to (re)try measuring an amplitude above the given threshold before stopping the recording and resetting the recorder
    private static final long service_runnable_interval = 1000;         // The interval in ms with which the runnable is called to check the volume.
    private BabyServiceBinder binder;                                   // Binder for the service
    private Handler mhandler;                                           // Handlers for runnable
    private Runnable mrunnable;                                         // Runnable
    private MediaRecorder _recorder;                                    // MediaRecorder object
    private String path;                                                // A String to store the path to the folder containing the audio files
    private String current_filename;                                    // A String to store the filename of the current recording
    private boolean hasRecorded;                                        // A boolean to store if the MediaRecorder has recorded something with an amplitude higher than the threshold
    private int attempt;                                                // An int containing the number of attempts to try to measure an amplitude higher than the threshold
    private int serverResponseCode = 0;                                 // An int to store the response code returned by the server
    private String upLoadServerUri;                                     // A String to store the server's url (set as file_upload_url in strings.xml)
    private File[] uploadFiles;                                         // An array to store all audio file paths when attempting to upload the files to the server
    private String imei;                                                // An unique  ID for each phone

    /**
     * @brief Entry point of the Service.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BabyService Created");

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        imei = telephonyManager.getDeviceId();
        Log.d(TAG, "IMEI: " + imei);

        upLoadServerUri = getResources().getString(R.string.file_upload_url);
        mhandler = new Handler();

        // Get the directory for the app's audio recordings.
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "BabyApp");
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created");
        }
        path = file.toString();

        _recorder = new MediaRecorder();
    }

    /**
     * @param intent  intent
     * @param flags   flags
     * @param startId startId
     * @return Returns START_STICKY.
     * @brief Start point of service
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "BabyService onStartCommand");

        // Let the LED indicator flash so the user knows the service is running.
        FlashLED();

        // Start a new recording.
        start_new_recording();

        // Updates every x sec (set above with service_runnable_interval).
        mrunnable = new Runnable() {
            public void run() {
                // Check if we have permission to read/write files.
                if (!isExternalStorageWritable()) {
                    Log.d(TAG, "No permission to write to external storage");
                    return;
                }
                // Check if there is enough storage space left.
                if (!IsThereStorageSpace()) {
                    notifyUser("(Almost) out of storage space");
                    Log.d(TAG, "(Almost) out of storage space");
                    // No more storage space, so stop the MediaRecorder.
                    _recorder.stop();
                    if (!hasRecorded) {
                        // Nothing interesting has been recorded, thus delete the file.
                        delete_file(current_filename);
                    }
                    _recorder.reset();
                    // Start uploading all audio files in the app's folder and start a new recording.
                    uploadAudioFiles();
                    start_new_recording();
                } else {
                    // There's enough storage space so we can continue.

                    // Returns the maximum absolute amplitude that was sampled since the last call to this method.
                    int amplitude = _recorder.getMaxAmplitude();
                    Log.d(TAG, "Amplitude: " + amplitude);
                    Log.d(TAG, "Attempt: " + attempt);
                    if (amplitude > amplitude_threshold) {
                        // The amplitude of the last measurement was higher than the threshold.
                        hasRecorded = true;
                        attempt = 0;
                        // Continue recording.
                        Log.d(TAG, "Continue recording.");
                    } else {
                        // The amplitude of the last measurement wasn't higher than the threshold.
                        if (attempt >= number_of_retries) {
                            // No more attempts left, stop the MediaRecorder.
                            Log.d(TAG, "Stop recording.");
                            _recorder.stop();
                            if (hasRecorded) {
                                // Something with a high enough amplitude has been recorded, start uploading.
                                uploadAudioFiles();
                            } else {
                                // Nothing interesting has been recorded, thus delete the file.
                                delete_file(current_filename);
                            }
                            _recorder.reset();
                            // Restart the MediaRecorder.
                            start_new_recording();
                        } else {
                            // There are some attempts left, update the counter and continue.
                            attempt++;
                        }
                    }
                }
                mhandler.postDelayed(mrunnable, service_runnable_interval);
            }
        };
        mhandler.postDelayed(mrunnable, service_runnable_interval);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    /**
     * @brief Configures the MediaRecorder for a new recording.
     * This method is called by start_new_recording() before starting the MediaRecorder.
     */
    private void configure_recorder() {
        Log.d(TAG, "Configuring the MediaRecorder");
        // Sets the audio source to be used for recording.
        _recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        // Sets the format of the output file produced during recording.
        _recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        // Sets the audio encoder to be used for recording.
        _recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        // Sets the audio sampling rate for recording.
        _recorder.setAudioSamplingRate(44100);

        @SuppressLint("SimpleDateFormat") SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        Date now = new Date();
        current_filename = formatter.format(now);
        // Sets the path of the output file to be produced.
        _recorder.setOutputFile(path + "/" + imei + "_" + current_filename + ".3gpp");

        // Prepares the recorder to begin capturing and encoding data.
        try {
            _recorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }
    }

    /**
     * @brief Configure the MediaRecorder and start a new recording.
     */
    private void start_new_recording() {
        Log.d(TAG, "Starting a new recording.");
        configure_recorder();
        hasRecorded = false;
        attempt = 0;
        _recorder.start();
    }

    /**
     * @param filename The filename of the file.
     * @brief Deletes an audio file with the given filename.
     */
    private void delete_file(String filename) {
        Log.d(TAG, "Delete file:" + filename);
        File file = new File(path + "/" + filename + ".3gpp");
        final boolean delete = file.delete();
        Log.d(TAG, "Delete success:" + delete);
    }

    /**
     * @param _message The message to show.
     * @brief Makes a notification for the user
     */
    private void notifyUser(String _message) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Baby App")
                        .setContentText(_message);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity Leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        // mId = 0
        mNotificationManager.notify(0, mBuilder.build());
    }


    /**
     * @brief Handles when the Service stops.
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "BabyService Destroyed");
        ClearLED();
        super.onDestroy();
        _recorder.reset();
        _recorder.release();
    }

    /**
     * @param intent An Intent.
     * @return A new BabyServiceBinder object.
     * @brief Binds the Service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        binder = new BabyServiceBinder(this);
        return binder;
    }

    /**
     * @brief Lets the LED indicator flash.
     */
    private void FlashLED() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notif = new Notification();
        notif.ledARGB = 0xFF0000ff;
        notif.flags = Notification.FLAG_SHOW_LIGHTS;
        notif.ledOnMS = 100;
        notif.ledOffMS = 900;
        nm.notify(0, notif);
    }

    /**
     * @brief Clears the LED indicator.
     */
    private void ClearLED() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(0);
    }

    /**
     * @brief Searches for audio files in the app's folder and starts uploading them to the server.
     */
    public void uploadAudioFiles() {

        uploadFiles = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "BabyApp").listFiles();
        new Thread(new Runnable() {
            public void run() {
                //uploadFile(uploadFilePath + "" + uploadFileName);
                for (File uploadFile : uploadFiles) {
                    uploadFile(uploadFile);
                }
            }
        }).start();
    }

    /**
     * @param sourceFile The file to upload.
     * @return int          The server's response code.
     * @brief Uploads a file to the server. Is called by uploadAudioFiles().
     */
    public int uploadFile(File sourceFile) {
        HttpURLConnection conn;
        DataOutputStream dos;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1024 * 1024;
        String sourceFileUri = sourceFile.getAbsolutePath();

        if (!sourceFile.isFile()) {
            Log.e("uploadFile", "Source File does not exist :"
                    + sourceFileUri);
            return 0;
        } else {
            try {
                // open a URL connection to the Servlet
                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                URL url = new URL(upLoadServerUri);

                // Open a HTTP  connection to  the URL
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true); // Allow Inputs
                conn.setDoOutput(true); // Allow Outputs
                conn.setUseCaches(false); // Don't use a Cached Copy
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setRequestProperty("fileToUpload", sourceFileUri);

                dos = new DataOutputStream(conn.getOutputStream());

                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\";filename=\""
                        + sourceFileUri + "\"" + lineEnd);

                dos.writeBytes(lineEnd);

                // create a buffer of  maximum size
                bytesAvailable = fileInputStream.available();

                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // read file and write it into form...
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {
                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                // send multipart form data necesssary after file data...
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                // Responses from the server (code and message)
                serverResponseCode = conn.getResponseCode();
                String serverResponseMessage = conn.getResponseMessage();

                Log.i("uploadFile", "HTTP Response is : "
                        + serverResponseMessage + ": " + serverResponseCode);

                if (serverResponseCode == 200) {
                    boolean deleted = sourceFile.delete();
                    Log.i("Deleted succes", String.valueOf(deleted));
                }

                //close the streams //
                fileInputStream.close();
                dos.flush();
                dos.close();
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
                Log.e("Upload file to server", "error: " + ex.getMessage(), ex);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Upload file to server", "Exception : "
                        + e.getMessage(), e);
            }
            return serverResponseCode;
        } // End else block
    }

    /**
     * @return true  Enough. false   Too little.
     * @brief Determines if there is enough storage space.
     */
    public boolean IsThereStorageSpace() {
        // Is the amount of free space <= min_free_storagespace% of the total space, return false. Otherwise return true;
        long freespace = Environment.getExternalStorageDirectory().getFreeSpace() / 1048576;
        long totalspace = Environment.getExternalStorageDirectory().getTotalSpace() / 1048576;
        Log.d(TAG, "StorageSpace: " + String.valueOf((int) (((double) freespace / (double) totalspace) * 100)) + "% remaining.");
        return freespace > ((totalspace / 100) * min_free_storagespace);
    }

    /**
     * @return True/false.
     * @brief Checks if external storage is available for read and write.
     */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}
