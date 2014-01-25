package com.smp.soundtouchandroid;

public interface PlaybackProgressListener
{
	void onProgressChanged(double currentPercentage); //returns the percentage of the track played so far.
}
