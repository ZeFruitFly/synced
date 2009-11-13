//
// $Id$

package com.threerings.msoy.tutorial.client {

import flash.display.Sprite;
import flash.events.TimerEvent;
import flash.utils.Timer;

import mx.core.UIComponent;

import caurina.transitions.Tweener;

import com.threerings.util.ArrayUtil;
import com.threerings.util.Map;
import com.threerings.util.Maps;
import com.threerings.util.Util;

import com.threerings.msoy.data.MemberObject;

import com.threerings.msoy.client.PlaceBox;
import com.threerings.msoy.client.Prefs;
import com.threerings.msoy.client.TopPanel;

import com.threerings.msoy.world.client.WorldContext;
import com.threerings.msoy.world.client.WorldControlBar;

import flash.utils.setTimeout; // function import
import flash.utils.getTimer; // function import

/**
 * Director for the tutorial. Notionally manages a collection of tutorial items, their display and
 * heuristics.
 */
public class TutorialDirector
{
    /**
     * Creates a new director.
     */
    public function TutorialDirector (ctx :WorldContext)
    {
        _ctx = ctx;
        _timer = new Timer(TIP_DELAY, 1);
        _timer.addEventListener(TimerEvent.TIMER, handleTimer);

        _panel = new TutorialPanel(onPanelClose);
    }

    /**
     * Creates a new builder for a suggestion with the given id and text. When queued, the
     * suggestion is shown immediately unless it is not appropriate to do so.
     */
    public function newSuggestion (id :String, text :String) :TutorialItemBuilder
    {
        return newItem(Kind.SUGGESTION, id, text);
    }

    /**
     * Creates a new builder for a tip with the given id and text. When queued, the tip is added to
     * the end of the tip list. It gets shown later once all preceding tips have been shown unless
     * it is not appropriate to do so at that time.
     */
    public function newTip (id :String, text :String) :TutorialItemBuilder
    {
        return newItem(Kind.TIP, id, text);
    }

    /**
     * Creates a new builder for a promption item with the given id and text. Currently promotions
     * are treated like tips. The plan is to make them non-ignorable. They may also get shown at a
     * different frequency.
     */
    public function newPromotion (id :String, text :String) :TutorialItemBuilder
    {
        return newItem(Kind.PROMOTION, id, text);
    }

    /**
     * Creates a new builder for a tutorial sequence. Once a sequence is activated, all of its
     * items must be viewed before the "ambient" tutorial proceeds. If any item in the sequence is
     * not available the tutorial will wait and check periodically until it becomes available. The
     * progress within a sequence is stored in <code>Prefs</code> so that a sequence will pick up
     * where it left off in the last session when activated.
     */
    public function newSequence (id :String) :TutorialSequenceBuilder
    {
        return new TutorialSequenceBuilder(id, this);
    }

    /**
     * Queues a previously created item to popup.
     */
    public function queueItem (item :TutorialItem) :void
    {
        var ignored :Boolean = isIgnored(item);
        if (isImmediate(item.kind)) {
            // either show this item now or just ignore it
            if (!ignored && item.isAvailable()) {
                _suggestions.push(item);
                update();
            }
        } else {
            // add to pool for later display, mark as seen if it was previously ignored
            // TODO: unfudge: "ignored" is not really the same as "seen"
            var seen :Boolean = ignored;
            _pool.put(item, seen);
            update();
        }
    }

    /**
     * Activates a sequence. All items will be viewed before normal tutorial behavior resumes.
     */
    public function activateSequence (seq :TutorialSequence) :Boolean
    {
        if (_sequence != null || !seq.isAvailable() ||
            Prefs.getTutorialProgress(seq.id) >= seq.size()) {
            return false;
        }

        _sequence = seq;
        update();
        return true;
    }

    /**
     * Gets the level of the logged in member, or -1 if the member is not logged in.
     */
    public function getMemberLevel () :int
    {
        var memObj :MemberObject = _ctx.getMemberObject();
        return memObj == null ? -1 : memObj.level;
    }

    public function test (delayMultiplier :Number) :void
    {
        function gibby (str :String) :String {
            var gibberish :String = "The quick brown fox jumped over the lazy dog.";
            return str + " " + gibberish + " " + gibberish;
        }
        if (_pool.size() == 0) {
            newTip("tip1", gibby("This is test tip #1.")).queue();
            newTip("tip2", gibby("This is test tip #2.")).queue();
            newTip("tip3", gibby("This tip is limited to advanced users.")).advanced().queue();
            newTip("tip4", gibby("This is a non-ignorable tip.")).noIgnore().queue();
            newTip("tip5", gibby("Hey! There's the go button."))
                .highlight(_ctx.getControlBar().goBtn).queue();
            newPromotion("promo1", gibby("This is a test promotion.")).queue();
            _ctx.getChatDirector().displayFeedback(null, "Test: added 4 tips and 1 promotion.");
        }

        var delay :Number = TIP_DELAY + (Math.random() - .5) * TIP_DELAY * .5;
        delay *= delayMultiplier;
        var id :int = getTimer();
        setTimeout(function () :void {
            newSuggestion("test" + id, gibby("This is a test suggestion (id " + id + ")."))
                .button("Do It!", function () :void {})
                .controlBarHighlight(_ctx.getControlBar().goBtn)
                .queue();
        }, delay);

        _ctx.getChatDirector().displayFeedback(null, "Test: queued suggestion id " + id +
            " for display in " + int(delay / 1000) + " seconds.");
    }

    public function testSequence () :void
    {
        var sequence :TutorialSequenceBuilder = newSequence("testSeq" + getTimer());
        sequence.newSuggestion("This is sequence item #1. Arrange your room dumbass!")
            .menuItemHighlight(WorldControlBar(_ctx.getControlBar()).roomBtn, "RoomEdit").queue();
        sequence.newSuggestion("This is sequence item #2.").queue();
        sequence.newSuggestion("This is sequence item #3.").queue();
        if (sequence.activate()) {
            _ctx.getChatDirector().displayFeedback(null, "Test: activated sequence.");
        } else {
            _ctx.getChatDirector().displayFeedback(null, "Test: sequence not activated.");
        }
    }

    /**
     * Creates a new builder for an item of the given kind with the given id and text.
     */
    protected function newItem (kind :Kind, id :String, text :String) :TutorialItemBuilder
    {
        return new TutorialItemBuilder(new TutorialItem(kind, id, text), this);
    }

    protected function isShowing () :Boolean
    {
        return _panel.parent != null;
    }

    protected function handleTimer (evt :TimerEvent) :void
    {
        var item :TutorialItem; // for use in multiple scopes
        if (!isShowing()) {
            if (_sequence != null) {
                var seqId :String = _sequence.id;
                var progress :int = Prefs.getTutorialProgress(seqId);
                if (progress >= _sequence.size()) {
                    // degenerate case, the sequence has changed since the cookie was last set
                    Prefs.setTutorialProgress(seqId, int.MAX_VALUE);
                    update();
                } else if ((item = _sequence.items[progress]).isAvailable()) {
                    if (++progress == _sequence.size()) {
                        progress = int.MAX_VALUE;
                        _sequence = null;
                    }
                    Prefs.setTutorialProgress(seqId, progress);
                    popup(item);
                } else {
                    update();
                }

            } else if (_suggestions.length > 0) {
                popup(_suggestions.shift());

            } else {
                // compute the candidate list of unseen tips and shuffle
                var unseen :Array = Maps.filter(_pool, isUnseen, Maps.selectKey);
                ArrayUtil.shuffle(unseen);

                // start from the end and find one that is not ignored and is available
                var changed :Boolean = false;
                while (unseen.length > 0) {
                    item = unseen.pop();
                    if (isIgnored(item)) {
                        // TODO: unfudge: "ignored" is not really the same as "seen"
                        _pool.put(item, true);
                        changed = true;
                    } else if (!item.isAvailable()) {
                        // skip it, it might become available later
                    } else {
                        // show it and bail
                        popup(item);
                        break;
                    }
                }

                // update if any tip changed state
                if (changed) {
                    update();
                }
            }
        }
    }

    protected function update () :void
    {
        var showing :Boolean = isShowing();
        if (showing) {
            _timer.reset();

        } else {
            var delay :Number =
                (_suggestions.length > 0 || _sequence != null) ? SUGGESTION_DELAY : TIP_DELAY;
            if (delay != _timer.delay) {
                _timer.delay = delay;
            }
            if (!_timer.running && (
                Maps.some(_pool, isUnseen) || _suggestions.length > 0 || _sequence != null)) {
                _timer.start();
            }
        }

        if (isShowing() && _suggestions.length > 0) {
            _panel.flashCloseButton();
        }
    }

    internal function get topPanel () :TopPanel
    {
        return _ctx.getTopPanel();
    }

    internal function get worldCtx () :WorldContext
    {
        return _ctx;
    }

    protected function placeBox () :PlaceBox
    {
        return _ctx.getTopPanel().getPlaceContainer();
    }

    protected function onPanelClose () :void
    {
        Tweener.addTween(_panel, {y :-_panel.height, time: ROLL_TIME, transition: "easeinquart",
            onComplete: Util.sequence(
                Util.adapt(placeBox().removeChild, _panel),
                Util.adapt(update))});
    }

    protected function popup (item :TutorialItem) :void
    {
        Tweener.removeTweens(_panel);

        if (!isShowing()) {
            placeBox().addOverlay(_panel, PlaceBox.LAYER_TUTORIAL);
            _panel.y = -_panel.height;
        }

        _panel.setContent(item);

        if (_panel.y != 0) {
            Tweener.addTween(_panel, {y :0, time: ROLL_TIME, transition: "easeoutquart"});
        }

        update();

        // mark as seen
        if (!isImmediate(item.kind)) {
            _pool.put(item, true);
        }
    }

    protected static function isIgnored (item :TutorialItem) :Boolean
    {
        return item.ignorable && Prefs.isTutorialIgnored(item.id);
    }

    protected static function isImmediate (kind :Kind) :Boolean
    {
        return kind == Kind.SUGGESTION;
    }

    protected static function isUnseen (item :TutorialItem, val :Boolean) :Boolean
    {
        return !val;
    }

    protected var _ctx :WorldContext;
    protected var _panel :TutorialPanel;
    protected var _timer :Timer;
    protected var _suggestions :Array = [];
    protected var _pool :Map = Maps.newMapOf(TutorialItem); // to boolean: seen
    protected var _sequence :TutorialSequence;

    protected var ROLL_TIME :Number = 0.6;
    protected var TIP_DELAY :Number = 60 * 1000;
    protected var SUGGESTION_DELAY :Number = (ROLL_TIME + .25) * 1000;
}
}
