//
// $Id$

package client.shop;

import client.shell.Pages;

import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.threerings.gwt.ui.WidgetUtil;

/**
 * A favorites panel with a sidebar menu for filtering the type of items to display.
 *
 * @author mjensen
 */
public class FavesPanel extends HorizontalPanel
{
    public FavesPanel ()
    {
        setStyleName("shopPanel");
        setVerticalAlignment(HasAlignment.ALIGN_TOP);

        _sideBar = new FavoritesSideBar();
        _favorites = new FavoritesPanel(Pages.SHOP, 3, 4);

        add(_sideBar);
        add(WidgetUtil.makeShim(10, 10));
        add(_favorites);
        add(WidgetUtil.makeShim(10, 10));
    }

    public void update (int memberId, byte selectedItemType, int gridPage, String[] prefixArgs)
    {
        _sideBar.update(selectedItemType, prefixArgs);
        _favorites.update(memberId, selectedItemType, gridPage, prefixArgs);
    }

    @Override
    public String getTitle()
    {
        return _favorites.getTitle();
    }

    protected FavoritesSideBar _sideBar;

    protected FavoritesPanel _favorites;
}
