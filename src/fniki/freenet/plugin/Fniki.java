/* Freenet Plugin to run WikiApp.
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

package fniki.freenet.plugin;

import java.io.IOException;
import java.util.Set;

import freenet.clients.http.PageMaker;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContainer;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.pluginmanager.AccessDeniedPluginHTTPException;
import freenet.pluginmanager.DownloadPluginHTTPException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.NotFoundPluginHTTPException;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.RedirectPluginHTTPException;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.api.HTTPUploadedFile;

import wormarc.IOUtil;

import fniki.wiki.ArchiveManager;
import fniki.wiki.Query;
import fniki.wiki.QueryBase;
import fniki.wiki.Request;
import fniki.wiki.WikiContext;
import fniki.wiki.WikiApp;

import fniki.wiki.AccessDeniedException;
import fniki.wiki.DownloadException;
import fniki.wiki.NotFoundException;
import fniki.wiki.RedirectException;
import fniki.wiki.ChildContainerException;

public class Fniki implements FredPlugin, FredPluginThreadless, FredPluginL10n {
    private WikiApp mWikiApp;

    private ToadletContainer mFredWebUI;
    private PageMaker mPageMaker;
    private Toadlet mToadlet;

    public void terminate() {
        System.err.println("jFniki plugin terminating...");
        mFredWebUI.unregister(mToadlet); // unload toadlet
        mPageMaker.removeNavigationCategory("jFniki");	// unload category
        mToadlet = null;
        System.err.println("jFniki plugin terminated.");
    }

    public void runPlugin(PluginRespirator pr) {
        System.err.println("Fniki plugin starting...");
        try {
            ArchiveManager archiveManager = new ArchiveManager();
            archiveManager.createEmptyArchive();

            WikiApp wikiApp = new WikiApp(archiveManager, false);
            if (wikiApp.getContext().getString("container_prefix", null) == null) {
                throw new RuntimeException("Assertion Failure: container_prefix not set!");
            }

            // IMPORTANT:
            // HTTP POSTS will be rejected without any useful error message if your form
            // doesn't contain a hidden field with the freenet per boot form password.
            wikiApp.setFormPassword(pr.getNode().clientCore.formPassword);

            mWikiApp = wikiApp;
            mFredWebUI = pr.getToadletContainer();
            mPageMaker = pr.getPageMaker();
            mToadlet = new WikiWebInterface(pr.getHLSimpleClient(), "/jfniki/" , mWikiApp);
            mPageMaker.addNavigationCategory(mToadlet.path(), "jFniki", "Wiki over Freenet", this);
            // Add a hidden navigation item to catch a click on main navigation category.
            mFredWebUI.register(mToadlet, null, mToadlet.path(), true, false);

            // Add normal sub items.
            // djk: Don't make deep links into the plugin.
            //      0) The UI has some modal states, so you won't always end up viewing
            //         the page you link to. e.g. If you try to go to the "Front_Page"
            //         while the "Discover" page is running, you see the "Discover"
            //         page not the "Front_Page".
            //      1) Not all pages exist in all wikis. e.g. "Index", "Changelog"
            //
            mFredWebUI.register(mToadlet, "jFniki", mToadlet.path() + "" , true, // "" == default redirect
                                "View Current Wiki", "view currently loaded wiki", false, null, this);

            // The WikiApp constructor adds a static route to this page.
            // The wikitext is read from doc/aboutplugin.txt.
            mFredWebUI.register(mToadlet, "jFniki", mToadlet.path() + "static_files/About_Jfniki_Plugin" , true,
                                "About", "information about this plugin", false, null, this);
        } catch (IOException ioe) {
            System.err.println("Fniki Plugin EPIC FAIL!");
            ioe.printStackTrace();
        }
    }

    @Override
    public String getString(String key) {
        // TODO Auto-generated method stub
        return key;
    }

    @Override
    public void setLanguage(LANGUAGE newLanguage) {
        // TODO Auto-generated method stub
    }

    protected static class ServerPluginHTTPException extends PluginHTTPException {
        private static final long serialVersionUID = -1;

        public static final short code = 500; // Bad Request
        public ServerPluginHTTPException(String errorMessage, String location) {
            super(errorMessage, location);
        }
    }

    private static class PluginQuery extends QueryBase {
        private final HTTPRequest mParent;
        private final String mPath;

        public void readParams() throws IOException {
            Set<String> allParams = paramsSet();

            // Read normal non-multipart params.
            for (String name : allParams) {
                if (!mParent.isParameterSet(name)) {
                    continue;
                }
                mParamTable.put(name, mParent.getParam(name).getBytes(IOUtil.UTF8));
                //System.err.println("Set Param: " + name + " : " + mParamTable.get(name));
            }

            // Then read multipart params if there are any.
            try {
                for (String part : mParent.getParts()) {
                    if (!allParams.contains(part)) {
                        continue;
                    }

                    // Special case file posts.
                    if (part.equals("upload")) {
                        HTTPUploadedFile uploadedFile = mParent.getUploadedFile(part);
                        Bucket bucket = uploadedFile.getData();
                        try {
                            byte[] data = IOUtil.readAndClose(bucket.getInputStream());
                            if (data == null) {
                                throw new IOException("Couldn't read uploaded file data from bucket.");
                            }
                            if (data.length > 128 * 1024) {
                                throw new IOException("Uploaded file too big.");
                            }
                            mParamTable.put(part, data);
                            mParamTable.put(part + ".filename", uploadedFile.getFilename().getBytes(IOUtil.UTF8));
                        } finally {
                            bucket.free();
                        }
                    } else {
                        byte[] value = mParent.getPartAsBytesFailsafe(part, 128 * 1024);
                        mParamTable.put(part, value);
                    }
                    // Can fail if value isn't utf-8
                    // System.err.println("Set multipart Param: " + part + " : " +
                    //                    mParamTable.get(part));
                }

                if (!mParamTable.containsKey("action")) {
                    //System.err.println("Forced default action to view");
                    mParamTable.put("action", "view".getBytes(IOUtil.UTF8));
                }
                if (!mParamTable.containsKey("title")) {
                    mParamTable.put("title", mPath.getBytes(IOUtil.UTF8));
                }
            } finally {
                mParent.freeParts();
            }
        }

        PluginQuery(HTTPRequest parent, String path) throws IOException {
            super();
            mParent = parent;
            mPath = path;
            readParams();
        }
    }

    static class PluginRequest implements Request {
        private final Query mQuery;
        private final String mPath;
        PluginRequest(HTTPRequest parent, String containerPrefix) throws IOException {
            String path = parent.getPath();
            if (!path.startsWith(containerPrefix)) {
                // This should be impossible because of the way plugin requests are routed.
                throw new RuntimeException("Request doesn't start with: " + containerPrefix);
            }

            //System.err.println("Raw path: " + path);
            path = path.substring(containerPrefix.length());

            while(path.startsWith("/")) {
                path = path.substring(1).trim();
            }
            mQuery = new PluginQuery(parent, path);
            mPath = path;
        }

        public String getPath() { return mPath; }
        public Query getQuery() { return mQuery; }
    }
}

