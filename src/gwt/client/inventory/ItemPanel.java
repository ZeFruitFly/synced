//
// $Id$

package client.inventory;

import java.util.ArrayList;
import java.util.List;

import client.editem.EditorHost;
import client.editem.ItemEditor;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.PagedGrid;
import com.threerings.gwt.util.SimpleDataModel;

import com.threerings.msoy.item.web.Item;

/**
 * Displays all items of a particular type in a player's inventory.
 */
public class ItemPanel extends VerticalPanel
    implements EditorHost
{
    /** The number of columns of items to display. */
    public static final int COLUMNS = 4;

    /** The number of rows of items to display. */
    public static final int ROWS = 3;

    public ItemPanel (byte type)
    {
        _type = type;

        // this will contain our items
        add(_contents = new PagedGrid(ROWS, COLUMNS) {
            protected Widget createWidget (Object item) {
                return new ItemContainer(ItemPanel.this, (Item)item);
            }
            protected String getEmptyMessage () {
                return CInventory.msgs.panelNoItems(Item.getTypeName(_type));
            }
        });
        _contents.setStyleName("inventoryContents");

        // this will allow us to create new items
        add(_create = new Button(CInventory.msgs.panelCreateNew()));
        _create.addClickListener(new ClickListener() {
            public void onClick (Widget widget) {
                createNewItem();
            }
        });
        add(_status = new Label(""));
    }

    // from EditorHost
    public void setStatus (String status)
    {
        _status.setText(status);
    }

    // from EditorHost
    public void editComplete (Item item)
    {
        _create.setEnabled(true);

        if (item != null) {
            // we really need to re-fetch the item from the database to get things like itemId
            // set. just refresh the entire list for now.
            onLoad();
        }
    }



    // TODO: each ItemPanel is currently loading everything up when the inventory panel loads.  We
    // should only load a category when it's made visible. (In addition to only loading inventory
    // in chunks. We cannot show the user's whole inventory, even in one category.)
    protected void onLoad ()
    {
        CInventory.membersvc.loadInventory(CInventory.creds, _type, new AsyncCallback() {
            public void onSuccess (Object result) {
                _contents.setModel(new SimpleDataModel((List)result));
            }
            public void onFailure (Throwable caught) {
                CInventory.log("loadInventory failed", caught);
                add(new Label(CInventory.serverError(caught)));
            }
        });
    }

    protected void createNewItem ()
    {
        ItemEditor editor = ItemEditor.createItemEditor(_type, this);
        if (editor != null) {
            _create.setEnabled(false);
            Item item = editor.createBlankItem();
            // TEMP: workaround null description problem
            item.description = "";
            editor.setItem(item);
            editor.setPopupPosition(_create.getAbsoluteLeft()+20, _create.getAbsoluteTop()-200);
            editor.show();
        }
    }

    /**
     * Called by an active {@link ItemDetailPopup} to let us know that an item has been deleted
     * from our inventory.
     */
    protected void itemDeleted (Item item)
    {
        _contents.removeItem(item);
        setStatus(CInventory.msgs.msgItemDeleted());
    }

    /**
     * Called by an active {@link ItemDetailPopup} to let us know that an item has been remixed.
     * Since this is a relatively uncommon operation and completely replaces the item with a new
     * one with a new id, we force a server roundtrip refresh.
     */
    protected void itemRemixed (Item oldItem, Item newItem)
    {
        setStatus(CInventory.msgs.msgItemRemixed());
        onLoad();
    }

    protected PagedGrid _contents;
    protected Button _create, _next, _prev;
    protected Label _status;

    protected byte _type;
    protected ArrayList _items;
    protected int _page;
}
