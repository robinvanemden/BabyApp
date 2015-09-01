# BabyApp Smart Audio Recorder

The BabyApp is a smart Audio Recorder that is triggered when ambient noise exceeds a preset threshold and uploads the consequent audio files to a webserver. 

# Specification

The app starts an Android background Service which measures the current sound level as registered by the microphone of an Android Smartphone. It will start an audio recording if the sound level exceeds a preset value.
Consequently, the app will send or upload the recorded audio files to a predefined LAMP server running a simple PHP script. This PHP script saves the audio file in a directory on the LAMP server.

# Download

You can download the latest version of the Android (APK) App here:

https://github.com/robinvanemden/BabyApp/blob/master/app/app-release.apk?raw=true

# Developers    

Johny Gorissen, johny.gorissen@hollandhaptics.com

Robin van Emden, r.a.van.emden@vu.nl / robinvanemden@gmail.com

#Acknowledgements

The BabyApp has been commisioned by Hans IJzerman of the VU University Amsterdam Emotion Regulation Lab as part of his research into Social Thermoregulatory patterns in mothers and newborns. Development by Johny Gorissen of Holland Haptics and Robin van Emden of the VU University Amsterdam Emotion Regulation Lab

Amsterdam Emotion Regulation lab, http://emotionregulationlab.com/

Hans IJzerman, https://sites.google.com/site/hijzerman/

Holland Haptics, http://myfrebble.com/

Robin van Emden, http://www.pavlov.io/

Cor Wit Fonds, http://www.corwitfonds.nl/

#License 

Attribution-ShareAlike 4.0 International
http://creativecommons.org/licenses/by-sa/4.0/
