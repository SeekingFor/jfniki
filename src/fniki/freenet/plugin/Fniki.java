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
import java.io.UnsupportedEncodingException;
import java.util.Set;

import freenet.pluginmanager.AccessDeniedPluginHTTPException;
import freenet.pluginmanager.DownloadPluginHTTPException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.NotFoundPluginHTTPException;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.RedirectPluginHTTPException;
import freenet.support.api.HTTPRequest;

import fniki.wiki.ArchiveManager;
import fniki.wiki.Query;
import fniki.wiki.QueryBase;
import fniki.wiki.Request;
import fniki.wiki.WikiApp;

import fniki.wiki.AccessDeniedException;
import fniki.wiki.DownloadException;
import fniki.wiki.NotFoundException;
import fniki.wiki.RedirectException;
import fniki.wiki.ChildContainerException;

public class Fniki implements FredPlugin, FredPluginHTTP, FredPluginThreadless {
    private WikiApp mWikiApp;
    private String mContainerPrefix;
    public void terminate() {
        System.err.println("Fniki plugin terminating...");
    }

    public void runPlugin(PluginRespirator pr) {
        System.err.println("Fniki plugin starting...");
        try {
            ArchiveManager archiveManager = new ArchiveManager();
            archiveManager.createEmptyArchive();

            WikiApp wikiApp = new WikiApp(archiveManager);
            if (wikiApp.getString("container_prefix", null) == null) {
                throw new RuntimeException("Assertion Failure: container_prefix not set!");
            }
            mContainerPrefix = wikiApp.getString("container_prefix", null);

            // IMPORTANT:
            // HTTP POSTS will be rejected without any useful error message if your form
            // doesn't contain a hidden field with the freenet per boot form password.
            wikiApp.setFormPassword(pr.getNode().clientCore.formPassword);

            mWikiApp = wikiApp;

        } catch (IOException ioe) {
            System.err.println("Fniki Plugin EPIC FAIL!");
            ioe.printStackTrace();
        }
    }

    private static class ServerPluginHTTPException extends PluginHTTPException {
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
                mParamTable.put(name, mParent.getParam(name));
                //System.err.println("Set Param: " + name + " : " + mParamTable.get(name));
            }

            // Then read multipart params if there are any.
            try {
                try {
                    for (String part : mParent.getParts()) {
                        if (!allParams.contains(part)) {
                            continue;
                        }

                        String value = new String(mParent.getPartAsBytesFailsafe(part, 64 * 1024), "utf-8");
                        mParamTable.put(part, value);
                        // System.err.println("Set multipart Param: " + part + " : " +
                        //                    mParamTable.get(part));
                    }
                } catch (UnsupportedEncodingException ue) {
                    // Shouldn't happen.
                    ue.printStackTrace();
                }

                if (!mParamTable.containsKey("action")) {
                    //System.err.println("Forced default action to view");
                    mParamTable.put("action", "view");
                }

                if (!mParamTable.containsKey("title")) {
                    mParamTable.put("title", mPath);
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

    private static class PluginRequest implements Request {
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

    public String handle(HTTPRequest request) throws PluginHTTPException {
        try {
            mWikiApp.setRequest(new PluginRequest(request, mContainerPrefix));
            return mWikiApp.handle(mWikiApp);

            // IMPORTANT: Look at these catch blocks carefully. They bypass the freenet ContentFilter.
        } catch(AccessDeniedException accessDenied) {
            throw new AccessDeniedPluginHTTPException(accessDenied.getMessage(), mContainerPrefix);
        } catch(NotFoundException notFound) {
            throw new NotFoundPluginHTTPException(notFound.getMessage(), mContainerPrefix);
        } catch(RedirectException redirected) {
            throw new RedirectPluginHTTPException(redirected.getMessage(), redirected.getLocation());
        } catch(DownloadException forceDownload) {
            // This is to allow exporting the configuration.
            throw new DownloadPluginHTTPException(forceDownload.mData,
                                                  forceDownload.mFilename,
                                                  forceDownload.mMimeType);
        } catch(ChildContainerException serverError) {
            throw new ServerPluginHTTPException(serverError.getMessage(), mContainerPrefix);
        } catch(IOException ioError) {
            throw new ServerPluginHTTPException(ioError.getMessage(), mContainerPrefix);
        }
    }

    public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
        return handle(request);
    }

    public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
        return handle(request);
    }
}

