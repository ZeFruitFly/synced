//
// $Id$

package com.threerings.msoy.party.client {

import mx.containers.HBox;
import mx.containers.VBox;
import mx.controls.Image;
import mx.controls.Label;
import mx.controls.List;
import mx.controls.Text;
import mx.core.ClassFactory;
import mx.core.ScrollPolicy;

import com.threerings.flex.CommandButton;
import com.threerings.flex.FlexUtil;

import com.threerings.msoy.client.Msgs;
import com.threerings.msoy.party.data.PartyCodes;
import com.threerings.msoy.ui.FloatingPanel;
import com.threerings.msoy.world.client.WorldContext;

public class PartyBoardPanel extends FloatingPanel
{
    public function PartyBoardPanel (ctx :WorldContext, mode :int = PartyCodes.BOARD_NORMAL)
    {
        super(ctx, Msgs.PARTY.get("t.board_" + mode));
        showCloseButton = true;
        setButtonWidth(0);
        _wctx = ctx;
        _mode = mode;

        var cf :ClassFactory = new ClassFactory(PartyBoardInfoRenderer);
        cf.properties = { wctx: _wctx };
        _partyList = new List();
        _partyList.selectable = false;
        _partyList.itemRenderer = cf;
        //_partyList.verticalScrollPolicy = ScrollPolicy.ON;
        _partyList.percentWidth = 100;
        _partyList.percentHeight = 100;

        var loading :Label = FlexUtil.createLabel(Msgs.PARTY.get("m.loading"), null);
        loading.percentWidth = 100;
        loading.percentHeight = 100;

        _content = new VBox();
        _content.width = 450;
        _content.height = 300;
        _content.addChild(loading);

        getPartyBoard();
    }

    override protected function createChildren () :void
    {
        super.createChildren();

        var top :HBox = new HBox();
        top.percentWidth = 100;
        top.styleName = "panelBottom";
        addChild(top);

        var sep :VBox = new VBox();
        sep.percentWidth = 100;
        sep.height = 1;
        sep.styleName = "panelBottomSeparator";
        addChild(sep);

        var img :Image = new Image();
        img.source = PARTY_HEADER;
        top.addChild(img);
        top.setStyle("paddingLeft", 10);
        top.setStyle("paddingTop", 10);

        var text :Text = FlexUtil.createWideText(null);
        text.selectable = true;
        text.htmlText = Msgs.PARTY.get("m.about_" + _mode);
        text.setStyle("fontSize", 12);
        top.addChild(text);

        addChild(_content);

        var btn :CommandButton = new CommandButton(Msgs.PARTY.get("b.create"));
        btn.setCallback(FloatingPanel.createPopper(function () :FloatingPanel {
            return new CreatePartyPanel(_wctx);
        }));

        addButtons(btn);
        _buttonBar.styleName = "buttonPadding"; // pad out the buttons since we have no border
        _buttonBar.setStyle("buttonStyleName", "orangeButton"); // oh you're kidding me
        // TODO: if we need to add more buttons, and want to undo orangeness, we will
        // have to put these buttons into an hbox or something
    }

    protected function getPartyBoard () :void
    {
        _wctx.getPartyDirector().getPartyBoard(gotPartyBoard, _mode);
    }

    /**
     * Called with the result of a getPartyBoard request.
     */
    protected function gotPartyBoard (result :Array) :void
    {
        _content.removeAllChildren();

        if (result.length > 0) {
            _content.addChild(_partyList);
            _partyList.dataProvider = result;
        } else {
            var none :Label = FlexUtil.createLabel(
                Msgs.PARTY.get("m.no_parties_" + _mode), null);
            none.percentWidth = 100;
            none.percentHeight = 100;
            _content.addChild(none);
        }
    }

    protected var _wctx :WorldContext;

    protected var _mode :int;

    protected var _partyList :List;

    /** Contains either the loading Label or party List. */
    protected var _content :VBox;

    [Embed(source="../../../../../../../rsrc/media/skins/party/board_header.png")]
    protected static const PARTY_HEADER :Class;
}
}
