package com.smp.soundtouchandroid;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

@SuppressLint("NewApi")
public class AudioSinkAudioEncoder implements AudioSink
{
	private MediaCodec codec;
	private MediaFormat format;

	private ByteBuffer[] codecInputBuffers, codecOutputBuffers;
	private boolean signalEndOfInput;
	private int numBytesSubmitted;
	private int numBytesDequeued;
	private static final String TAG = "ENCODE";

	// private long kNumInputBytes;
	private static final long kTimeoutUs = 10000;
	
	private BufferedOutputStream outputStream;
	private boolean doneDequeing;

	static String testPath;
	static
	{
		String baseDir = Environment.getExternalStorageDirectory()
				.getAbsolutePath();
		testPath = baseDir + "/musicWRITING.mp4";
	}

	public AudioSinkAudioEncoder(String fileName)
	{
		String componentName = "audio/mp4a-latm";
		codec = MediaCodec.createByCodecName(componentName);
		format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, 2); // AAC LC
		format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
		format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);

		codec.configure(format, null /* surface */, null /* crypto */,
				MediaCodec.CONFIGURE_FLAG_ENCODE);
		codec.start();

		codecInputBuffers = codec.getInputBuffers();
		codecOutputBuffers = codec.getOutputBuffers();
	}




	@Override
	public int write(byte[] input, int offsetInBytes, int sizeInBytes)
	{
		int size = 0;
		int index = codec.dequeueInputBuffer(kTimeoutUs /* timeoutUs */);

		if (signalEndOfInput)
		{
			codec.queueInputBuffer(index, 0 /* offset */, 0 /* size */,
					0 /* timeUs */, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
			if (true)
			{
				Log.d(TAG, "queued input EOS.");
			}
			while (!doneDequeing) writeOutput();
		} else
		{
			ByteBuffer buffer = codecInputBuffers[index];
			size = buffer.limit();
			buffer.clear();
			buffer.put(input);
			codec.queueInputBuffer(index, offsetInBytes, sizeInBytes, 0, 0);
			numBytesSubmitted += size;
			if (true)
			{
				Log.d(TAG, "queued " + size + " bytes of input data.");
			}

		}
		writeOutput();
		return size;
	}

	private void writeOutput()
	{
		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int index = codec.dequeueOutputBuffer(info, kTimeoutUs /* timeoutUs */);
        
        if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
        } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            codecOutputBuffers = codec.getOutputBuffers();
        } else {
        	int outBitsSize   = info.size;
            int outPacketSize = outBitsSize + 7;    // 7 is ADTS size
            ByteBuffer outBuf = codecOutputBuffers[index];

            outBuf.position(info.offset);
            outBuf.limit(info.offset + outBitsSize);
            try {
                byte[] data = new byte[outPacketSize];  //space for ADTS header included
                addADTStoPacket(data, outPacketSize);
                outBuf.get(data, 7, outBitsSize);
                outBuf.position(info.offset);
                outputStream.write(data, 0, outPacketSize);  //open FileOutputStream beforehand
            } catch (IOException e) {
                Log.e(TAG, "failed writing bitstream data to file");
                e.printStackTrace();
            }

            numBytesDequeued += info.size;

            outBuf.clear();
            codec.releaseOutputBuffer(index, false /* render */);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (true) {
                    Log.d(TAG, "dequeued output EOS.");
                }
                doneDequeing = true;
            }
            if (true) {
                Log.d(TAG, "dequeued " + info.size + " bytes of output data.");
            }
        }
	}
	private void addADTStoPacket(byte[] packet, int packetLen) {
	    int profile = 2;  //AAC LC
	                      //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
	    int freqIdx = 4;  //44.1KHz
	    int chanCfg = 2;  //CPE

	    // fill in ADTS data
	    packet[0] = (byte)0xFF;
	    packet[1] = (byte)0xF9;
	    packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
	    packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
	    packet[4] = (byte)((packetLen&0x7FF) >> 3);
	    packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
	    packet[6] = (byte)0xFC;
	}
	public void setSignalEndOfInput(boolean value)
	{
		signalEndOfInput = value;
	}

	@Override
	public void close()
	{
		codec.flush();
		codec.release();
		codec = null;
		
		try
		{
			outputStream.flush();
			outputStream.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

}
