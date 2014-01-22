package com.smp.soundtouchandroid;

import static com.smp.soundtouchandroid.Constants.*;

import java.io.IOException;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.widget.Toast;

public class SoundTouchPlayable implements Runnable
{
	private Object pauseLock;
	private Object trackLock;
	private Object decodeLock;

	private SoundTouch soundTouch;
	private volatile AudioTrack track;
	private Mp3Decoder file;
	private int id;

	private volatile boolean paused, finished;

	public SoundTouchPlayable(String file, int id, float tempo, int pitchSemi)
			throws IOException
	{
		if (Build.VERSION.SDK_INT >= 16)
		{
			this.file = new MediaCodecMp3Decoder(file);
		}
		else
		{
			this.file = new JLayerMp3Decoder(file);
		}

		setup(id, tempo, pitchSemi);
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
				pauseWait();
				playFile();

				paused = true;
				file.resetEOS();
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

			file.close();
		}
	}

	public void seekTo(double percentage)
	{
		long timeInUs = (long) (file.getDuration() * (percentage / 100.0));
		seekTo(timeInUs);
	}

	public void seekTo(long timeInUs)
	{
		if (timeInUs < 0 || timeInUs >= file.getDuration())
			throw new SoundTouchAndroidException("" + timeInUs + " Not a valid seek time.");

		this.pause();
		synchronized (trackLock)
		{
			track.flush();
		}
		soundTouch.clearBuffer();
		synchronized (decodeLock)
		{
			file.seek(timeInUs);
		}
	}

	public AudioTrack getAudioTrack()
	{
		return track;
	}

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

	private void setup(int id, float tempo, int pitchSemi)
	{
		this.id = id;

		pauseLock = new Object();
		trackLock = new Object();
		decodeLock = new Object();

		paused = true;
		finished = false;

		int channels = file.getChannels();
		int samplingRate = file.getSamplingRate();

		int channelFormat = -1;
		if (channels == 1) // mono
			channelFormat = AudioFormat.CHANNEL_OUT_MONO;
		if (channels == 2) // stereo
			channelFormat = AudioFormat.CHANNEL_OUT_STEREO;

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
			if (finished)
				break;

			synchronized (decodeLock)
			{
				input = file.decodeChunk();
			}

			processChunk(input, false);
		}
		while (!file.sawOutputEOS());

		soundTouch.finish();

		do
		{
			if (finished)
				break;
			bytesReceived = processChunk(input, true);
		}
		while (bytesReceived > 0);
	}

	private int processChunk(byte[] input, boolean finishing) throws SoundTouchAndroidException
	{
		pauseWait();
		
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
