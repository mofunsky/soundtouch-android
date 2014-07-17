package com.smp.soundtouchandroid;

import java.io.IOException;

import android.util.Log;

public class SoundTouchFileWriter extends SoundTouchPlayableBase
{	
	private long start, end;
	private AudioSinkAudioEncoder encoder;
	public SoundTouchFileWriter(int id, String fileName, float tempo,
			float pitchSemi) throws IOException
	{
		super(id, fileName, tempo, pitchSemi);
	}

	@Override
	protected AudioSink initAudioSink() throws IOException
	{
		encoder = new AudioSinkAudioEncoder("whatever");
		return encoder;
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
			encoder.finishWriting();
			end = System.nanoTime();
			long elapsedTime = end - start;
			double seconds = (double)elapsedTime / 1000000000.0;
			Log.i("ENCODE", "SECONDS: " + String.valueOf(seconds));
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
