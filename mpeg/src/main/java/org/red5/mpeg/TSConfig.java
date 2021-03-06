package org.red5.mpeg;

/**
 * Configuration model. Public members are used for easy access in the C++ code.
 *
 * @author Paul Gregoire
 */
public class TSConfig {

    public String name = "Red5 MPEG";

    // width and height
    public int width, height;

    // audio sample rate and channel count
    public int sampleRate, channels;

    // mpeg-ts options (0 = not set)
    public short pmtPid, audioPid, videoPid, metaPid;

    // mpeg-ts es data stream id
    public byte streamId = (byte) 0xe0; // default id, start of video range

    // used for connection identification
    public int connectionId;

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getConnectionId() {
        return connectionId;
    }

}
