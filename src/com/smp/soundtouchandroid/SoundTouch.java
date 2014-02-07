
package com.smp.soundtouchandroid;
import static com.smp.soundtouchandroid.Constants.*;
public class SoundTouch
{
	
	static
    {
        System.loadLibrary("soundtouch");
    }
	
	private int channels, samplingRate, bytesPerSample;
	public int getChannels()
	{
		return channels;
	}

	public void setChannels(int channels)
	{
		this.channels = channels;
	}

	public int getSamplingRate()
	{
		return samplingRate;
	}

	public void setSamplingRate(int samplingRate)
	{
		this.samplingRate = samplingRate;
	}

	public int getBytesPerSample()
	{
		return bytesPerSample;
	}

	public void setBytesPerSample(int bytesPerSample)
	{
		this.bytesPerSample = bytesPerSample;
	}

	private float tempo;
	private float pitchSemi;
	private int track;
	
	public SoundTouch(int track, int channels, int samplingRate, int bytesPerSample, float tempo, float pitchSemi)
	{
		
		this.channels = channels;
		this.samplingRate = samplingRate;
		this.bytesPerSample = bytesPerSample;
		this.tempo = tempo;
		this.pitchSemi = pitchSemi;
		this.track = track;
		
		setup(track, channels, samplingRate, bytesPerSample, tempo, pitchSemi);
	}
	
	private static synchronized native final void setup(int track, int channels, int samplingRate, int bytesPerSample, float tempo, float pitchSemi);
    private static synchronized native final void putBytes(int track, byte[] input, int length);
    private static synchronized native final int getBytes(int track, byte[] output, int toGet);
    private static synchronized native final void finish(int track, int bufSize);
    private static synchronized native final void clearBytes(int track);
    private static synchronized native final void setTempo(int track, float tempo);
    private static synchronized native final void setTempoChange(int track, float tempoChange);
    private static synchronized native final void setPitchSemi(int track, float pitchSemi);
    private static synchronized native final long getOutputBufferSize(int track);
    
    public float getTempo()
    {
    	return tempo;
    }
    
    public float getPitchSemi()
    {
    	return pitchSemi;
    }
    public long getOutputBufferSize()
    {
    	return getOutputBufferSize(track);
    }
    
    public void setTempo(float tempo)
    {
    	this.tempo = tempo;
    	setTempo(track, tempo);
    }
    public void setTempoChange(float tempoChange)
    {
    	if (tempoChange < -50 || tempoChange > 100)
    		throw new SoundTouchAndroidException("Tempo percentage must be between -50 and 100");
    	this.tempo = 1.0f + 0.01f * tempoChange;
    	setTempoChange(track, tempoChange);
    }
    public void setPitchSemi(float pitchSemi)
    {
    	this.pitchSemi = pitchSemi;
    	setPitchSemi(track, pitchSemi);
    }
    
    public void clearBuffer()
    {
    	clearBytes(track);
    }
    public void putBytes(byte[] input)
    {
    	putBytes(track, input, input.length);
    }
    
    public int getBytes(byte[] output)
    {
    	return getBytes(track, output, output.length);
    }
    
    //call finish after the last bytes have been written
    public void finish()
    {
    	finish(track, DEFAULT_BUFFER_SIZE);
    }
}
