/* Toadlet used by the Plugin implmentation.
 *
 * Copyright (C) 2010, 2011 SeekingForAttention
 * Changes Copyright (C) 2010, 2011 Darrell Karbott
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
 * Author: SeekingForAttention.
 *         Many changes by djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */
package fniki.freenet.plugin;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

import fniki.freenet.plugin.Fniki.PluginRequest;
import fniki.wiki.AccessDeniedException;
import fniki.wiki.ChildContainerException;
import fniki.wiki.ChildContainerResult;
import fniki.wiki.DownloadException;
import fniki.wiki.RedirectException;
import fniki.wiki.Request;
import fniki.wiki.WikiContext;
import fniki.wiki.WikiApp;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageNode;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.pluginmanager.PluginHTTPException;
import freenet.support.api.HTTPRequest;
import freenet.support.MultiValueTable;

public class WikiWebInterface extends Toadlet {
    private String mNameSpace;
    private WikiApp mWikiApp;

    protected WikiWebInterface(HighLevelSimpleClient client, String path, WikiApp wikiapp) {
        super(client);
        mNameSpace = path;
        mWikiApp = wikiapp;
    }

    @Override
    public String path() {
        return mNameSpace;
    }

    public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws
        ToadletContextClosedException, IOException, RedirectException, PluginHTTPException {
        handleJfnikiRequest(request, ctx);
    }

    // djk: Check with Toad.  This should only be called if the form has the
    //     form password.
    //
    // djk: What  did these comments from SFA mean?
    //FIXME link the core.
    //FIXME validate referrer.
    //FIXME validate session.
    // SFA: this can be additional security checks.. maybe not needed though. for "link the core": no idea either :)
    public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx)
        throws ToadletContextClosedException, IOException, RedirectException, PluginHTTPException {
        handleJfnikiRequest(request, ctx);
    }

    private void handleJfnikiRequest(HTTPRequest request, ToadletContext ctx)
        throws ToadletContextClosedException,  IOException, PluginHTTPException {
        // djk: why is SFA doing this on every request?
    	// SFA: afaik this should not be needed, feel free to move this elsewhere.
        mWikiApp.setContainerPrefix(mNameSpace.substring(0, mNameSpace.length() - 1));
        try {
            mWikiApp.setRequest(new PluginRequest(request, mNameSpace));
            WikiContext context = mWikiApp.getContext();
            ChildContainerResult appResult = mWikiApp.handle(context);
            if (appResult.getMimeType().equals("text/html")) {
                PageNode pageNode = ctx.getPageMaker().getPageNode("jFniki", true, ctx);
                pageNode.addCustomStyleSheet(mNameSpace + "jfniki.css");
                pageNode.content.addChild("%", new String(appResult.getData(), "UTF-8"));
                if (appResult.getMetaRefreshSeconds() > 0) {
                    pageNode.headNode.addChild("meta", "http-equiv", "Refresh").
                        addAttribute("content","" + appResult.getMetaRefreshSeconds());
                }
                writeHTMLReply(ctx, 200, "OK", pageNode.outer.generate());
            } else {
                // i.e. for stuff like jfniki.css, images (none yet).
                writeReply(ctx, 200, appResult.getMimeType(), "OK",
                           appResult.getData(), 0, appResult.getData().length);
            }
            // IMPORTANT: Look at these catch blocks carefully. They bypass the freenet ContentFilter.
            // SeekingForAttention: This could be insecure code because I have no clue and no documentation.
            //                      Do not use it if you don't know what you are doing.
            // djk: All I meant is that the my code runs the content filter over the html which is returned
            //      by handle() above, but  not  over whatever you return from the catch blocks below.
            // SFA: so i guess this comment block but the first line can be removed.
        } catch(AccessDeniedException accessDenied) {
            // FIXME: Check. This doesn't need to be HTML escaped because it is text/plain, right?
            writeReply(ctx, 403, "text/plain", "Forbidden", accessDenied.getMessage());
        } catch(RedirectException redirected) {
            writeTemporaryRedirect(ctx, redirected.getLocalizedMessage(), redirected.getLocation());
        } catch(DownloadException forceDownload) {
            // This is to allow exporting files. e.g. configuration, archive exports.

            MultiValueTable<String, String> headers
                = new MultiValueTable<String, String>();
            // Required to set the suggested file name.
            headers.put("Content-disposition",
                        String.format("attachment; filename=%s",
                                      forceDownload.mFilename));
            ctx.sendReplyHeaders(200, "sending file", headers,
                                 forceDownload.mMimeType,
                                 forceDownload.mData.length);
	    ctx.writeData(forceDownload.mData, 0,
                          forceDownload.mData.length);

            // Can't do this for Toadlet's.
            // throw new DownloadPluginHTTPException(forceDownload.mData,
            //                                       forceDownload.mFilename,
            //                                       forceDownload.mMimeType);
	    	// SFA: so this can be removed too i guess?

        } catch(ChildContainerException childError) {
            writeReply(ctx, 500, "text/plain", "Internal Server Error", childError.getMessage());
        }
    }

    // djk: SFA, I ripped out your createRequestInfo method, because I didn't
    // know if it needed to be doing HTML escaping and it looked like it was
    // only for debugging.
    // SFA: it was only for debugging. this comment block can be removed.
}
