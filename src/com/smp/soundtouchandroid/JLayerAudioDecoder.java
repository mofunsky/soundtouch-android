package com.smp.soundtouchandroid;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.util.Log;
import static com.smp.soundtouchandroid.Constants.*;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

//Not well supported.

public class JLayerAudioDecoder implements AudioDecoder
{
	public JLayerAudioDecoder(String path) throws FileNotFoundException
	{
		file = new File(path);
		inputStream = new BufferedInputStream(new FileInputStream(file), 8 * 1024);
		bitstream = new Bitstream(inputStream);
		decoder = new Decoder();
		outStream = new ByteArrayOutputStream(MAX_CHUNK_SIZE);
		determineFormat();
	}

	private void determineFormat()
	{
		final int LOTS = 18000;
		
		inputStream.mark(LOTS);
		Header frameHeader;
		try
		{
			frameHeader = bitstream.readFrame();
			if (frameHeader == null)
			{
				sawOutputEOS = true;
				return;
			}
			else
			{
				SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
				channels = output.getChannelCount();
				samplingRate = output.getSampleFrequency();
				bitstream.unreadFrame();
				bitstream.closeFrame();
			}
		}
		catch (BitstreamException e1)
		{
			e1.printStackTrace();
			throw new SoundTouchAndroidException("Error decoding mp3 file");
		}
		catch (javazoom.jl.decoder.DecoderException e)
		{
			e.printStackTrace();
			throw new SoundTouchAndroidException("Error decoding mp3 file");
		}
		
		try
		{
			inputStream.reset();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new SoundTouchAndroidException("Error decoding mp3 file");
		}
	}

	public JLayerAudioDecoder(String path, long position)
	{
		// TODO
	}

	private File file;
	private String path;
	private InputStream inputStream;
	private byte[] currentChunk;
	private Bitstream bitstream;
	private Decoder decoder;
	private ByteArrayOutputStream outStream;
	private volatile boolean sawOutputEOS;
	private int channels, samplingRate;
	
	public void close()
	{
		try
		{
			inputStream.close();
			outStream.close();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public byte[] decodeChunk() throws SoundTouchAndroidException
	{
		outStream.reset();
		boolean done = false;
		try
		{
			while (!done)
			{
				Header frameHeader = bitstream.readFrame();
				if (frameHeader == null)
				{
					done = true;
					sawOutputEOS = true;
				}
				else
				{
					SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
					short[] pcm = output.getBuffer();
					for (short s : pcm)
					{
						outStream.write(s & 0xff);
						outStream.write((s >> 8) & 0xff);
					}
					if (outStream.size() > MAX_CHUNK_SIZE)
					{
						done = true;
					}
				}
				bitstream.closeFrame();
			}
			outStream.flush();
		}
		catch (BitstreamException e)
		{
			e.printStackTrace();
			throw new SoundTouchAndroidException("Error decoding mp3 file");
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new SoundTouchAndroidException("Error decoding mp3 file");
		}
		catch (javazoom.jl.decoder.DecoderException e)
		{
			e.printStackTrace();
			throw new SoundTouchAndroidException("Error decoding mp3 file");
		}
		return outStream.toByteArray();
	}
	//not used
	public static byte[] decode(String path, int startMs, int maxMs)
			throws IOException, SoundTouchAndroidException
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream(1024);

		float totalMs = 0;
		boolean seeking = true;

		File file = new File(path);
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file), 8 * 1024);
		try
		{
			Bitstream bitstream = new Bitstream(inputStream);
			Decoder decoder = new Decoder();

			boolean done = false;
			while (!done)
			{
				Header frameHeader = bitstream.readFrame();
				if (frameHeader == null)
				{
					done = true;
				}
				else
				{
					totalMs += frameHeader.ms_per_frame();

					if (totalMs >= startMs)
					{
						seeking = false;
					}

					if (!seeking)
					{
						SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);

						if (output.getSampleFrequency() != 44100
								|| output.getChannelCount() != 2)
						{
							throw new SoundTouchAndroidException("mono or non-44100 MP3 not supported");
						}

						short[] pcm = output.getBuffer();
						for (short s : pcm)
						{
							outStream.write(s & 0xff);
							outStream.write((s >> 8) & 0xff);
						}
					}

					if (totalMs >= (startMs + maxMs))
					{
						done = true;
					}
				}
				// should be in finally
				bitstream.closeFrame();
			}

		}
		catch (BitstreamException e)
		{
			throw new IOException("Bitstream error: " + e);
		}
		catch (SoundTouchAndroidException e)
		{
			Log.w("DECODE", "Decoder error", e);
			throw new SoundTouchAndroidException("Decoder Exception");
		}
		catch (javazoom.jl.decoder.DecoderException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			try
			{
				inputStream.close();
			}
			catch (IOException e)
			{

			}
		}
		return outStream.toByteArray();
	}

	@Override
	public boolean sawOutputEOS()
	{
		return sawOutputEOS;
	}

	@Override
	public int getChannels()
	{
		return channels;
	}

	@Override
	public int getSamplingRate()
	{
		return samplingRate;
	}

	@Override
	public void seek(long timeInUs)
	{
		throw new UnsupportedOperationException("Seeking is not implemented on API < 16");	
	}

	@Override
	public long getDuration()
	{
		throw new UnsupportedOperationException("Seeking is not implemented on API < 16");	
	}

	@Override
	public void resetEOS()
	{
		sawOutputEOS = false;
	}

	@Override
	public long getPlayedDuration()
	{
		throw new UnsupportedOperationException("Seeking is not implemented on API < 16");	
	}

}
