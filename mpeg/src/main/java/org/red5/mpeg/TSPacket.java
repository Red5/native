package org.red5.mpeg;

import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packetized data received via native layer.
 * 
 * @author Paul Gregoire
 */
public class TSPacket {

    private static Logger log = LoggerFactory.getLogger(TSPacket.class);

    private final long timestamp;

    // payload can be either byte[] or short[]
    private final Object payload;

    // payload type identifier
    private final int typeId;

    // audio flag
    private final boolean audio;

    // video flag
    private final boolean video;

    // mpeg-ts flag indicating muxed content
    private final boolean ts;

    /**
     * Expects video or muxed mpeg-ts content as a byte array.
     * 
     * @param timestamp
     * @param payload
     */
    private TSPacket(long timestamp, byte[] payload) {
        this.timestamp = timestamp;
        this.payload = (byte[]) payload;
        this.audio = false;
        // determine if video or mpeg-ts bytes
        if (payload[0] == (byte) 0x47) {
            this.ts = true;
            this.video = false;
            this.typeId = PayloadType.TYPE_UNKNOWN.typeId;
        } else {
            this.ts = false;
            this.video = true;
            this.typeId = PayloadType.TYPE_VIDEO.typeId;;
        }
    }

    /**
     * Expects data as a byte array with a type identifier.
     * 
     * @param timestamp
     * @param payload
     * @param typeId
     */
    private TSPacket(long timestamp, byte[] payload, int typeId) {
        log.info("TSPacket - timestamp: {} typeId: {} payload size: {}", timestamp, typeId, payload.length);
        this.timestamp = timestamp;
        this.payload = (byte[]) payload;
        this.typeId = typeId;
        // determine if mpeg-ts bytes
        if (payload[0] == (byte) 0x47) {
            this.ts = true;
        } else {
            this.ts = false;
        }
        final PayloadType type = PayloadType.valueOfTypeId(typeId);
        switch (type) {
            case TYPE_AUDIO:
            case TYPE_ADTS:
            case TYPE_MP2A:
                this.audio = true;
                this.video = false;
                break;
            case TYPE_VIDEO:
            case TYPE_H264:
            case TYPE_HEVC:
            case TYPE_MP1V:
                this.audio = false;
                this.video = true;
                break;
            case TYPE_META:
            case TYPE_ID3:
            case TYPE_KLV:
                this.audio = false;
                this.video = false;
                break;
            default:
                // if we're here, the typeId isn't precoded; assume fourCC, but keep a/v flags off
                this.audio = false;
                this.video = false;
                break;
        }
    }

    /**
     * Expects audio short array.
     */
    private TSPacket(long timestamp, short[] payload) {
        this.timestamp = timestamp;
        this.payload = (short[]) payload;
        this.audio = true;
        this.video = false;
        this.ts = false;
        this.typeId = PayloadType.TYPE_AUDIO.typeId;;
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

    public boolean isAudio() {
        return audio;
    }

    public boolean isVideo() {
        return video;
    }

    public boolean isMpegTs() {
        return ts;
    }

	public static TSPacket build(long timestamp, byte[] data) {
        TSPacket packet = new TSPacket(timestamp, data);
		return packet;
	}

	public static TSPacket build(long timestamp, byte[] data, int typeId) {
        TSPacket packet = new TSPacket(timestamp, data, typeId);
		return packet;
	}

	public static TSPacket build(long timestamp, short[] data) {
        TSPacket packet = new TSPacket(timestamp, data);
		return packet;
	}

}
