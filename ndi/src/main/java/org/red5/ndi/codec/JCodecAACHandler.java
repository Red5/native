package org.red5.ndi.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.jcodec.common.io.NIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;

/**
 * AAC handling using JCodec.
 * 
 * @author Paul Gregoire
 */
public class JCodecAACHandler implements IAudioHandler {

    private static Logger log = LoggerFactory.getLogger(JCodecAACHandler.class);

    // jcodec ref https://github.com/jcodec/jcodec/commit/a3b8745bc874244359075c3e7302f9e6f386788b
    // adts ref https://wiki.multimedia.cx/index.php/ADTS

    private Decoder decoder;

    private SampleBuffer sampleBuffer;

    public boolean configure(ByteBuffer config) {
        try {
            decoder = new Decoder(config.array());
            sampleBuffer = new SampleBuffer();
            return true;
        } catch (AACException e) {
            log.warn("Exception on AAC configure", e);
        }
        return false;
    }

    public short[] process(ByteBuffer buf) {
        try {
            // decodes into raw PCM
            decoder.decodeFrame(NIOUtils.toArray(buf), sampleBuffer);
            //if (sampleBuffer.isBigEndian()) {
            //    toLittleEndian(sampleBuffer);
            //}
            byte[] sampleBytes = sampleBuffer.getData();
            short[] samples = new short[sampleBytes.length / 2];
            ByteBuffer.wrap(sampleBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
            return samples;
        } catch (AACException e) {
            log.warn("Exception on AAC process", e);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private void toLittleEndian(SampleBuffer sampleBuffer) {
        byte[] data = sampleBuffer.getData();
        for (int i = 0; i < data.length; i += 2) {
            byte tmp = data[i];
            data[i] = data[i + 1];
            data[i + 1] = tmp;
        }
    }

}
