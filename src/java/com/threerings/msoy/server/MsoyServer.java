//
// $Id$

package com.threerings.msoy.server;

import static com.threerings.msoy.Log.log;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.mina.common.IoAcceptor;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.Interval;
import com.samskivert.util.Invoker;

import com.threerings.util.Name;

import com.threerings.messaging.DelayedMessageConnection;
import com.threerings.messaging.MessageConnection;
import com.threerings.messaging.amqp.AMQPMessageConfig;
import com.threerings.messaging.amqp.AMQPMessageConnection;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.net.AuthRequest;
import com.threerings.presents.peer.server.PeerManager;
import com.threerings.presents.server.Authenticator;
import com.threerings.presents.server.ClientFactory;
import com.threerings.presents.server.ClientResolver;
import com.threerings.presents.server.PresentsSession;
import com.threerings.presents.server.PresentsDObjectMgr;
import com.threerings.presents.server.PresentsServer;
import com.threerings.presents.server.ShutdownManager;

import com.threerings.crowd.chat.server.ChatChannelManager;
import com.threerings.crowd.chat.server.ChatProvider;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.peer.server.CrowdPeerManager;
import com.threerings.crowd.server.BodyLocator;
import com.threerings.crowd.server.PlaceRegistry;

import com.threerings.admin.server.ConfigRegistry;
import com.threerings.admin.server.PeeredDatabaseConfigRegistry;

import com.threerings.parlor.game.server.GameManager;

import com.threerings.whirled.server.SceneRegistry;
import com.threerings.whirled.server.persist.SceneRepository;
import com.threerings.whirled.util.SceneFactory;

import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.all.DeploymentConfig;
import com.threerings.msoy.data.all.MemberName;

import com.threerings.msoy.admin.server.MsoyAdminManager;
import com.threerings.msoy.bureau.server.WindowAuthenticator;
import com.threerings.msoy.bureau.server.WindowClientFactory;
import com.threerings.msoy.chat.server.JabberManager;
import com.threerings.msoy.chat.server.MsoyChatChannelManager;
import com.threerings.msoy.chat.server.MsoyChatProvider;
import com.threerings.msoy.game.server.WorldGameRegistry;
import com.threerings.msoy.item.server.ItemManager;
import com.threerings.msoy.money.server.MoneyLogic;
import com.threerings.msoy.party.server.PartyRegistry;
import com.threerings.msoy.peer.server.MsoyPeerManager;
import com.threerings.msoy.room.server.MsoySceneFactory;
import com.threerings.msoy.room.server.MsoySceneRegistry;
import com.threerings.msoy.room.server.PetManager;
import com.threerings.msoy.room.server.persist.MsoySceneRepository;
import com.threerings.msoy.swiftly.server.SwiftlyManager;
import com.threerings.msoy.web.server.MsoyHttpServer;
import com.threerings.msoy.world.server.WorldWatcherManager;
import com.threerings.msoy.world.tour.server.TourManager;

/**
 * Brings together all of the services needed by the World server.
 */
@Singleton
public class MsoyServer extends MsoyBaseServer
    implements ShutdownManager.Shutdowner
{
    /** Configures dependencies needed by the world server. */
    public static class Module extends MsoyBaseServer.Module
    {
        @Override protected void configure () {
            super.configure();
            // presents dependencies
            bind(Authenticator.class).to(MsoyAuthenticator.class);
            bind(PresentsServer.class).to(MsoyServer.class);
            bind(PeerManager.class).to(MsoyPeerManager.class);
            // crowd dependencies
            bind(BodyLocator.class).to(MemberLocator.class);
            bind(ChatProvider.class).to(MsoyChatProvider.class);
            bind(PlaceRegistry.class).to(RoomRegistry.class);
            bind(CrowdPeerManager.class).to(MsoyPeerManager.class);
            bind(ChatChannelManager.class).to(MsoyChatChannelManager.class);
            // vilya whirled dependencies
            bind(SceneRepository.class).to(MsoySceneRepository.class);
            bind(SceneFactory.class).to(MsoySceneFactory.class);
            bind(SceneRegistry.class).to(MsoySceneRegistry.class);
            bind(SceneRegistry.ConfigFactory.class).to(MsoySceneFactory.class);
            // msoy auth dependencies
            bind(MsoyAuthenticator.Domain.class).to(OOOAuthenticationDomain.class);
            // Messaging dependencies
            bind(MessageConnection.class).toInstance(createAMQPConnection());
        }
    }

    /**
     * Starts everything a runnin'.
     */
    public static void main (final String[] args)
    {
        // if we're on the dev server, up our long invoker warning to 3 seconds
        if (ServerConfig.autoRestart) {
            Invoker.setDefaultLongThreshold(3000L);
        }

        final Injector injector = Guice.createInjector(new Module());
        final MsoyServer server = injector.getInstance(MsoyServer.class);
        try {
            server.init(injector);
            server.run();
        } catch (final Exception e) {
            log.warning("Unable to initialize server", e);
            System.exit(255);
        }
    }

    // from interface ShutdownManager.Shutdowner
    public void shutdown ()
    {
        // shut down our http server
        try {
            _httpServer.stop();
        } catch (final Exception e) {
            log.warning("Failed to stop http server.", e);
        }

        // and our policy server if one is running
        if (_policyServer != null) {
            _policyServer.unbindAll();
        }

        // and the message connection
        try {
            _messageConn.close();
        } catch (IOException ioe) {
            log.warning("Failed to close the connection to the messaging server.", ioe);
        }
    }

    @Override // from MsoyBaseServer
    public void init (final Injector injector)
        throws Exception
    {
        super.init(injector);

        // we need to know when we're shutting down
        _shutmgr.registerShutdowner(this);

        // initialize our HTTP server
        _httpServer.init(new File(ServerConfig.serverRoot, "log"));

        // Due to a circular dependency, this instance cannot be injected into MsoyChatProvider.
        // This is also temporary to support broadcasts in games.  When you maintain a subscription
        // to a PlaceObject on the WorldServer while in games, MsoyChatProvider's overridden
        // broadcast method becomes unneccessary
        ((MsoyChatProvider) _chatprov).init(_gameReg);

        // start up our peer manager
        log.info("Running in cluster mode as node '" + ServerConfig.nodeName + "'.");
        _peerMan.init(injector, ServerConfig.nodeName, ServerConfig.sharedSecret,
                      ServerConfig.backChannelHost, ServerConfig.serverHost, getListenPorts()[0]);

        // intialize various services
        _adminMan.init();
        _memberMan.init();
        _friendMan.init();
        _jabberMan.init();
        _itemMan.init();
        _swiftlyMan.init(_invmgr);
        _gameReg.init();
        _partyReg.init();
        _moneyLogic.init();
        _tourMan.init();
        
        // Let the bureaus connect to our game server(s)
        _bureauMgr.setGameServerRegistryOid(_gameReg.getServerRegistryObject().getOid());

        // TEMP: give a peer manager refernce to MemberNodeActions
        MemberNodeActions.init(_peerMan);

        GameManager.setUserIdentifier(new GameManager.UserIdentifier() {
            public int getUserId (final BodyObject bodyObj) {
                final int memberId = ((MemberObject) bodyObj).getMemberId();
                return MemberName.isGuest(memberId) ? 0 : memberId;
            }
        });

        // start up our HTTP server
        _httpServer.start();

        // if we're a dev deployment and our policy port is not privileged, run the policy server
        // right in the msoy server to simplify life for developers
        if (DeploymentConfig.devDeployment && ServerConfig.socketPolicyPort > 1024 &&
                ServerConfig.nodeName.equals("msoy1")) {
            _policyServer = MsoyPolicyServer.init();
        }

        // start up an interval that checks to see if our code has changed and auto-restarts the
        // server as soon as possible when it has
        if (ServerConfig.autoRestart) {
            _codeModified = codeModifiedTime();
            new Interval() { // Note well: this interval does not run on the dobj thread
                @Override
                public void expired () {
                    // ...we simply post a LongRunnable to do the job
                    _omgr.postRunnable(new PresentsDObjectMgr.LongRunnable() {
                        public void run () {
                            checkAutoRestart();
                        }
                    });
                }
                @Override
                public String toString () {
                    return "checkAutoRestart interval";
                }
            }.schedule(AUTO_RESTART_CHECK_INTERVAL, true);
        }

        log.info("Msoy server initialized.");
    }

    /**
     * On dev deployments, restart the policy server, including these ports from another node
     * on this machine.
     */
    public void addPortsToPolicy (final int[] ports)
    {
        if (!DeploymentConfig.devDeployment || _shutmgr.isShuttingDown()) {
            return;
        }

        if (_otherNodePorts == null) {
            _otherNodePorts = new ArrayIntSet();
        }
        _otherNodePorts.add(ports);

        if (_policyServer != null) {
            _policyServer.unbindAll();
        }
        try {
            _policyServer = MsoyPolicyServer.init(_otherNodePorts.toIntArray());
        } catch (final IOException ioe) {
            log.warning("Failed to restart MsoyPolicyServer with new ports", ioe);
        }
    }

    /**
     * On dev deployments, restarts the policy server, removing these ports from another node
     * on this machine.
     */
    public void removePortsFromPolicy (final int[] ports)
    {
        if (!DeploymentConfig.devDeployment || _shutmgr.isShuttingDown()) {
            return;
        }

        if (_otherNodePorts == null) {
            _otherNodePorts = new ArrayIntSet();
        }
        _otherNodePorts.remove(ports);

        if (_policyServer != null) {
            _policyServer.unbindAll();
        }
        try {
            _policyServer = MsoyPolicyServer.init(_otherNodePorts.toIntArray());
        } catch (final IOException ioe) {
            log.warning("Failed to restart MsoyPolicyServer with ports removed", ioe);
        }
    }

    @Override // from MsoyBaseServer
    protected String getIdent ()
    {
        return ServerConfig.nodeName;
    }

    @Override // from MsoyBaseServer
    protected ConfigRegistry createConfigRegistry ()
        throws Exception
    {
        return _peerConfReg;
    }

    @Override // from MsoyBaseServer
    protected void configClientFactory ()
    {
        // configure our primary client factory
        _clmgr.setClientFactory(new ClientFactory() {
            public Class<? extends PresentsSession> getClientClass (final AuthRequest areq) {
                return MsoySession.class;
            }
            public Class<? extends ClientResolver> getClientResolverClass (final Name username) {
                return MsoyClientResolver.class;
            }
        });

        // Add in the authenticator and client factory which will allow bureau windows (for avrgs)
        // to be distinguished and connected
        _conmgr.addChainedAuthenticator(new WindowAuthenticator(ServerConfig.windowSharedSecret));
        _clmgr.setClientFactory(new WindowClientFactory(_clmgr.getClientFactory()));
    }

    @Override // from PresentsServer
    protected int[] getListenPorts ()
    {
        return ServerConfig.serverPorts;
    }

    /**
     * Check the filesystem and return the newest timestamp for any of our code jars. This method
     * should remain safe to run on any thread.
     */
    protected long codeModifiedTime ()
    {
        // just the one...
        return new File(ServerConfig.serverRoot, "dist/msoy-code.jar").lastModified();
    }

    /**
     * Check to see if the server should be restarted.
     */
    protected void checkAutoRestart ()
    {
        // look up the last-modified time
        final long lastModified = codeModifiedTime();
        if (lastModified <= _codeModified || _adminMan.statObj.serverRebootTime != 0L) {
            return;
        }

        // if someone is online, give 'em two minutes, otherwise reboot immediately
        boolean playersOnline = false;
        for (final Iterator<ClientObject> iter = _clmgr.enumerateClientObjects(); iter.hasNext(); ) {
            if (iter.next() instanceof MemberObject) {
                playersOnline = true;
                break;
            }
        }
        _adminMan.scheduleReboot(playersOnline ? 2 : 0, "codeUpdateAutoRestart");
    }

    /**
     * Creates a connection to the AMQP server based on the configuration settings.
     */
    protected static MessageConnection createAMQPConnection ()
    {
        final DelayedMessageConnection delayedConn = new DelayedMessageConnection();
        final AMQPMessageConfig config = ServerConfig.geAMQPMessageConfig();
        if (config == null) {
            log.info("No AMQP messaging server configured.");
        } else {
            delayedConn.init(new AMQPMessageConnection(config));
        }
        return delayedConn;
    }

    /** Used to auto-restart the development server when its code is updated. */
    protected long _codeModified;

    /** A policy server used on dev deployments. */
    protected IoAcceptor _policyServer;

    /** On dev deployments, we keep track of the ports on other nodes (hosted on the same machine
     * that need to be accepted by the policy server. */
    protected ArrayIntSet _otherNodePorts;

    /** Our runtime jabber manager. */
    @Inject protected JabberManager _jabberMan;

    /** Manages interactions with our peer servers. */
    @Inject protected MsoyPeerManager _peerMan;

    /** Manages our external game servers. */
    @Inject protected WorldGameRegistry _gameReg;

    /** Manages our parties. */
    @Inject protected PartyRegistry _partyReg;

    /** Our runtime swiftly editor manager. */
    @Inject protected SwiftlyManager _swiftlyMan;

    /** Our runtime admin manager. */
    @Inject protected MsoyAdminManager _adminMan;

    /** Handles HTTP servlet requests. */
    @Inject protected MsoyHttpServer _httpServer;

    /** Our runtime member manager. */
    @Inject protected MemberManager _memberMan;

    /** Handles management of member's friends lists. */
    @Inject protected FriendManager _friendMan;

    /** Handles management of chat channels. */
    @Inject protected MsoyChatChannelManager _channelMan;

    /** Handles item-related services. */
    @Inject protected ItemManager _itemMan;

    /** Handles our cuddly little pets. */
    @Inject protected PetManager _petMan;

    /** The member movement observation manager. */
    @Inject protected WorldWatcherManager _watcherMan;

    /** The Whirled Tour manager. */
    @Inject protected TourManager _tourMan;

    /** Connection to the AMQP messaging server. */
    @Inject protected MessageConnection _messageConn;

    /** Provides money services. */
    @Inject protected MoneyLogic _moneyLogic;

    /** Provides our peer-aware runtime configuration. */
    @Inject protected PeeredDatabaseConfigRegistry _peerConfReg;

    /** Check for modified code every 30 seconds. */
    protected static final long AUTO_RESTART_CHECK_INTERVAL = 30 * 1000L;
}
