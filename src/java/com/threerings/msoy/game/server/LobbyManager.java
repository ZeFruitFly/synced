//
// $Id$

package com.threerings.msoy.game.server;

import com.google.inject.Inject;
import com.google.inject.Injector;

import com.whirled.game.data.GameDefinition;
import com.whirled.game.data.TableMatchConfig;

import com.samskivert.util.Interval;
import com.samskivert.util.Invoker;

import com.threerings.util.Name;

import com.threerings.presents.annotation.EventThread;
import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.dobj.RootDObjectManager;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;

import com.threerings.crowd.server.PlaceRegistry;

import com.threerings.parlor.data.Parameter;
import com.threerings.parlor.data.Table;
import com.threerings.parlor.game.server.GameManager;
import com.threerings.parlor.server.ParlorSender;

import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.game.data.LobbyObject;
import com.threerings.msoy.game.data.MsoyMatchConfig;
import com.threerings.msoy.game.data.MsoyTableConfig;
import com.threerings.msoy.game.data.ParlorGameConfig;
import com.threerings.msoy.game.data.PlayerObject;
import com.threerings.msoy.game.xml.MsoyGameParser;
import com.threerings.msoy.server.MsoyEventLogger;
import com.threerings.msoy.server.ServerConfig;

import static com.threerings.msoy.Log.log;

/**
 * Manages a lobby room.
 */
@EventThread
public class LobbyManager
{
    /** Allows interested parties to know when a lobby shuts down. */
    public interface ShutdownObserver
    {
        void lobbyDidShutdown (int gameId);
    }

    /**
     * Initializes this lobby manager and prepares it for operation.
     */
    public void init (InvocationManager invmgr, ShutdownObserver shutObs)
    {
        _shutObs = shutObs;

        _lobj = _omgr.registerObject(new LobbyObject());
        _lobj.subscriberListener = new LobbyObject.SubscriberListener() {
            public void subscriberCountChanged (LobbyObject lobj) {
                recheckShutdownInterval();
            }
        };

        _tableMgr.init(this);

        // since we start empty, we need to immediately assume shutdown
        recheckShutdownInterval();
    }

    /**
     * Return the ID of the game for which we're the lobby.
     */
    public int getGameId ()
    {
        return _content.gameId;
    }

    /**
     * Return the object ID of the LobbyObject
     */
    public LobbyObject getLobbyObject ()
    {
        return _lobj;
    }

    /**
     * Returns the metadata for the lobby managed by this game. This is only available after the
     * first call to {@link #setGameContent} which the GameGameRegistry does shortly after the
     * lobby is resolved.
     */
    public GameContent getGameContent ()
    {
        return _content;
    }

    /**
     * Called when a lobby is first created and possibly again later to refresh its game metadata.
     */
    public void setGameContent (GameContent content)
    {
        // parse the game definition. start with a null in case parse fails
        GameDefinition gameDef = null;
        try {
            gameDef = new MsoyGameParser().parseGame(content.code);
        } catch (Exception e) {
            log.warning("Error parsing game definition", "id", content.gameId, e);
        }

        // accept the new game
        _content = content;

        // update the lobby object
        _lobj.startTransaction();
        try {
            _lobj.setGame(_content.toGameSummary());
            _lobj.setGameDef(gameDef);
            _lobj.setGroupId(ServerConfig.getGameGroupId(_content.game.groupId));
            _lobj.setSplashMedia(_content.code.splashMedia);
        } finally {
            _lobj.commitTransaction();
        }
    }

    /**
     * Initializes the supplied config. Exposed to allow MsoyTableManager to call it.
     */
    public void initConfig (ParlorGameConfig config)
    {
        config.init(_content.gameId, _lobj.game, _lobj.gameDef, _lobj.groupId, _lobj.splashMedia);
    }

    /**
     * Returns true if the specified player is waiting at a (pending) table in this lobby, false
     * otherwise.
     */
    public boolean playerAtTable (int playerId)
    {
        for (Table table : _lobj.tables) {
            if (table.inPlay()) {
                continue;
            }
            for (Name name : table.getPlayers()) {
                if (((MemberName)name).getId() == playerId) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to create and place the player into a single player game.
     *
     * @return if a game was created for the player, false if we were unable to create a game.
     */
    public boolean playNowSingle (PlayerObject player)
    {
        MsoyMatchConfig match = (MsoyMatchConfig)_lobj.gameDef.match;
        if (match == null || match.isPartyGame || match.minSeats != 1) {
            return false;
        }

        // start up a single player game
        ParlorGameConfig config = new ParlorGameConfig();
        initConfig(config);
        if (_lobj.gameDef.params != null) {
            for (Parameter param : _lobj.gameDef.params) {
                config.params.put(param.ident, param.getDefaultValue());
            }
        }
        MsoyTableConfig tconfig = new MsoyTableConfig();
        tconfig.title = player.memberName.toString();
        tconfig.desiredPlayerCount = tconfig.minimumPlayerCount = 1;
        Table table = null;
        try {
            table = _tableMgr.createTable(player, tconfig, config);
        } catch (InvocationException ie) {
            log.warning("Play now table will be unstable... [who=" + player.who() +
                        ", error=" + ie.getMessage() + "].");
        }

        // if this is a party or seated continuous game, we need to tell the player to head
        // into the game because the game manager ain't oging to do it for us
        if (_lobj.gameDef.match.getMatchType() != ParlorGameConfig.SEATED_GAME) {
            ParlorSender.gameIsReady(player, table.gameOid);
        }
        return true; //we'll force the game to work!
    }

    /**
     * Attempts to send the specified player directly into a game.
     */
    public boolean playNowMulti (PlayerObject player)
    {
        // if this is a party game (or seated continuous); send them into an existing game
        if (_lobj.gameDef.match.getMatchType() != ParlorGameConfig.SEATED_GAME) {
            // TODO: order the tables most occupants to least?
            for (Table table : _lobj.tables) {
                if (table.gameOid > 0 && shouldJoinGame(player, table)) {
                    ParlorSender.gameIsReady(player, table.gameOid);
                    return true;
                }
            }
        }

        // TODO: if we can add them to a table and that table will become immediately ready to
        // play, we could do that here and save the caller the trouble of subscribing to the lobby

        // otherwise we'll just send the player to the lobby and the client will look for a table
        // to join since it would have to download all that business anyway
        return false;
    }

    protected boolean shouldJoinGame (PlayerObject player, Table table)
    {
        // if this table has been marked as private, we don't want to butt in
        if (table.tconfig.privateTable) {
            return false;
        }

        // if the game is over its maximum capacity, don't join it
        int maxSeats = ((TableMatchConfig)_lobj.gameDef.match).maxSeats;
        if (table.watchers.length >= maxSeats) {
            return false;
        }
        return true;
    }

    public void shutdown ()
    {
        _lobj.subscriberListener = null;
        _shutObs.lobbyDidShutdown(getGameId());
        _tableMgr.shutdown();

        // make sure we don't have any shutdowner in the queue
        cancelShutdowner();

        // finally, destroy the Lobby DObject
        _omgr.destroyObject(_lobj.getOid());
    }

    /**
     * Called by {@link MsoyTableManager} when it wants to create a game.
     */
    protected GameManager createGameManager (ParlorGameConfig config)
        throws InstantiationException, InvocationException
    {
        return (GameManager)_plreg.createPlace(
            config, _ggreg.createGameDelegates(config, _content));
    }

    /**
     * Check the current status of the lobby and maybe schedule or maybe cancel the shutdown
     * interval, as appropriate.
     */
    protected void recheckShutdownInterval ()
    {
        if (_lobj.getSubscriberCount() == 0 && _tableMgr.getTableCount() == 0) {
            // queue up a shutdown interval, unless we've already got one.
            if (_shutdownInterval == null) {
                _shutdownInterval = new Interval(_omgr) {
                    @Override public void expired () {
                        log.debug("Unloading idle game lobby [gameId=" + getGameId() + "]");
                        shutdown();
                    }
                };
                _shutdownInterval.schedule(IDLE_UNLOAD_PERIOD);
            }

        } else {
            cancelShutdowner();
        }
    }

    /**
     * Unconditionally cancel the shutdown interval.
     */
    protected void cancelShutdowner ()
    {
        if (_shutdownInterval != null) {
            _shutdownInterval.cancel();
            _shutdownInterval = null;
        }
    }

    /** The Lobby object we're using. */
    protected LobbyObject _lobj;

    /** This fellow wants to hear when we shutdown. */
    protected ShutdownObserver _shutObs;

    /** The metadata for the game for which we're lobbying. */
    protected GameContent _content;

    /** Manages the actual tables. This is not a singleton. */
    @Inject protected MsoyTableManager _tableMgr;

    /** An interval to let us delay lobby shutdown for awhile. */
    protected Interval _shutdownInterval;

    // our dependencies
    @Inject protected @MainInvoker Invoker _invoker;
    @Inject protected GameGameRegistry _ggreg;
    @Inject protected Injector _injector;
    @Inject protected MsoyEventLogger _eventLog;
    @Inject protected PlaceRegistry _plreg;
    @Inject protected PlayerNodeActions _playerActions;
    @Inject protected RootDObjectManager _omgr;

    /** idle time before shutting down the manager. */
    protected static final long IDLE_UNLOAD_PERIOD = 60 * 1000L; // in ms
}
