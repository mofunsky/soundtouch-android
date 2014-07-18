package com.smp.soundtouchandroid;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AACFileAudioSink implements AudioSink
{
	private AudioEncoder encoder;
	private ExecutorService exec;
	
	public AACFileAudioSink(String fileNameIn, String fileNameOut) throws FileNotFoundException
	{
		encoder = new MediaCodecAudioEncoder(fileNameIn, fileNameOut);
		exec = Executors.newSingleThreadExecutor();
	}
	
	@Override
	public int write(byte[] input, final int offSetInBytes, final int sizeInBytes)
			throws IOException
	{
		final byte[] tmp = Arrays.copyOf(input, input.length);
		
		exec.submit(new Runnable() {

			@Override
			public void run()
			{
				try
				{
					encoder.writeChunk(tmp, offSetInBytes, sizeInBytes);
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}});
		
		return sizeInBytes - offSetInBytes;
	}

	@Override
	public void close() throws IOException
	{
		encoder.close();
	}

	public void finishWriting() throws IOException
	{
		exec.submit(new Runnable() {

			@Override
			public void run()
			{
				try
				{
					encoder.finishWriting();
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}});
		
		exec.shutdown();
		try
		{
			exec.awaitTermination(20, TimeUnit.SECONDS);
		} catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
