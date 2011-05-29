/* A WikiTextChanges implementation to diff wikitext from wormarc archives.
 *
 * Copyright (C) 2010, 2011 Darrell Karbott
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package fniki.wiki;

import java.io.IOException;

import wormarc.Archive;
import wormarc.FileManifest;
import wormarc.IOUtil;

public class RemoteWikiTextChanges implements WikiTextChanges {
    private final Archive mArchive;
    private final FileManifest mManifest;
    private final FileManifest.Changes mChanges;

    public final static WikiTextChanges NO_REMOTE_CHANGES = new WikiTextChanges() {
            public boolean hasChange(String name) throws IOException { return false; }
            public boolean wasDeleted(String name) throws IOException { return false; }
            public String getPage(String name) throws IOException { throw new IOException("No such change."); }
        };

    public RemoteWikiTextChanges(FileManifest parentManifest,
                                 Archive remoteArchive,
                                 FileManifest remoteManifest) {
        mChanges = FileManifest.diff(parentManifest.getMap(), remoteManifest.getMap());
        mArchive = remoteArchive;
        mManifest = remoteManifest;
    }

    public boolean hasChange(String name) throws IOException {
        return mChanges.mDeleted.contains(name) ||
            mChanges.mAdded.contains(name) ||
            mChanges.mModified.contains(name);
    }

    public boolean wasDeleted(String name) throws IOException {
        return mChanges.mDeleted.contains(name);
    }

    public String getPage(String name) throws IOException {
        return IOUtil.readUtf8StringAndClose(mManifest.getFile(mArchive, name));
    }
}