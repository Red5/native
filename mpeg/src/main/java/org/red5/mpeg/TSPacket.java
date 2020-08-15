package org.red5.mpeg;

public class TSPacket {

    private final long timestamp;

    // payload can be either byte[] or short[]
    private final Object payload;

    private final boolean audio;

    private final boolean video;

    private TSPacket(long timestamp, byte[] payload) {
        this.timestamp = timestamp;
        this.payload = (byte[]) payload;
        this.audio = false;
        this.video = true;
    }

    private TSPacket(long timestamp, short[] payload) {
        this.timestamp = timestamp;
        this.payload = (short[]) payload;
        this.audio = true;
        this.video = false;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getPayload() {
        return (byte[]) payload;
    }

    public short[] getPayloadAsShorts() {
        return (short[]) payload;
    }

	public static TSPacket build(long timestamp, byte[] data) {
        TSPacket packet = new TSPacket(timestamp, data);
		return packet;
	}

	public static TSPacket build(long timestamp, short[] data) {
        TSPacket packet = new TSPacket(timestamp, data);
		return packet;
	}

}