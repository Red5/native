package org.red5.ndi;

/**
 * Configuration model. Public members are used for easy access in the C++ code.
 *
 * @author Paul Gregoire
 */
public class NDIConfig {

    // name used to locate the source via NDI
    public String name = "Red5 NDI";

    // width and height
    public int width, height;

    // frame rate
    public int numerator, denominator;

    // picture aspect ratio ex. 16.0/9.0 = 1.778 is 16:9 video. (0 means square pixels)
    public float aspectRatio;

    // audio sample rate and channel count
    public int sampleRate, channels;

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

    public int getNumerator() {
        return numerator;
    }

    public int getDenominator() {
        return denominator;
    }

    public float getAspectRatio() {
        return aspectRatio;
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
