package org.red5.mpeg.servlet;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.adapter.StatefulScopeWrappingAdapter;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import org.red5.mpeg.Main;
import org.red5.mpeg.PayloadType;
import org.red5.mpeg.TSIngestConnection;
import org.red5.mpeg.ws.WebSocketRouter;

/**
 * This servlet provides create and kill actions for a mpeg-ts ingest stream. Requests are handled as simple key/value via HTTP.GET
 * and responses are plain-text or HTTP status codes. Under the hood, this servlet will open the specified port and start a listener
 * for incoming packets framed as mpeg-ts.
 * <br>
 * Create: <pre>http://localhost:5080/mpeg/createingest?action=create&port=49152&name=stream1</pre>
 * <br>
 * Kill: <pre>http://localhost:5080/mpeg/createingest?action=kill&name=stream1</pre>
 * <br>
 * 
 * @author Paul Gregoire
 */
public class CreateIngestServlet extends HttpServlet {

    private static final long serialVersionUID = 82471927112L;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int PORT_DEFAULT = 49152; // first port in ephemeral range

    // set this as-soon-as-possible after the server has started
    private static IScope appScope;

    static {
        // ensure the libs are loaded
        Main.loadLibrary();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String result = null;
        // ensure appScope is configured
        if (appScope == null) {
            ApplicationContext appCtx = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
            StatefulScopeWrappingAdapter app = (StatefulScopeWrappingAdapter) appCtx.getBean("web.handler");
            appScope = app.getScope();
            logger.debug("Application scope: {}", appScope);
        }
        // get the requested action
        String action = request.getParameter("action");
        logger.debug("Action: {}", action);
        String streamName = request.getParameter("name");
        // if the stream name contains slashes, assume room type scoping
        if (StringUtils.isNotBlank(streamName)) {
            String scopePath = null;
            if (streamName.contains("/")) {
                String[] parts = streamName.split("/");
                // strip-off stream name
                scopePath = streamName.substring(0, streamName.lastIndexOf('/'));
                // grab only the stream name itself
                streamName = parts[parts.length - 1];
            }
            IScope scope = StringUtils.isNotBlank(scopePath) ? ScopeUtils.resolveScope(appScope, scopePath) : appScope; // default to mpeg, our app
            logger.info("Stream: {} context path: {}", streamName, scope.getContextPath());
            if ("create".equals(action)) {
                // check to see if the stream name on the given scope is available (not bound)
                if (!isAvailable(scope, streamName)) {
                    // return resource already exists
                    response.sendError(409, "Stream name conflict, already in-use");
                } else {
                    String host = Optional.ofNullable(request.getParameter("host")).orElse("127.0.0.1");
                    // TODO determine if multicast is requested by looking at the host address; check for class D
                    int port = Integer.valueOf(request.getParameter("port"));
                    PayloadType audio = PayloadType.valueOf(String.format("TYPE_%s", Optional.ofNullable(request.getParameter("audio")).orElse("ADTS").toUpperCase()));
                    PayloadType video = PayloadType.valueOf(String.format("TYPE_%s", Optional.ofNullable(request.getParameter("video")).orElse("H264").toUpperCase()));
                    int audioFourCC = audio.typeId, videoFourCC = video.typeId, metadataFourCC = 0;
                    try {
                        // create an endpoint for ingest
                        TSIngestConnection conn = new TSIngestConnection();
                        // if multicast is wanted, it has to be set before init
                        if (request.getParameter("multicast") != null) {
                            conn.setMulticast(true);
                        }
                        if (conn.init(scope, streamName, host, port, audioFourCC, videoFourCC, metadataFourCC)) {
                            result = "Ingest configured and started successfully";
                        } else {
                            throw new Exception("Connection init failed");
                        }
                    } catch (Exception e) {
                        logger.warn("Exception setting up stream", e);
                        response.sendError(500, "Error setting up the stream ingest");
                    }
                }
            } else if ("kill".equals(action)) {
                if (isAvailable(scope, streamName)) {
                    response.sendError(409, "Stream name is not active");
                } else {
                    // TODO get the connection for the stream and close it
                    result = "Ingest stopped";
                }
            }
            if (result != null) {
                try {
                    response.getOutputStream().write(result.getBytes());
                } catch (Exception e) {
                    logger.warn("Exception writing response", e);
                }
            }
        } else {
            // return invalid request 400
            response.sendError(400, "Blank stream name not allowed");
        }
    }

    /**
     * Returns whether or not a stream name is available for a given scope.
     * 
     * @param scope
     * @param streamName
     * @return true if the stream doesnt exist in the scope and false if its not available
     */
    private boolean isAvailable(IScope scope, String streamName) {
        Optional<IBroadcastScope> bs = Optional.ofNullable(scope.getBroadcastScope(streamName));
        if (bs.isPresent() && bs.get().getClientBroadcastStream() != null) {
            return false;
        }
        return true;
    }

}
