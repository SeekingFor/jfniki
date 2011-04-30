/* A helper class to read and write archive change notifications to FMS.
 *
 *  Copyright (C) 2010, 2011 Darrell Karbott
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.0 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 *  Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package fmsutil;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.ConsoleHandler;

import java.util.Map;

import gnu.inet.nntp.ArticleResponse;
import gnu.inet.nntp.GroupResponse;
import gnu.inet.nntp.Overview;
import gnu.inet.nntp.OverviewIterator;
import gnu.inet.nntp.Range;

public class FMSUtil {
    private final static String UTF8 = "utf8";

    // Because I Say So name resolution record.
    public final static class BISSRecord {
        public final String mFmsId;
        public final String mDate;
        public final String mKey;
        final int[] mTrust;
        public BISSRecord(String fmsId, String date,
                          String key,
                          int[] trust) {
            mFmsId = fmsId;
            mDate = date;
            mKey = key;
            if (trust.length != 4) {
                throw new IllegalArgumentException("trust.length != 4");
            }
            mTrust = trust;
        }
        public int msgTrust() { return mTrust[0]; }
        public int trustListTrust() { return mTrust[1]; }
        public int peerMsgTrust() { return mTrust[2]; }
        public int peerTrustListTrust() { return mTrust[3]; }

        public String toString() {
            return String.format("{mFmsId=%s, mDate=%s, mTrust=%s, mKey=%s}",
                                 mFmsId,
                                 mDate,
                                 String.format("{%d, %d, %d, %d}",
                                               mTrust[0], mTrust[1],
                                               mTrust[2], mTrust[3]),
                                 mKey);
        }
    }

    // Set true to data written to / read from the nntp server to stdout.
    public static boolean sNNTPDebugging = false;

    public final static String SUBJECT_PREFIX = "BISS|";

    private static boolean isValidKey(String key) {
        return key.startsWith("SSK@"); // DCI: do much better.
    }

    private static void debug(String text) {
        if (!sNNTPDebugging) { return; }
        System.err.println("FMSUtil: " + text);
    }

    // Read the first non-header line of an NNTP article.
    private static String getFirstLine(FMSConnection connection, int articleId) throws IOException {
        debug("Trying to read article: " + articleId);
        ArticleResponse response = connection.article(articleId);

        // MUST be on the first line of the message body.
        LineNumberReader reader =
            new LineNumberReader(new InputStreamReader(response.in, UTF8));
        String line = null;
        String firstLine = null;
        boolean sawHeaderSeparator = false;
        for (;;) {
            line = reader.readLine();
            if (line == null) {
                return null;
            }
            if (line.trim().equals("") && firstLine == null) {
                sawHeaderSeparator = true;
                continue;
            }
            if (!sawHeaderSeparator) {
                continue;
            }
            firstLine = line;
            break;
        }
        debug("GOT LINE: " + firstLine);
        while (reader.readLine() != null) { /* Must consume all data */ }
        return firstLine;
    }

    private static int[] readTrusts(FMSConnection connection, String fmsId)
        throws IOException {

        int[] values = new int[4];
        final int kinds[] = new int[] {FMSConnection.MESSAGE,
                                       FMSConnection.TRUSTLIST,
                                       FMSConnection.PEERMESSAGE,
                                       FMSConnection.PEERTRUSTLIST};
        int index = 0;
        for (int kind : kinds) {
            values[index] = connection.xgettrust(kinds[index], fmsId);
            index++;
        }
        return values;
    }

    private static int[] getTrusts(FMSConnection connection, Map<String, int[]> cache, String fmsId)
        throws IOException {
        int[] values = cache.get(fmsId);
        if (values == null) {
            values = readTrusts(connection, fmsId);
            cache.put(fmsId, values);
        }
        return values;
    }

    final static class XOverInfo {
        final int mNumber;
        final String mFmsId;
        final String mDate;
        public XOverInfo(int number, String fmsId, String date) {
            mNumber = number;
            mFmsId = fmsId;
            mDate = date;
        }
    }

    // LATER: Cleanup. Seems like there's too much code here, give how little this does.

    // LATER: other kinds of records. STAKE. SANCTION
    // Requires user because it looks up trust.
    public static List<BISSRecord> getBISSRecords(String host, int port, String user,
                                                  String group, String nameToResolve,
                                                  int maxArticles)
        throws IOException {

        FMSConnection connection = makeConnection(host, port, user);

        Map<String, int[]> trustCache = new HashMap<String, int[]> ();
        try {
            GroupResponse groupInfo = connection.group(group);

            long  highArticleNumber = groupInfo.last;
            long lowArticleNumber = groupInfo.first - maxArticles;

            if (lowArticleNumber < groupInfo.first) {
                lowArticleNumber = groupInfo.first;
            }

            if (lowArticleNumber < 0) {
                lowArticleNumber = 0;
            }

            final long first = lowArticleNumber;
            final long last = highArticleNumber;

            OverviewIterator overviews = connection.xover(new Range() {
                    // Am I missing something? This seems wacky.
                    public  boolean contains(int num) {
                        return num >= first && num <= last;
                    }
                    public String toString() {
                        return String.format("%d-%d", first, last);
                    }
                });

            // MUST fully read xover returned data before new commands?
            List<XOverInfo> xoverInfos = new ArrayList<XOverInfo>();
            while(overviews.hasNext()) {
                Overview overview = (Overview)(overviews.next());
                for (int index = 0; index < 6; index++) {
                    debug(String.format("%d: [%s]", index, overview.getHeader(index).toString()));
                }
                if (((String)overview.getHeader(4)).length() > 0) {
                    continue; // Skip replies.
                }
                if (!((String)(overview.getHeader(0))).equals(SUBJECT_PREFIX + nameToResolve)) {
                    // Skip articles that don't match BISS|<name>.
                    continue;
                }
                xoverInfos.add(new XOverInfo(overview.getArticleNumber(),
                                             (String)overview.getHeader(1),
                                             (String)overview.getHeader(2)));
            }

            List<BISSRecord> records = new ArrayList<BISSRecord>();

            // Iterate over list in reverse so most recent records are at start of list.
            ListIterator<XOverInfo> iter = xoverInfos.listIterator(xoverInfos.size());
            while (iter.hasPrevious()) {
                XOverInfo info = iter.previous();
                String bissLine = getFirstLine(connection, info.mNumber);
                if (bissLine == null) {
                    continue;
                }

                if (!bissLine.startsWith(nameToResolve + "|") ||
                    bissLine.length() <= nameToResolve.length() + 1) {
                    continue; // Expected a BISS name -> key mapping entry. Give up.
                }

                String key = bissLine.substring(nameToResolve.length() + 1);

                if (!isValidKey(key)) {
                    continue;
                }

                int trusts[] = getTrusts(connection, trustCache, info.mFmsId);

                records.add(new BISSRecord(info.mFmsId, info.mDate, key, trusts));
            }

            return records;
        } finally {
            try {
                connection.quit();
            } catch (IOException ioe) {
                // Shouldn't happen.
                System.err.println("connection.quit() raised: " + ioe.getMessage());
            }
        }
    }

    private static FMSConnection makeConnection(String host, int port, String user)
        throws IOException {

        if (sNNTPDebugging) {
            Logger.getLogger("gnu.inet.nntp").setLevel(FMSConnection.NNTP_TRACE);
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.FINEST);
            Logger.getLogger("gnu.inet.nntp").addHandler(handler);
        }

        FMSConnection connection = new FMSConnection(host, port);

        if (!connection.authinfo(user, "")) {
            throw new IOException("Couldn't authenticate as: " + user);
        }
        return connection;
    }

    private final static String MSG_TEMPLATE =
        "From: %s\n" +
        "Newsgroups: %s\n" +
        "Subject: %s\n" +
        "\n" +
        "%s";

    public static void sendBISSMsg(String host, int port, String user, String group,
                                   String name, String value) throws IOException {
        FMSConnection connection = makeConnection(host, port, user);
        try {
            OutputStream out = connection.post();
            try {
                String msg = String.format(MSG_TEMPLATE, user, group, SUBJECT_PREFIX + name,
                                           String.format("%s|%s", name, value));
                byte[] rawBytes = msg.getBytes(UTF8);
                debug("Writing post bytes: " + rawBytes.length);
                out.write(rawBytes);
                out.flush();
            } finally {
                out.close();
            }
        } finally {
            connection.quit();
        }
    }
}