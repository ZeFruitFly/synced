//
// $Id$

package com.threerings.msoy.server.persist;

import com.samskivert.depot.Key;
import com.samskivert.depot.PersistentRecord;
import com.samskivert.depot.annotation.*;
import com.samskivert.depot.expression.ColumnExp;

public class FriendshipRecord extends PersistentRecord
{
    // AUTO-GENERATED: FIELDS START
    public static final Class<FriendshipRecord> _R = FriendshipRecord.class;
    public static final ColumnExp<Integer> MEMBER_ID = colexp(_R, "memberId");
    public static final ColumnExp<Integer> FRIEND_ID = colexp(_R, "friendId");
    public static final ColumnExp<Boolean> VALID = colexp(_R, "valid");
    // AUTO-GENERATED: FIELDS END

    /** The version of this record. */
    public static final int SCHEMA_VERSION = 2;

    /** The memberId for whom this record applies. */
    @Id
    public int memberId;

    /** The memberId of the other member. */
    @Id @Index(name="ixFriend")
    public int friendId;

    /** If true, the person is our friend, otherwise we've just sent them an invite. */
    public boolean valid;

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link FriendshipRecord}
     * with the supplied key values.
     */
    public static Key<FriendshipRecord> getKey (int memberId, int friendId)
    {
        return newKey(_R, memberId, friendId);
    }

    /** Register the key fields in an order matching the getKey() factory. */
    static { registerKeyFields(MEMBER_ID, FRIEND_ID); }
    // AUTO-GENERATED: METHODS END

    /** Blank */
    public FriendshipRecord ()
    {
    }

    /** Construct. */
    public FriendshipRecord (int memberId, int friendId, boolean valid)
    {
        this.memberId = memberId;
        this.friendId = friendId;
        this.valid = valid;
    }
}
