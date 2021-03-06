//
// $Id$

package com.threerings.msoy.server;

import java.security.Security;

import com.google.inject.Inject;
import com.google.inject.Injector;

import net.sf.ehcache.CacheManager;

import com.samskivert.util.Invoker;

import com.samskivert.jdbc.ConnectionProvider;

import com.samskivert.depot.EHCacheAdapter.EHCacheConfig;
import com.samskivert.depot.EHCacheAdapter;
import com.samskivert.depot.PersistenceContext;

import com.threerings.presents.dobj.AccessController;
import com.threerings.presents.server.PresentsInvoker;
import com.threerings.presents.server.ReportManager;

import com.threerings.whirled.server.WhirledServer;

import com.threerings.pulse.server.persist.PulseDatabase;

import com.threerings.msoy.admin.server.RuntimeConfig;
import com.threerings.msoy.data.StatType;
import com.threerings.msoy.server.persist.BatchInvoker;

import static com.threerings.msoy.Log.log;

/**
 * Provides the set of services that are shared between the Game and World servers.
 */
public abstract class MsoyBaseServer extends WhirledServer
{
    /** Configures dependencies needed by the Msoy servers. */
    public static class MsoyBaseModule extends WhirledModule
    {
        @Override protected void configure () {
            super.configure();
            // depot dependencies (we will initialize this persistence context later when the
            // server is ready to do database operations; not initializing it now ensures that no
            // one sneaks any database manipulations into the dependency resolution phase)
            PersistenceContext pctx = new PersistenceContext();
            bind(PersistenceContext.class).toInstance(pctx);
            bind(PersistenceContext.class).annotatedWith(PulseDatabase.class).toInstance(pctx);
            // bind the batch invoker
            bind(Invoker.class).annotatedWith(BatchInvoker.class).to(MsoyBatchInvoker.class);
        }
    }

    @Override // from WhirledServer
    public void init (final Injector injector)
        throws Exception
    {
        // before doing anything else, let's ensure that we don't cache DNS queries forever -- this
        // breaks Amazon S3, specifically.
        Security.setProperty("networkaddress.cache.ttl" , "30");

        // hack up our String streamer
        System.setProperty("com.threerings.io.unmodifiedUTFStreaming", "true");

        // initialize event logger
        _eventLog.init(getIdent());

        // initialize our persistence context and repositories; run schema and data migrations
        ConnectionProvider conprov = ServerConfig.createConnectionProvider();
        _perCtx.init("msoy", conprov, new EHCacheAdapter(_cacheMgr, "msoy",
                new EHCacheConfig("depotRecord", 400000, 300, 600),
                new EHCacheConfig("depotShortKeyset", 35000, 10, 10),
                new EHCacheConfig("depotLongKeyset", 25000, 300, 300),
                new EHCacheConfig("depotResult", 5000, 300, 300)));

        _perCtx.initializeRepositories(true);

        // set up our runtime config now so it's ready before any other initialization happens
        _runtime.init(_omgr);

        // increase highest bucket for invoker profiling and decrease resolution for batch invoker
        _invoker.setProfilingParameters(50, 40);
        _authInvoker.setProfilingParameters(50, 40);
        _batchInvoker.setProfilingParameters(500, 30);

        super.init(injector);

        // when we shutdown, make sure the batch invoker clears its queue first
        ((PresentsInvoker)_invoker).addInterdependentInvoker(_batchInvoker);

        // start the batch invoker thread
        _batchInvoker.start();

        // initialize our bureau manager, then set its additional factories
        _bureauMgr.init(getListenPorts()[0]);
    }

    @Override // from PresentsServer
    protected AccessController createDefaultObjectAccessController ()
    {
        return MsoyObjectAccess.DEFAULT;
    }

    @Override // from PresentsServer
    protected void invokerDidShutdown ()
    {
        super.invokerDidShutdown();

        _batchInvoker.shutdown();

        // shutdown our persistence context (JDBC connections) and the cache manager
        _perCtx.shutdown();

        try {
            _cacheMgr.shutdown();
        } catch (Exception e) {
            log.warning("EHCache manager did not shut down gracefully", e);
        }

        // and shutdown our event logger now that everything else is done shutting down
        _eventLog.shutdown();
    }

    /**
     * Returns an identifier used to distinguish this server from others on this machine when
     * generating log files.
     */
    protected abstract String getIdent ();

    /** Used for caching things. */
    protected CacheManager _cacheMgr = CacheManager.getInstance();

    /** Manages our bureau launchers. */
    @Inject protected BureauManager _bureauMgr;

    /** The batch invoker thread. */
    @Inject protected MsoyBatchInvoker _batchInvoker;

    /** Sends event information to an external log database. */
    @Inject protected MsoyEventLogger _eventLog;

    /** Provides database access to all of our repositories. */
    @Inject protected PersistenceContext _perCtx;

    /** Handles state of the server reporting (used by the /status servlet). */
    @Inject protected ReportManager _reportMan;

    /** Maintains runtime modifiable configuration information. */
    @Inject protected RuntimeConfig _runtime;

    /** This is needed to ensure that the StatType enum's static initializer runs before anything
     * else in the server that might rely on stats runs. */
    protected static final StatType STAT_TRIGGER = StatType.UNUSED;
}
