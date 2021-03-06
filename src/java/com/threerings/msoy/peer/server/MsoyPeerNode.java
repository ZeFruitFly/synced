//
// $Id$

package com.threerings.msoy.peer.server;

import com.google.inject.Inject;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.Communicator;
import com.threerings.presents.dobj.DSet;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.peer.server.PeerNode;
import com.threerings.presents.peer.server.persist.NodeRecord;
import com.threerings.presents.server.net.ServerCommunicator;

import com.threerings.msoy.data.all.DeploymentConfig;
import com.threerings.msoy.party.data.MemberParty;
import com.threerings.msoy.party.data.PartyInfo;
import com.threerings.msoy.party.server.PartyRegistry;
import com.threerings.msoy.peer.data.MemberScene;
import com.threerings.msoy.peer.data.MsoyNodeObject;
import com.threerings.msoy.server.ServerConfig;

/**
 * Handles Whirled-specific peer bits.
 */
public class MsoyPeerNode extends PeerNode
{
    @Override // from PeerNode
    public void init (NodeRecord record)
    {
        super.init(record);
        _httpPort = ServerConfig.getHttpPort(record.nodeName);
    }

    /**
     * Return the HTTP port this Whirled node is listening on.
     */
    public int getHttpPort ()
    {
        return _httpPort;
    }

    @Override // from PeerNode
    protected Communicator createCommunicator (Client client)
    {
        if (DeploymentConfig.devDeployment) {
            return new ServerCommunicator(client, _conmgr, _omgr);
        } else {
            return super.createCommunicator(client);
        }
    }

    @Override // from PeerNode
    protected NodeObjectListener createListener ()
    {
        return new MsoyNodeObjectListener();
    }

    /**
     * Extends the base NodeListener with Msoy-specific bits.
     */
    protected class MsoyNodeObjectListener extends NodeObjectListener
    {
        @Override public void entryAdded (EntryAddedEvent<DSet.Entry> event) {
            super.entryAdded(event);
            String name = event.getName();
            if (MsoyNodeObject.MEMBER_SCENES.equals(name)) {
                MemberScene datum = (MemberScene)event.getEntry();
                ((MsoyPeerManager)_peermgr).memberEnteredScene(
                    getNodeName(), datum.memberId, datum.sceneId);

            } else if (MsoyNodeObject.MEMBER_PARTIES.equals(name)) {
                MemberParty memParty = (MemberParty) event.getEntry();
                _partyReg.updateUserParty(
                    memParty.memberId, memParty.partyId, (MsoyNodeObject)nodeobj);
            }
        }

        @Override public void entryUpdated (EntryUpdatedEvent<DSet.Entry> event) {
            super.entryUpdated(event);
            String name = event.getName();
            if (MsoyNodeObject.MEMBER_SCENES.equals(name)) {
                MemberScene datum = (MemberScene)event.getEntry();
                ((MsoyPeerManager)_peermgr).memberEnteredScene(
                    getNodeName(), datum.memberId, datum.sceneId);

            } else if (MsoyNodeObject.MEMBER_PARTIES.equals(name)) {
                MemberParty memParty = (MemberParty) event.getEntry();
                _partyReg.updateUserParty(
                    memParty.memberId, memParty.partyId, (MsoyNodeObject)nodeobj);

            } else if (MsoyNodeObject.PARTY_INFOS.equals(name)) {
                _partyReg.partyInfoChanged(
                    (PartyInfo)event.getOldEntry(), (PartyInfo)event.getEntry());
            }
        }

        @Override public void entryRemoved (EntryRemovedEvent<DSet.Entry> event) {
            super.entryRemoved(event);
            String name = event.getName();
            if (MsoyNodeObject.MEMBER_SCENES.equals(name)) {
                int memberId = (Integer)event.getKey();
                ((MsoyPeerManager)_peermgr).memberEnteredScene(getNodeName(), memberId, 0);

            } else if (MsoyNodeObject.MEMBER_PARTIES.equals(name)) {
                _partyReg.updateUserParty((Integer)event.getKey(), 0, (MsoyNodeObject)nodeobj);
            }
        }
    } // END: class MsoyNodeObjectListener

    /** The HTTP port this Whirled node is listening on.  */
    protected int _httpPort;

    // our dependencies
    @Inject protected PartyRegistry _partyReg;
}
