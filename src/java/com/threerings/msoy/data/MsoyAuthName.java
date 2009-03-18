//
// $Id$

package com.threerings.msoy.data;

/**
 * An auth name used to identify clients logged into a world session. This could be called
 * WorldAuthName, but we use MsoyFoo for all of the "primary" connection classes:
 * MsoyAuthenticator, MsoyClientResolver, etc.
 */
public class MsoyAuthName extends AuthName
{
    /**
     * Creates an instance that can be used as a DSet key.
     */
    public static MsoyAuthName makeKey (int memberId)
    {
        return new MsoyAuthName("", memberId);
    }

    /** Creates a name for the member with the supplied account name and member id. */
    public MsoyAuthName (String accountName, int memberId)
    {
        super(accountName, memberId);
    }

    /** Used for unserializing. */
    public MsoyAuthName ()
    {
    }
}