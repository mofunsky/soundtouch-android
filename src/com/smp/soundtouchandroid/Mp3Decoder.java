package com.smp.soundtouchandroid;


public interface Mp3Decoder
{
	public byte[] decodeChunk() throws SoundTouchAndroidException;
	public void close();
	public boolean sawOutputEOS();
	public int getChannels();
	public int getSamplingRate();
}
