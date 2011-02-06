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
import fniki.wiki.child.SettingConfig;
import fniki.wiki.child.Submitting;
import fniki.wiki.child.WikiContainer;

import fniki.freenet.filter.ContentFilterFactory;

// Aggregates a bunch of other ChildContainers and runs UI state machine.
public class WikiApp implements ChildContainer, WikiContext {
    public final static int LISTEN_PORT = 8083;
    private final static String FPROXY_PREFIX = "http://127.0.0.1:8888/";
    private final static boolean ALLOW_IMAGES = false;
    private final static Configuration DEFAULT_CONFIG =
        new Configuration(LISTEN_PORT,
                          ArchiveManager.FCP_HOST,
                          ArchiveManager.FCP_PORT,
                          FPROXY_PREFIX,
                          ALLOW_IMAGES,
                          ArchiveManager.FMS_HOST,
                          ArchiveManager.FMS_PORT,
                          "",
                          "",
                          ArchiveManager.FMS_GROUP,
                          ArchiveManager.BISS_NAME);

    // Delegate to implement link, image and macro handling in wikitext.
    private final FreenetWikiTextParser.ParserDelegate mParserDelegate;

    private final ChildContainer mDefaultRedirect;
    private final ChildContainer mGotoRedirect;
    private final ChildContainer mQueryError;
    private final ChildContainer mWikiContainer;

    // ChildContainers for modal UI states.
    private final ChildContainer mSettingConfig;
    private final ChildContainer mLoadingVersionList;
    private final ChildContainer mLoadingArchive;
    private final ChildContainer mSubmitting;
    private final ChildContainer mLoadingChangeLog;

    // The current default UI state.
    private ChildContainer mState;

    // Transient, per request state.
    private Request mRequest;

    private ArchiveManager mArchiveManager;

    // Belt and braces. Run the ContentFilter from the Freenet fred codebase
    // over all output before serving it.
    private ContentFilter mFilter;

    private String mFproxyPrefix = FPROXY_PREFIX;
    private boolean mAllowImages = ALLOW_IMAGES;
    private String mFormPassword;
    private int mListenPort = LISTEN_PORT;


    // final because it is called from the ctor.
    private final void resetContentFilter() {
        mFilter = ContentFilterFactory.create(mFproxyPrefix, containerPrefix());
    }

    public WikiApp(ArchiveManager archiveManager) {
        mParserDelegate = new LocalParserDelegate(this, archiveManager);

        mDefaultRedirect = new DefaultRedirect();
        mGotoRedirect = new GotoRedirect();
        mQueryError = new QueryError();
        mWikiContainer = new WikiContainer();

        mSettingConfig = new SettingConfig();
        mLoadingVersionList = new LoadingVersionList(archiveManager);
        mLoadingArchive = new LoadingArchive(archiveManager);
        mSubmitting = new Submitting(archiveManager);
        mLoadingChangeLog = new LoadingChangeLog(archiveManager);

        mState = mWikiContainer;
        mArchiveManager = archiveManager;

        resetContentFilter();
    }

    public void setFproxyPrefix(String value) {
        if (!value.startsWith("http") && value.endsWith("/")) {
            throw new IllegalArgumentException("Expected a value starting with 'http' and ending with '/'");
        }
        mFproxyPrefix = value;
        resetContentFilter();
    }

    public void setAllowImages(boolean value) {
        mAllowImages = value;
    }

    public void setFormPassword(String value) {
        mFormPassword = value;
    }

    // Doesn't change port, just sets value returned by getInt("listen_port", -1)
    public void setListenPort(int value) {
        mListenPort = value;
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

        // DCI: Fix. Use a hashmap of paths -> instances for static paths
        System.err.println("WikiApp.routeRequest: " + path);
        if (path.equals("fniki/config")) {
            return setState(request, mSettingConfig);
        } else if (path.equals("fniki/submit")) {
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

            return mFilter.filter(childContainer.handle(context));
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
    private static String containerPrefix() { return "/plugins/fniki.freenet.plugin.Fniki"; }

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
        } else if (keyName.equals("default_wikitext")) {
            return getDefaultWikiText();
        }

        return defaultValue;
    }

    public int getInt(String keyName, int defaultValue) {
        if (keyName.equals("allow_images")) {
            return mAllowImages ? 1 : 0;
        }
        if (keyName.equals("listen_port")) {
            return mListenPort;
        }

        return defaultValue;
    }

    // Can return an invalid configuration. e.g. if fms id and private ssk are not set.
    public Configuration getConfiguration() {
        // Converts null values to ""
        return new Configuration(getInt("listen_port", LISTEN_PORT), //DCI: clean up magic numbers
                                 mArchiveManager.getFcpHost(),
                                 mArchiveManager.getFcpPort(),
                                 getString("fproxy_prefix", FPROXY_PREFIX),
                                 mAllowImages,
                                 mArchiveManager.getFmsHost(),
                                 mArchiveManager.getFmsPort(),
                                 mArchiveManager.getFmsId(),
                                 mArchiveManager.getPrivateSSK(),
                                 mArchiveManager.getFmsGroup(),
                                 mArchiveManager.getBissName());
    }

    public Configuration getDefaultConfiguration() { return DEFAULT_CONFIG; }

    // For setting data from forms and restoring saved settings.
    // throws unchecked Configuration.ConfigurationException
    public void setConfiguration(Configuration config) {
        config.validate();
        setListenPort(config.mListenPort);
        mArchiveManager.setFcpHost(config.mFcpHost);
        mArchiveManager.setFcpPort(config.mFcpPort);
        setFproxyPrefix(config.mFproxyPrefix);
        setAllowImages(config.mAllowImages);
        mArchiveManager.setFmsHost(config.mFmsHost);
        mArchiveManager.setFmsPort(config.mFmsPort);
        mArchiveManager.setFmsId(config.mFmsId);
        mArchiveManager.setPrivateSSK(config.mFmsSsk);
        mArchiveManager.setFmsGroup(config.mFmsGroup);
        mArchiveManager.setBissName(config.mWikiName);
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

    public void raiseDownload(byte[] data, String filename, String mimeType) throws DownloadException {
        throw new DownloadException(data, filename, mimeType);
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

    private static String getDefaultWikiText() {
        StringBuilder sb = new StringBuilder();
        sb.append("//You are seeing this Quick Start because the wiki has no Front_Page. It will disappear as soon as you edit and save the page. //\n");
        sb.append("----\n");
        sb.append("\n");
        sb.append("== Quick Start ==\n");
        sb.append("\n");
        sb.append("===Configuration===\n");
        sb.append("# Click on the \"View\" link below to view (and edit) the configuration.\n");
        sb.append("# Set the \"FMS ID\" to the human readable part of your FMS ID (everything before the '@').\n");
        sb.append("# Set the FMS Private SSK to your private FMS SSK (see below if you don't know how to find this).\n");
        sb.append("# Adjust any other values as necessary.  If you're running FMS and Fred on the same machine on the default ports this shouldn't be necessary.\n");
        sb.append("# Click the \"Done\" button to save the configuration changes.\n");
        sb.append("\n");
        sb.append("=== Finding Other Versions===\n");
        sb.append("Click the \"Discover\" link below to search for other versions of the wiki.\n");
        sb.append("\n");
        sb.append("=== Submitting ===\n");
        sb.append("Use the \"Submit\" link below to submit your changes.  It may take a long time for other people to see them.\n");
        sb.append("\n");
        sb.append("=== Finding Your Private SSK ===\n");
        sb.append("# Go to http://127.0.0.1:18080/localidentities.htm in the FMS web interface and click the \"Export Identities\" button\n");
        sb.append("to save your FMS indentities to a file.\n");
        sb.append("\n");
        sb.append("# In the text editor of your choice, open the file you saved above and look for the Name and PrivateKey values for the identity you want to use.\n");
        sb.append("\n");
        sb.append("In the example identity snippet below, the FMS ID value would be:\\\\ \n");
        sb.append("SomeUser\n");
        sb.append("\n");
        sb.append("and the FMS Private Key would be: \\\\ \n");
        sb.append("SSK@YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY,YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY,AQECAAE/ \n");
        sb.append("\n");
        sb.append("----\n");
        sb.append("{{{\n");
        sb.append("<Identity>\n");
        sb.append("   <Name><![CDATA[SomeUser]]></Name>\n");
        sb.append("   <PublicKey>SSK@XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX,XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX,AQACAAE/</PublicKey>\n");
        sb.append("   <PrivateKey>SSK@YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY,YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY,AQECAAE/</PrivateKey>\n");
        sb.append("   <SingleUse>false</SingleUse>\n");
        sb.append("   <PublishTrustList>false</PublishTrustList>\n");
        sb.append("   <PublishBoardList>false</PublishBoardList>\n");
        sb.append("   <PublishFreesite>false</PublishFreesite>\n");
        sb.append("</Identity>\n");
        sb.append("}}}\n");
        sb.append("\n");
        return sb.toString();
    }
}
