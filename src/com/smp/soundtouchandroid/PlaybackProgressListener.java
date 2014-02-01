package com.smp.soundtouchandroid;

public interface PlaybackProgressListener
{
	void onProgressChanged(int track, double currentPercentage, long position); 
	//the percentage of the track played so far 
}
