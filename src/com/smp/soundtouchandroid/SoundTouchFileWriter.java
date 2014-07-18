package com.smp.soundtouchandroid;

import java.io.IOException;

import android.util.Log;

public class SoundTouchFileWriter extends SoundTouchRunnable
{	
	private long start, end;
	private AACFileAudioSink file;
	private String fileNameIn;
	public SoundTouchFileWriter(int id, String fileName, float tempo,
			float pitchSemi) throws IOException
	{
		super(id, fileName, tempo, pitchSemi);
		fileNameIn = fileName;
	}
	private String generateFileNameOut(String fileNameIn)
	{
		return fileNameIn;
	}
	
	@Override
	protected AudioSink initAudioSink() throws IOException
	{
		file = new AACFileAudioSink(fileNameIn, generateFileNameOut(fileNameIn));
		return file;
	}

	@Override
	protected void onStart()
	{
		start = System.nanoTime();
		
	}

	@Override
	protected void onPause()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onStop()
	{
		try
		{
			file.finishWriting();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		end = System.nanoTime();
		long elapsedTime = end - start;
		double seconds = (double)elapsedTime / 1000000000.0;
		Log.i("ENCODE", "SECONDS: " + String.valueOf(seconds));
		
	}
}
