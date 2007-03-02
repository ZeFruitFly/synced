//
// $Id$

package com.threerings.msoy.game.data;

import com.threerings.msoy.game.client.MsoyGameService;
import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.InvocationMarshaller;
import com.threerings.presents.dobj.InvocationResponseEvent;

/**
 * Provides the implementation of the {@link MsoyGameService} interface
 * that marshalls the arguments and delivers the request to the provider
 * on the server. Also provides an implementation of the response listener
 * interfaces that marshall the response arguments and deliver them back
 * to the requesting client.
 */
public class MsoyGameMarshaller extends InvocationMarshaller
    implements MsoyGameService
{
    /** The method id used to dispatch {@link #awardFlow} requests. */
    public static final int AWARD_FLOW = 1;

    // from interface MsoyGameService
    public void awardFlow (Client arg1, int arg2, InvocationService.InvocationListener arg3)
    {
        ListenerMarshaller listener3 = new ListenerMarshaller();
        listener3.listener = arg3;
        sendRequest(arg1, AWARD_FLOW, new Object[] {
            Integer.valueOf(arg2), listener3
        });
    }
}
