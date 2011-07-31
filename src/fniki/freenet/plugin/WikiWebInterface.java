package fniki.freenet.plugin;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

import fniki.freenet.plugin.Fniki.PluginRequest;
import fniki.wiki.AccessDeniedException;
import fniki.wiki.ChildContainerException;
import fniki.wiki.DownloadException;
import fniki.wiki.NotFoundException;
import fniki.wiki.RedirectException;
import fniki.wiki.Request;
import fniki.wiki.WikiApp;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageNode;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.pluginmanager.AccessDeniedPluginHTTPException;
import freenet.pluginmanager.DownloadPluginHTTPException;
import freenet.pluginmanager.NotFoundPluginHTTPException;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.RedirectPluginHTTPException;
import freenet.support.api.HTTPRequest;

public class WikiWebInterface extends Toadlet {
    private String mNameSpace;
    private WikiApp mWikiApp;
    private final String mJfnikiCss;

    protected WikiWebInterface(HighLevelSimpleClient client, String path, WikiApp wikiapp) {
        super(client);
        mNameSpace = path;
        mWikiApp = wikiapp;

        // Loaded from ./style/plugin_jfniki.css.
        mJfnikiCss = mWikiApp.getString("/plugin_jfniki.css", null);
        if (mJfnikiCss == null) {
            throw new RuntimeException("/plugin_jfniki.css not found in .jar");
        }
    }

    @Override
    public String path() {
        return mNameSpace;
    }

    public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws
        ToadletContextClosedException, IOException, RedirectException, PluginHTTPException {
        if(uri.toASCIIString().equals(mNameSpace + "jfniki.css")) {
            writeReply(ctx, 200, "text/css", "OK", mJfnikiCss);
        } else {
            PageNode mPageNode = ctx.getPageMaker().getPageNode("jFniki", true, ctx);
            mPageNode.addCustomStyleSheet(mNameSpace + "jfniki.css");
            mPageNode.content.setContent(handleWebRequest(req, ctx));
            writeHTMLReply(ctx, 200, "OK", mPageNode.outer.generate());
        }
    }

    public void handleMethodPOST(URI uri, HTTPRequest req, ToadletContext ctx)
        throws ToadletContextClosedException, IOException, RedirectException, PluginHTTPException {
        // This method is called whenever a user requests a page from our mNameSpace
        // POST form authentication
        //FIXME link the core
        //FIXME validate referrer
        //FIXME validate session
        //String passwordPlain = req.getPartAsString("formPassword", 32);
        //if((passwordPlain.length() == 0) || !passwordPlain.equals(core.formPassword)) {
        //	writeHTMLReply(ctx, 403, "Forbidden", "Invalid form password.");
        //	return;
        //}
        PageNode mPageNode = ctx.getPageMaker().getPageNode("jFniki", true, ctx);
        mPageNode.addCustomStyleSheet(mNameSpace + "jfniki.css");
        mPageNode.content.setContent(handleWebRequest(req, ctx));
        writeHTMLReply(ctx, 200, "OK", mPageNode.outer.generate());
    }

    private String handleWebRequest(HTTPRequest request, ToadletContext ctx)
        throws ToadletContextClosedException,  IOException, PluginHTTPException {
        mWikiApp.setContainerPrefix(mNameSpace.substring(0, mNameSpace.length()-1));
        try {
            mWikiApp.setRequest(new PluginRequest(request, mNameSpace));
            return mWikiApp.handle(mWikiApp);
            // IMPORTANT: Look at these catch blocks carefully. They bypass the freenet ContentFilter.
            // SeekingForAttention: This could be insecure code because I have no clue and no documentation.
            //                      Do not use it if you don't know what you are doing.
            // djk: All I meant is that the my code runs the content filter over the html which is returned
            //      by handle() above, but  not  over whatever you return from the catch blocks below.
        } catch(AccessDeniedException accessDenied) {
            // FIXME: Check. This doesn't need to be HTML escaped because it is text/plain, right?
            writeReply(ctx, 403, "text/plain", "Forbidden", accessDenied.getMessage());
        } catch(NotFoundException notFound) { // Not currently used.
            writeHTMLReply(ctx, 200, "OK", createRequestInfo(request,ctx).outer.generate());
        } catch(RedirectException redirected) {
            writeTemporaryRedirect(ctx, redirected.getLocalizedMessage(), redirected.getLocation());
        } catch(DownloadException forceDownload) {
            // This is to allow exporting the configuration.
            writeReply(ctx, 200, forceDownload.mMimeType, "OK", forceDownload.mData, 0, forceDownload.mData.length);
        } catch(ChildContainerException ex) {
            System.err.println("WikiWebInterface::handleWebRequest failed: " + ex.getMessage());
            return
                // FIXME: getPath() and getMessage() should be HTML escaped.
                "Requested path " + request.getPath() + " can not be delivered: " +
                ex.getMessage() + "<br />Please report this message.<br />" +
                createRequestInfo(request, ctx).content.generate();
        }

        // djk: Why were you catching ToadletContextClosedException and IOException without
        //      handling them?
        return ""; // Handled in a catch block.
    }

    private PageNode createRequestInfo(HTTPRequest req, ToadletContext ctx) {
        // djk: I don't know these APIs. Does this HTML escape for you?
        // FIXME: return a content node only instead of PageNode
        URI uri = ctx.getUri();
        PageNode mPageNode = ctx.getPageMaker().getPageNode("HelloWorld InfoPage", true, ctx);
        mPageNode.content.addChild("br");
        mPageNode.content.addChild("span","Sorry, something went wrong with your request to");
        mPageNode.content.addChild("br");
        mPageNode.content.addChild("br");
        // requested URI
        mPageNode.content.addChild("b", "URI:");
        mPageNode.content.addChild("br");
        mPageNode.content.addChild("i", uri.toString());
        mPageNode.content.addChild("br");
        mPageNode.content.addChild("br");
        // used Method
        mPageNode.content.addChild("b", "Method:");
        mPageNode.content.addChild("br");
        mPageNode.content.addChild("i", req.getMethod());
        mPageNode.content.addChild("br");
        mPageNode.content.addChild("br");
        // POST data?
        mPageNode.content.addChild("b", "HTTPRequest.getParts():");
        mPageNode.content.addChild("br");
        String tmpGetRequestParts[] = req.getParts();
        for (int i = 0; i < tmpGetRequestParts.length; i++) {
            mPageNode.content.addChild("i", tmpGetRequestParts[i]);
            mPageNode.content.addChild("br");
        }
        mPageNode.content.addChild("br");
        mPageNode.content.addChild("br");
        // Parameters Key-->Value
        mPageNode.content.addChild("b", "HTTPRequest.getParameterNames()-->HTTPRequest.getParam(parameter):");
        mPageNode.content.addChild("br");
        String partString = "";
        Collection<String> tmpGetRequestParameterNames = req.getParameterNames();
        for (Iterator<String> tmpIterator = tmpGetRequestParameterNames.iterator(); tmpIterator.hasNext();) {
            partString = tmpIterator.next();
            mPageNode.content.addChild("i", partString + "-->" + req.getParam(partString));
            mPageNode.content.addChild("br");
        }
        return mPageNode;
    }
}
