package com.john1q.safeandsound;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

/**
 * Created by kkerz on 29-May-18.
 */

public class AudioRecorderThread extends Thread {
    private final AudioRecorderInterface callback;
    private final int callOnSecondsPassed;
    private final int sampleRate;

    private boolean stopped = false;

    AudioRecorderThread(AudioRecorderInterface callback, int callOnSecondsPassed, int sampleRate) {
        this.callback = callback;
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        this.callOnSecondsPassed = callOnSecondsPassed;
        this.sampleRate = sampleRate;
        start();
    }

    @Override
    public void run() {
       Log.i("AudioRecorder", "Starting Audio Thread");

       AudioRecord recorder = null;
       //AudioTrack track = null;
       short[] buffer = new short[1024];

       try {
           int N = AudioRecord.getMinBufferSize(
                   sampleRate,
                   AudioFormat.CHANNEL_IN_MONO,
                   AudioFormat.ENCODING_PCM_16BIT);

           recorder = new AudioRecord(
                   MediaRecorder.AudioSource.MIC,
                   sampleRate,
                   AudioFormat.CHANNEL_IN_MONO,
                   AudioFormat.ENCODING_PCM_16BIT,
                   N * 10);

           /*track = new AudioTrack(
                   AudioManager.STREAM_MUSIC,
                   sampleRate,
                   AudioFormat.CHANNEL_OUT_MONO,
                   AudioFormat.ENCODING_PCM_16BIT,
                   N*10,
                   AudioTrack.MODE_STREAM
           );*/

           recorder.startRecording();
           //track.play();

           while(!stopped) {
               Log.i("AudioRecorder", "Writing data to buffer");
               N = recorder.read(buffer, 0, buffer.length);
               callback.onDataReceive(normalizePCMBuffer(buffer));
           }
       }
       catch(Throwable e) {
           Log.w("AudioRecorder", "Error reading voice audio", e);
       }
       finally {
           recorder.stop();
           recorder.release();
           //track.stop();
           //track.release();
       }
    }

    public float shortMaxValue = Short.MAX_VALUE;
    public float[] normalizePCMBuffer(short[] buffer) {
        float[] normalizedBuffer = new float[buffer.length];

        for(int bufferIndex = 0; bufferIndex < buffer.length; bufferIndex++) {
            short bufferValue = buffer[bufferIndex];
            float normalizedBufferValue = (float)bufferValue / shortMaxValue;

            if(normalizedBufferValue > 1f) {
                normalizedBufferValue = 1f;
            }
            else if(normalizedBufferValue < -1f) {
                normalizedBufferValue = -1f;
            }

            normalizedBuffer[bufferIndex] = normalizedBufferValue;
        }

        return normalizedBuffer;
    }

    public void close()
    {
        stopped = true;
    }
}
