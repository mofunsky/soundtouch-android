package com.smp.soundtouchandroid;

import static com.smp.soundtouchandroid.Constants.*;

import java.io.IOException;
import java.util.Arrays;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class SoundTouchPlayable implements Runnable
{
	private Object pauseLock;
	private Object trackLock;
	private Object decodeLock;

	private Handler handler;
	private OnProgressChangedListener playbackListener;
	private SoundTouch soundTouch;
	private AudioDecoder decoder;
	private String fileName;
	private int id;
	private boolean bypassSoundTouch;

	private volatile AudioTrack track;
	private volatile boolean paused, finished;

	public interface OnProgressChangedListener
	{
		void onProgressChanged(int track, double currentPercentage, long position);

		void onTrackEnd(int track);
	}

	public long getPlayedDuration()
	{
		synchronized (decodeLock)
		{
			return decoder.getPlayedDuration();
		}
	}

	public boolean isInitialized()
	{
		return track.getState() == AudioTrack.STATE_INITIALIZED;
	}

	public int getSamplingRate()
	{
		return soundTouch.getSamplingRate();
	}

	public int getChannels()
	{
		return soundTouch.getChannels();
	}

	public void setBypassSoundTouch(boolean bypassSoundTouch)
	{
		this.bypassSoundTouch = bypassSoundTouch;
	}

	public void setTempo(float tempo)
	{
		soundTouch.setTempo(tempo);
	}

	public void setTempoChange(float tempoChange)
	{
		soundTouch.setTempoChange(tempoChange);
	}

	public void setPitchSemi(float pitchSemi)
	{
		soundTouch.setPitchSemi(pitchSemi);
	}

	public float getPitchSemi()
	{
		return soundTouch.getPitchSemi();
	}

	public float getTempo()
	{
		return soundTouch.getTempo();
	}

	public String getFileName()
	{
		return fileName;
	}

	public boolean isPaused()
	{
		return paused;
	}

	public long getDuration()
	{
		return decoder.getDuration();
	}

	public SoundTouchPlayable(OnProgressChangedListener playbackListener, String fileName, int id, float tempo, float pitchSemi)
			throws IOException
	{
		this(fileName, id, tempo, pitchSemi);
		this.playbackListener = playbackListener;
	}

	public SoundTouchPlayable(String fileName, int id, float tempo, float pitchSemi)
			throws IOException, SoundTouchAndroidException
	{
		if (Build.VERSION.SDK_INT >= 16)
		{
			decoder = new MediaCodecAudioDecoder(fileName);
		}
		else
		{
			decoder = new JLayerAudioDecoder(fileName);
		}

		this.fileName = fileName;
		this.id = id;

		handler = new Handler();

		pauseLock = new Object();
		trackLock = new Object();
		decodeLock = new Object();

		paused = true;
		finished = false;

		setupAudio(id, tempo, pitchSemi);
	}

	private void pauseWait()
	{
		synchronized (pauseLock)
		{
			while (paused)
			{
				try
				{
					pauseLock.wait();
				}
				catch (InterruptedException e)
				{
				}
			}
		}
	}

	@Override
	public void run()
	{
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

		try
		{
			while (!finished)
			{
				playFile();
				paused = true;
				if (playbackListener != null && !finished)
				{
					handler.post(new Runnable()
					{
						@Override
						public void run()
						{
							playbackListener.onTrackEnd(id);
						}
					});
				}
				decoder.resetEOS();
			}
		}
		catch (SoundTouchAndroidException e)
		{
			// need to notify...something?
			e.printStackTrace();
		}
		finally
		{
			soundTouch.clearBuffer();

			synchronized (trackLock)
			{
				track.pause();
				track.flush();
				track.release();
			}
			synchronized (decodeLock)
			{
				decoder.close();
			}
		}
	}

	public void seekTo(double percentage, boolean shouldFlush) // 0.0 - 1.0
	{
		long timeInUs = (long) (decoder.getDuration() * percentage);
		seekTo(timeInUs, shouldFlush);
	}

	public void seekTo(long timeInUs, boolean shouldFlush)
	{
		if (timeInUs < 0 || timeInUs > decoder.getDuration())
			throw new SoundTouchAndroidException("" + timeInUs + " Not a valid seek time.");

		if (shouldFlush)
		{
			this.pause();
			synchronized (trackLock)
			{
				track.flush();
			}
			soundTouch.clearBuffer();
		}
		synchronized (decodeLock)
		{
			decoder.seek(timeInUs);
		}
	}

	public int getSessionId()
	{
		return track.getAudioSessionId();
	}

	/*
	 * public AudioTrack getAudioTrack() { return track; }
	 */
	public void setVolume(float left, float right)
	{
		synchronized (trackLock)
		{
			track.setStereoVolume(left, right);
		}
	}

	public void play()
	{
		synchronized (pauseLock)
		{
			synchronized (trackLock)
			{
				track.play();
			}
			paused = false;
			finished = false;
			pauseLock.notifyAll();
		}
	}

	public void pause()
	{
		synchronized (pauseLock)
		{
			synchronized (trackLock)
			{
				track.pause();
			}
			paused = true;
		}
	}

	public void stop()
	{
		if (paused)
		{
			synchronized (pauseLock)
			{
				paused = false;
				pauseLock.notifyAll();
			}
		}
		finished = true;
	}

	private void setupAudio(int id, float tempo, float pitchSemi) throws IOException
	{
		int channels = decoder.getChannels();
		int samplingRate = decoder.getSamplingRate();

		int channelFormat = -1;

		if (channels == 1) // mono
			channelFormat = AudioFormat.CHANNEL_OUT_MONO;
		else if (channels == 2) // stereo
			channelFormat = AudioFormat.CHANNEL_OUT_STEREO;
		else
			throw new SoundTouchAndroidException("Valid channel count is 1 or 2");

		soundTouch = new SoundTouch(id, channels, samplingRate, DEFAULT_BYTES_PER_SAMPLE, tempo, pitchSemi);

		track = new AudioTrack(AudioManager.STREAM_MUSIC, samplingRate, channelFormat,
				AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE_TRACK, AudioTrack.MODE_STREAM);
	}

	private void playFile() throws SoundTouchAndroidException
	{
		int bytesReceived = 0;
		byte[] input = null;

		do
		{
			pauseWait();
			if (finished)
				break;

			if (soundTouch.getOutputBufferSize() <= MAX_OUTPUT_BUFFER_SIZE)
			{
				synchronized (decodeLock)
				{
					input = decoder.decodeChunk();

					if (playbackListener != null)
					{
						handler.post(new Runnable()
						{

							@Override
							public void run()
							{
								long pd = decoder.getPlayedDuration();
								long d = decoder.getDuration();
								double cp = pd == 0 ? 0 : (double) pd / d;
								playbackListener.onProgressChanged(id, cp, pd);
							}
						});
					}
				}

				processChunk(input, true);
			}
			else
			{
				processChunk(input, false);
			}
		}
		while (!decoder.sawOutputEOS());

		soundTouch.finish();

		do
		{
			if (finished)
				break;
			bytesReceived = processChunk(input, false);
		}
		while (bytesReceived > 0);
	}

	private int processChunk(final byte[] input, boolean putBytes) throws SoundTouchAndroidException
	{
		int bytesReceived = 0;

		if (input != null)
		{
			if (bypassSoundTouch)
			{
				bytesReceived = input.length;
			}
			else
			{
				if (putBytes)
					soundTouch.putBytes(input);

				bytesReceived = soundTouch.getBytes(input);
			}
			synchronized (trackLock)
			{
				track.write(input, 0, bytesReceived);
			}

		}
		return bytesReceived;
	}
}
