/* A UI subcomponent for loading a wiki version from a WORM Archive in Freenet.
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
import static fniki.wiki.HtmlUtils.*;

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.WikiContext;

public class LoadingArchive extends AsyncTaskContainer {
    private String mUri;
    private boolean mSecondary = false;
    public LoadingArchive(ArchiveManager archiveManager) {
        super(archiveManager);
    }

    public String getHtml(WikiContext context) throws ChildContainerException {
        try {
            if (context.getQuery().get("uri") != null && mUri == null) {
                mUri = context.getQuery().get("uri");
            }

            if (context.getQuery().get("secondary") != null) {
                mSecondary = context.getQuery().get("secondary").toLowerCase().equals("true");
            }

            if (context.getAction().equals("confirm")) {
                if (mUri != null) {
                    startTask();
                    sendRedirect(context, context.getPath());
                    return "Unreachable code";
                }
            }

            boolean showBuffer = false;
            boolean showUri = false;
            String confirmTitle = null;
            String cancelTitle = null;
            String title = null;
            switch (getState()) {
            case STATE_WORKING:
                showBuffer = true;
                title = "Loading Archive: " + mUri;;
                cancelTitle = "Cancel";
                break;
            case STATE_WAITING:
                showBuffer = false;
                showUri = true;
                title = "Confirm Load";
                confirmTitle = "Load";
                cancelTitle = "Cancel";
                break;
            case STATE_SUCCEEDED:
                showBuffer = true;
                title = "Archive Loaded: " + mUri;
                cancelTitle = "Done";
                break;
            case STATE_FAILED:
                showBuffer = true;
                title = "Archive Load Failed";
                confirmTitle = "Retry";
                cancelTitle = "Done";
                break;
            }

            setTitle(title);

            StringWriter buffer = new StringWriter();
            PrintWriter body = new PrintWriter(buffer);

            if (getState() == STATE_WORKING || getState() == STATE_SUCCEEDED) {
                if (getState() == STATE_WORKING) {
                    title = "Loading Archive:"; // Don't put full uri in header
                } else {
                    title = "Loaded Archive.";
                }
            }
            body.println("<h3>" + escapeHTML(title) + "</h3>");
            if (showUri) {
                String secondary = mSecondary ? "Secondary Archive " : "";
                body.println(escapeHTML(String.format("Load %sVersion: %s", secondary, getVersionHex(mUri))));
                body.println("<br>");
                body.println(escapeHTML("from: " + mUri));
                body.println("<p>Clicking Load will discard any unsubmitted local changes.</p>");
            } else if (getState() == STATE_WORKING || getState() == STATE_SUCCEEDED) {
                body.println(escapeHTML(mUri));
            }

            if (showBuffer) {
                body.println("<pre>");
                body.print(escapeHTML(getOutput()));
                body.println("</pre>");
            }

            addButtonsHtml(context, body, confirmTitle, cancelTitle);

            body.close();
            return buffer.toString();

        } catch (IOException ioe) {
            context.logError("Loading Archive", ioe);
            context.raiseServerError("LoadingArchive.handle coding error. Sorry :-(");
            return "unreachable code";
        }
    }

    public boolean doWork(PrintStream out) throws Exception {
        if (mUri == null) {
            out.println("The request uri isn't set");
            return false;
        }

        try {
            String msg = "";
            if (mSecondary) { msg = " secondary archive."; }
            out.println(String.format("Loading%s. Please be patient...", msg));
            mArchiveManager.load(mUri, mSecondary);
            out.println("Loaded " + mUri);
            return true;
        } catch (IOException ioe) {
            out.println("Load failed from background thread: " + ioe.getMessage());
            return false;
        }
    }

    public void entered(WikiContext context) {
        mUri = null;
        mSecondary = false;
    }

}