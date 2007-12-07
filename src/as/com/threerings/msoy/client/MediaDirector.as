//
// $Id$

package com.threerings.msoy.client {

import com.threerings.util.Log;

import com.threerings.presents.client.BasicDirector;
import com.threerings.presents.client.ClientEvent;

import com.threerings.crowd.data.OccupantInfo;

import com.threerings.msoy.item.data.all.Decor;
import com.threerings.msoy.item.data.all.ItemIdent;

import com.threerings.msoy.world.client.DecorSprite;
import com.threerings.msoy.world.client.FurniSprite;
import com.threerings.msoy.world.client.MemberSprite;
import com.threerings.msoy.world.client.MobSprite;
import com.threerings.msoy.world.client.MsoySprite;
import com.threerings.msoy.world.client.OccupantSprite;
import com.threerings.msoy.world.client.PetSprite;

import com.threerings.msoy.world.data.FurniData;
import com.threerings.msoy.world.data.MemberInfo;
import com.threerings.msoy.world.data.MobInfo;
import com.threerings.msoy.world.data.ObserverInfo;
import com.threerings.msoy.world.data.PetInfo;

/**
 * Handles the loading of various media.
 */
public class MediaDirector extends BasicDirector
{
    public static const log :Log = Log.getLog(MediaDirector);

    public function MediaDirector (ctx :MsoyContext)
    {
        super(ctx);
        _mctx = ctx;
    }

    /**
     * Creates an occupant sprite for the specified occupant info.
     */
    public function getSprite (occInfo :OccupantInfo) :OccupantSprite
    {
        var isOurs :Boolean = (occInfo.bodyOid == _ctx.getClient().getClientOid());
        if (isOurs && _ourAvatar != null) {
            _ourAvatar.setOccupantInfo(occInfo);
            return _ourAvatar;
        }

        if (occInfo is PetInfo) {
            return new PetSprite(occInfo as PetInfo);

        } else if (occInfo is MemberInfo) {
            var sprite :MemberSprite = new MemberSprite(occInfo as MemberInfo);
            if (isOurs) {
                _ourAvatar = sprite;
            }
            return sprite;

        } else if (occInfo is MobInfo) {
            return new MobSprite(occInfo as MobInfo);

        } else if (occInfo is ObserverInfo) {
            // view-only members have no sprite visualization
            return null;

        } else {
            log.warning("Don't know how to create sprite for occupant " + occInfo + ".");
            return null;
        }
    }

    /**
     * Get a Furni sprite for the specified furni data, caching as appropriate.
     */
    public function getFurni (furni :FurniData) :FurniSprite
    {
        return new FurniSprite(furni);
    }

    /**
     * Get a Decor sprite for the specified decor data, caching as appropriate.
     */
    public function getDecor (decor :Decor) :DecorSprite
    {
        return new DecorSprite(decor);
    }
    
    /**
     * Release any references to the specified sprite, if appropriate.
     */
    public function returnSprite (sprite :MsoySprite) :void
    {
        if (sprite != _ourAvatar) {
            sprite.shutdown();

        } else {
            // prevent it from continuing to move, but don't shut it down
            _ourAvatar.stopMove();
        }
    }

    override public function clientDidLogoff (event :ClientEvent) :void
    {
        super.clientDidLogoff(event);

        // release our hold on our avatar
        _ourAvatar = null;
    }

    /** A casted copy of the context. */
    protected var _mctx :MsoyContext;

    /** Our very own avatar: avoid loading and unloading it. */
    protected var _ourAvatar :MemberSprite;
}
}
