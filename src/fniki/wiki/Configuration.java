/* Configuration settings for the WikiApp
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

public final class Configuration {
    public final static class ConfigurationException extends IllegalArgumentException {
        protected ConfigurationException(String msg) {
            super(msg);
        }
    }

    public final int mListenPort;
    public final String mFcpHost;
    public final int mFcpPort;
    public final String mFproxyPrefix;
    public final boolean mAllowImages;

    public final String mFmsHost;
    public final int mFmsPort;
    public final String mFmsId;
    public final String mFmsSsk;
    public final String mFmsGroup;

    public final String mWikiName;

    public Configuration(int listenPort,
                         String fcpHost,
                         int fcpPort,
                         String fproxyPrefix,
                         boolean allowImages,
                         String fmsHost,
                         int fmsPort,
                         String fmsId,
                         String fmsSsk,
                         String fmsGroup,
                         String wikiName) {
        mListenPort = listenPort;
        mFcpHost = noNulls(fcpHost);
        mFcpPort = fcpPort;
        mFproxyPrefix = noNulls(fproxyPrefix);
        mAllowImages = allowImages;
        mFmsHost = noNulls(fmsHost);
        mFmsPort = fmsPort;
        mFmsId = noNulls(fmsId);
        mFmsSsk = noNulls(fmsSsk);
        mFmsGroup = noNulls(fmsGroup);
        mWikiName = noNulls(wikiName);
    }

    private static String noNulls(String text) {
        if (text == null) {
            return "";
        }
        return text;
    }

    private static void checkSet(String value, String field) throws ConfigurationException {
        if (value == null || value.trim().length() == 0) {
            throw new ConfigurationException(String.format("%s is not set.", field));
        }
    }

    private static void checkSet(int value, String field) throws ConfigurationException {
        if (value <= 0) {
            throw new ConfigurationException(String.format("%s is not set.", field));
        }
    }

    public void validate() throws ConfigurationException {
        // LATER: Do better. Unlocalized strings which show up in the UI.
        checkSet(mListenPort, "Listen Port");
        checkSet(mFcpHost, "FCP Host");
        checkSet(mFcpPort, "FCP Port");
        checkSet(mFproxyPrefix, "Fproxy Prefix");
        //mAllowImages,
        checkSet(mFmsHost, "FMS Host");
        checkSet(mFmsPort, "FMS Port");
        checkSet(mFmsId, "FMS Id");
        checkSet(mFmsSsk, "FMS Private SSK");
        checkSet(mFmsGroup, "FMS Group");
        checkSet(mWikiName, "Wiki Name");

        if (!Validations.isValidPrivateSsk(mFmsSsk)) {
            throw new ConfigurationException("The private SSK value must start with 'SSK@' " +
                                             "and end with ',AQECAAE/'.");
        }
        if (mFmsId.indexOf("@") != -1 && mFmsId.indexOf(".freetalk") == -1) {
            throw new ConfigurationException("FMS Id Should only include the part before the '@'.");
        }
        if (!mFproxyPrefix.startsWith("http") || !mFproxyPrefix.endsWith("/")) {
            throw new ConfigurationException("The fproxy prefix must start with 'http' and end with '/'.");
        }

        fromStringRep(toStringRep()); // traps '\n's in values.
    }

    public String toStringRep() throws ConfigurationException {
        return String.format("%d\n%s\n%d\n%s\n%d\n%s\n%d\n%s\n%s\n%s\n%s\n",
                             mListenPort,
                             mFcpHost,
                             mFcpPort,
                             mFproxyPrefix,
                             mAllowImages ? 1 : 0,
                             mFmsHost,
                             mFmsPort,
                             mFmsId,
                             mFmsSsk,
                             mFmsGroup,
                             mWikiName);
    }

    // Doesn't validate.
    public static Configuration fromStringRep(String text) {
        String[] fields = text.split("\n");
        if (fields.length != 11) {
            throw new ConfigurationException("Couldn't parse configuration.");
        }
        int listenPort = -1;
        try { listenPort = Integer.parseInt(fields[0]); } catch (NumberFormatException nfe) { /*NOP*/ }
        String fcpHost = fields[1];
        int fcpPort = -1;
        try { fcpPort = Integer.parseInt(fields[2]); } catch (NumberFormatException nfe) { /*NOP*/ }
        String fproxyPrefix = fields[3];
        boolean allowImages = fields[4].equals("1");
        String fmsHost = fields[5];
        int fmsPort = -1;
        try { fmsPort = Integer.parseInt(fields[6]); } catch (NumberFormatException nfe) { /*NOP*/ }
        String fmsId = fields[7];
        String fmsSsk = fields[8];
        String fmsGroup = fields[9];
        String wikiName = fields[10];

        Configuration config = new Configuration(listenPort,
                                                 fcpHost,
                                                 fcpPort,
                                                 fproxyPrefix,
                                                 allowImages,
                                                 fmsHost,
                                                 fmsPort,
                                                 fmsId,
                                                 fmsSsk,
                                                 fmsGroup,
                                                 wikiName);
        return config;
    }

    public String toString() { return toStringRep().replace("\n", "|"); }
}
