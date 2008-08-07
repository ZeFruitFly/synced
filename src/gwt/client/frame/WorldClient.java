//
// $Id$

package client.frame;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.msoy.web.client.WebUserService;
import com.threerings.msoy.web.client.WebUserServiceAsync;
import com.threerings.msoy.web.data.ConnectConfig;
import com.threerings.msoy.web.data.LaunchConfig;
import com.threerings.msoy.web.data.WebCreds;

import client.shell.CShell;
import client.util.FlashClients;
import client.util.MsoyCallback;
import client.util.ServiceUtil;

/**
 * Manages our World client (which also handles Flash games).
 */
public class WorldClient extends Widget
{
    public static interface PanelProvider {
        public Panel get ();
    }

    public static void displayFlash (String flashArgs, final PanelProvider pprov)
    {
        // if we have not yet determined our default server, find that out now
        if (_defaultServer == null) {
            final String savedArgs = flashArgs;
            _usersvc.getConnectConfig(new MsoyCallback<ConnectConfig>() {
                public void onSuccess (ConnectConfig config) {
                    _defaultServer = config;
                    displayFlash(savedArgs, pprov);
                }
            });
            return;
        }

        // if we're currently already displaying exactly what we've been asked to display; then
        // stop here because we're just restoring our client after closing a GWT page
        if (flashArgs.equals(_curFlashArgs)) {
            return;
        }

        // create our client if necessary
        if (_curFlashArgs == null) {
            clientWillClose(); // clear our Java client if we have one
            _curFlashArgs = flashArgs; // note our new flash args before we tack on server info
            flashArgs += "&host=" + _defaultServer.server + "&port=" + _defaultServer.port;
            String partner = CShell.getPartner();
            if (partner != null) {
                flashArgs += "&partner=" + partner;
            }
            if (CShell.getAuthToken() != null) {
                flashArgs += "&token=" + CShell.getAuthToken();
            }
            FlashClients.embedWorldClient(pprov.get(), flashArgs);

        } else {
            // note our new current flash args
            clientGo("asclient", _curFlashArgs = flashArgs);
            clientMinimized(false);
        }
    }

    public static void displayFlashLobby (LaunchConfig config, String action, PanelProvider pprov)
    {
        clientWillClose(); // clear our Java or Flash client if we have one

        String flashArgs = "gameLobby=" + config.gameId;
        if (!action.equals("")) {
            flashArgs += "&gameMode=" + action;
        }
        flashArgs += ("&host=" + config.server + "&port=" + config.port);
        if (CShell.getAuthToken() != null) {
            flashArgs += "&token=" + CShell.getAuthToken();
        }
        FlashClients.embedGameClient(pprov.get(), flashArgs);
    }

    public static void displayJava (Widget client, PanelProvider pprov)
    {
        // clear out any flash page args
        _curFlashArgs = null;

        if (_jclient != client) {
            clientWillClose(); // clear out our flash client if we have one
            pprov.get().add(_jclient = client);
        } else {
            clientMinimized(false);
        }
    }

    public static void setMinimized (boolean minimized)
    {
        clientMinimized(minimized);
    }

    public static void clientWillClose ()
    {
        if (_curFlashArgs != null || _jclient != null) {
            if (_curFlashArgs != null) {
                clientUnload(); // TODO: make this work for jclient
            }
            _curFlashArgs = null;
            _jclient = null;
        }
    }

    public static void didLogon (WebCreds creds)
    {
        if (_curFlashArgs != null) {
            clientLogon(creds.getMemberId(), creds.token);
        }
        // TODO: let jclient know about logon?
    }

    /**
     * Tells the World client to go to a particular location.
     */
    protected static native boolean clientGo (String id, String where) /*-{
        var client = $doc.getElementById(id);
        if (client) {
            // exception from JavaScript break GWT; don't let that happen
            try { client.clientGo(where);  return true; } catch (e) {}
        }
        return false;
    }-*/;

    /**
     * Logs on the MetaSOY Flash client using magical JavaScript.
     */
    protected static native void clientLogon (int memberId, String token) /*-{
        var client = $doc.getElementById("asclient");
        if (client) {
            // exception from JavaScript break GWT; don't let that happen
            try { client.clientLogon(memberId, token); } catch (e) {}
        }
    }-*/;

    /**
     * Logs off the MetaSOY Flash client using magical JavaScript.
     */
    protected static native void clientUnload () /*-{
        var client = $doc.getElementById("asclient");
        if (client) {
            // exception from JavaScript break GWT; don't let that happen
            try { client.onUnload(); } catch (e) {}
        }
    }-*/;

    /**
     * Notifies the flash client that we're either minimized or not.
     */
    protected static native void clientMinimized (boolean mini) /*-{
        var client = $doc.getElementById("asclient");
        if (client) {
            // exception from JavaScript break GWT; don't let that happen
            try { client.setMinimized(mini); } catch (e) {}
        }
    }-*/;

    protected static String _curFlashArgs;
    protected static Widget _jclient;

    /** Our default world server. Configured the first time Flash is used. */
    protected static ConnectConfig _defaultServer;

    protected static final WebUserServiceAsync _usersvc = (WebUserServiceAsync)
        ServiceUtil.bind(GWT.create(WebUserService.class), WebUserService.ENTRY_POINT);
}
