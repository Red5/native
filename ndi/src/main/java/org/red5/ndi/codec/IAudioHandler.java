package org.red5.ndi.codec;

import java.nio.ByteBuffer;

/**
 * Interface for audio handling.
 * 
 * @author Paul Gregoire
 *
 */
public interface IAudioHandler {

    boolean configure(ByteBuffer config);

    short[] process(ByteBuffer buf);

}
