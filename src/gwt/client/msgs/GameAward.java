//
// $Id$

package client.msgs;

import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.msoy.item.data.all.MediaDesc;

import com.threerings.msoy.person.data.GameAwardPayload;

import client.games.GameDetailPanel;
import client.shell.Application;
import client.shell.Args;
import client.shell.Page;
import client.util.MediaUtil;

/**
 * Contains a renderer for a game award.
 */
public abstract class GameAward
{
    public static final class Display extends MailPayloadDisplay
    {
        // @Override // from MailPayloadDisplay
        public Widget widgetForRecipient (MailUpdateListener listener)
        {
            FlexTable table = new FlexTable();

            table.setText(0, 0, CMsgs.mmsgs.awardTitle());
            String args = Args.compose(new String[] {
                "d", "" + _payload.gameId, GameDetailPanel.TROPHIES_TAB });
            table.setWidget(0, 1, Application.createLink(_payload.gameName, Page.GAMES, args));
            table.getFlexCellFormatter().setHorizontalAlignment(0, 1, HasAlignment.ALIGN_CENTER);

            table.setWidget(1, 1, MediaUtil.createMediaView(
                                _payload.getAwardMedia(), MediaDesc.THUMBNAIL_SIZE));
            table.getFlexCellFormatter().setHorizontalAlignment(1, 1, HasAlignment.ALIGN_CENTER);
            switch (_payload.awardType) {
            case GameAwardPayload.TROPHY:
                table.setText(2, 0, CMsgs.mmsgs.trophyName());
                break;
            case GameAwardPayload.PRIZE:
                table.setText(2, 0, CMsgs.mmsgs.prizeName());
                break;
            }

            table.setText(2, 1, _payload.awardName);
            table.getFlexCellFormatter().setHorizontalAlignment(2, 1, HasAlignment.ALIGN_CENTER);

            return table;
        }

        // @Override // from MailPayloadDisplay
        public Widget widgetForOthers ()
        {
            throw new IllegalStateException("Non-recipients should not see game awards.");
        }

        // @Override // from MailPayloadDisplay
        public String okToDelete ()
        {
            return null;
        }

        // @Override // from MailPayloadDisplay
        protected void didInit ()
        {
            _payload = (GameAwardPayload)_message.payload;
        }

        protected GameAwardPayload _payload;
    }
}
