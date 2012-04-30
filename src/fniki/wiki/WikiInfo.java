/* Class to hold information about a loaded version of a wiki.
 *
 * Copyright (C) 2010, 2011, 2012 Darrell Karbott
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

import java.io.Serializable;

import java.util.HashSet;
import java.util.Set;

// NOTE: WikiInfo's with the same name and group are "the same".
public class WikiInfo implements Serializable, Comparable<WikiInfo> {
    static final long serialVersionUID = -273633694486925920L;
    private final String mName;
    private final String mGroup;
    private String mUri;
    private Set<String> mKnownHeads = new HashSet<String>();

    public String getName() { return mName; }
    public String getGroup() { return mGroup; }

    public String getUri() { return mUri; }
    public void setUri(String value) { mUri = value; }
    public Set<String> getKnownHeads() { return mKnownHeads; }
    public void setKnownHeads(Set<String> value) { mKnownHeads = value; }

    public WikiInfo(String name,
                    String group,
                    String uri,
                    Set<String> knownHeads) {
        mName = name;
        mGroup = group;
        mUri = uri;
        mKnownHeads = knownHeads;
    }

    // Delegate comparison and equality.
    public int compareTo(WikiInfo other) { return (mName + mGroup).compareTo(other.mName + mGroup); }
    public boolean equals(Object other) {
        if (other == null) { return false; }
        WikiInfo info = (WikiInfo)other;
        return (mName + mGroup).equals(info.mName + info.mGroup);
    }
    public int hashCode() { return (mName + mGroup).hashCode(); }

    public void debugDump(String msg) {
        System.err.println("--- WikiInfo: " + msg + " ---");
        System.err.println("mName: " + mName);
        System.err.println("mGroup: " + mGroup);
        System.err.println("mUri: " + mUri);
        System.err.println("mKnownHeads:");
        for (String head : mKnownHeads) {
            System.err.println("   " + head);
        }
        System.err.println("---");
    }
}
