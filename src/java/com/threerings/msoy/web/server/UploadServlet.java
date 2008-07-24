//
// $Id$

package com.threerings.msoy.web.server;

import java.io.IOException;
import java.io.PrintStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.samskivert.io.StreamUtil;

import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.MediaDesc;

import com.threerings.msoy.web.server.UploadUtil.MediaInfo;

import static com.threerings.msoy.Log.log;

/**
 * Handles the uploading of digital media for later use by a digital item.
 */
public class UploadServlet extends AbstractUploadServlet
{
    /**
     * Generates the MediaInfo object for this FileItem and publishes the data into the media store
     * and returns the results via Javascript to the GWT client.
     */
    @Override // from AbstractUploadServlet
    protected void handleFileItems (FileItem item, FileItem[] allItems, int uploadLength,
                                    HttpServletRequest req, HttpServletResponse rsp)
        throws IOException, FileUploadException, AccessDeniedException
    {
        // wrap the FileItem in an UploadFile for publishing
        UploadFile uploadFile = new FileItemUploadFile(item);

        // attempt to extract the mediaId
        String mediaId = item.getFieldName();
        if (mediaId == null) {
            throw new FileUploadException("Failed to extract mediaId from upload request.");
        }

        // TODO: check that the user is logged in. This will require the various item editors to
        // pass a WebIdent down here.

        // TODO: check that this is a supported content type
        log.info("Received file: [type: " + item.getContentType() +
                 ", size=" + item.getSize() + ", id=" + item.getFieldName() + "].");

        // check the file size now that we know mimetype,
        // or freak out if we still don't know the mimetype.
        byte mimeType = uploadFile.getMimeType();
        if (mimeType != MediaDesc.INVALID_MIME_TYPE) {
            // now we can validate the file size
            validateFileLength(mimeType, uploadLength);

        } else {
            throw new FileUploadException("Received upload of unknown mime type [type=" +
                item.getContentType() + ", name=" + item.getName() + "].");
        }

        // determine whether they also want a thumbnail media generated
        boolean generateThumb = mediaId.endsWith(Item.PLUS_THUMB);
        if (generateThumb) {
            mediaId = mediaId.substring(0, mediaId.length()-Item.PLUS_THUMB.length());
        }

        // if this is an image...
        MediaInfo info, tinfo = null;
        if (MediaDesc.isImage(mimeType)) {
            // ...determine its constraints, generate a thumbnail, and publish the data into the
            // media store
            info = UploadUtil.publishImage(mediaId, uploadFile);

            // if the client has requested a thumbnail image to be generated along with this image,
            // then do that as well
            if (generateThumb) {
                tinfo = UploadUtil.publishImage(Item.THUMB_MEDIA, uploadFile);
            }

        } else {
            // treat all other file types in the same manner, just publish them
            info = new MediaInfo(uploadFile.getHash(), mimeType);
            UploadUtil.publishUploadFile(uploadFile);
        }

        // determine whether we're responding to the mchooser or a GWT upload
        Client client = Client.GWT;
        for (FileItem fitem : allItems) {
            if (fitem.getFieldName().equals("client")) {
                String text = null;
                try {
                    text = fitem.getString("UTF-8");
                    client = Client.valueOf(text.toUpperCase());
                } catch (Exception e) {
                    log.warning("Invalid client identifier [text=" + text + ", error=" + e + "].");
                }
            }
        }

        // now that we have published the file, post a response back to the caller
        sendResponse(rsp, client, mediaId, info, tinfo);
    }

    /**
     * Send the response back to the caller.
     */
    protected void sendResponse (
        HttpServletResponse rsp, Client client, String mediaId, MediaInfo info, MediaInfo tinfo)
        throws IOException
    {
        // write out the magical incantations that are needed to cause our magical little
        // frame to communicate the newly assigned mediaHash to the ItemEditor widget
        PrintStream out = null;
        try {
            out = new PrintStream(rsp.getOutputStream());
            switch (client) {
            case GWT:
                out.println("<html>");
                out.println("<head></head>");
                String script = "parent.setHash('" + mediaId + "', '" + info.hash + "', " +
                    info.mimeType + ", " + info.constraint + ", " +
                    info.width + ", " + info.height + ")";
                if (tinfo != null) {
                    script += "; parent.setHash('" + Item.THUMB_MEDIA + "', '" + tinfo.hash + "', " +
                        tinfo.mimeType + ", " + tinfo.constraint + ", " +
                        tinfo.width + ", " + tinfo.height + ")";
                }
                out.println("<body onLoad=\"" + script + "\"></body>");
                out.println("</html>");
                break;

            case MCHOOSER:
                out.println(mediaId + " " + info.hash + " " + info.mimeType + " " +
                            info.constraint + " " + info.width + " " + info.height);
                if (tinfo != null) {
                    out.println(Item.THUMB_MEDIA + " " + tinfo.hash + " " + tinfo.mimeType + " " +
                                tinfo.constraint + " " + tinfo.width + " " + tinfo.height);
                }
                break;
            }

        } finally {
            StreamUtil.close(out);
        }
    }

    @Override // from AbstractUploadServlet
    protected int getMaxUploadSize ()
    {
        return LARGE_MEDIA_MAX_SIZE;
    }

    /**
     * Validate that the upload size is acceptable for the specified mimetype.
     */
    protected void validateFileLength (byte mimeType, int length)
        throws FileUploadException
    {
        long limit;
        switch (mimeType) {
        case MediaDesc.AUDIO_MPEG:
        case MediaDesc.VIDEO_FLASH:
        case MediaDesc.VIDEO_MPEG:
        case MediaDesc.VIDEO_QUICKTIME:
        case MediaDesc.VIDEO_MSVIDEO:
        case MediaDesc.APPLICATION_ZIP:
            limit = LARGE_MEDIA_MAX_SIZE;
            break;

        case MediaDesc.APPLICATION_SHOCKWAVE_FLASH:
            limit = MEDIUM_MEDIA_MAX_SIZE;
            break;

        default:
            limit = SMALL_MEDIA_MAX_SIZE;
            break;
        }

        // do the actual check
        if (length > limit) {
            throw new ServletFileUpload.SizeLimitExceededException(
                "File size is too big for specified mimeType", length, limit);
        }
    }

    /** Represents different potential upload clients. */
    protected enum Client { GWT, MCHOOSER };

    /** Prevent Captain Insano from showing up to fill our drives. */
    protected static final int SMALL_MEDIA_MAX_SIZE = 5 * MEGABYTE;
    protected static final int MEDIUM_MEDIA_MAX_SIZE = 10 * MEGABYTE;
    protected static final int LARGE_MEDIA_MAX_SIZE = 100 * MEGABYTE;
}
