package com.smp.soundtouchandroid;

import static com.smp.soundtouchandroid.Constants.*;

import java.io.IOException;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;

public class SoundTouchPlayable implements Runnable
{
	private static final long NOT_SET = Long.MIN_VALUE;

	private Object pauseLock;
	private Object trackLock;
	private Object decodeLock;

	private Handler handler;
	private OnProgressChangedListener playbackListener;
	private SoundTouch soundTouch;

	private String fileName;
	private int id;
	private boolean bypassSoundTouch;

	private volatile long bytesWritten;
	private volatile long loopStart = NOT_SET;
	private volatile long loopEnd = NOT_SET;
	private volatile AudioTrack track;
	private volatile AudioDecoder decoder;
	private volatile boolean paused, finished;

	public interface OnProgressChangedListener
	{
		void onProgressChanged(int track, double currentPercentage,
				long position);

		void onTrackEnd(int track);
	}

	public long getBytesWritten()
	{
		return bytesWritten;
	}

	public int getChannels()
	{
		return soundTouch.getChannels();
	}

	public long getDuration()
	{
		return decoder.getDuration();
	}

	public String getFileName()
	{
		return fileName;
	}

	public long getPlayedDuration()
	{
		synchronized (decodeLock)
		{
			return decoder.getPlayedDuration();
		}
	}

	public float getPitchSemi()
	{
		return soundTouch.getPitchSemi();
	}

	public long getPlaybackLimit()
	{
		return loopEnd;
	}

	public int getSamplingRate()
	{
		return soundTouch.getSamplingRate();
	}

	public int getSessionId()
	{
		return track.getAudioSessionId();
	}

	public long getSoundTouchBufferSize()
	{
		return soundTouch.getOutputBufferSize();
	}

	public long getAudioTrackBufferSize()
	{
		synchronized (trackLock)
		{
			long playbackHead = track.getPlaybackHeadPosition() & 0xffffffffL;
			return bytesWritten - playbackHead * DEFAULT_BYTES_PER_SAMPLE
					* getChannels();
		}

	}

	public float getTempo()
	{
		return soundTouch.getTempo();
	}

	public long getLoopEnd()
	{
		return loopEnd;
	}

	public long getLoopStart()
	{
		return loopStart;
	}

	public boolean isInitialized()
	{
		return track.getState() == AudioTrack.STATE_INITIALIZED;
	}
	
	public boolean isFinished()
	{
		return finished;
	}

	public boolean isLooping()
	{
		return loopStart != NOT_SET && loopEnd != NOT_SET;
	}

	public boolean isPaused()
	{
		return paused;
	}

	public void setBypassSoundTouch(boolean bypassSoundTouch)
	{
		this.bypassSoundTouch = bypassSoundTouch;
	}

	public void setLoopEnd(long loopEnd)
	{
		long pd = decoder.getPlayedDuration();
		if (loopStart != NOT_SET && pd <= loopStart)
			throw new SoundTouchAndroidException(
					"Invalid Loop Time, loop start must be < loop end");
		this.loopEnd = loopEnd;
	}

	public void setLoopStart(long loopStart)
	{
		long pd = decoder.getPlayedDuration();
		if (loopEnd != NOT_SET && pd >= loopEnd)
			throw new SoundTouchAndroidException(
					"Invalid Loop Time, loop start must be < loop end");
		this.loopStart = loopStart;
	}

	public void setPitchSemi(float pitchSemi)
	{
		soundTouch.setPitchSemi(pitchSemi);
	}

	public void setTempo(float tempo)
	{
		soundTouch.setTempo(tempo);
	}

	public void setTempoChange(float tempoChange)
	{
		soundTouch.setTempoChange(tempoChange);
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

	public SoundTouchPlayable(OnProgressChangedListener playbackListener,
			String fileName, int id, float tempo, float pitchSemi)
			throws IOException
	{
		this(fileName, id, tempo, pitchSemi);
		this.playbackListener = playbackListener;
	}

	public SoundTouchPlayable(String fileName, int id, float tempo,
			float pitchSemi) throws IOException, SoundTouchAndroidException
	{
		if (Build.VERSION.SDK_INT >= 16)
		{
			decoder = new MediaCodecAudioDecoder(fileName);
		} else
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
				synchronized (decodeLock)
				{
					decoder.resetEOS();
				}
			}
		} catch (SoundTouchAndroidException e)
		{
			// need to notify...something?
			e.printStackTrace();
		} finally
		{
			finished = true;
			soundTouch.clearBuffer();

			synchronized (trackLock)
			{
				track.pause();
				track.flush();
				track.release();
				track = null;
			}
			synchronized (decodeLock)
			{
				decoder.close();
				decoder = null;
			}
		}
	}

	public void clearLoopPoints()
	{
		loopStart = NOT_SET;
		loopEnd = NOT_SET;
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
			synchronized (trackLock)
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

	private void pauseWait()
	{
		synchronized (pauseLock)
		{
			while (paused)
			{
				try
				{
					pauseLock.wait();
				} catch (InterruptedException e)
				{
				}
			}
		}
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

			if (isLooping() && decoder.getPlayedDuration() >= loopEnd)
			{
				seekTo(loopStart, false);
			}

			if (soundTouch.getOutputBufferSize() <= MAX_OUTPUT_BUFFER_SIZE)
			{
				synchronized (decodeLock)
				{
					input = decoder.decodeChunk();
				}
				sendProgressUpdate();
				processChunk(input, true);
			} else
			{
				processChunk(input, false);
			}
		} while (!decoder.sawOutputEOS());

		soundTouch.finish();

		do
		{
			if (finished)
				break;
			bytesReceived = processChunk(input, false);
		} while (bytesReceived > 0);
	}

	private void sendProgressUpdate()
	{
		if (playbackListener != null)
		{
			handler.post(new Runnable()
			{

				@Override
				public void run()
				{
					if (!finished)
					{
						long pd = decoder.getPlayedDuration();
						long d = decoder.getDuration();
						double cp = pd == 0 ? 0 : (double) pd / d;
						playbackListener.onProgressChanged(id, cp, pd);
					}
				}
			});
		}
	}

	private int processChunk(final byte[] input, boolean putBytes)
			throws SoundTouchAndroidException
	{
		int bytesReceived = 0;

		if (input != null)
		{
			if (bypassSoundTouch)
			{
				bytesReceived = input.length;
			} else
			{
				if (putBytes)
					soundTouch.putBytes(input);
				// Log.d("bytes", String.valueOf(input.length));
				bytesReceived = soundTouch.getBytes(input);
			}
			synchronized (trackLock)
			{
				bytesWritten += track.write(input, 0, bytesReceived);
			}

		}
		return bytesReceived;
	}

	private void setupAudio(int id, float tempo, float pitchSemi)
			throws IOException
	{
		int channels = decoder.getChannels();
		int samplingRate = decoder.getSamplingRate();

		int channelFormat = -1;

		if (channels == 1) // mono
			channelFormat = AudioFormat.CHANNEL_OUT_MONO;
		else if (channels == 2) // stereo
			channelFormat = AudioFormat.CHANNEL_OUT_STEREO;
		else
			throw new SoundTouchAndroidException(
					"Valid channel count is 1 or 2");

		soundTouch = new SoundTouch(id, channels, samplingRate,
				DEFAULT_BYTES_PER_SAMPLE, tempo, pitchSemi);

		track = new AudioTrack(AudioManager.STREAM_MUSIC, samplingRate,
				channelFormat, AudioFormat.ENCODING_PCM_16BIT,
				BUFFER_SIZE_TRACK, AudioTrack.MODE_STREAM);
	}
}
