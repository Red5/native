package org.red5.ndi;

/**
 * Sends media / data via NDI.
 * 
 * @author Paul Gregoire
 */
public class NDISender {

    private long senderId;

    private NDIConfig config;

    private NDIReceiver receiver = new NDIReceiver();

    public NDISender(long senderId) {
        this.senderId = senderId;
    }

    /**
     * Creates a NDI sender instance.
     * 
     * @return unique identifier or -1 if theres an error
     */
    private static native long createSender();

    /**
     * Starts the loop; this method blocks.
     * 
     * @param id
     * @param config
     * @param receiver
     */
    private native void start(long id, NDIConfig config, NDIReceiver receiver);

    /**
     * Send data via the NDI sender matching the given id.
     * 
     * <ul>
     * <li>NDIlib_frame_type_none = 0</li>
     * <li>NDIlib_frame_type_video = 1</li>
     * <li>NDIlib_frame_type_audio = 2</li>
     * <li>NDIlib_frame_type_metadata = 3</li>
     * <li>NDIlib_frame_type_error = 4</li>
     * </ul>
     * 
     * @param id sender id
     * @param data
     * @param dataType type of data in the byte array
     * @return number of bytes written; -1 means connection isn't ready
     */
    private native int sendData(long id, byte[] data, int dataType);

    /**
     * Send audio data via the ndi sender matching the given id.
     * 
     * @param id sender id
     * @param data
     * @return number of shorts written; -1 means connection isn't ready
     */
    private native int sendAudio(long id, short[] data);

    /**
     * Send video data via the ndi sender matching the given id.
     * 
     * @param id sender id
     * @param data
     * @return number of bytes written; -1 means connection isn't ready
     */
    private native int sendVideo(long id, byte[] data);

    /**
     * Stops the sender matching the given id.
     * 
     * @param id
     */
    private native void stop(long id);

    /**
     * Starts the sender. This method blocks so it should be in its own thread.
     */
    public void start() {
        start(senderId, config, receiver);
    }

    /**
     * Stops the sender.
     */
    public void stop() {
        stop(senderId);
    }

    /**
     * Send data out
     * 
     * @param data
     * @param dataType type of data in the byte array
     * @return number of bytes written; -1 means failure
     */
    public int send(byte[] data, NDIDataType dataType) {
        return sendData(senderId, data, dataType.ordinal());
    }
    
    /**
     * Send audio out
     * 
     * @param data
     * @return number of shorts written; -1 means failure
     */
    public int send(short[] data) {
        return sendAudio(senderId, data);
    }

    /**
     * Returns the senders instance id (technically a pointer to its memory location).
     * 
     * @return senderId
     */
	public long getId() {
		return senderId;
	}

    /**
     * Returns the receiver.
     * 
     * @return receiver
     */
    public NDIReceiver getReceiver() {
        return receiver;
    }

    /**
     * Builder for a new sender.
     * 
     * @param config
     * @return NDISender if no errors occur, otherwise return null
     */
    public static NDISender build(NDIConfig config) {
        long senderId = createSender();
        if (senderId > 0) {
            NDISender sender = new NDISender(senderId);
            sender.config = config;
            return sender;
        }
        return null;
	}

}
