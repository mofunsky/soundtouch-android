SoundTouch-Android
==================
<pre>
Copyright [2013] [Steve Myers]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>


An Android wrapper for the the SoundTouch Audio Processing Library by Olli Parviainen 2001-2012,
distributed under the LGPL license.

Currently capable of time-stretching and pitch shifting.

Please see issues for a list of known limitations.

Currently only supports Androids with an FPU (armeabi-v7a).

Example usage:

```java
//There are currently 16 track id's you can use (0-15), each one has a separate SoundTouch processor.

//Set your audio processing requirements: track id, channels, samplingRate, bytesPerSample, 
//                              tempoChange (1.0 is normal speed), pitchChange (in semi-tones)

SoundTouch soundTouch = new SoundTouch(0, 2, 44100, 2, 1.0f, 2.0f);

//byte[] sizes are recommended to be 8192 bytes.

//put a byte[] of PCM audio in the sound processor:
soundTouch.putBytes(input);

//write output to a byte[]:
int bytesReceived = soundTouch.getBytes(output);

//after you write the last byte[], call finish().
soundTouch.finish();

//now get the remaining bytes from the sound processor.
int bytesReceived = 0;
do
{
    bytesReceived = soundTouch.getBytes(output);
    //do stuff with output.
} while (bytesReceived != 0)

//if you stop playing, call clear on the track to clear the pipeline for later use.
soundTouch.clearBuffer()
```

Take a look at SoundTouchPlayable.java for a ready made implementation of a time-stretching, pitch-shifting
Runnable that streams a decoded audio file to an AudioTrack. This implementation has been tested on Android API >= 16,
lower API level support via JLayer decoding is very rough and alpha quality.

To demonstrate, execute the following in your app:

```java
//the last two parameters are speed of playback and pitch in semi-tones.
SoundTouchPlayable st = new SoundTouchPlayable(fullPathToAudioFile, 0, 1.0f, 0.0f);
new Thread(st).start();
st.play();
````
The track can be paused or stopped at a later time.
stop() should always be called on a SoundTouchPlayable after use to release resources.

```java
st.pause();
st.stop();
````

