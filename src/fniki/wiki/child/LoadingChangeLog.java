/* A UI subcomponent to load change history for the current wiki version from Freenet.
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

import static ys.wikiparser.Utils.*;

import wormarc.ExternalRefs;
import wormarc.FileManifest;
import wormarc.AuditArchive;

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import static fniki.wiki.HtmlUtils.*;
import fniki.wiki.WikiContext;

public class LoadingChangeLog extends AsyncTaskContainer
    implements AuditArchive.ChangeLogCallback {
    private StringBuilder mListHtml = new StringBuilder();
    private String mPath;
    private String mContainerPrefix;
    public LoadingChangeLog(ArchiveManager archiveManager) {
        super(archiveManager);
    }

    public synchronized String getListHtml() {
        return mListHtml.toString();
    }

    public String getHtml(WikiContext context) throws ChildContainerException {
        try {
            if (context.getAction().equals("confirm")) {
                mPath = context.getPath();
                mContainerPrefix = context.getString("container_prefix", null);
                if (mContainerPrefix == null) {
                    throw new RuntimeException("Assertion Failure: mContainerPrefix == null");
                }
                startTask();
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
                title = "Loading Change Log";
                cancelTitle = "Cancel";
                break;
            case STATE_WAITING:
                // Shouldn't hit this state.
                showBuffer = false;
                title = "Loading Change Log";
                confirmTitle = "Load";
                cancelTitle = "Cancel";
                break;
            case STATE_SUCCEEDED:
                showBuffer = true;
                title = "Read Full Change Log";
                confirmTitle = null;
                cancelTitle = "Done";
                break;
            case STATE_FAILED:
                showBuffer = true;
                title = "Full Read of Change Log Failed";
                confirmTitle = "Reload";
                cancelTitle = "Done";
                break;
            }

            setTitle(title);

            StringWriter buffer = new StringWriter();
            PrintWriter body = new PrintWriter(buffer);

            body.println("<h3>" + escapeHTML(title) + "</h3>");
            if (showBuffer) {
                body.println(getListHtml());
                body.println("<hr>");
                body.println("<pre>");
                body.print(escapeHTML(getOutput()));
                body.println("</pre>");
            }
            body.println("<hr>");

            addButtonsHtml(context, body, confirmTitle, cancelTitle);
            body.close();
            return buffer.toString();
        } catch (IOException ioe) {
            context.logError("Submitting", ioe);
            context.raiseServerError("LoadingChangeLog.handle coding error. Sorry :-(");
            return "unreachable code";
        }
    }

    public boolean doWork(PrintStream out) throws Exception {
        synchronized (this) {
            mListHtml = new StringBuilder();
        }

        try {
            out.println("Reading the wiki changelog out of freenet. ");
            mArchiveManager.readChangeLog(out, this);
            return true;
        } catch (IOException ioe) {
            out.println("Error reading log: " + ioe.getMessage());
            return false;
        }
    }

    public synchronized boolean onChangeEntry(ExternalRefs.Reference oldVer,
                                              ExternalRefs.Reference newVer,
                                              FileManifest.Changes fromNewToOld) {

        mListHtml.append("<br>");
        mListHtml.append(getShortVersionLink(mContainerPrefix, "/jfniki/changelog", oldVer.mExternalKey));
        mListHtml.append("<br>\n");
        mListHtml.append("FmsID: ");
        mListHtml.append(escapeHTML(mArchiveManager.getNym(oldVer.mExternalKey, true)));
        mListHtml.append("<br>\n");
        appendChangesHtml(fromNewToOld, mContainerPrefix, mListHtml);
        return true;
    }
}