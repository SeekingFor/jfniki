/* A UI subcomponent to load a list of other versions of this wiki via FMS.
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

package fniki.wiki.child;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.List;

import static ys.wikiparser.Utils.*;

import fmsutil.FMSUtil;
import wormarc.ExternalRefs;
import wormarc.FileManifest;


import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import static fniki.wiki.HtmlUtils.*;
import fniki.wiki.WikiContext;

public class LoadingVersionList extends AsyncTaskContainer {
    private StringBuilder mListHtml = new StringBuilder();
    private String mName = "";
    private String mContainerPrefix;
    public LoadingVersionList(ArchiveManager archiveManager) {
        super(archiveManager);
    }

    public synchronized String getListHtml() {
        return mListHtml.toString();
    }

    public String handle(WikiContext context) throws ChildContainerException {
        try {
            if (context.getAction().equals("confirm")) {
                // Copy stuff we need out because context isn't threadsafe.
                mName = context.getPath();
                mContainerPrefix = context.getString("container_prefix", null);
                if (mContainerPrefix == null) {
                    throw new RuntimeException("Assertion Failure: mContainerPrefix == null");
                }
                startTask();
                try {
                    Thread.sleep(1000); // Hack. Give task thread a chance to finish.
                } catch (InterruptedException ioe) {
                    /* NOP */
                }
                sendRedirect(context, context.getPath());
                return "unreachable code";
            }

            boolean showBuffer = false;
            String confirmTitle = null;
            String cancelTitle = null;
            String title = null;
            switch (getState()) {
            case STATE_WORKING:
                showBuffer = true;
                title = "Loading Wiki Version Info from FMS";
                cancelTitle = "Cancel";
                break;
            case STATE_WAITING:
                // Shouldn't hit this state.
                showBuffer = false;
                title = "Load Wiki Version Info from FMS";
                confirmTitle = "Load";
                cancelTitle = "Cancel";
                break;
            case STATE_SUCCEEDED:
                showBuffer = true;
                title = "Loaded Wiki Version Info from FMS";
                confirmTitle = null;
                cancelTitle = "Done";
                break;
            case STATE_FAILED:
                showBuffer = true;
                title = "Full Read of Wiki Version Info Failed";
                confirmTitle = "Reload";
                cancelTitle = "Done";
                break;
            }

            StringWriter buffer = new StringWriter();
            PrintWriter body = new PrintWriter(buffer);
            body.println("<html><head>\n");
            body.println(metaRefresh());
            body.println("<style type=\"text/css\">\n");
            body.println("TD{font-family: Arial; font-size: 7pt;}\n");
            body.println("</style>\n");
            body.println("<title>" + escapeHTML(title) + "</title>\n");
            body.println("</head><body>\n");

            body.println("<h3>" + escapeHTML(title) + "</h3>");
            body.println(String.format("wikiname:%s<br>FMS group:%s<p>",
                                    escapeHTML(context.getString("wikiname", "NOT_SET")),
                                    escapeHTML(context.getString("fms_group", "NOT_SET"))));

            if (showBuffer) {
                body.println(getListHtml());
                body.println("<hr>");
                body.println("<pre>");
                body.print(escapeHTML(getOutput()));
                body.println("</pre>");
            }
            body.println("<hr>");
            addButtonsHtml(context, body, confirmTitle, cancelTitle);
            body.println("</body></html>");
            body.close();
            return buffer.toString();
        } catch (IOException ioe) {
            context.logError("LoadingVersionList", ioe);
            return "Error LoadingVersionList";
        }
    }

    // Doesn't need escaping.
    public static String trustString(int value) {
        if (value == -1) {
            return "null";
        }
        return Integer.toString(value);
    }

    public boolean doWork(PrintStream out) throws Exception {
        synchronized (this) {
            mListHtml = new StringBuilder();
        }
        try {
            out.println("Reading versions from FMS.");
            List<FMSUtil.BISSRecord> records = mArchiveManager.getRecentWikiVersions(out);

            synchronized (this) {

                mListHtml.append("<table border=\"1\">\n");
                mListHtml.append("<tr><td>FMS ID</td><td>Date</td><td>Key</td><td>Msg Trust</td><td>TL Trust</td>" +
                                 "<td>Peer Msg Trust</td><td>Peer TL Trust</td></tr>\n");

                final String fmt = "<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>" +
                "<td>%s</td><td>%s</td></tr>\n";

                // DCI: BUG. fix to force finished
                for (FMSUtil.BISSRecord record : records)  {
                    mListHtml.append(String.format(fmt,
                                                   escapeHTML(record.mFmsId),
                                                   escapeHTML(record.mDate),
                                                   getVersionLink(mContainerPrefix,
                                                                  "/jfniki/loadarchive", record.mKey),
                                                   trustString(record.msgTrust()),
                                                   trustString(record.trustListTrust()),
                                                   trustString(record.peerMsgTrust()),
                                                   trustString(record.peerTrustListTrust())));
                }
                mListHtml.append("</table>\n");
            }
            return true;
        } catch (IOException ioe) {
            out.println("Error reading log: " + ioe.getMessage());
            return false;
        }
    }
}