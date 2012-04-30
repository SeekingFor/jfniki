/* Helper class to store jfniki app state between invocations.
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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import wormarc.IOUtil;

public class PersistedState implements Serializable {
    // GOTCHA:
    // Be mindful of serialization when changing this class
    // or any of the classes it depends on to implement
    // serialization.
    static final long serialVersionUID = -5763661342186848473L;
    public final static int VERSION_0 = 0;
    public final int mFormatVersion = VERSION_0;

    public final Configuration mConfiguration;

    public final Map<String, WikiInfo> mWikiInfos;
    public final String mWikiName;
    public final String mWikiGroup;

    public final Map<String, SiteTheme> mThemes;
    public final String mCurrentTheme;

    // Used for unmarshaling.
    public PersistedState() {
        mConfiguration = null;
        mWikiInfos = null;
        mWikiName = null;
        mWikiGroup = null;
        mThemes = null;
        mCurrentTheme = null;
    }

    // Does not attempt to save rebase version
    public PersistedState(Configuration configuration,
                          Map<String, WikiInfo> wikiInfos,
                          String wikiName,
                          String wikiGroup,
                          Map<String, SiteTheme> themes,
                          String currentTheme) {

        mConfiguration = configuration;
        mWikiInfos = wikiInfos;
        mWikiName = wikiName;
        mWikiGroup = wikiGroup;
        mThemes = themes;
        mCurrentTheme = currentTheme;
    }

    public void debugDump(String msg) {
        System.err.println("--- PersistedState: " + msg + " ---");
        System.err.println("mConfiguration: ");
        System.err.println(mConfiguration.toString());
        System.err.println("mWikiInfo: ...");
        for (Map.Entry<String, WikiInfo> infoEntry : mWikiInfos.entrySet()) {
            infoEntry.getValue().debugDump(infoEntry.getKey());
        }
        System.err.println("");
        System.err.println("mWikiName: " + mWikiName);
        System.err.println("mWikiGroup:" + mWikiGroup);
        System.err.println("mThemes: ...");
        for (Map.Entry<String, SiteTheme> themeEntry : mThemes.entrySet()) {
            themeEntry.getValue().debugDump(themeEntry.getKey());
        }
        System.err.println("---");
    }

    // All length values are int's.
    public byte[] marshal() throws IOException {
        // Build entire persisted rep in RAM. Good enough.  Not great.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream objOut = new ObjectOutputStream(buffer);
        objOut.writeObject(this);
        objOut.close();
        return buffer.toByteArray();
    }

    // INTENT: Use with a default constructed exemplar.
    // I did this instead of making unmarshal static
    // in order to allow clean subclassing.
    public PersistedState unmarshal(byte[] raw) throws IOException {
        if (raw == null || raw.length == 0) {
            // Should not happen.
            throw new IOException("No data to unmarshal from!");
        }
        try {
            ObjectInputStream objIn = new ObjectInputStream(new ByteArrayInputStream(raw));
            PersistedState depersisted = (PersistedState)objIn.readObject();
            objIn.close();
            return depersisted;
        } catch (ClassNotFoundException cnfe) {
            throw new IOException(cnfe);
        }
    }
}
