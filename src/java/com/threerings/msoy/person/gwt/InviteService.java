//
// $Id$

package com.threerings.msoy.person.gwt;

import java.util.List;

import com.google.gwt.user.client.rpc.RemoteService;

import com.threerings.msoy.web.data.EmailContact;
import com.threerings.msoy.web.data.ServiceException;
import com.threerings.msoy.web.data.WebIdent;

/**
 * Handles invitation-related functionality.
 */
public interface InviteService extends RemoteService
{
    /** The entry point for this service. */
    public static final String ENTRY_POINT = "/invitesvc";

    /**
     * Loads up e-mail addresses from a user's webmail account.
     */
    List<EmailContact> getWebMailAddresses (WebIdent ident, String email, String password)
        throws ServiceException;

    /**
     * Return the invitation details for the given ident.
     */
    MemberInvites getInvitationsStatus (WebIdent ident)
        throws ServiceException;

    /**
     * Send out some of this person's available invites.
     *
     * @param anonymous if true, the invitations will not be from the caller but will be
     * anonymous. This is only allowed for admin callers.
     */
    InvitationResults sendInvites (WebIdent ident, List<EmailContact> addresses,
                                   String fromName, String customMessage, boolean anonymous)
        throws ServiceException;

    /**
     * Removes a pending invitation.
     */
    void removeInvitation (WebIdent ident, String inviteId)
        throws ServiceException;
}
