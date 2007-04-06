package com.threerings.msoy.game.chiyogami.client {

import flash.display.DisplayObject;
import flash.display.Loader;
import flash.display.Shape;
import flash.display.Sprite;

import flash.text.TextField;

import flash.events.Event;
import flash.events.TimerEvent;

import flash.media.Sound;
import flash.media.SoundChannel;

import flash.utils.getTimer; // function import
import flash.utils.Timer;

import mx.containers.Canvas;

import com.threerings.flash.FPSDisplay;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.msoy.client.WorldContext;

import com.threerings.msoy.item.web.Item;
import com.threerings.msoy.item.web.MediaDesc;
import com.threerings.msoy.item.web.StaticMediaDesc;

import com.threerings.msoy.game.client.MiniGameContainer;

public class ChiyogamiPanel extends Canvas
    implements PlaceView
{
    public function ChiyogamiPanel (ctx :WorldContext, ctrl :ChiyogamiController)
    {
        rawChildren.addChild(new SPLASH() as DisplayObject);
        // TODO: Splash screen
    }

    // from PlaceView
    public function willEnterPlace (plobj :PlaceObject) :void
    {
    }

    // from PlaceView
    public function didLeavePlace (plobj :PlaceObject) :void
    {
    }

    /**
     * Start things a-moving. TODO
     */
    public function gameDidStart () :void
    {
        // pick a game!
        var game :MediaDesc = MediaDesc(GAMES[
            int(Math.floor(Math.random() * GAMES.length))]);

        _minigame = new MiniGameContainer();
        _minigame.setup(game);

        _minigame.performanceCallback = miniGameReportedPerformance;

        rawChildren.addChild(_minigame);

        var mask :Shape = new Shape();
        with (mask.graphics) {
            beginFill(0xffFFff);
            drawRect(0, 0, 800, 100);
            endFill();
        }

        _minigame.mask = mask;
        rawChildren.addChild(mask);
    }

    public function gameDidEnd () :void
    {
        rawChildren.removeChild(_minigame);
    }

    /**
     * Routed from usercode- the score and style will be reported at
     * the discretion of the minigame.
     */
    protected function miniGameReportedPerformance (score :Number, style :Number) :void
    {
        trace("Got performance from minigame! [score=" + score + ", style=" + style + "]");
    }

    protected var _minigame :MiniGameContainer;

    /** The hardcoded games we currently use. */
    protected static const GAMES :Array = [
        new StaticMediaDesc(MediaDesc.APPLICATION_SHOCKWAVE_FLASH,
            Item.GAME, "chiyogami/KeyJam"),
        new StaticMediaDesc(MediaDesc.APPLICATION_SHOCKWAVE_FLASH,
            Item.GAME, "chiyogami/Match3")
    ];

    [Embed(source="splash.png")]
    protected static const SPLASH :Class;
}
}
