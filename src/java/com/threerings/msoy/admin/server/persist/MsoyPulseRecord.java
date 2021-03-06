//
// $Id$

package com.threerings.msoy.admin.server.persist;

import java.sql.Timestamp;

import com.samskivert.depot.Key;
import com.samskivert.depot.expression.ColumnExp;

import com.threerings.pulse.server.persist.PulseRecord;

/**
 * Contains information on a running Whirled server.
 */
public class MsoyPulseRecord extends PulseRecord
{
    // AUTO-GENERATED: FIELDS START
    public static final Class<MsoyPulseRecord> _R = MsoyPulseRecord.class;
    public static final ColumnExp<Integer> MEMBERS = colexp(_R, "members");
    public static final ColumnExp<Integer> GAMERS = colexp(_R, "gamers");
    public static final ColumnExp<Integer> PARTIERS = colexp(_R, "partiers");
    public static final ColumnExp<Integer> ROOMS = colexp(_R, "rooms");
    public static final ColumnExp<Integer> GAMES = colexp(_R, "games");
    public static final ColumnExp<Integer> CHANNELS = colexp(_R, "channels");
    public static final ColumnExp<Integer> PARTIES = colexp(_R, "parties");
    public static final ColumnExp<Timestamp> RECORDED = colexp(_R, "recorded");
    public static final ColumnExp<String> SERVER = colexp(_R, "server");
    // AUTO-GENERATED: FIELDS END

    /** Increment this when making any schema changes. */
    public static final int SCHEMA_VERSION = 1;

    /** The number of members online on this server. */
    public int members;

    /** The number of gamers online on this server. */
    public int gamers;

    /** The number of partiers online on this server. */
    public int partiers;

    /** The number of rooms resolved on this server. */
    public int rooms;

    /** The number of games resolved on this server. */
    public int games;

    /** The number of chat channels resolved on this server. */
    public int channels;

    /** The number of parties resolved on this server. */
    public int parties;

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link MsoyPulseRecord}
     * with the supplied key values.
     */
    public static Key<MsoyPulseRecord> getKey (Timestamp recorded, String server)
    {
        return newKey(_R, recorded, server);
    }

    /** Register the key fields in an order matching the getKey() factory. */
    static { registerKeyFields(RECORDED, SERVER); }
    // AUTO-GENERATED: METHODS END
}
