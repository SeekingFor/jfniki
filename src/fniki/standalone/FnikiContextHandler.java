/* Glue code to run WikiApp to run from within HTTPServer.
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

package fniki.standalone;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import java.util.Map;
import java.util.Set;
import net.freeutils.httpserver.HTTPServer;

import fniki.wiki.Query;
import fniki.wiki.QueryBase;
import fniki.wiki.Request;
import fniki.wiki.WikiApp;

import fniki.wiki.AccessDeniedException;
import fniki.wiki.DownloadException;
import fniki.wiki.NotFoundException;
import fniki.wiki.RedirectException;
import fniki.wiki.ChildContainerException;

// Adapter class to run WikiApp from HTTPServer
public class FnikiContextHandler implements HTTPServer.ContextHandler {
    private final WikiApp mApp;
    private final String mContainerPrefix;

    private static class WikiQuery extends QueryBase {
        private final HTTPServer.Request mParent;
        private final String mPath;

        // Hmmmm... can't figure out any other way to know when part is done.
        private final String readAsUtf8(HTTPServer.MultipartIterator.Part part) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            while (part.body.available() > 0) { // Do better? Does it matter?
                int oneByte = part.body.read();
                if (oneByte == -1) {
                    throw new IOException("Unexpected EOF???");
                }
                baos.write(oneByte);
            }

            return new String(baos.toByteArray(), "utf8");
        }

        public void readParams() throws IOException {
            Set<String> allParams = paramsSet();

            // Read normal non-multipart params.
            Map<String, String> parentParams = mParent.getParams();
            for (String name : allParams) {
                if (!parentParams.containsKey(name)) {
                    continue;
                }
                System.err.println("Set Param: " + name + " : " + parentParams.get(name));
                mParamTable.put(name, parentParams.get(name));
            }

            // Then read multipart params if there are any.
            if (mParent.getHeaders().getParams("Content-Type").
                containsKey("multipart/form-data")) {
                HTTPServer.MultipartIterator iter = new HTTPServer.MultipartIterator(mParent);
                while (iter.hasNext()) {
                    HTTPServer.MultipartIterator.Part part = iter.next();
                    if (!allParams.contains(part.name)) {
                        continue;
                    }
                    mParamTable.put(part.name, readAsUtf8(part));
                    System.err.println("Set multipart Param: " + part.name + " : " +
                                       mParamTable.get(part.name));
                }
                mParent.consumeBody();
            }

            if (!mParamTable.containsKey("action")) {
                //System.err.println("Forced default action to view");
                mParamTable.put("action", "view");
            }

            if (!mParamTable.containsKey("title")) {
                mParamTable.put("title", mPath);
            }
        }

        WikiQuery(HTTPServer.Request parent, String path) throws IOException {
            super();
            mParent = parent;
            mPath = path;
            readParams();
        }
    }

    private static class WikiRequest implements Request {
        private final Query mQuery;
        private final String mPath;

        WikiRequest(HTTPServer.Request parent, String containerPrefix) throws IOException {
            String path = parent.getPath();
            if (!path.startsWith(containerPrefix)) {
                // This should be impossible because of the way HTTPServer routes requests.
                throw new RuntimeException("Request doesn't start with: " + containerPrefix);
            }

            System.err.println("Raw path: " + path);

            path = path.substring(containerPrefix.length());
            while(path.startsWith("/")) {
                path = path.substring(1).trim();
            }

            mPath = path;
            mQuery = new WikiQuery(parent, path);
        }

        public String getPath() { return mPath; }
        public Query getQuery() { return mQuery; }
    }

    public FnikiContextHandler(WikiApp app) {
        mApp = app;
        mContainerPrefix = mApp.getString("container_prefix", null);
        if (mContainerPrefix == null) {
            throw new IllegalArgumentException("mContainerPrefix == null");
        }
    }

    /**
     * Serves the given request using the given response.
     * 
     * @param req the request to be served
     * @param resp the response to be filled
     * @return an HTTP status code, which will be used in returning
     *         a default response appropriate for this status. If this
     *         method invocation already sent anything in the response
     *         (headers or content), it must return 0, and no further
     *         processing will be done
     * @throws IOException if an IO error occurs
     */
    public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        synchronized (mApp) { // Only allow one thread to touch app at a time.
            mApp.setRequest(new WikiRequest(req, mContainerPrefix));
            try {
                String html = mApp.handle(mApp);
                resp.send(200, html);
                return 0;
            } catch(AccessDeniedException accessDenied) {
                resp.sendError(403, accessDenied.getMessage());
                return 0;
            } catch(NotFoundException notFound) {
                resp.sendError(404, notFound.getMessage());
                return 0;
            } catch(RedirectException redirected) {
                resp.redirect(redirected.getLocation(), false);
                return 0;
            } catch(DownloadException forceDownload) {
                try {
                    resp.getHeaders().add("Content-disposition",
                                          String.format("attachment; filename=%s", forceDownload.mFilename));
                    resp.sendHeaders(200, forceDownload.mData.length, -1,
                                     null, forceDownload.mMimeType, null);
                    OutputStream body = resp.getBody();
                    if (body == null) {
                        return 0; // hmmm... getBody() can return null.
                    }
                    try {
                        body.write(forceDownload.mData);
                    } finally {
                        body.close();
                    }
                    return 0;
                } catch (IOException ioe) {
                    // Totally hosed. We already sent the headers so we can't send a response.
                    ioe.printStackTrace();
                    return 0;
                }
            } catch(ChildContainerException serverError) {
                // This also handles ServerErrorException.
                resp.sendError(500, serverError.getMessage());
                return 0;
            }
        }
    }
}
