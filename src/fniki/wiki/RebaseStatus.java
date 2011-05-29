/* Class to generate the underlying rep used to display rebase status.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import wormarc.FileManifest;
import wormarc.LinkDigest;

public class RebaseStatus {
    public final static int LOCALLY_MODIFIED = 1;
    public final static int PARENT = 2;
    public final static int REBASE = 3;

    public final static int OP_ADDED = 4;
    public final static int OP_DELETED = 5;
    public final static int OP_MODIFIED = 6;

    public final static class Record {
        public final String mName;
        public final int mDiffOp;
        public final int mStatus;
        Record(String name, int op, int status) {
            if (name == null || name.trim().equals("")) {
                throw new IllegalArgumentException("Bad name.");
            }
            if (op < OP_ADDED || op > OP_MODIFIED) {
                throw new IllegalArgumentException("Bad op.");
            }
            if (status < LOCALLY_MODIFIED || status > REBASE) {
                throw new IllegalArgumentException("Bad status.");
            }
            mName = name;
            mDiffOp = op;
            mStatus = status;
        }
    }

    private static LinkDigest get(Map<String, LinkDigest> map, String name) {
        LinkDigest digest = map.get(name);
        if (digest == null) {
            return LinkDigest.NULL_DIGEST;
        }
        return digest;
    }

    // Get status of all rebased files at once.
    public static List<Record> getStatus(Map<String, LinkDigest> working,
                                         Map<String, LinkDigest> parent,
                                         Map<String, LinkDigest> rebase) {

        // LATER: Is this worth cleaning up?
        // RemoteWikiTextChanges has a copy of changes. Pass it in? Not worth it? diff is cheap
        FileManifest.Changes changes = FileManifest.diff(parent, rebase);

        HashMap<String, Integer> opLut = new HashMap<String, Integer>();
        for (String name : changes.mAdded) { opLut.put(name, OP_ADDED); }
        for (String name : changes.mDeleted) { opLut.put(name, OP_DELETED); }
        for (String name : changes.mModified) { opLut.put(name, OP_MODIFIED); }

        List<String> ordered = new ArrayList<String>(opLut.keySet());
        Collections.sort(ordered);

        List<Record> records = new ArrayList<Record>();
        for (String name : ordered) {
            int state = 0;
            LinkDigest workingDigest = get(working, name);
            if (workingDigest.equals(get(parent, name))) {
                state = PARENT;
            } else if (workingDigest.equals(get(rebase, name))) {
                state = REBASE;
            } else {
                state = LOCALLY_MODIFIED;
            }
            //System.err.println(String.format("%s, %d, %d", name, opLut.get(name), state));
            records.add(new Record(name, opLut.get(name), state));
        }
        return records;
    }

    private static void assert_(boolean condition) {
        if (!condition) {throw new RuntimeException("Assertion failure."); }
    }

    // Determine whether the current version of a single page  is the
    // PARENT, REBASE or LOCALLY_MODIFIED version.
    //
    // INTENT: Encapsulate logic and keep it separate from presentation.
    // LATER: Possible to simplify this? Seems way too complicated. This is really crappy code.
    public static int pageChangeKind(WikiTextStorage localStorage, WikiTextChanges remoteChanges,
                                      String name) throws IOException {
        if ((!localStorage.hasLocalChange(name)) &&
            (!remoteChanges.hasChange(name))) {
            //System.err.println("BC0");
            if (localStorage.hasPage(name)) {
                return PARENT;
            } else {
                return LOCALLY_MODIFIED; // Handle new pages.
            }
        }

        String remote = null;
        if (remoteChanges.hasChange(name) &&
            !remoteChanges.wasDeleted(name)) {
            remote = remoteChanges.getPage(name);
        }

        String local = null;
        if (localStorage.hasLocalChange(name) &&
            !localStorage.wasLocallyDeleted(name)) {
            local = localStorage.getPage(name);
        }

        if (local == null && remote == null) {
            // Hmmmm... could get rid of this case (remove assertion and handle below).
            // Leave it for now. This is already hard enough to understand.
            if (localStorage.hasLocalChange(name) &&
                remoteChanges.hasChange(name)) {
                assert_(localStorage.wasLocallyDeleted(name));
                assert_(remoteChanges.wasDeleted(name));
                //System.err.println("BC1");
                return REBASE; // Deleted in both. Is remote.
            }

            // Was deleted in one but didn't exist in the other.
            if (localStorage.hasLocalChange(name)) {
                assert_(localStorage.wasLocallyDeleted(name));
                assert_(!remoteChanges.hasChange(name));
                //System.err.println("BC2");
                return LOCALLY_MODIFIED; // Locally deleted
            }
            if (remoteChanges.hasChange(name)) {
                assert_(remoteChanges.wasDeleted(name));
                assert_(!localStorage.hasLocalChange(name));
                assert_(localStorage.hasUnmodifiedPage(name));
                //System.err.println("BC3");
                return PARENT; // Deleted in remote
            }
            assert_(false);
        }

        if (local != null && remote != null) {
            // Has changes in both, and deleted in neither.
            if (local.equals(remote)) {
                //System.err.println("BC4");
                return REBASE;
            } else {
                //System.err.println("BC5");
                return LOCALLY_MODIFIED;
            }
        }

        if (local != null) {
            // Has local, non-delete changes.
            // Either it was deleted in the remote, or didn't exist in the remote.
            //System.err.println("BC6");
            return LOCALLY_MODIFIED;
        }

        if (remote != null) {
            // Has remote, non-delete changes
            // Either it was deleted in the local, or didn't exist in the local.
            if (localStorage.hasLocalChange(name)) {
                //System.err.println("BC7");
                return LOCALLY_MODIFIED;
            } else {
                //System.err.println("BC8");
                return PARENT;
            }
        }
        assert_(false);
        return -1; // unreachable.
    }
}
