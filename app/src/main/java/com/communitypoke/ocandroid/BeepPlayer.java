package com.communitypoke.ocandroid;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Renders speaker component beeps as PCM sine waves on an AudioTrack.
 * Each call generates one short tone buffer and streams it.
 */
final class BeepPlayer {
    private static final int SAMPLE_RATE = 22050;

    /** Play a tone of the given frequency for the given duration in ms. */
    void play(double frequency, double durationMs) {
        int frames = Math.max(1, (int) (SAMPLE_RATE * durationMs / 1000));
        short[] pcm = new short[frames];
        for (int i = 0; i < frames; i++) {
            // Square-ish wave (OC beeps are square-ish): sine + 1/3 octave harmonic.
            double t = (double) i / SAMPLE_RATE;
            double v = Math.sin(2 * Math.PI * frequency * t)
                    + 0.3 * Math.sin(6 * Math.PI * frequency * t);
            pcm[i] = (short) (v * 0.3 * Short.MAX_VALUE);
        }
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(pcm.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        try {
            track.write(pcm, 0, pcm.length);
            track.play();
            // Release on completion instead of blocking the tick thread.
            new Thread(() -> {
                try {
                    Thread.sleep((long) durationMs + 100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                try {
                    track.stop();
                    track.release();
                } catch (IllegalStateException ignored) {
                }
            }, "beep-release").start();
        } catch (IllegalStateException ignored) {
            track.release();
        }
    }
}
