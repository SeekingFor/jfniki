/* Class to overlay local changes on top of a version from a FileManifest.
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

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wormarc.Archive;
import wormarc.FileManifest;
import wormarc.IOUtil;
import wormarc.LinkDigest;

public class LocalWikiChanges implements WikiTextStorage, FileManifest.IO {
    final static class LocalChange {
        public final String mData;
        public final boolean mDeleted;
        LocalChange(String data, boolean deleted) {
            mData = data;
            mDeleted = deleted;
        }
    }

    private Map<String, LocalChange> mMap = new HashMap<String, LocalChange> ();
    private Archive mArchive;
    private FileManifest mBaseVersion;

    public LocalWikiChanges(Archive archive, FileManifest manifest) {
        mArchive = archive;
        mBaseVersion = manifest;
    }

    ////////////////////////////////////////////////////////////
    // WikiTextStorage implementation
    public boolean hasPage(String name) throws IOException {
        if (mMap.containsKey(name)) {
            return !mMap.get(name).mDeleted;
        }

        return mBaseVersion.contains(name);
    }

    public String getPage(String name) throws IOException {
        if (mMap.containsKey(name)) {
            if (mMap.get(name).mDeleted) {
                throw new FileNotFoundException("Deleted by overlay: " + name);
            }
            return mMap.get(name).mData;
        }

        return IOUtil.readUtf8StringAndClose(mBaseVersion.getFile(mArchive, name));
    }

    public void putPage(String name, String text) throws IOException {
        mMap.put(name, new LocalChange(text, false));
    }

    public List<String> getNames() throws IOException {
        Set<String> baseNames = new HashSet(mBaseVersion.allFiles()); // Must copy!

        for (String name : mMap.keySet()) {
            if (mMap.get(name).mDeleted) {
                baseNames.remove(name);
            } else {
                baseNames.add(name);
            }
        }

        List<String> names = new ArrayList<String>(baseNames);
        Collections.sort(names);
        return names;
    }

    public void deletePage(String name) throws IOException {
        mMap.put(name, new LocalChange(null, true));
    }

    public boolean hasLocalChange(String pageName) {
        return mMap.containsKey(pageName);
    }

    public void revertLocalChange(String pageName) {
        if (!mMap.containsKey(pageName)) {
            return;
        }
        mMap.remove(pageName);
    }

    ////////////////////////////////////////////////////////////
    // FileManifest.IO implementation
    public Map<String, LinkDigest> getFiles() throws IOException {
        Map<String, LinkDigest> files = new HashMap<String, LinkDigest>();
        for (String name : getNames()) {
            files.put(name, LinkDigest.NULL_DIGEST);
        }
        return files;
    }

    public InputStream getFile(String name) throws IOException {
        if (mMap.containsKey(name)) {
            if (mMap.get(name).mDeleted) {
                throw new FileNotFoundException("Deleted by overlay: " + name);
            }
            return new ByteArrayInputStream(mMap.get(name).mData.getBytes(IOUtil.UTF8));
        }
        return mBaseVersion.getFile(mArchive, name);
    }

    private static void enotimpl() throws IOException {
        throw new IOException("Only reading is implemented!");
    }
    public void putFile(String name, InputStream rawBytes) throws IOException { enotimpl(); }
    public void deleteFile(String name) throws IOException { enotimpl(); }
    public void startSync(Set<String> allFiles) throws IOException { enotimpl(); }
    public void endSync(Set<String> allFiles) throws IOException { enotimpl(); }
}
