package fniki.freenet.plugin;

//import fniki.freenet.plugin.Fniki.PluginRequest;
//import fniki.freenet.plugin.Fniki.ServerPluginHTTPException;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

import fniki.freenet.plugin.Fniki.*;
//import fniki.wiki.AccessDeniedException;
//import fniki.wiki.ChildContainerException;
//import fniki.wiki.DownloadException;
//import fniki.wiki.NotFoundException;
//import fniki.wiki.RedirectException;
//import fniki.wiki.Request;
//import fniki.wiki.WikiApp;
import fniki.wiki.*;

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
	
	protected WikiWebInterface(HighLevelSimpleClient client, String path, WikiApp wikiapp) {
		super(client);
		mNameSpace = path;
		mWikiApp = wikiapp;
	}
	@Override
	public String path() {
		return mNameSpace;
	}
	
	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, PluginHTTPException {
		PageNode mPageNode = ctx.getPageMaker().getPageNode("jFniki", true, ctx);
		mPageNode.content.setContent(handleWebRequest(req, ctx, false));
		writeHTMLReply(ctx, 200, "OK", mPageNode.outer.generate());
		//return handleWebRequest(req);
	}
	public void handleMethodPOST(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, PluginHTTPException {
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
		mPageNode.content.setContent(handleWebRequest(req,ctx, false));
		writeHTMLReply(ctx, 200, "OK", mPageNode.outer.generate());
		//return handleWebRequest(req);
	}
	private String handleWebRequest(HTTPRequest request, ToadletContext ctx, boolean createHtmlHeader) throws PluginHTTPException {
		String tmpResult = "nothing to see. move along.";
		//System.err.println("WikiWebInterface::handleWebRequest");
		try {
			mWikiApp.setRequest(new PluginRequest(request, mNameSpace), createHtmlHeader);
			tmpResult = mWikiApp.handle(mWikiApp);
	        // IMPORTANT: Look at these catch blocks carefully. They bypass the freenet ContentFilter.
	        // SeekingForAttention: This is insecure code. I hacked around almost everything because I have no clue and no documentation. Do not use it if you don't know what you are doing.
			//} catch(AccessDeniedException accessDenied) {
			//	throw new AccessDeniedPluginHTTPException(accessDenied.getMessage(), mNameSpace);
		} catch(NotFoundException notFound) {
			try {
				writeHTMLReply(ctx, 200, "OK", createRequestInfo(request,ctx).outer.generate());
			} catch (ToadletContextClosedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			throw new NotFoundPluginHTTPException(notFound.getMessage(), mNameSpace);
		} catch(RedirectException redirected) {
			try {
				writeTemporaryRedirect(ctx, redirected.getLocalizedMessage(), redirected.getLocation());
			} catch (ToadletContextClosedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//} catch(DownloadException forceDownload) {
			// This is to allow exporting the configuration.
			//    throw new DownloadPluginHTTPException(forceDownload.mData,
			//                                          forceDownload.mFilename,
			//                                          forceDownload.mMimeType);
			//} catch(ChildContainerException serverError) {
			//    throw new ServerPluginHTTPException(serverError.getMessage(), mNameSpace);
			//} catch(IOException ioError) {
			//    throw new ServerPluginHTTPException(ioError.getMessage(), mNameSpace);
		} catch(Exception ex) {
			System.err.println("WikiWebInterface::handleWebRequest failed: " + ex.getMessage());
			tmpResult = "Requested path " + request.getPath() + " can not be delivered. " + ex.getMessage() + "<br />Please report this message.<br />";
			tmpResult += createRequestInfo(request, ctx).content.generate();
		}
		//System.err.println("WikiWebInterface::handleWebRequest::should return a page with length " + tmpResult.length());
		return tmpResult;
		// IMPORTANT: Look at these catch blocks carefully. They bypass the freenet ContentFilter.
		//} catch(AccessDeniedException accessDenied) {
		 //   throw new AccessDeniedPluginHTTPException(accessDenied.getMessage(), mNameSpace);
		//} catch(NotFoundException notFound) {
		//	throw new NotFoundPluginHTTPException(notFound.getMessage(), mNameSpace);
		//} catch(RedirectException redirected) {
		//    throw new RedirectPluginHTTPException(redirected.getMessage(), redirected.getLocation());//redirected.getLocation());
		//} catch(DownloadException forceDownload) {
		// This is to allow exporting the configuration.
		//    throw new DownloadPluginHTTPException(forceDownload.mData,
		//                                          forceDownload.mFilename,
		//                                          forceDownload.mMimeType);
		//} catch(ChildContainerException serverError) {
		//    throw new ServerPluginHTTPException(serverError.getMessage(), mNameSpace);
		//} catch(IOException ioError) {
		//    throw new ServerPluginHTTPException(ioError.getMessage(), mNameSpace);
		//}
		//return mNameSpace;
	}
	
	private PageNode createRequestInfo(HTTPRequest req, ToadletContext ctx) { 
		// FIXME: return a content node only instead of PageNode
		URI uri = ctx.getUri();
		PageNode mPageNode = ctx.getPageMaker().getPageNode("HelloWorld InfoPage", true, ctx);
		mPageNode.content.addChild("br");
		mPageNode.content.addChild("span","You reached this page because the HelloWorld plugin did not found the requested URI");
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
