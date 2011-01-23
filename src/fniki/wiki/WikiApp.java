/* A web application to read, edit and submit changes to a jfniki wiki in Freenet.
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

import static ys.wikiparser.Utils.*; // DCI: clean up

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import wormarc.FileManifest;

import static fniki.wiki.HtmlUtils.*;

import fniki.wiki.child.AsyncTaskContainer;
import fniki.wiki.child.DefaultRedirect;
import fniki.wiki.child.GotoRedirect;
import fniki.wiki.child.LoadingArchive;
import fniki.wiki.child.LoadingChangeLog;
import fniki.wiki.child.LoadingVersionList;
import fniki.wiki.child.QueryError;
import fniki.wiki.child.Submitting;
import fniki.wiki.child.WikiContainer;

// Aggregates a bunch of other Containers and runs UI state machine.
public class WikiApp implements ChildContainer, WikiContext {
    // Delegate to implement link, image and macro handling in wikitext.
    private final FreenetWikiTextParser.ParserDelegate mParserDelegate;

    private final ChildContainer mDefaultRedirect;
    private final ChildContainer mGotoRedirect;
    private final ChildContainer mQueryError;
    private final ChildContainer mWikiContainer;

    // Containers for asynchronous tasks.
    private final ChildContainer mLoadingVersionList;
    private final ChildContainer mLoadingArchive;
    private final ChildContainer mSubmitting;
    private final ChildContainer mLoadingChangeLog;

    // The current default UI state.
    private ChildContainer mState;

    // Transient, per request state.
    private Request mRequest;

    private ArchiveManager mArchiveManager;

    private String mFproxyPrefix = "http://127.0.0.1:8888/";
    private boolean mAllowImages = true;
    private String mFormPassword;
    private boolean mUseMultiPartForms;

    public WikiApp(ArchiveManager archiveManager) {
        mParserDelegate = new LocalParserDelegate(this, archiveManager);

        mDefaultRedirect = new DefaultRedirect();
        mGotoRedirect = new GotoRedirect();
        mQueryError = new QueryError();
        mWikiContainer = new WikiContainer();

        mLoadingVersionList = new LoadingVersionList(archiveManager);
        mLoadingArchive = new LoadingArchive(archiveManager);
        mSubmitting = new Submitting(archiveManager);
        mLoadingChangeLog = new LoadingChangeLog(archiveManager);

        mState = mWikiContainer;
        mArchiveManager = archiveManager;
    }

    public void setFproxyPrefix(String value) {
        if (!value.startsWith("http") && value.endsWith("/")) {
            throw new IllegalArgumentException("Expected a value starting with 'http' and ending with '/'");
        }
        mFproxyPrefix = value;
    }

    public void setAllowImages(boolean value) {
        mAllowImages = value;
    }

    public void setFormPassword(String value) {
        mFormPassword = value;
    }

    public void setUseMultiPartForms(boolean value) {
        mUseMultiPartForms = value;
    }

    private ChildContainer setState(WikiContext context, ChildContainer container) {
        if (mState == container) {
            return mState;
        }

        System.err.println(String.format("[%s] => [%s]",
                                         mState.getClass().getName(),
                                         container.getClass().getName()));
        if (mState != null && mState instanceof ModalContainer) {
            ((ModalContainer)mState).exited();
        }

        mState = container;

        if (mState instanceof ModalContainer) {
            ((ModalContainer)mState).entered(context);
        }

        return mState;
    }

    // This function defines the UI state machine.
    private ChildContainer routeRequest(WikiContext request)
        throws IOException {

        // DCI: move this into the freenet plugin implementation
        // mPath = "";
        // mQuery = null;
        // mAction = "";
        // mTitle = "";

        // System.err.println("Method: " + request.getMethod());
        // String path = request.getPath();
        // System.err.println(String.format("Raw Path: [%s]", path));

        // String prefix = containerPrefix();
        // if (!path.startsWith(prefix)) {
        //     return mQueryError;
        // }

        // path = path.substring(prefix.length());
        // if (path.equals("")) {
        //     path = "/";
        // }

        // System.err.println(String.format("Local Path: [%s]", path));

        // int slashCount = 0;
        // for (int index = 0; index < path.length(); index++) {
        //     if (path.charAt(index) == '/') {
        //         slashCount++;
        //     }
        // }

        // if (!path.startsWith("/")) {
        //     System.err.println("Bad path!");
        //     return mQueryError;
        // }

        // path = path.substring(1);

        // Query query = new Query(request);
        // System.err.println("Query: " + query.toString());
        // String title = path;
        // if (query.containsKey("title")) {
        //     title = query.get("title");
        // }

        // // DCI: validate title here

        // String action = "view";
        // if (query.containsKey("action")) {
        //     action = query.get("action");
        // }

        // mPath = path;
        // mQuery = query;
        // mAction = action;
        // mTitle = title;

        // The glue code is repsonsible for parsing.
        // Fail immediately if there are problems in the glue code.
        if (request.getPath() == null) {
            throw new RuntimeException("Assertion Failure: path == null");
        }
        if (request.getQuery() == null) {
            throw new RuntimeException("Assertion Failure: query == null");
        }
        if (request.getAction() == null) {
            throw new RuntimeException("Assertion Failure: action == null");
        }
        if (request.getTitle() == null) {
            throw new RuntimeException("Assertion Failure: title == null");
        }

        String action = request.getAction();

        if (mState instanceof ModalContainer) {
            // Handle transitions out of modal UI states.
            ModalContainer state = (ModalContainer)mState;
            if (action.equals("finished")) {
                System.err.println("finished");
                if (!state.isFinished()) {
                    System.err.println("canceling");
                    state.cancel();
                    try {
                        Thread.sleep(250); // HACK
                    }
                    catch (InterruptedException ie) {
                    }
                }
                // No "else" because it might have finished while sleeping.
                if (state.isFinished()) {
                    System.err.println("finished");
                    setState(request, mWikiContainer);
                    return mGotoRedirect;
                }
            }
            return state;  // Don't leave the modal UI state until finished.
        }

        String path = request.getPath();
        int slashCount = 0;
        for (int index = 0; index < path.length(); index++) {
            if (path.charAt(index) == '/') {
                slashCount++;
            }
        }

        System.err.println("WikiApp.routeRequest: " + path);
        if (path.equals("fniki/submit")) {
            System.err.println("BC0");
            return setState(request, mSubmitting);
        } else if (path.equals("fniki/changelog")) {
            return setState(request, mLoadingChangeLog);
        } else if (path.equals("fniki/getversions")) {
            return setState(request, mLoadingVersionList);
        } else if (path.equals("fniki/loadarchive")) {
            return setState(request, mLoadingArchive);
        } else if (path.equals("")) {
            return mDefaultRedirect;
        } else if (slashCount != 0) {
            return mQueryError;
        } else {
            setState(request, mWikiContainer);
        }

        return mState;
    }

    // All requests are serialized! Hmmmm....
    public synchronized String handle(WikiContext context) throws ChildContainerException {
        try {
            ChildContainer childContainer = routeRequest(context);
            System.err.println("Request routed to: " + childContainer.getClass().getName());
            return childContainer.handle(context);
        } catch (ChildContainerException cce) {
            // Normal, used to do redirection.
            throw cce;
        } catch (Exception e) {
            context.logError("WikiApp.handle -- untrapped!:", e);
            throw new ServerErrorException("Coding error. Sorry :-(");
        }
    }

    ////////////////////////////////////////////////////////////
    private static class LocalParserDelegate implements FreenetWikiTextParser.ParserDelegate {
        // Pedantic.  Explictly copy references instead of making this class non-static
        // so that the code uses well defined interfaces.
        final WikiContext mContext;
        final ArchiveManager mArchiveManager;

        LocalParserDelegate(WikiContext context, ArchiveManager archiveManager) {
            mContext = context;
            mArchiveManager = archiveManager;
        }

        public boolean processedMacro(StringBuilder sb, String text) {
            if (text.equals("LocalChanges")) {
                try {
                    FileManifest.Changes changes  = mArchiveManager.getLocalChanges();
                    if (changes.isUnmodified()) {
                        sb.append("<br>No local changes.<br>");
                        return true;
                    }
                    appendChangesHtml(changes, containerPrefix(), sb);
                    return true;
                } catch (IOException ioe) {
                    sb.append("{ERROR PROCESSING LOCALCHANGES MACRO}");
                    return true;
                }
            } else if (text.equals("TitleIndex")) {
                try {
                    for (String name : mArchiveManager.getStorage().getNames()) {
                        appendPageLink(containerPrefix(), sb, name, null, true);
                        sb.append("<br>");
                    }
                } catch (IOException ioe) {
                    sb.append("{ERROR PROCESSING TITLEINDEX MACRO}");
                    return true;
                }

                return true;
            }

            return false;
        }

        // CHK, SSK, USK freenet links.
        public void appendLink(StringBuilder sb, String text) {
            String fproxyPrefix = mContext.getString("fproxy_prefix", null);

            String[] link=split(text, '|');
            if (fproxyPrefix != null &&
                isValidFreenetUri(link[0])) {
                sb.append("<a href=\""+ makeFproxyHref(fproxyPrefix, link[0].trim()) +"\" rel=\"nofollow\">");
                sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
                sb.append("</a>");
                return;
            }
            if (isValidLocalLink(link[0])) {
                // Link to an internal wiki page.
                sb.append("<a href=\""+ makeHref(mContext.makeLink("/" + link[0].trim())) +"\" rel=\"nofollow\">");
                sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
                sb.append("</a>");
                return;
            }

            sb.append("<a href=\"" + makeHref(mContext.makeLink("/ExternalLink")) +"\" rel=\"nofollow\">");
            sb.append(escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0])));
            sb.append("</a>");
        }

        // Only CHK and SSK freenet links.
        public void appendImage(StringBuilder sb, String text) {
            boolean allowed = mContext.getInt("allow_images", 0) != 0;
            if (!allowed) {
                sb.append("{IMAGES DISABLED. IMAGE WIKITEXT IGNORED}");
                return;
            }

            String fproxyPrefix = mContext.getString("fproxy_prefix", null);
            if (fproxyPrefix == null) {
                sb.append("{FPROXY PREFIX NOT SET. IMAGE WIKITEXT IGNORED}");
                return;
            }

            String[] link=split(text, '|');
            if (fproxyPrefix != null &&
                isValidFreenetUri(link[0]) &&
                !link[0].startsWith("freenet:USK@")) {
                String alt=escapeHTML(unescapeHTML(link.length>=2 && !isEmpty(link[1].trim())? link[1]:link[0]));
                sb.append("<img src=\"" + makeFproxyHref(fproxyPrefix, link[0].trim())
                          + "\" alt=\""+alt+"\" title=\""+alt+"\" />");
                return;
            }
            sb.append("{ERROR PROCESSING IMAGE WIKITEXT}");;
        }
    }

    // NO trailing slash.
    private static String containerPrefix() { return "/plugins/fniki.plugin.Fniki"; }

    ////////////////////////////////////////////////////////////
    // Wiki context implementations.
    public WikiTextStorage getStorage() throws IOException { return mArchiveManager.getStorage(); }

    public FreenetWikiTextParser.ParserDelegate getParserDelegate() { return mParserDelegate; }

    public String getString(String keyName, String defaultValue) {
        if (keyName.equals("default_page")) {
            return "Front_Page";
        } else if (keyName.equals("fproxy_prefix")) {
            if (mFproxyPrefix == null) {
                return defaultValue;
            }
            return mFproxyPrefix;
        } else if (keyName.equals("parent_uri")) {
            if (mArchiveManager.getParentUri() == null) {
                // Can be null
                return defaultValue;
            }
            return mArchiveManager.getParentUri();
        } else if (keyName.equals("container_prefix")) {
            return containerPrefix();
        } else if (keyName.equals("form_password") && mFormPassword != null) {
            return mFormPassword;
        } else if (keyName.equals("form_encoding")) {
            if (mUseMultiPartForms) {
                return "multipart/form-data";
            }
            return "application/x-www-form-urlencoded";
        }

        return defaultValue;
    }

    public int getInt(String keyName, int defaultValue) {
        if (keyName.equals("allow_images")) {
            return mAllowImages ? 1 : 0;
        }
        return defaultValue;
    }

    // DCI: Think this through.
    public String makeLink(String containerRelativePath) {
        // Hacks to find bugs
        if (!containerRelativePath.startsWith("/")) {
            containerRelativePath = "/" + containerRelativePath;
            System.err.println("WikiApp.makeLink -- added leading '/': " +
                               containerRelativePath);
        }
        String full = containerPrefix() + containerRelativePath;
        while (full.indexOf("//") != -1) {
            System.err.println("WikiApp.makeLink -- fixing  '//': " +
                               full);
            full = full.replace("//", "/");
        }
        return full;
    }

    public void raiseRedirect(String toLocation, String msg) throws RedirectException {
        throw new RedirectException(toLocation, msg);
    }

    public void raiseNotFound(String msg) throws NotFoundException {
        throw new NotFoundException(msg);
    }

    public void raiseAccessDenied(String msg) throws AccessDeniedException {
        throw new AccessDeniedException(msg);
    }

    public void raiseServerError(String msg) throws ServerErrorException {
        throw new ServerErrorException(msg);
    }

    public void logError(String msg, Throwable t) {
        if (msg == null) {
            msg = "null";
        }
        if (t == null) {
            t = new RuntimeException("FAKE EXCEPTION: logError called with t == null!");
        }
        System.err.println("Unexpected error: " + msg + " : " + t.toString());
        t.printStackTrace();
    }

    // Delegate to the mRequest helper instance set with setRequest().
    public String getPath() { return mRequest.getPath(); }
    public Query getQuery() { return mRequest.getQuery(); }

    public String getAction() { return mRequest.getQuery().get("action"); }
    public String getTitle() { return mRequest.getQuery().get("title"); }

    ////////////////////////////////////////////////////////////
    public void setRequest(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("request == null");
        }
        mRequest = request;
    }
}
