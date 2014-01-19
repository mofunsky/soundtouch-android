package com.smp.soundtouchandroid;


public interface Mp3Decoder
{
	public byte[] decodeChunk() throws DecoderException;
	public void close();
	public boolean sawOutputEOS();
	public int getChannels();
	public int getSamplingRate();
}
