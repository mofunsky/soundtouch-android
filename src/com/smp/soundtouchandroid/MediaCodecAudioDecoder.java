package com.smp.soundtouchandroid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import static com.smp.soundtouchandroid.Constants.*;

@SuppressLint("NewApi")
public class MediaCodecAudioDecoder implements AudioDecoder
{
	public static String getExtension(String uri)
	{
		if (uri == null)
		{
			return null;
		}

		int dot = uri.lastIndexOf(".");
		if (dot >= 0)
		{
			return uri.substring(dot);
		} else
		{
			// No extension.
			return "";
		}
	}

	private static final long TIMEOUT_US = 200000;

	private long durationUs; // track duration in us
	private volatile long lastPresentationTime;
	private volatile long currentTimeUs; // total played duration thus far
	private BufferInfo info;
	private MediaCodec codec;
	private MediaExtractor extractor;
	private MediaFormat format;
	private ByteBuffer[] codecInputBuffers;
	private ByteBuffer[] codecOutputBuffers;
	private byte[] chunk;
	private volatile boolean sawOutputEOS;

	public int getChannels() throws IOException
	{
		if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
			return format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
		throw new IOException("Not a valid audio file");
	}

	public long getDuration()
	{
		return durationUs;
	}

	@Override
	public long getPlayedDuration()
	{
		return currentTimeUs;
	}

	public int getSamplingRate() throws IOException
	{
		if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
			return format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
		throw new IOException("Not a valid audio file");
	}

	@SuppressLint("NewApi")
	public MediaCodecAudioDecoder(String fullPath) throws IOException
	{
		Locale locale = Locale.getDefault();
		if (getExtension(fullPath).toLowerCase(locale).equals(".wma"))
			throw new IOException("WMA file not supported");

		extractor = new MediaExtractor();
		extractor.setDataSource(fullPath);

		format = extractor.getTrackFormat(0);
		String mime = format.getString(MediaFormat.KEY_MIME);
		durationUs = format.getLong(MediaFormat.KEY_DURATION);

		codec = MediaCodec.createDecoderByType(mime);
		codec.configure(format, null, null, 0);
		codec.start();
		codecInputBuffers = codec.getInputBuffers();
		codecOutputBuffers = codec.getOutputBuffers();

		extractor.selectTrack(0);
		info = new MediaCodec.BufferInfo();
	}

	@SuppressLint("NewApi")
	public MediaCodecAudioDecoder(AssetFileDescriptor sampleFD)
			throws IOException
	{
		extractor = new MediaExtractor();
		extractor.setDataSource(sampleFD.getFileDescriptor(),
				sampleFD.getStartOffset(), sampleFD.getLength());

		format = extractor.getTrackFormat(0);
		String mime = format.getString(MediaFormat.KEY_MIME);
		durationUs = format.getLong(MediaFormat.KEY_DURATION);

		codec = MediaCodec.createDecoderByType(mime);
		codec.configure(format, null, null, 0);
		codec.start();
		codecInputBuffers = codec.getInputBuffers();
		codecOutputBuffers = codec.getOutputBuffers();

		extractor.selectTrack(0);
		info = new MediaCodec.BufferInfo();
	}

	@Override
	public void close()
	{
		codec.stop();
		codec.release();
		codec = null;
		extractor.release();
		extractor = null;
	}

	@Override
	public byte[] decodeChunk()
	{
		advanceInput();

		final int res = codec.dequeueOutputBuffer(info, TIMEOUT_US);
		if (res >= 0)
		{
			int outputBufIndex = res;
			ByteBuffer buf = codecOutputBuffers[outputBufIndex];
			if (chunk == null || chunk.length != info.size)
			{
				chunk = new byte[info.size];
			}
			buf.get(chunk);
			buf.clear();
			codec.releaseOutputBuffer(outputBufIndex, false);
		}
		if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
		{
			sawOutputEOS = true;
		} else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
		{
			codecOutputBuffers = codec.getOutputBuffers();
		} else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
		{
			format = codec.getOutputFormat();
			Log.d("MP3", "Output format has changed to " + format);
		}

		return chunk;
	}

	@Override
	public void resetEOS()
	{
		sawOutputEOS = false;
		info.flags = 0;
	}

	@Override
	public boolean sawOutputEOS()
	{
		return sawOutputEOS;
	}

	@Override
	public void seek(long timeInUs)
	{
		extractor.seekTo(timeInUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
		lastPresentationTime = currentTimeUs = timeInUs;
		codec.flush();
	}

	private void advanceInput()
	{
		boolean sawInputEOS = false;

		int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US);
		if (inputBufIndex >= 0)
		{
			ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

			int sampleSize = extractor.readSampleData(dstBuf, 0);
			long presentationTimeUs = 0;

			if (sampleSize < 0)
			{
				sawInputEOS = true;
				sampleSize = 0;
			} else
			{
				presentationTimeUs = extractor.getSampleTime();
				currentTimeUs += presentationTimeUs - lastPresentationTime;
				lastPresentationTime = presentationTimeUs;
			}

			codec.queueInputBuffer(inputBufIndex, 0, sampleSize,
					presentationTimeUs,
					sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
			if (!sawInputEOS)
			{
				extractor.advance();
			}
		}
	}

}
