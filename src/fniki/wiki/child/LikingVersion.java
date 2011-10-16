/* A UI subcomponent for posting an NNTP message to 'like' a version.
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

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.ChildContainerResult;

import static fniki.wiki.HtmlUtils.*;
import fniki.wiki.WikiContext;

public class LikingVersion extends AsyncTaskContainer {
    public LikingVersion(ArchiveManager archiveManager) {
        super(archiveManager);
    }

    public String getHtml(WikiContext context) throws ChildContainerException {
        try {
            if (context.getAction().equals("confirm")) {
                startTask();
                sendRedirect(context, context.getPath());
                return "Unreachable code";
            }

            boolean showBuffer = false;
            String confirmTitle = null;
            String cancelTitle = null;
            String title = null;
            switch (getState()) {
            case STATE_WORKING:
                showBuffer = true;
                title = "Posting 'Like' message to NNTP...";
                cancelTitle = "Cancel";
                break;
            case STATE_WAITING:
                showBuffer = false;
                title = "Confirm send of 'Like' message.";
                confirmTitle = "Send";
                cancelTitle = "Cancel";
                break;
            case STATE_SUCCEEDED:
                showBuffer = true;
                title = "Send Succeeded";
                cancelTitle = "Done";
                break;
            case STATE_FAILED:
                showBuffer = true;
                title = "Send Failed";
                confirmTitle = "Retry";
                cancelTitle = "Done";
                break;
            }

            setTitle(title);

            StringWriter buffer = new StringWriter();
            PrintWriter body = new PrintWriter(buffer);
            body.println("<h3>" + escapeHTML(title) + "</h3>");
            if (showBuffer) {
                body.println("<pre>");
                body.print(escapeHTML(getOutput()));
                body.println("</pre>");
            }
            addButtonsHtml(context, body, confirmTitle, cancelTitle);
            body.close();
            return buffer.toString();
        } catch (IOException ioe) {
            context.logError("LikingVerson", ioe);
            return "Error LikingVersion.";
        }
    }

    public boolean doWork(PrintStream out) throws Exception {
        try {
            mArchiveManager.stakeCurrentVersion(out);
            out.println("");
            out.println("Please be patient, it may take up to a day for your notification to appear.");
           return true;
        } catch (IOException ioe) {
            out.println("Send failed from background thread: " + ioe.getMessage());
            return false;
        }
    }
}