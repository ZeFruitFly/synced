//
// $Id$

package client.account;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.msoy.web.gwt.Pages;

import client.ui.MsoyUI;
import client.util.Link;

/**
 * A plain page wrapper for the logon interface.
 */
public class LogonPagePanel extends FlowPanel
{
    public LogonPagePanel ()
    {
        setStyleName("logonPagePanel");

        // add the interface for logging in with Whirled credentials
        add(MsoyUI.createLabel(_msgs.lpLogonHeader(), "Header"));
        add(new FullLogonPanel());

        // add a link to accout creation
        add(MsoyUI.createLabel(_msgs.lpCreateHeader(), "Header"));
        add(tagButton(MsoyUI.createButton(MsoyUI.MEDIUM_THIN, _msgs.lpCreate(),
                                          Link.createListener(Pages.ACCOUNT, "create"))));
    }

    protected Widget tagButton (Widget widget)
    {
        widget.addStyleName("Button");
        return widget;
    }

    protected static final AccountMessages _msgs = GWT.create(AccountMessages.class);
}
