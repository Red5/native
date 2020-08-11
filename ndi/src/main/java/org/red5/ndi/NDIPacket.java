package org.red5.ndi;

public class NDIPacket {

    private final long timestamp;

    // payload can be either byte[] or short[]
    private final Object payload;

    private NDIPacket(long timestamp, byte[] payload) {
        this.timestamp = timestamp;
        this.payload = (byte[]) payload;
    }

    private NDIPacket(long timestamp, short[] payload) {
        this.timestamp = timestamp;
        this.payload = (short[]) payload;
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

	public static NDIPacket build(long timestamp, byte[] data) {
        NDIPacket packet = new NDIPacket(timestamp, data);
		return packet;
	}

	public static NDIPacket build(long timestamp, short[] data) {
        NDIPacket packet = new NDIPacket(timestamp, data);
		return packet;
	}

}
