package com.smp.soundtouchandroid;

import java.io.IOException;


public interface Mp3Decoder
{
	byte[] decodeChunk() throws SoundTouchAndroidException;
	void close();
	boolean sawOutputEOS();
	int getChannels() throws IOException;
	int getSamplingRate() throws IOException;
	void seek(long timeInUs);
	long getDuration();
	long getPlayedDuration();
	void resetEOS();
	
}
