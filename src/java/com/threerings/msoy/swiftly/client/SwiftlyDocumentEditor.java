//
// $Id$

package com.threerings.msoy.swiftly.client;        

import java.util.List;

import com.threerings.msoy.swiftly.data.SwiftlyTextDocument;

public interface SwiftlyDocumentEditor
{
    public void editTextDocument (SwiftlyTextDocument document);

    public List<FileTypes> getCreateableFileTypes ();

    /** Maps a human friendly name to a mime type. */
    public static class FileTypes
    {
        public FileTypes (String displayName, String mimeType)
        {
            this.displayName = displayName;
            this.mimeType = mimeType;
        }

        public String toString()
        {
            return displayName;
        }

        public String displayName;
        public String mimeType;
    }
}
