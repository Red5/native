package org.red5.mpeg;

import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.WebSocketScopeManager;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.plugin.PluginRegistry;

import org.red5.mpeg.ws.WebSocketRouter;

/**
 * Simple Red5 webapp for setting up the websocket.
 * 
 */
public class Application extends MultiThreadedApplicationAdapter {

    @Override
    public boolean appStart(IScope scope) {
        // ensure the libs are loaded
        Main.loadLibrary();
        // configure websocket support
        WebSocketPlugin wsPlugin = ((WebSocketPlugin) PluginRegistry.getPlugin(WebSocketPlugin.NAME));
        WebSocketScopeManager manager = wsPlugin.getManager(scope);
        if (manager == null) {
            MultiThreadedApplicationAdapter app = (MultiThreadedApplicationAdapter) scope.getHandler();
            log.info("Creating WebSocketScopeManager for {}", app);
            // set the application in the plugin to create a websocket scope manager for it
            wsPlugin.setApplication(app);
            // get the new manager
            manager = wsPlugin.getManager(scope);
        }
        // the websocket scope
        WebSocketScope wsScope = (WebSocketScope) scope.getAttribute(WSConstants.WS_SCOPE);
        // check to see if its already configured
        if (wsScope == null) {
            log.debug("Configuring application scope: {}", scope);
            // create a websocket scope for the application
            wsScope = new WebSocketScope(scope);
            // register the ws scope
            wsScope.register();
        }
        // add the listeners if absent
        if (!wsScope.hasListener(WebSocketRouter.class)) {
            WebSocketRouter router = new WebSocketRouter();
            // add the router
            wsScope.addListener(router);
            // set the router in the connection for ws proxy / relay
            TSIngestConnection.setWebSocketRouter(router);
        }
	    return true;
    }

    @Override
    public void streamBroadcastStart(IBroadcastStream stream) {
    }

    @Override
    public void streamBroadcastClose(IBroadcastStream stream) {
    }

}
