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

import java.io.InputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wormarc.FileManifest;
import wormarc.IOUtil;

import static fniki.wiki.HtmlUtils.*;
import static fniki.wiki.Validations.*;

import fniki.wiki.child.AsyncTaskContainer;
import fniki.wiki.child.DefaultRedirect;
import fniki.wiki.child.GotoRedirect;
import fniki.wiki.child.InsertingFreesite;
import fniki.wiki.child.LoadingArchive;
import fniki.wiki.child.LoadingChangeLog;
import fniki.wiki.child.LoadingVersionList;
import fniki.wiki.child.QueryError;
import fniki.wiki.child.ResetToEmptyWiki;
import fniki.wiki.child.SettingConfig;
import fniki.wiki.child.StaticFile;
import fniki.wiki.child.StaticWikiText;
import fniki.wiki.child.Submitting;
import fniki.wiki.child.WikiContainer;

import fniki.freenet.filter.ContentFilterFactory;

// Aggregates a bunch of other ChildContainers and runs UI state machine.
public class WikiApp implements ChildContainer {
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

    // Time to wait for FCP before giving up on inverting private key.
    private final static int INVERT_TIMEOUT_MS = 30 * 1000;

    // Delegate to implement WikiContext.
    private final WikiContext mWikiContext = new WikiContextImplementation();

    // Delegate to implement link, image and macro handling in wikitext.
    private final FreenetWikiTextParser.ParserDelegate mParserDelegate;

    // UI subcomponents and actions (e.g. redirecting).
    private final Map<String, ChildContainer> mRoutes = new HashMap<String, ChildContainer>();

    // The current UI state.
    private ChildContainer mState;

    // Transient, per request state.
    private Request mRequest;

    private ArchiveManager mArchiveManager;

    private final boolean mCreateOuterHtml;

    // Belt and braces. Run the ContentFilter from the Freenet fred codebase
    // over all output before serving it.
    private ContentFilter mFilter;

    private String mFproxyPrefix = FPROXY_PREFIX;
    private boolean mAllowImages = ALLOW_IMAGES;
    private String mFormPassword;
    private int mListenPort = LISTEN_PORT;
    private static String sContainerPrefix = "/plugins/fniki.freenet.plugin.Fniki";

    // final because it is called from the ctor.
    private final void resetContentFilter() {
        mFilter = ContentFilterFactory.create(mFproxyPrefix, containerPrefix());
    }

    public WikiContext getContext() { return mWikiContext; }

    public WikiApp(ArchiveManager archiveManager, boolean createOuterHtml) {
        mArchiveManager = archiveManager;
        mCreateOuterHtml = createOuterHtml;
        mParserDelegate = new LocalParserDelegate(getContext(), mArchiveManager);

        // Static routes.
        mRoutes.put("", new DefaultRedirect());
        mRoutes.put("fniki/config", new SettingConfig());
        mRoutes.put("fniki/submit", new Submitting(mArchiveManager));
        mRoutes.put("fniki/changelog", new LoadingChangeLog(mArchiveManager));
        mRoutes.put("fniki/getversions", new LoadingVersionList(mArchiveManager));
        mRoutes.put("fniki/loadarchive", new LoadingArchive(mArchiveManager));
        mRoutes.put("fniki/resettoempty", new ResetToEmptyWiki(mArchiveManager));
        mRoutes.put("fniki/insertsite", new InsertingFreesite(mArchiveManager));

        // Routes to files in the jar.
        // IMPORTANT: Paths MUST not contain '.' or you won't be able to create links to them.
        // IMPORTANT: These are included in the .jar so they should be small.
        mRoutes.put("static_files/Quick_Start",
                    new StaticWikiText("/quickstart.txt", "Quick Start"));
        mRoutes.put("static_files/About_Jfniki_Plugin",
                    new StaticWikiText("/aboutplugin.txt", "About the jFniki Plugin"));

        mRoutes.put("static_files/jfniki_markup",
                    new StaticWikiText("/jfniki_markup.txt", "jFniki Markup"));

        mRoutes.put("static_files/creole_cheat_sheet.png",
                    new StaticFile("/creole_cheat_sheet.png", null /*hmmm...*/, "image/png"));

        mRoutes.put("jfniki.css", // Only used by Plugin.
                    new StaticFile("/plugin_jfniki.css", "UTF-8", "text/css"));

        // Routes determined by code.
        mRoutes.put("from_code/goto_redirect", new GotoRedirect());
        mRoutes.put("from_code/query_error", new QueryError());
        mRoutes.put("from_code/wiki_container", new WikiContainer());

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

        // System.err.println(String.format("[%s] => [%s]",
        //                                  mState.getClass().getName(),
        //                                  container.getClass().getName()));
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

        String action = request.getAction();
        String path = request.getPath();

        if (mState instanceof ModalContainer) {
            if (mRoutes.containsKey(path) &&
                ((mRoutes.get(path) instanceof StaticFile ||
                  mRoutes.get(path) instanceof StaticWikiText))) {
                // Serve static files even when in a modal state.
                // Note that we don't transition into the new state.
                return mRoutes.get(path);
            }


            // Handle transitions out of modal UI states.
            ModalContainer state = (ModalContainer)mState;
            if (action.equals("finished")) {
                if (!state.isFinished()) {
                    state.cancel();
                    try {
                        Thread.sleep(250); // HACK
                    }
                    catch (InterruptedException ie) {
                    }
                }
                // No "else" because it might have finished while sleeping.
                if (state.isFinished()) {
                    setState(request, mRoutes.get("from_code/wiki_container"));
                    return mRoutes.get("from_code/goto_redirect");
                }
            }
            return state;  // Don't leave the modal UI state until finished.
        }


        // Handle static routes.
        if (mRoutes.containsKey(path) && !path.startsWith("from_code/")) {
            return setState(request, mRoutes.get(path));
        }

        if (path.indexOf("/") != -1) {
            // The wiki container doesn't allow subdirectories.
            return mRoutes.get("from_code/query_error");
        }

        return setState(request, mRoutes.get("from_code/wiki_container"));
    }


    // All requests are serialized! Hmmmm....
    public synchronized ChildContainerResult handle(WikiContext context)
        throws ChildContainerException {
        try {
            ChildContainer childContainer = routeRequest(context);
            //System.err.println("Request routed to: " + childContainer.getClass().getName());

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
    private static class LocalParserDelegate extends WikiParserDelegate {
        final WikiContext mContext;

        LocalParserDelegate(WikiContext context, ArchiveManager archiveManager) {
            super(archiveManager);
            mContext = context;
        }

        // Implement base class abstract methods to supply the functionality
        // specific to live wikis, mostly by delegating to the WikiContext.
        protected String getContainerPrefix() {
            return containerPrefix(); // LATER: revisit.
        }

        protected boolean getFreenetLinksAllowed(){
            return mContext.getString("fproxy_prefix", null) != null;
        }

        protected boolean getImagesAllowed() {
            return mContext.getInt("allow_images", 0) != 0;
        }

        protected String makeLink(String containerRelativePath) {
            return mContext.makeLink(containerRelativePath);
        }

        protected String makeFreenetLink(String uri) {
            String prefix = mContext.getString("fproxy_prefix", null);
            if (prefix == null) {
                throw new RuntimeException("fproxy_prefix is null!");
            }
            return makeFproxyHref(prefix, uri);
        }
    }

    // Hmmmm... kind of weird. I can't remember why I used this static method instead of a constant.
    // NO trailing slash.
    private static String containerPrefix() { return sContainerPrefix; }
    public void setContainerPrefix(String containerPrefix) {
        // TODO(djk): Why remove this if it is not necessary.
    	sContainerPrefix = containerPrefix;
    	resetContentFilter();
    }


    ////////////////////////////////////////////////////////////
    public void setRequest(Request request) {
        // Fail immediately if there are problems in the glue code.
        if (request == null) {
            throw new IllegalArgumentException("request == null");
        }
        if (request.getPath() == null) {
            throw new RuntimeException("Assertion Failure: request.getPath() == null");
        }
        if (request.getQuery() == null) {
            throw new RuntimeException("Assertion Failure: request.getQuery() == null");
        }
        if (request.getQuery().get("action") == null) {
            throw new RuntimeException("Assertion Failure: request.getAction() == null");
        }
        if (request.getQuery().get("title") == null) {
            throw new RuntimeException("Assertion Failure: request.getTitle() == null");
        }
        mRequest = request;
    }

    ////////////////////////////////////////////////////////////
    // Wiki context implementations.
    //
    // Pendantic. Implement as a private inner class instead of having WikiApp
    // implement WikiContext directly to prevent unintended coupling from creeping
    // into the code.
    private class WikiContextImplementation implements WikiContext {
        public WikiTextStorage getStorage() throws IOException { return mArchiveManager.getStorage(); }
        public WikiTextChanges getRemoteChanges() throws IOException { return mArchiveManager.getRemoteChanges(); }

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
            } else if (keyName.equals("secondary_uri")) {
                if (mArchiveManager.getSecondaryUri() == null) {
                    // Can be null
                    return defaultValue;
                }
                return mArchiveManager.getSecondaryUri();
            } else if (keyName.equals("container_prefix")) {
                return containerPrefix();
            } else if (keyName.equals("form_password") && mFormPassword != null) {
                return mFormPassword;
            } else if (keyName.equals("default_wikitext")) {
                return getString("/quickstart.txt", "Couldn't load default wikitext from jar???");
            } else if (keyName.equals("wikiname")) {
                if (mArchiveManager.getBissName() != null) {
                    return mArchiveManager.getBissName();
                }
            } else if (keyName.equals("fms_group")) {
                if (mArchiveManager.getFmsGroup() != null) {
                    return mArchiveManager.getFmsGroup();
                }
            } else if (keyName.startsWith("/")) {
                // Assume any name starting with a "/" is a UTF-8 encoded file in the jar.
                try {
                    InputStream resourceStream = WikiApp.class.getResourceAsStream(keyName);
                    if (resourceStream != null) {
                        return IOUtil.readUtf8StringAndClose(resourceStream);
                    }
                } catch (IOException ioe) {
                    /* NOP: Caller gets default */
                }
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
        public boolean isCreatingOuterHtml() { return mCreateOuterHtml; }

        // Can return an invalid configuration. e.g. if fms id and private ssk are not set.
        public Configuration getConfiguration() {
            // Converts null values to ""
            return new Configuration(getInt("listen_port", LISTEN_PORT),
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

        public String getPublicFmsId(String fmsId, String privateSSK) {
            if (fmsId == null || privateSSK == null ||
                (fmsId.indexOf("@") != -1 && (!fmsId.endsWith(".freetalk")))) {
                return "???";
            }
            try {
                try {
                    String publicKey = mArchiveManager.invertPrivateSSK(privateSSK, INVERT_TIMEOUT_MS);
                    int pos = publicKey.indexOf(",");
                    if (pos == -1 || pos < 5) {
                        return "???";
                    }

                    if (!fmsId.endsWith(".freetalk")) {
                        return fmsId + publicKey.substring("SSK".length(), pos);
                    } else {
                        int atPos = fmsId.indexOf("@");
                        if (atPos == -1) {
                            return "???";
                        }

                        // LATER: Fix config to take only human readable id for Freetalk.
                        String invertedFmsId  = fmsId.substring(0, atPos) +
                            publicKey.substring("SSK".length(), pos) + ".freetalk";
                        if (!invertedFmsId.equals(fmsId)) {
                            return "???";
                        }
                        return invertedFmsId;
                    }

                } catch (IllegalArgumentException iae) {
                    // Was called with an invalid privateSSK value
                    return "???";
                }
            } catch (IOException ioe) {
                logError("getPublicFmsId failed", ioe);
                return "???";
            }
        }

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

        public String makeLink(String containerRelativePath) {
            // Hacks to find bugs
            if (!containerRelativePath.startsWith("/")) {
                containerRelativePath = "/" + containerRelativePath;
                // System.err.println("WikiApp.makeLink -- added leading '/': " +
                //                    containerRelativePath);
                (new RuntimeException("find missing /")).printStackTrace();

            }
            String full = containerPrefix() + containerRelativePath;
            while (full.indexOf("//") != -1) {
                // System.err.println("WikiApp.makeLink -- fixing  '//': " +
                //                    full);
                full = full.replace("//", "/");
                (new RuntimeException("find extra /")).printStackTrace();
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

        // Delegate to the mRequest helper instance set with WikiApp.setRequest().
        public String getPath() { return mRequest.getPath(); }
        public Query getQuery() { return mRequest.getQuery(); }

        public String getAction() { return mRequest.getQuery().get("action"); }
        public String getTitle() { return mRequest.getQuery().get("title"); }

    }
}
