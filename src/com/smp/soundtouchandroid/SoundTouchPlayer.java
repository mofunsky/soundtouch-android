package com.smp.soundtouchandroid;

import static com.smp.soundtouchandroid.Constants.DEFAULT_BYTES_PER_SAMPLE;

import java.io.IOException;

import android.media.AudioTrack;

public class SoundTouchPlayer extends SoundTouchPlayableBase
{
	private AudioSinkAudioTrack track;
	
	public SoundTouchPlayer(String fileName, int id, float tempo,
			float pitchSemi) throws IOException, SoundTouchAndroidException
	{
		super(fileName, id, tempo, pitchSemi);
		// TODO Auto-generated constructor stub
	}
	public int getSessionId()
	{
		return track.getAudioSessionId();
	}
	public long getAudioTrackBufferSize()
	{
		synchronized (sinkLock)
		{
			long playbackHead = track.getPlaybackHeadPosition() & 0xffffffffL;
			return bytesWritten - playbackHead * DEFAULT_BYTES_PER_SAMPLE
					* getChannels();
		}

	}
	public void setVolume(float left, float right)
	{
		synchronized (sinkLock)
		{
			track.setStereoVolume(left, right);
		}
	}
	public boolean isInitialized()
	{
		return track.getState() == AudioTrack.STATE_INITIALIZED;
	}
	public void seekTo(double percentage, boolean shouldFlush) // 0.0 - 1.0
	{
		long timeInUs = (long) (decoder.getDuration() * percentage);
		seekTo(timeInUs, shouldFlush);
	}

	public void seekTo(long timeInUs, boolean shouldFlush)
	{
		if (timeInUs < 0 || timeInUs > decoder.getDuration())
			throw new SoundTouchAndroidException("" + timeInUs
					+ " Not a valid seek time.");

		if (shouldFlush)
		{
			this.pause();
			synchronized (sinkLock)
			{
				track.flush();
				bytesWritten = 0;
			}
			soundTouch.clearBuffer();
		}
		synchronized (decodeLock)
		{
			decoder.seek(timeInUs);
		}
	}
	@Override
	public void onStart()
	{
		synchronized (sinkLock)
		{
			track.play();
		}
	}
	@Override
	public void onPause()
	{
		synchronized (sinkLock)
		{
			track.pause();
		}	
	}
	@Override
	public void onStop()
	{
		// TODO Auto-generated method stub
		
	}
	@Override
	public void seekTo(long timeInUs)
	{
		seekTo(timeInUs, false);
	}
	
}
