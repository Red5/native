package org.red5.mpeg;

/**
 * Decodes media / data via TS handler.
 * 
 * @author Paul Gregoire
 */
public class TSHandler {

    private long handlerId;

    private TSConfig config;

    private TSReceiver receiver;

    public TSHandler(long handlerId) {
        this.handlerId = handlerId;
    }

    /**
     * Creates a TS handler instance.
     * 
     * @param config
     * @param receiver
     * @return unique identifier or -1 if theres an error
     */
    private static native long createHandler(TSConfig config, TSReceiver receiver);

    /**
     * Decode audio data via the TS handler matching the given id.
     * 
     * @param id handler id
     * @param data
     * @return true if the decoder accepts and false if something failed
     */
    private native boolean decodeAudio(long id, short[] data);

    /**
     * Decode video data via the TS handler matching the given id.
     * 
     * @param id handler id
     * @param data
     * @return true if the decoder accepts and false if something failed
     */
    private native boolean decodeVideo(long id, byte[] data);

    /**
     * Destroys the handler matching the given id.
     * 
     * @param id
     */
    private native void destroy(long id);

    /**
     * Destroys the handler.
     */
    public void destroy() {
        destroy(handlerId);
    }

    /**
     * Decode video.
     * 
     * @param data
     * @return true if the decoder accepts and false if something failed
     */
    public boolean decode(byte[] data) {
        return decodeVideo(handlerId, data);
    }
    
    /**
     * Decode audio.
     * 
     * @param data
     * @return true if the decoder accepts and false if something failed
     */
    public boolean decode(short[] data) {
        return decodeAudio(handlerId, data);
    }

    /**
     * Returns the handlers instance id (technically a pointer to its memory location).
     * 
     * @return handlerId
     */
	public long getId() {
		return handlerId;
	}

    /**
     * Returns the receiver.
     * 
     * @return receiver
     */
    public TSReceiver getReceiver() {
        return receiver;
    }

    /**
     * Builder for a new handler.
     * 
     * @param config
     * @return TSHandler if no errors occur, otherwise return null
     */
    public static TSHandler build(TSConfig config) {
        TSReceiver receiver = new TSReceiver();
        long handlerId = createHandler(config, receiver);
        if (handlerId > 0) {
            TSHandler handler = new TSHandler(handlerId);
            handler.config = config;
            handler.receiver = receiver;
            return handler;
        }
        return null;
	}

}
