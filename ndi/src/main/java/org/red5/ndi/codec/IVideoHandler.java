package org.red5.ndi.codec;

import java.awt.Dimension;
import java.nio.ByteBuffer;

/**
 * Interface for video handling.
 * 
 * @author Paul Gregoire
 *
 */
public interface IVideoHandler {

    boolean configure(ByteBuffer config, Dimension dim);

    byte[] process(ByteBuffer buf);

}
