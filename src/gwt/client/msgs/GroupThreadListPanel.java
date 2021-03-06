//
// $Id$

package client.msgs;

import java.util.List;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;

import com.threerings.gwt.util.DataModel;

import com.threerings.msoy.fora.gwt.ForumThread;
import com.threerings.msoy.web.gwt.Pages;

import client.ui.MsoyUI;
import client.util.Link;

/**
 * Overrides and adds functionality to the threads list for displaying group threads.
 */
public class GroupThreadListPanel extends ThreadListPanel
{
    public GroupThreadListPanel (ForumPanel parent, ForumModels fmodels, int groupId)
    {
        super(parent, fmodels, new Object[] {"f", groupId});
        _groupId = groupId;
    }

    @Override // from ThreadListPanel
    protected DataModel<ForumThread> doSearch (String query)
    {
        return _fmodels.searchGroupThreads(_groupId, query);
    }

    @Override // from ThreadListPanel
    protected DataModel<ForumThread> getThreadListModel ()
    {
        return _fmodels.getGroupThreads(_groupId);
    }

    @Override // from PagedGrid
    protected void addCustomControls (FlexTable controls)
    {
        super.addCustomControls(controls);

        // add a button for starting a new thread that will optionally be enabled later
        _startThread = new Button(_mmsgs.tlpStartNewThread(), new ClickHandler() {
            public void onClick (ClickEvent event) {
                if (MsoyUI.requireValidated()) {
                    Link.go(Pages.GROUPS, "p", _groupId);
                }
            }
        });
        _startThread.setEnabled(false);
        controls.setWidget(0, 0, _startThread);
    }

    @Override // from PagedGrid
    protected void displayResults (int start, int count, List<ForumThread> list)
    {
        super.displayResults(start, count, list);
        _startThread.setEnabled(((ForumModels.GroupThreads)_model).canStartThread());
    }

    /** Contains the id of the group whose threads we are displaying or zero. */
    protected int _groupId;

    /** A button for starting a new thread. */
    protected Button _startThread;
}
