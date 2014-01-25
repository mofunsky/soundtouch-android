package com.smp.soundtouchandroid;


public interface Mp3Decoder
{
	byte[] decodeChunk() throws SoundTouchAndroidException;
	void close();
	boolean sawOutputEOS();
	int getChannels();
	int getSamplingRate();
	void seek(long timeInUs);
	long getDuration();
	long getPlayedDuration();
	void resetEOS();
	
}
