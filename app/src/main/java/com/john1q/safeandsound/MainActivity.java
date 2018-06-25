package com.john1q.safeandsound;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;

import java.util.ArrayList;
import java.util.List;

import static android.media.AudioManager.STREAM_MUSIC;
import static com.john1q.safeandsound.R.layout.activity_main;

public class MainActivity extends AppCompatActivity {

    final int PERMISSION_REQUEST_CALLBACK = 25;

    Button toggleThreadButton;
    TextView peakText;
    ProgressBar volumeProgress;
    GraphView dataGraph;

    AudioRecorderThread thread;
    int indexOfThreadRun = 0;
    int indexToUpdateGraph = 1;
    long time = System.currentTimeMillis();
    long timeStarted = System.currentTimeMillis();
    long timeStopped = System.currentTimeMillis();
    long timeScooterStarted = System.currentTimeMillis();
    long timeScooterStopped = System.currentTimeMillis();
    long timeStartedBeep = System.currentTimeMillis();
    long timeStoppedBeep = System.currentTimeMillis();

    int changed_volume = 0;
    int changed_volume_scooter = 0;
    int changed_volume_beep = 0;
    boolean carDetectedBefore = false;
    boolean scooterDetectedBefore = false;
    boolean carDetectedBeforeBeep = false;
    private TextView textview;
    private SeekBar seekbar;






    FastFourierTransform fastFourierTransform = new FastFourierTransform(1024, 44100);


    public void toggleThread() {
        final AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        final int desired_volume = audioManager.getStreamVolume(STREAM_MUSIC);
        final int desired_volume_scooter = audioManager.getStreamVolume(STREAM_MUSIC);
        final int desired_volume_beep = audioManager.getStreamVolume(STREAM_MUSIC);
        if(thread == null) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {


                // Permission is not granted
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        PERMISSION_REQUEST_CALLBACK);
            }
            else {
                thread = new AudioRecorderThread(new AudioRecorderInterface() {
                    @Override
                    public void onDataReceive(final float[] buffer) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //Make sure that everything runs in 30fps
                                indexOfThreadRun++;
                                if (indexOfThreadRun % indexToUpdateGraph == 0) {
                                    //Evaluate Fourier Transform
                                    fastFourierTransform.forward(buffer);

                                    //Get the volume
                                    float maxAmplitude = Float.MIN_VALUE;
                                    for (float sample : buffer) {
                                        maxAmplitude = (sample > maxAmplitude) ? sample : maxAmplitude;
                                    }
                                    volumeProgress.setProgress(Math.round(100 * maxAmplitude));

                                    float[] transformValues = new float[fastFourierTransform.specSize()];
                                    float transformMax = -1;
                                    float transformAvg = 0f;
                                    for (int i = 0; i < transformValues.length; i++) {
                                        float freq = (i / 1024f) * 44100f;
                                        float invVolume = (1f - (float) Math.sqrt((freq / 20000f)));
                                        transformValues[i] = fastFourierTransform.getBand(i) * invVolume;
                                        transformMax = (transformValues[i] > transformMax) ? transformValues[i] : transformMax;
                                        transformAvg += transformValues[i];
                                    }
                                    transformAvg /= transformValues.length;

                                    //Apply low pass filter below avg value
                                        /*for(int i =0; i < transformValues.length; i++) {
                                            if(transformValues[i] < transformAvg) {
                                                transformValues[i] = 0;
                                            }
                                        }
                                        */
                                    DataPoint[] dataPoints = new DataPoint[transformValues.length];
                                    for (int i = 0; i < transformValues.length; i++) {
                                        dataPoints[i] = new DataPoint((i / 1024f) * 44100f, transformValues[i]);
                                    }
                                    //dataGraph.removeAllSeries();
                                    //LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dataPoints);
                                    //dataGraph.addSeries(series);

                                    //Find peaks
                                    List<Integer> peaks = new ArrayList<>();
                                    for (int i = 0; i < transformValues.length - 2; i++) {
                                        float slopeFirst = transformValues[i + 1] - transformValues[i];
                                        float slopeSecond = transformValues[i + 2] - transformValues[i + 1];
                                        float slopeAvg = (slopeFirst - slopeSecond) / 2f;
                                        if (slopeFirst * slopeSecond <= 0) { // changed sign?
                                            if (slopeAvg > transformMax / 6f && transformValues[i + 1] > transformAvg) {
                                                peaks.add(i + 1);
                                            }
                                        }
                                    }

                                    //Fill String data about peaks
                                    StringBuilder peakData = new StringBuilder("Peaking at: \n");
                                    boolean lowPass = false;
                                    boolean ringPass = false;
                                    boolean beep = false;
                                    for (Integer peak : peaks) {
                                        Integer freq = Math.round((peak / 1024f) * 44100f);
                                        if (freq <= 130 && freq >= 128) {
                                            lowPass = true;
                                        }
                                        if (freq <= 5500 && freq >= 5300) {
                                            ringPass = true;
                                        }
                                        if (freq <= 1500 && freq >= 1400)  {
                                            beep = true;
                                        }
                                        if ((freq <= 200 && freq >= 0) || (freq <= 6000 && freq >= 5000) || (freq <= 1500 && freq >= 1400)) {
                                            peakData.append(freq.toString());
                                            peakData.append("Hz\n");
                                        }
                                    }
                                    peakText.setText(peakData.toString());
                                    boolean carDetected = lowPass;
                                    boolean scooterDetected = ringPass;
                                    boolean carDetectedBeep = beep;
                                    time = System.currentTimeMillis();
                                    Log.i("carnow", String.valueOf(carDetected));
                                    Log.i("carbefore", String.valueOf(carDetectedBefore));
                                    Log.i("time", String.valueOf(time));
                                    Log.i("desired", String.valueOf(desired_volume));
                                    /////////SOUND VOLUME!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                                    int currentVolume = audioManager.getStreamVolume(STREAM_MUSIC);
                                    if (carDetected && carDetectedBefore) {
                                        if (time - timeStarted > 500) {
                                            carDetectedBefore = true;
                                            Log.i("AudioRecorder", "CAR IS ONCOMING");
                                            if (currentVolume != desired_volume) {
                                                changed_volume = currentVolume - desired_volume;
                                                audioManager.setStreamVolume(STREAM_MUSIC, desired_volume, AudioManager.FLAG_SHOW_UI);
                                            }
                                        }
                                    } else if (carDetected && !carDetectedBefore) {
                                        carDetectedBefore = true;
                                        timeStarted = time;
                                    } else if (!carDetected && carDetectedBefore) {
                                        carDetectedBefore = false;
                                        timeStopped = time;
                                        Log.i("AudioRecorder", "CAR IS GONE");
                                    } else if (!carDetected && !carDetectedBefore) {
                                        if (time - timeStopped > 100 && currentVolume == desired_volume) {
                                            if (changed_volume != 0) {
                                                Log.i("changed_volume", String.valueOf(changed_volume));
                                                audioManager.setStreamVolume(STREAM_MUSIC, desired_volume + changed_volume, AudioManager.FLAG_SHOW_UI);
                                                changed_volume = 0;
                                            }

                                        }
                                    }
                                    if (carDetectedBeep && carDetectedBeforeBeep) {
                                        if (time - timeStartedBeep > 500) {
                                            carDetectedBeforeBeep = true;
                                            Log.i("AudioRecorder", "CAR IS ONCOMING");
                                            if (currentVolume != desired_volume_beep) {
                                                changed_volume_beep = currentVolume - desired_volume_beep;
                                                audioManager.setStreamVolume(STREAM_MUSIC, desired_volume_beep, AudioManager.FLAG_SHOW_UI);
                                            }
                                        }
                                    } else if (carDetectedBeep && !carDetectedBeforeBeep) {
                                        carDetectedBeforeBeep = true;
                                        timeStartedBeep = time;
                                    } else if (!carDetectedBeep && carDetectedBeforeBeep) {
                                        carDetectedBeforeBeep = false;
                                        timeStoppedBeep = time;
                                        Log.i("AudioRecorder", "CAR IS GONE");
                                    } else if (!carDetectedBeep && !carDetectedBeforeBeep) {
                                        if (time - timeStoppedBeep > 100 && currentVolume == desired_volume_beep) {
                                            if (changed_volume_beep != 0) {
                                                Log.i("changed_volume", String.valueOf(changed_volume));
                                                audioManager.setStreamVolume(STREAM_MUSIC, desired_volume_beep + changed_volume_beep, AudioManager.FLAG_SHOW_UI);
                                                changed_volume_beep = 0;
                                            }

                                        }
                                    }
                                    if (scooterDetected && scooterDetectedBefore) {
                                        if (time - timeScooterStarted > 200) {
                                            scooterDetectedBefore = true;
                                            Log.i("AudioRecorder", "SCOOTER IS ONCOMING");
                                            if (currentVolume != desired_volume_scooter) {
                                                changed_volume_scooter = currentVolume - desired_volume_scooter;
                                                audioManager.setStreamVolume(STREAM_MUSIC, desired_volume_scooter, AudioManager.FLAG_SHOW_UI);
                                            }
                                        }
                                    } else if (scooterDetected && !scooterDetectedBefore) {
                                        scooterDetectedBefore = true;
                                        timeScooterStarted = time;
                                    } else if (!scooterDetected && scooterDetectedBefore) {
                                        scooterDetectedBefore = false;
                                        timeScooterStopped = time;
                                        Log.i("AudioRecorder", "SCOOTER IS GONE");
                                    } else if (!scooterDetected && !scooterDetectedBefore) {
                                        if (time - timeScooterStopped > 5000 && currentVolume == desired_volume_scooter) {
                                            if (changed_volume_scooter != 0) {
                                                Log.i("changed_volume_scooter", String.valueOf(changed_volume_scooter));
                                                audioManager.setStreamVolume(STREAM_MUSIC, desired_volume_scooter + changed_volume_scooter, AudioManager.FLAG_SHOW_UI);
                                                changed_volume_scooter = 0;
                                            }

                                        }
                                    }
                                }
                            }
                        });

                    }
                }, 1, 44100);
            }
            toggleThreadButton.setText("Stop Thread");
        }
        else {
            thread.close();
            thread = null;
            toggleThreadButton.setText("Start Thread");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(activity_main);

        toggleThreadButton = findViewById(R.id.mainActivityButtonStartThread);
        peakText = findViewById(R.id.mainActivityTextPeak);
        volumeProgress = findViewById(R.id.mainActivityProgressVolume);
        dataGraph = findViewById(R.id.mainActivityGraphData);




        //novie veshi dlya seekbara
        textview = (TextView) findViewById(R.id.textView);
        seekbar = (SeekBar)findViewById(R.id.seekBar);
        final AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int currentVolume = audioManager.getStreamVolume(STREAM_MUSIC);
        final int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);



        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textview.setText("" + progress + "%");
                int seekVolume = (int) (progress * 0.15);
                audioManager.setStreamVolume(STREAM_MUSIC, seekVolume, AudioManager.FLAG_SHOW_UI);


            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //get the volume you need to hear the cars and music
                Toast.makeText(getApplicationContext() , "You now saved the desired volume level" , Toast.LENGTH_LONG).show();

            }
        });

        toggleThreadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleThread();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CALLBACK: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    toggleThread();
                }
                else {
                    Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onStop() {
        thread.close();
        super.onStop();
    }
}