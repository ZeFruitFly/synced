//
// $Id$

package client.stuff;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.gwt.user.client.rpc.AsyncCallback;

import client.util.events.FlashEvents;
import client.util.events.ItemUsageEvent;
import client.util.events.ItemUsageListener;

import com.threerings.gwt.util.Predicate;
import com.threerings.gwt.util.SimpleDataModel;

import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.SubItem;

/**
 * Maintains information on our member's inventory.
 */
public class InventoryModels
    implements ItemUsageListener
{
    public void startup ()
    {
        FlashEvents.addListener(this);
    }

    public void shutdown ()
    {
        FlashEvents.removeListener(this);
    }

    public void loadModel (byte type, int suiteId, final AsyncCallback<SimpleDataModel> cb)
    {
        final Key key = new Key(type, suiteId);
        SimpleDataModel model = (SimpleDataModel)_models.get(key);
        if (model != null) {
            cb.onSuccess(model);
            return;
        }

        CStuff.membersvc.loadInventory(CStuff.ident, type, suiteId, new AsyncCallback() {
            public void onSuccess (Object result) {
                SimpleDataModel model = new SimpleDataModel((List)result);
                _models.put(key, model);
                cb.onSuccess(model);
            }
            public void onFailure (Throwable caught) {
                cb.onFailure(caught);
            }
        });
    }

    public SimpleDataModel getModel (byte type, int suiteId)
    {
        return (SimpleDataModel)_models.get(new Key(type, suiteId));
    }

    public Item findItem (byte type, final int itemId)
    {
        Predicate itemP = new Predicate() {
            public boolean isMatch (Object object) {
                return ((Item)object).itemId == itemId;
            }
        };
        for (Iterator iter = _models.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry entry = (Map.Entry)iter.next();
            Key key = (Key)entry.getKey();
            if (key.type == type) {
                SimpleDataModel model = (SimpleDataModel)entry.getValue();
                Item item = (Item)model.findItem(itemP);
                if (item != null) {
                    return item;
                }
            }
        }
        return null;
    }

    public void updateItem (Item item)
    {
        for (Iterator iter = _models.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry entry = (Map.Entry)iter.next();
            Key key = (Key)entry.getKey();
            if (key.matches(item)) {
                ((SimpleDataModel)entry.getValue()).updateItem(item);
            }
        }
    }

    // from interface ItemUsageListener
    public void itemUsageChanged (ItemUsageEvent event)
    {
        Item item = findItem(event.getItemType(), event.getItemId());
        if (item != null) {
            item.used = event.getUsage();
            item.location = event.getLocation();
            // TODO: update lastTouched time locally?

            // TODO: right now, the ItemActivators listen to the usageChangedEvent just
            // like we do, but perhaps this class should dispatch a more generic itemChanged
            // event, and have the ItemActivators respond to that.
        }
    }

    protected static class Key {
        public final byte type;
        public final int suiteId;

        public Key (byte type, int suiteId) {
            this.type = type;
            this.suiteId = suiteId;
        }

        public boolean matches (Item item) {
            return item.getType() == type &&
                suiteId == (item instanceof SubItem ? ((SubItem)item).suiteId : 0);
        }

        public int hashCode() {
            return type ^ suiteId;
        }

        public boolean equals (Object other) {
            Key okey = (Key)other;
            return type == okey.type && suiteId == okey.suiteId;
        }
    }

    protected Map _models = new HashMap();
}
