package com.smp.soundtouchandroid;

import java.io.IOException;


public interface AudioDecoder
{
	int getChannels() throws IOException;
	long getPlayedDuration();
	long getDuration();
	int getSamplingRate() throws IOException;
	void close();
	byte[] decodeChunk() throws SoundTouchAndroidException;
	boolean sawOutputEOS();
	void seek(long timeInUs);
	void resetEOS();	
}
