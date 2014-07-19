package com.smp.soundtouchandroid;

import java.io.IOException;

import android.util.Log;

public class SoundTouchFileWriter extends SoundTouchRunnable
{	
	private long start, end;
	private AACFileAudioSink file;
	private String fileNameIn, fileNameOut;
	
	public SoundTouchFileWriter(int id, String fileNameIn, String fileNameOut, float tempo,
			float pitchSemi) throws IOException
	{
		super(id, fileNameIn, tempo, pitchSemi);
		this.fileNameIn = fileNameIn;
		this.fileNameOut = fileNameOut;
	}

	@Override
	protected AudioSink initAudioSink() throws IOException
	{
		file = new AACFileAudioSink(fileNameIn, fileNameOut);
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
			file.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		end = System.nanoTime();
		long elapsedTime = end - start;
		double seconds = (double)elapsedTime / 1000000000.0;
		Log.i("ENCODE", "SECONDS: " + String.valueOf(seconds));
		
	}
}
