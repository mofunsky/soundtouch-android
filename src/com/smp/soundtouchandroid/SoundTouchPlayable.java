package com.smp.soundtouchandroid;

import static com.smp.soundtouchandroid.Constants.*;

import java.io.IOException;
import java.util.Arrays;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.widget.Toast;

public class SoundTouchPlayable implements Runnable
{
	private Object pauseLock;
	private Object trackLock;
	private Object decodeLock;

	private Handler handler;
	private PlaybackProgressListener playbackListener;
	private SoundTouch soundTouch;
	private volatile AudioTrack track;
	private Mp3Decoder decoder;
	private String fileName;
	private int id;

	private volatile boolean paused, finished;

	public String getFileName()
	{
		return fileName;
	}
	public boolean isPaused()
	{
		return paused;
	}

	public SoundTouchPlayable(PlaybackProgressListener playbackListener, String fileName, int id, float tempo, int pitchSemi)
			throws IOException
	{
		this(fileName, id, tempo, pitchSemi);
		this.playbackListener = playbackListener;
	}

	public SoundTouchPlayable(String fileName, int id, float tempo, int pitchSemi)
			throws IOException
	{
		if (Build.VERSION.SDK_INT >= 16)
		{
			decoder = new MediaCodecMp3Decoder(fileName);
		}
		else
		{
			decoder = new JLayerMp3Decoder(fileName);
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

			decoder.close();
		}
	}

	public void seekTo(double percentage) // 0.0 - 1.0
	{
		long timeInUs = (long) (decoder.getDuration() * percentage);
		seekTo(timeInUs);
	}

	public void seekTo(long timeInUs)
	{
		if (timeInUs < 0 || timeInUs >= decoder.getDuration())
			throw new SoundTouchAndroidException("" + timeInUs + " Not a valid seek time.");

		this.pause();
		synchronized (trackLock)
		{
			track.flush();
		}
		soundTouch.clearBuffer();
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
	public AudioTrack getAudioTrack()
	{
		return track;
	}
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

	private void setupAudio(int id, float tempo, int pitchSemi)
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
							double cp;
							long pd;
							long d;
							pd = decoder.getPlayedDuration();
							d = decoder.getDuration();
							cp = pd == 0 ? 0 : (double) pd / d;
							playbackListener.onProgressChanged(cp);
						}
					});

				}
			}

			processChunk(input, false);
		}
		while (!decoder.sawOutputEOS());

		soundTouch.finish();

		do
		{
			if (finished)
				break;
			bytesReceived = processChunk(input, true);
		}
		while (bytesReceived > 0);
	}

	private int processChunk(final byte[] input, boolean finishing) throws SoundTouchAndroidException
	{
		int bytesReceived = 0;
		
		if (input != null)
		{
			if (!finishing)
				soundTouch.putBytes(input);

			bytesReceived = soundTouch.getBytes(input);

			synchronized (trackLock)
			{
				track.write(input, 0, bytesReceived);
			}
			
		}
		return bytesReceived;
	}
}
