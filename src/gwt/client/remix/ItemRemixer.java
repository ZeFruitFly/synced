//
// $Id$

package client.remix;

import com.google.gwt.core.client.GWT;

import com.google.gwt.http.client.URL;

import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.item.data.all.Decor;
import com.threerings.msoy.item.data.all.MediaDesc;

import com.threerings.msoy.web.client.DeploymentConfig;

import client.shell.CShell;

import client.editem.EditorHost;

import client.util.ImageChooserPopup;
import client.util.FlashClients;
import client.util.MsoyCallback;
import client.util.MsoyUI;

public class ItemRemixer extends FlexTable
{
    public ItemRemixer (EditorHost host)
    {
        _singleton = this;
        _parent = host;

        setStyleName("itemRemixer");
        setCellPadding(0);
        setCellSpacing(5);

        configureBridges();
    }

    public void setItem (byte type, int itemId)
    {
        CShell.itemsvc.loadItem(CShell.ident, new ItemIdent(type, itemId), 
            new MsoyCallback<Item>() {
                public void onSuccess (Item result) {
                    setItem(result);
                }
            });
    }

    public void setItem (Item item)
    {
        _item = item;
        HorizontalPanel hpan = new HorizontalPanel();
        hpan.add(createRemixControls(item));
        setWidget(0, 0, hpan);
    }

    protected Widget createRemixControls (Item item)
    {
        MediaDesc main = item.getPrimaryMedia();
        String serverURL = GWT.isScript() ? GWT.getHostPageBaseURL()
                                          : "http://localhost:8080/";

        String flashVars = "media=" + URL.encodeComponent(main.getMediaPath()) +
            "&name=" + URL.encodeComponent(item.name) +
            "&type=" + URL.encodeComponent(Item.getTypeName(item.getType())) +
            "&server=" + URL.encodeComponent(serverURL) +
            "&mediaId=" + URL.encodeComponent(Item.MAIN_MEDIA) +
            "&auth=" + URL.encodeComponent(CShell.ident.token);

        if (item instanceof Decor) {
            flashVars += "&" + FlashClients.createDecorViewerParams((Decor) item);
        }

        return WidgetUtil.createFlashContainer("remixControls",
            "/clients/" + DeploymentConfig.version + "/remixer-client.swf",
            680, 550, flashVars);
    }

    /**
     * Show the ImageFileChooser and let the user select a photo from their inventory.
     *
     * TODO: the damn ImageChooserPopup needs a proper cancel button and a response when it
     * cancels so that we can try to do the right thing in PopupFilePreview.
     */
    protected void pickPhoto ()
    {
        ImageChooserPopup.displayImageChooser(false, new MsoyCallback<MediaDesc>() {
            public void onSuccess (MediaDesc photo) {
                setPhotoUrl(photo.getMediaPath());
            }
        });
    }

    protected void cancelRemix ()
    {
        _parent.editComplete(null);
    }

    protected void setHash (
        String id, String mediaHash, int mimeType, int constraint, int width, int height)
    {
        if (id != Item.MAIN_MEDIA) {
            CShell.log("setHash() called on remixer for non-main media: " + id);
            return;
        }

        _item.setPrimaryMedia(new MediaDesc(mediaHash, (byte) mimeType, (byte) constraint));

        CShell.itemsvc.remixItem(CShell.ident, _item, new MsoyCallback<Item>() {
            public void onSuccess (Item item) {
                MsoyUI.info(CShell.emsgs.msgItemUpdated());
                _parent.editComplete(item);
            }
        });
    }

    /**
     * Set a photo as a new image source in the remixer. The PopupFilePreview needs to be up..
     */
    protected static native void setPhotoUrl (String url) /*-{
        var controls = $doc.getElementById("remixControls");
        if (controls) {
            try {
                controls.setPhotoUrl(url);
            } catch (e) {
                // nada
            }
        }
    }-*/;

    protected static void bridgeSetHash (
        String id, String mediaHash, int mimeType, int constraint, int width, int height)
    {
        // for some reason the strings that come in from JavaScript aren't quite right, so
        // we jiggle them thusly
        String fid = "" + id;
        String fhash = "" + mediaHash;
        _singleton.setHash(fid, fhash, mimeType, constraint, width, height);
    }

    protected static void bridgeCancelRemix ()
    {
        _singleton.cancelRemix();
    }

    protected static void bridgePickPhoto ()
    {
        _singleton.pickPhoto();
    }

    protected static native void configureBridges () /*-{
        $wnd.setHash = function (id, hash, type, constraint, width, height) {
            @client.remix.ItemRemixer::bridgeSetHash(Ljava/lang/String;Ljava/lang/String;IIII)(id, hash, type, constraint, width, height);
        };
        $wnd.cancelRemix = function () {
            @client.remix.ItemRemixer::bridgeCancelRemix()();
        };
        $wnd.pickPhoto = function () {
            @client.remix.ItemRemixer::bridgePickPhoto()();
        };
    }-*/;

    protected static ItemRemixer _singleton;

    protected EditorHost _parent;

    /** The item we're remixing. */
    protected Item _item;
}
