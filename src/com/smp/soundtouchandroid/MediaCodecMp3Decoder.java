package com.smp.soundtouchandroid;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import static com.smp.soundtouchandroid.Constants.*;

@SuppressLint("NewApi")
public class MediaCodecMp3Decoder implements Mp3Decoder
{
	private static final long TIMEOUT_US = 100000;
	
	private long durationUs; //track duration in us
	private volatile long lastPresentationTime;
	private volatile long currentTimeUs; //total played duration thus far
	private BufferInfo info;
	private MediaCodec codec;
	private MediaExtractor extractor;
	private MediaFormat format;
	private ByteBuffer[] codecInputBuffers;
	private ByteBuffer[] codecOutputBuffers;
	private byte[] chunk;
	private volatile boolean sawOutputEOS;

	public int getSamplingRate()
	{
		return format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
	}
	
	public int getChannels()
	{
		return format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
	}
	@SuppressLint("NewApi")
	public MediaCodecMp3Decoder(String fullPath) throws IOException
	{
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
	public MediaCodecMp3Decoder(AssetFileDescriptor sampleFD) throws IOException
	{
		extractor = new MediaExtractor();
		extractor.setDataSource(sampleFD.getFileDescriptor(), sampleFD.getStartOffset(), sampleFD.getLength());

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
	public void seek(long timeInUs)
	{
		extractor.seekTo(timeInUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
		lastPresentationTime = currentTimeUs = timeInUs;
		codec.flush();
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
			if(chunk == null || chunk.length != info.size)
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
		}
		else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
		{
			codecOutputBuffers = codec.getOutputBuffers();
		}
		else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
		{
			final MediaFormat oformat = codec.getOutputFormat();
			Log.d("MP3", "Output format has changed to " + oformat);
		}
		
		return chunk;
	}

	@Override
	public void close()
	{
		codec.flush();
		codec.stop();
		codec.release();
		extractor.release();
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
		}
		else
		{
			presentationTimeUs = extractor.getSampleTime();
			currentTimeUs += presentationTimeUs - lastPresentationTime;
			lastPresentationTime = presentationTimeUs;
		}

		codec.queueInputBuffer(inputBufIndex,
				0,
				sampleSize,
				presentationTimeUs,
				sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
		if (!sawInputEOS)
		{
			extractor.advance();
		}
	}
}
	@Override
	public boolean sawOutputEOS()
	{
		return sawOutputEOS;
	}

	public long getDuration()
	{
		return durationUs;
	}

	@Override
	public void resetEOS()
	{
		sawOutputEOS = false;
	}

	@Override
	public long getPlayedDuration()
	{
		return currentTimeUs;
	}

}
