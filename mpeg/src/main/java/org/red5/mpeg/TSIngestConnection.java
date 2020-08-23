package org.red5.mpeg;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.mina.core.buffer.IoBuffer;

import org.red5.codec.AACAudio;
import org.red5.codec.AVCVideo;
import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.StreamCodecInfo;
import org.red5.server.BaseConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.Red5;
import org.red5.server.api.IConnection.Duty;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.service.IStreamSecurityService;
import org.red5.server.api.stream.IStreamPublishSecurity;
import org.red5.server.api.stream.IStreamService;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.event.*;
import org.red5.server.net.rtmp.message.Packet;
import org.red5.server.plugin.PluginRegistry;
import org.red5.server.scope.Scope;
import org.red5.server.stream.ClientBroadcastStream;
import org.red5.server.stream.IProviderService;
import org.red5.server.stream.StreamService;
import org.red5.server.util.ScopeUtils;

/**
 * Pseudo connection for ingesting MPEG-TS data.
 * 
 * @author Paul Gregoire
 */
public class TSIngestConnection extends RTMPConnection {

    private static Logger logger = LoggerFactory.getLogger(TSIngestConnection.class);

    // executor for listeners
    private static ExecutorService executor = Executors.newCachedThreadPool();

    // largest chunk size we'll attempt to read at once
    private static int datagramSize = 8192;

    // socket idle timeout value in milliseconds
    public static long socketIdleTimeout = 8000;

    // host to listen on
    private String host;

    // port ingesting on
    private int port;

    // if we're using multicast for receive
    private boolean multicast;

    // fourCC codes for audio, video, and metadata
    private int audioFourCC, videoFourCC, metadataFourCC;

    // time for the latest incoming packet
    private volatile long lastReceiveTime;

    // name for the published stream
    private String streamName;

    private ClientBroadcastStream stream;

    private Listener listener;

    // closed flag
    private AtomicBoolean closed = new AtomicBoolean(false);

    public TSIngestConnection() throws InstantiationException {
        super(null);
        // ensure the scheduler is null to prevent any RTMPConnection conflicts
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
        lastReceiveTime = System.currentTimeMillis();
    }

    public boolean init(IScope scope, String streamName, String host, int port, int audioFourCC, int videoFourCC, int metadataFourCC) {
        // initialize connection properties
        this.host = host;
        this.port = port;
        if (audioFourCC > 0) {
            this.audioFourCC = audioFourCC;
        }
        if (videoFourCC > 0) {
            this.videoFourCC = videoFourCC;
        }
        if (metadataFourCC > 0) {
            this.metadataFourCC = metadataFourCC;
        }
        // create a stream
        stream = (ClientBroadcastStream) scope.getContext().getBean("clientBroadcastStream");
        stream.setScope(scope);
        stream.setName(streamName);
        stream.setPublishedName(streamName);
        stream.setStreamId(1);
        stream.setRegisterJMX(false);
        stream.setConnection(this);
        // register the stream
        IContext context = scope.getContext();
        IProviderService providerService = (IProviderService) context.getBean(IProviderService.BEAN_NAME);
        if (providerService.registerBroadcastStream(scope, streamName, stream)) {
            logger.debug("Stream registered: {}", stream.getPublishedName());
            IBroadcastScope bsScope = scope.getBroadcastScope(streamName);
            bsScope.setClientBroadcastStream(stream);
            ((BaseConnection) this).registerBasicScope(bsScope);
            logger.debug("Scope: {} registered with connection: {}", bsScope.getPath(), getSessionId());
            setDuty(Duty.PUBLISHER);
            // start the stream
            stream.start();
            // start publishing
            stream.startPublishing();
            // instance the listener
            listener = new Listener();
            // start listening
            listener.start();
            // making it this far implies that we're good to go!
            return true;
        } else {
            logger.warn("Stream publish failed for {}", streamName);
        }
        return false;
    }

    @Override
    public boolean isIdle() {
        long now = System.currentTimeMillis();
        boolean idle = false;
        if (listener != null) {
            idle = (now - lastReceiveTime) > socketIdleTimeout;
        }
        if (idle) {
            logger.info("Closing due to inactivity; last recv: {}", (now - lastReceiveTime));
            close();
        }
        return idle;
    }

    @Override
    public boolean isConnected() {
        final byte state = getState().getState();
        if (state == RTMP.STATE_DISCONNECTED || state == RTMP.STATE_DISCONNECTING) {
            return false;
        }
        return true;
    }

    @Override
    public void onInactive() {
        close();
    }

    @Override
    public void write(Packet out) {
        // no-op
    }

    @Override
    public void writeRaw(IoBuffer out) {
        // no-op
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            logger.debug("close");
            // set disconnecting flag first
            setStateCode(RTMP.STATE_DISCONNECTING);
            // set connection local
            Red5.setConnectionLocal(this);
            if (stream != null) {
                stream.close();
                stream = null;
            }
            // stops the consumer
            Optional.ofNullable(listener).ifPresent(listener -> {
                listener.stop();
                listener = null;
            });
            // super close!
            super.close();
            // reset connection local
            Red5.setConnectionLocal(null);
        }
    }

    public int getPort() {
        return port;
    }

    public void setMulticast(boolean multicast) {
        this.multicast = multicast;
    }

    public class Listener {

        // datagram socket instance (DatagramSocket for unicast and MulticastSocket for multicast)
        DatagramSocket socket;

        InetAddress addr;

        TSHandler handler;

        volatile boolean listening;

        Future<?> recvFuture;

        StreamCodecInfo codecInfo;

        public void start() {
            logger.info("MPEG-TS listener starting on: {}", port);
            try {
                // join the address or multicast group
                addr = InetAddress.getByName(host);
                // create the multicast socket
                if (multicast) {
                    socket = new MulticastSocket(port);
                    ((MulticastSocket) socket).joinGroup(addr);
                } else {
                    socket = new DatagramSocket(port, addr);
                }
                // set a timeout so receive cannot block forever
                socket.setSoTimeout((int) socketIdleTimeout);
                // create a single packet for re-use in the recv loop
                final DatagramPacket packet = new DatagramPacket(new byte[datagramSize], datagramSize);
                // stream codec configuration
                codecInfo = (StreamCodecInfo) stream.getCodecInfo();
                // ts configuration
                TSConfig config = new TSConfig();
                config.name = streamName;
                config.pmtPid = (short) 4096;
                config.audioPid = (short) 257;
                config.videoPid = (short) 256;
                // configure the handler
                handler = TSHandler.build(config);
                logger.info("Handler id: {}", handler.getId());
                TSReceiver receiver = handler.getReceiver();
                // get the receiver thread
                recvFuture = executor.submit(() -> {
                    // set the listening flag
                    listening = true;
                    do {
                        TSPacket pkt = receiver.getNext();
                        if (pkt != null) {
                            logger.info("Received: {}", pkt.getPayload().length);
                            // only demuxed ts should show up here
                            if (!pkt.isMpegTs()) {
                                process(pkt);
                            }
                        } else {
                            try {
                                // read data until the socket is closed
                                socket.receive(packet); // this blocks!
                                // update the receive time so we dont go idle
                                lastReceiveTime = System.currentTimeMillis();
                                // used for the copy of packet data for the ts handler since it queues this up
                                byte[] data = new byte[packet.getLength()];
                                // copy the packet content to an array for the tsHandler
                                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, data.length);
                                // demux the data
                                handler.demux(data);
                            } catch (Throwable t) {
                                if (t.getMessage().contains("closed")) {
                                    logger.debug("Socket was closed during receive");
                                } else {
                                    logger.warn("Exception in receive", t);
                                }
                                break;
                            }
                        }
                    } while (listening);
                    // write any left over packets
                    LinkedList<TSPacket> pkts = receiver.drain();
                    pkts.forEach(pkt -> process(pkt));
                    pkts.clear();
                }, "ReceiveHandler");
            } catch (Throwable t) {
                logger.warn("Exception in listen", t);
            }
        }

        public void process(TSPacket pkt) {
            if (pkt.isAudio()) {
                if (!codecInfo.hasAudio()) {
                    codecInfo.setAudioCodec(new AACAudio());
                    codecInfo.setHasAudio(true);
                }
                // TODO handle MPEG-TS ES (adts/etc) to Flash Audio

                AudioData audio = new AudioData(IoBuffer.wrap(pkt.getPayload()));
                audio.setTimestamp((int) pkt.getTimestamp());
                stream.dispatchEvent(audio);
            } else if (pkt.isVideo()) {
                if (!codecInfo.hasVideo()) {
                    AVCVideo video = new AVCVideo();
                    video.setBufferInterframes(false);
                    codecInfo.setVideoCodec(video);
                    codecInfo.setHasVideo(true);
                }
                // TODO handle MPEG-TS ES (h264/hevc/etc) to Flash Video

                VideoData video = new VideoData(IoBuffer.wrap(pkt.getPayload()));
                video.setTimestamp((int) pkt.getTimestamp());
                stream.dispatchEvent(video);
            }
        }

        public void stop() {
            logger.info("Listener stop");
            // reset our flag so the receiver can exit
            listening = false;
            // stop the future without interrupting
            if (recvFuture != null) {
                recvFuture.cancel(false);
                recvFuture = null;
            }
            // destroy the handler
            if (handler != null) {
                logger.info("Listener handler destroy");
                try {
                    handler.destroy();
                } catch (Exception e) {
                    logger.warn("Exception destroying handler", e);
                }
                handler = null;
            }
            if (socket != null) {
                try {
                    if (multicast) {
                        ((MulticastSocket) socket).leaveGroup(addr);
                    }
                    socket.close();
                } catch (IOException e) {
                }
                socket = null;
            }
        }

    }

}