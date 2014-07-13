package com.smp.soundtouchandroid;

import java.io.IOException;

public class SoundTouchFileWriter extends SoundTouchPlayableBase
{	
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
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onPause()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onStop()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void seekTo(long timeInUs)
	{
		// TODO Auto-generated method stub
		
	}
	
	

}
