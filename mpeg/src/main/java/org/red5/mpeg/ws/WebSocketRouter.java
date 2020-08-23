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

    @Override
    public void onWSConnect(WebSocketConnection conn) {
        log.info("Connect: {}", conn);
        connections.add(conn);
        // query string usage
        Map<String, Object> qparams = conn.getQuerystringParameters();
        log.debug("Query Str params: {}", qparams);
        if (qparams != null && !qparams.isEmpty()) {
            int i = 0;
            qparams.forEach((key, value) -> {
                char prefix = key.charAt(0);
                // strip any 'bad' prefixing of the key / name
                key = (prefix == '?' || prefix == '&') ? key.substring(1) : key;
                log.debug("Key: {} value: {}", key, value);
                // look for stream name
                if ("streamName".equals(key)) {
                    // set the stream name so we can send the data their interested in
                    conn.setAttribute("streamName", value);
                }
            });
        }
        log.debug("Connection tagged for stream: {}", conn.getAttribute("streamName"));
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
                log.debug("Found a match for stream name, sending data");
                try {
                    conn.send(data);
                } catch (Throwable t) {
                }
            }
        });
    }

    @Override
    public void stop() {
        connections.forEach(conn -> conn.close());
        connections.clear();
    }

}