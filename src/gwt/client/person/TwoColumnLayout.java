//
// $Id$

package client.person;

import java.util.ArrayList;

import com.google.gwt.user.client.ui.VerticalPanel;

import com.threerings.msoy.web.client.WebContext;
import com.threerings.msoy.web.data.BlurbData;
import com.threerings.msoy.web.data.PersonLayout;

/**
 * Lays out a person page in two columns.
 */
public class TwoColumnLayout extends VerticalPanel
{
    public TwoColumnLayout (
        WebContext ctx, int memberId, PersonLayout layout, ArrayList blurbs)
    {
        // TODO: actually do two columns...
        for (int ii = 0; ii < layout.blurbs.size(); ii++) {
            BlurbData bdata = (BlurbData)layout.blurbs.get(ii);
            Blurb blurb = Blurb.createBlurb(bdata.type);
            blurb.init(ctx, memberId, bdata.blurbId, blurbs.get(ii));
            add(blurb);
        }
    }
}
