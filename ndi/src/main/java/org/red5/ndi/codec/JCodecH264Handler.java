package org.red5.ndi.codec;

import java.awt.Dimension;
import java.nio.ByteBuffer;

import org.apache.mina.core.buffer.IoBuffer;
import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.io.model.Frame;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * H264/AVC handling using JCodec.
 * 
 * @author Paul Gregoire
 */
public class JCodecH264Handler implements IVideoHandler {

    private static Logger log = LoggerFactory.getLogger(JCodecH264Handler.class);

    private H264Decoder decoder;

    private Picture pic;

    @Override
    public boolean configure(ByteBuffer config, Dimension dim) {
        try {
            // config is expected as SPS and PPS in annexB format 0001
            decoder = H264Decoder.createH264DecoderFromCodecPrivate(config);
            pic = Picture.create(dim.width, dim.height, ColorSpace.YUV420J);
            return true;
        } catch (Exception e) {
            log.warn("Exception on H264/AVC configure", e);
        }
        return false;
    }

    // https://github.com/jcodec/jcodec/blob/a3b8745bc874244359075c3e7302f9e6f386788b/samples/main/java/org/jcodec/samples/transcode/TranscodeMain.java#L1150
    @Override
    public byte[] process(ByteBuffer buf) {
        Frame frame = decoder.decodeFrame(buf, pic.getData());
        // gather the plane data for our return yuv420 array
        IoBuffer yuv = IoBuffer.allocate(pic.getWidth() * pic.getHeight()).setAutoExpand(true);
        yuv.put(frame.getPlaneData(0)); // y
        yuv.put(frame.getPlaneData(1)); // u
        yuv.put(frame.getPlaneData(2)); // v
        yuv.flip();
        return yuv.array();
    }

}
