/* A base class for modal UI states which run background tasks.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import ys.wikiparser.Utils;

import fniki.wiki.ArchiveManager;
import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.ChildContainerResult;
import fniki.wiki.HtmlResultFactory;
import static fniki.wiki.HtmlUtils.*;
import fniki.wiki.ModalContainer;
import fniki.wiki.RedirectException;
import fniki.wiki.WikiContext;

public abstract class AsyncTaskContainer implements ChildContainer, ModalContainer {
    final protected ArchiveManager mArchiveManager;

    protected final int STATE_WAITING = 1;
    protected final int STATE_WORKING = 2;
    protected final int STATE_SUCCEEDED = 3;
    protected final int STATE_FAILED = 4;

    private String mTitle;

    private int mState;
    private Thread mThread;
    protected ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();
    protected boolean mUIRunning = false;
    protected String mExitPage = "/";

    // IMPORTANT: See hacks in WikiContentFilter.EXCEPTIONS if this stops working.
    // 15 second refresh if the task isn't finished.
    protected int getMetaRefreshSeconds() {
        if (isFinished()) {
            return 0;
        }
        return 15;
    }

    // DCI: make these return a string? To get rid of no return value warnings
    protected void sendRedirect(WikiContext context, String toName) throws RedirectException {
        sendRedirect(context, toName, null);
    }

    protected void sendRedirect(WikiContext context, String toName, String action) throws RedirectException {
        String target = toName;
        if (action != null) {
            target += "?action=" + action;
        }

        context.raiseRedirect(context.makeLink("/" + target), "Redirecting...");
    }

    // DCI: use a single form? Really ugly.
    protected void addButtonsHtml(WikiContext context, PrintWriter writer,
                                  String confirmTitle, String cancelTitle) {
        if (confirmTitle != null) {
            writer.println(buttonHtml(context.makeLink("/" + context.getPath()), confirmTitle, "confirm"));
        }

        if (cancelTitle != null) {
            writer.println(buttonHtml(context.makeLink("/" + context.getPath()), cancelTitle, "finished"));
        }
    }

    protected synchronized String getOutput() throws UnsupportedEncodingException {
        return mBuffer.toString("UTF-8");
    }

    protected synchronized int getState() { return mState; }

    public AsyncTaskContainer(ArchiveManager archiveManager) {
        mArchiveManager = archiveManager;
        mState = STATE_WAITING;
    }

    public synchronized boolean isFinished() {
        return mState == STATE_SUCCEEDED ||
            mState == STATE_FAILED ||
            mState == STATE_WAITING;
    }

    public synchronized void cancel() {
        if (mThread == null) {
            return;
        }
        mThread.interrupt();
    }

    public void entered(WikiContext context) {
        mUIRunning = true;
        mExitPage = context.getTitle();
    }

    // Subclasses should reset state here.
    public void exited() {
        if (!isFinished()) {
            throw new IllegalStateException("Task didn't finish yet!");
        }
        mBuffer = new ByteArrayOutputStream();
        mExitPage = "/";
        mState = STATE_WAITING;
        mUIRunning = false;
    }

    public synchronized void startTask() {
        if (mThread != null) {
            return;
        }
        mState = STATE_WORKING;
        mBuffer = new ByteArrayOutputStream();
        mThread = new Thread( new Runnable() {
                public void run() {
                    invokeWorkerMethod();
                }
            });
        mThread.start();
    }

    protected void invokeWorkerMethod() {
        boolean failed = true;
        try {
            //System.err.println("Task started: " + mState);
           PrintStream log = new PrintStream(mBuffer, true);
            mArchiveManager.setDebugOutput(log);
            failed = !doWork(new PrintStream(mBuffer, true));
        } catch (Exception e) {
            e.printStackTrace();
            failed = true;
        } finally {
            synchronized (this) {
                mState = failed ? STATE_FAILED : STATE_SUCCEEDED;
                //System.err.println("Task finished: " + mState);
                mThread = null;
            }
        }
    }


    public String getTitle() { return mTitle; }
    public void setTitle(String value) { mTitle = value; }

    public ChildContainerResult handle(WikiContext context)
        throws ChildContainerException {

        // There is a harmless race condition.
        // Get the refresh time first, so that if the background
        // thread finishes in the meantime we still refresh.
        int refreshSeconds = getMetaRefreshSeconds();
        String html = getHtml(context);

        return HtmlResultFactory.makeResult(getTitle(), // Subclass must implement get title.
                                            getHtml(context),
                                            context.isCreatingOuterHtml(),
                                            refreshSeconds);
    }

    public abstract String getHtml(WikiContext context) throws ChildContainerException;
    public abstract boolean doWork(PrintStream out) throws Exception;
}