package com.smp.soundtouchandroid;

import android.media.AudioTrack;

public class AudioSinkAudioTrack extends AudioTrack implements AudioSink
{
	public AudioSinkAudioTrack(int streamType, int sampleRateInHz,
			int channelConfig, int audioFormat, int bufferSizeInBytes, int mode)
			throws IllegalArgumentException
	{
		super(streamType, sampleRateInHz, channelConfig, audioFormat,
				bufferSizeInBytes, mode);
	}

	@Override
	public void close()
	{
		pause();
		flush();
		release();
	}
}
