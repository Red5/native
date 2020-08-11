package org.red5.ndi;

import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.mina.core.buffer.IoBuffer;
import org.bouncycastle.util.encoders.Hex;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.common.model.Rational;
import org.jcodec.common.model.Size;
import org.red5.codec.AudioCodec;
import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.VideoCodec;
import org.red5.ndi.codec.AACCodec;
import org.red5.ndi.codec.IAudioHandler;
import org.red5.ndi.codec.IVideoHandler;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.net.rtmp.event.Aggregate;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.stream.IStreamData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens on an IBroadcastStream and produces content over NDI.
 * 
 * @author Paul Gregoire
 */
public class NDIStreamProducer implements IStreamListener {

    private static Logger log = LoggerFactory.getLogger(NDIStreamProducer.class);

    private static boolean isTrace = log.isTraceEnabled();

    //private static boolean isDebug = log.isDebugEnabled();

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private NDISender sender;

    private Future<?> senderFuture;

    private volatile boolean haveAudioPrivate, haveVideoPrivate;

    private int audioSampleRate = 44100;

    private int audioChannels = 1; // mono

    private int aacProfile = 2; // AAC LC (Low Complexity)

    private int aacFrequencyIndex = -1;
    
    private IAudioHandler audioHandler;

    private AvcCBox avcCBox;

    private Dimension dim;

    private IVideoHandler videoHandler;

    static {
        // get the party started
        NDI.loadLibrary();
    }

    /**
     * Creates and starts the NDI sender.
     * 
     * @param name
     *            display name for NDI discovery
     * @param dim
     *            video dimensions
     * @param num
     *            frame rate numerator
     * @param den
     *            frame rate denominator
     * @param aspect
     *            video aspect ratio
     * @param sampleRate
     *            audio sample rate
     * @param channels
     *            audio channel count
     */
    public void start(String name, Dimension dim, int num, int den, float aspect, int sampleRate, int channels) {
        // create a configuration
        NDIConfig config = new NDIConfig();
        config.name = name;
        config.width = dim.width;
        config.height = dim.height;
        // get fps or default to 25
        if (num > 0 && den > 0) {
            config.numerator = num;
            config.denominator = den;
        } else {
            Rational defaultFps = Rational.R(25, 1);
            config.numerator = defaultFps.num;
            config.denominator = defaultFps.den;
        }
        //config.aspectRatio = aspect;
        config.sampleRate = sampleRate;
        config.channels = channels;
        sender = NDISender.build(config);
        log.info("Sender id: {}", sender.getId());
        // start the sender thread
        senderFuture = executor.submit(() -> {
            Thread.currentThread().setName(String.format("StartHandler@%s", name));
            try {
                // this blocks!
                sender.start();
            } catch (Throwable t) {
                log.warn("Exception in start", t);
            } finally {
                sender.stop();
            }
        });
    }

    /**
     * Stops this producer.
     */
    public void stop() {
        if (senderFuture != null) {
            senderFuture.cancel(false);
            senderFuture = null;
        }
    }

    /** {@inheritDoc} */
    public void packetReceived(IBroadcastStream stream, IStreamPacket packet) {
        // check for the existence of audio and / or video
        IStreamCodecInfo codecInfo = stream.getCodecInfo();
        codecInfo.hasAudio();
        codecInfo.hasVideo();
        // handle packet types as they arrive
        if (!senderFuture.isDone()) {
            try {
                // get the packet data
                IoBuffer data = ((IStreamData<?>) packet).getData();
                // duplicate if possible, copy if we must
                IoBuffer buf;
                if (data.isReadOnly() || !data.hasArray()) {
                    // cannot duplicate; make a copy
                    byte[] array = new byte[data.remaining()];
                    data.get(array);
                    data.rewind();
                    buf = IoBuffer.wrap(array);
                } else {
                    buf = packet.getData().duplicate();
                }
                // get the data type
                final byte dataType = packet.getDataType();
                switch (dataType) {
                    case Constants.TYPE_AUDIO_DATA:
                        // audio - decode into shorts
                        decodeAndSend(new AudioData(buf), packet.getTimestamp());
                        break;
                    case Constants.TYPE_VIDEO_DATA:
                        // video - decode into yuv420p
                        decodeAndSend(new VideoData(buf), packet.getTimestamp());
                        break;
                    case Constants.TYPE_NOTIFY:
                        // metadata - encode into XML
                        decodeAndSend(new Notify(buf), packet.getTimestamp());
                        break;
                    case Constants.TYPE_AGGREGATE:
                        Aggregate aggregate = new Aggregate(buf);
                        aggregate.getParts().forEach(part -> {
                            byte aggDataType = part.getDataType();
                            switch (aggDataType) {
                                case Constants.TYPE_AUDIO_DATA:
                                    // audio - decode into shorts
                                    decodeAndSend((AudioData) part, part.getTimestamp());
                                    break;
                                case Constants.TYPE_VIDEO_DATA:
                                    // video - decode into yuv420p
                                    decodeAndSend((VideoData) part, part.getTimestamp());
                                    break;
                            }
                        });
                        break;
                    default:
                        log.warn("Packet type: {} not handled", dataType);
                }
            } catch (Exception e) {
                log.warn("Exception in packet receive", e);
            }
        }
    }

    /**
     * Decode audio data and send it out via NDI.
     * 
     * @param audio
     * @param timestamp
     */
    @SuppressWarnings("incomplete-switch")
    private void decodeAndSend(AudioData audio, int timestamp) {
        // get the audio data
        IoBuffer buf = audio.getData();
        int bufLength = buf.remaining();
        // get the codec
        AudioCodec codec = NDIStreamProducer.valueOfAudioCodecId((byte) ((buf.get() & 0xf0) >> 4)); // position 0
        // handle based on audio codec type
        switch (codec) {
            case AAC:
                // we haven't seen the aac config yet
                if (!haveAudioPrivate) {
                    if (buf.get() == 0) { // position 1
                        // set mark
                        buf.mark();
                        // pull-out in-line config data
                        byte objAndFreq = buf.get();
                        byte freqAndChannel = buf.get();
                        aacProfile = ((objAndFreq & 0xFF) >> 3) & 0x1F;
                        aacFrequencyIndex = (objAndFreq & 0x7) << 1 | (freqAndChannel >> 7) & 0x1;
                        audioSampleRate = AACCodec.samplingFrequencyIndexMap.get(aacFrequencyIndex);
                        audioChannels = (freqAndChannel & 0x78) >> 3;
                        log.debug("AAC config - profile: {} freq: {} rate: {} channels: {}", new Object[] { aacProfile, aacFrequencyIndex, audioSampleRate, audioChannels });
                        // reset to mark
                        buf.reset();
                        // construct aac config / private data
                        ByteBuffer config = ByteBuffer.allocate(bufLength - 2);
                        buf.get(config.array());
                        config.flip();
                        // flip the audio private / config flag
                        haveAudioPrivate = audioHandler.configure(config);
                    } else {
                        log.debug("Rejecting AAC, private not seen yet");
                        return;
                    }
                }
                // skip 1
                buf.skip(1);
                // add ADTS header
                byte[] data = new byte[bufLength + 5]; // (bodySize - 2) + 7 (no protection)
                if (aacFrequencyIndex == -1) {
                    aacFrequencyIndex = AACCodec.samplingFrequencyIndexMap.get(audioSampleRate);
                }
                int finallength = data.length;
                data[0] = (byte) 0xff; // syncword 0xFFF, all bits must be 1
                data[1] = (byte) 0b11110001; // mpeg v0, layer 0, protection absent
                data[2] = (byte) (((aacProfile - 1) << 6) + (aacFrequencyIndex << 2) + (audioChannels >> 2));
                data[3] = (byte) (((audioChannels & 0x3) << 6) + (finallength >> 11));
                data[4] = (byte) ((finallength & 0x7ff) >> 3);
                data[5] = (byte) (((finallength & 7) << 5) + 0x1f);
                data[6] = (byte) 0xfc;
                // slice out what we want, skip af 01; offset to 7
                buf.get(data, 7, bufLength - 2);
                // send the AAC packet to a handler for decoding
                short[] samples = audioHandler.process(ByteBuffer.wrap(data));
                // send the samples out
                sender.send(samples);
                break;
            case SPEEX:
                break;
        }
    }

    /**
     * Decode video data and send it out via NDI.
     * 
     * @param video
     * @param timestamp
     */
    @SuppressWarnings("incomplete-switch")
    private void decodeAndSend(VideoData video, int timestamp) {
        // get the video data
        IoBuffer buf = video.getData();
        // grab codec flags
        byte flags = buf.get(); // position 0
        // get the codec
        VideoCodec codec = NDIStreamProducer.valueOfVideoCodecId((byte) (flags & 0x0f));
        // determine if we've got a keyframe
        @SuppressWarnings("unused")
        boolean isKey = false;
        if ((flags & 0xf0) == IVideoStreamCodec.FLV_FRAME_KEY) {
            isKey = true;
        }
        // handle based on video codec type
        switch (codec) {
            case AVC:
                // we haven't seen the avc config yet
                if (!haveVideoPrivate) {
                    if (buf.get() == 0) { // position 1
                        // move past bytes we dont care about
                        buf.position(5);
                        log.debug("AvcCBox data: {}", Hex.toHexString(buf.array(), 5, buf.limit() - 5));
                        avcCBox = AvcCBox.parseAvcCBox(buf.buf());
                        SeqParameterSet sps = SeqParameterSet.read(avcCBox.getSpsList().get(0));
                        PictureParameterSet pps = PictureParameterSet.read(avcCBox.getPpsList().get(0));
                        log.info("SPS id: {} PPS id: {} profile idc: {}", sps.seqParameterSetId, pps.picParameterSetId, sps.profileIdc);
                        // get size
                        Size size = H264Utils.getPicSize(sps);
                        if (size != null) {
                            dim = new Dimension(size.getWidth(), size.getHeight());
                        }
                        // get the sps/pps data into a buffer for configuring the decoder
                        IoBuffer config = IoBuffer.allocate(256).setAutoExpand(true); // guesstimate size + autoexpand
                        config.putInt(1);
                        // TODO determine the nalu type values instead of hard-coding them
                        config.put((byte) 0x67); // SPS
                        sps.write(config.buf());
                        config.putInt(1);
                        // TODO determine the nalu type values instead of hard-coding them
                        config.put((byte) 0x68); // PPS
                        pps.write(config.buf());
                        config.flip();
                        if (isTrace) {
                            log.trace("SPS/PPS nalu: {}", Hex.toHexString(config.array()));
                        }
                        // flip the video private / config flag
                        haveVideoPrivate = videoHandler.configure(config.buf(), dim);
                        // we may or may not be configured for decoding, but return anyway
                        return;
                    } else {
                        log.debug("Rejecting h264, private not seen yet");
                        // decoder isn't configured yet, so we can't decode the nalu
                        return;
                    }
                }
                // gather nalu and send them to the decoder (assumed to contain 1..n nalu)
                ByteBuffer nalus = ByteBuffer.wrap(Arrays.copyOfRange(buf.array(), 5, buf.remaining() - 5));
                // we've got decoded frame / image data
                byte[] frameData = videoHandler.process(nalus);
                // send out the frame data
                sender.send(frameData, NDIDataType.video);
                break;
        }
    }

    /**
     * Decode notify / metadata and send it out via NDI.
     * 
     * @param notify
     * @param timestamp
     */
    private void decodeAndSend(Notify notify, int timestamp) {

    }

    /**
     * Audio codec id to AudioCodec enum helper.
     * 
     * @param codecId
     * @return AudioCodec
     */
    public static AudioCodec valueOfAudioCodecId(byte codecId) {
        // TODO move this to AudioCodec enum
        for (AudioCodec c : AudioCodec.values()) {
            if (c.getId() == codecId) {
                return c;
            }
        }
        return null;
    }

    /**
     * Video codec id to VideoCodec enum helper.
     * 
     * @param codecId
     * @return VideoCodec
     */
    public static VideoCodec valueOfVideoCodecId(byte codecId) {
        // TODO move this to VideoCodec enum
        for (VideoCodec c : VideoCodec.values()) {
            if (c.getId() == codecId) {
                return c;
            }
        }
        return null;
    }
}