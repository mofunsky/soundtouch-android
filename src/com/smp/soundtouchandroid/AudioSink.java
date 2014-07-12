package com.smp.soundtouchandroid;

public interface AudioSink
{
	int write(byte[] input, int i, int bytesReceived);
	void close();
}
