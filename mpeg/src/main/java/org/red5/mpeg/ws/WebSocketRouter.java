package org.red5.mpeg.ws;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketConnection;
import org.red5.net.websocket.listener.WebSocketDataListener;
import org.red5.net.websocket.model.WSMessage;

import org.red5.mpeg.TSIngestConnection;

/**
 * Routes content to connected websocket connections.
 * 
 * @author Paul Gregoire
 */
public class WebSocketRouter extends WebSocketDataListener {

    private static Logger log = LoggerFactory.getLogger(TSIngestConnection.class);

    private CopyOnWriteArraySet<WebSocketConnection> connections = new CopyOnWriteArraySet<>();

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private Future<?> pinger;

    private volatile boolean ping = true;

    @Override
    public void onWSConnect(WebSocketConnection conn) {
        log.info("Connect: {}", conn);
        connections.add(conn);
        // query string usage
        Map<String, Object> qparams = conn.getQuerystringParameters();
        log.debug("Query Str params: {}", qparams);
        Object[] params = {};
        if (qparams != null && !qparams.isEmpty()) {
            params = new Object[qparams.size()];
            int i = 0;
            for (Map.Entry<String, Object> entry : qparams.entrySet()) {
                String key = entry.getKey();
                char prefix = key.charAt(0);
                // strip any 'bad' prefixing of the key / name
                key = (prefix == '?' || prefix == '&') ? key.substring(1) : key;
                // look for stream name
                if ("streamName".equals(key)) {
                    // set the stream name so we can send the data their interested in
                    conn.setAttribute("streamName", entry.getValue());
                }
                params[i] = key + '=' + entry.getValue();
                i++;
            }
        }
        // add a pinger
        if (pinger == null) {
            pinger = executor.submit(() -> {
                do {
                    try {
                        // sleep 2 seconds
                        Thread.sleep(2000L);
                        // create a ping packet
                        byte[] ping = "PING!".getBytes();
                        // loop through the connections and ping them
                        connections.forEach(c -> {
                            try {
                                c.send(ping);
                            } catch (Throwable t) {
                                log.warn("Exception in ping send", t);
                            }
                        });
                    } catch (Throwable t) {
                        log.warn("Exception in pinger", t);
                    }
                } while (ping);
            });
        }
    }

    @Override
    public void onWSDisconnect(WebSocketConnection conn) {
        log.info("Disconnect: {}", conn);
        connections.remove(conn);
    }

    @Override
    public void onWSMessage(WSMessage message) {
        // get the connection path for routing
        String path = message.getConnection().getPath();
        log.debug("WebSocket connection path: {}", path);
        // assume we have text
        String msg = new String(message.getPayload().array()).trim();
        log.info("onWSMessage: {}\n{}", msg, message.getConnection());
    }

    /**
     * Send text data to any websocket connection with a matching stream name attribute.
     * 
     * @param streamName
     * @param text
     */
    public void sendText(String streamName, String text) {
        connections.forEach(conn -> {
            if (streamName.equals(conn.getStringAttribute("streamName"))) {
                try {
                    conn.send(text);
                } catch (Throwable t) {
                }
            }
        });
    }

    /**
     * Send binary data to any websocket connection with a matching stream name attribute.
     * 
     * @param streamName
     * @param data
     */
    public void sendData(String streamName, byte[] data) {
        connections.forEach(conn -> {
            if (streamName.equals(conn.getStringAttribute("streamName"))) {
                try {
                    conn.send(data);
                } catch (Throwable t) {
                }
            }
        });
    }

    @Override
    public void stop() {
        // stop the pinging
        ping = false;
        pinger.cancel(false);
        executor.shutdownNow();
    }

}