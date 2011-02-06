/* ContentFilter implemented using freenet.client.filter.ContentFilter.
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
package fniki.freenet.filter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import freenet.client.filter.FilterCallback;
import freenet.client.filter.CommentException;
import freenet.client.filter.UnsafeContentTypeException;
import freenet.client.filter.HTMLFilter.ParsedTag;

import fniki.wiki.ContentFilter;
import fniki.wiki.ServerErrorException;

class WikiContentFilter implements ContentFilter, FilterCallback  {
    private String mContainerPrefix;
    private String mFproxyPrefix;
    private final static String UTF8 = "UTF-8";
    private static class FilterTrippedException extends RuntimeException {
        FilterTrippedException() { super("Freenet content filter tripped!"); }
    }

    private static void filterTripped() {
        throw new FilterTrippedException();
    }

    protected WikiContentFilter(String containerPrefix, String fproxyPrefix) {
        mContainerPrefix = containerPrefix;
        mFproxyPrefix = fproxyPrefix;
    }

    /**
     * Process a URI.
     * If it cannot be turned into something sufficiently safe, then return null.
     * @param overrideType Force the return type.
     * @throws CommentException If the URI is nvalid or unacceptable in some way.
     */
    public String processURI(String uri, String overrideType) throws CommentException {
        System.err.println("processURI(0): " + uri + " : " + overrideType);
        if (!(uri.startsWith(mContainerPrefix) || uri.startsWith(mFproxyPrefix))) {
            System.err.println("processURI(0): REJECTED URI");
            filterTripped();
            return null;
        }

        return uri;
    }

    /**
     * Process a URI.
     * If it cannot be turned into something sufficiently safe, then return null.
     * @param overrideType Force the return type.
     * @throws CommentException If the URI is nvalid or unacceptable in some way.
     */
    public String processURI(String uri, String overrideType, boolean noRelative, boolean inline) throws CommentException {
        // inline is true for images (which we allow mod URI filtering).
        // noRelative is true if you must return an absolute URI, which we don't allow.
        System.err.println("processURI(1): " + uri + " : " + overrideType + " : " + noRelative + " : " + inline);
        if (noRelative) {
            System.err.println("processURI(1): REJECTED URI because of noRelative.");
            filterTripped();
            return null;
        }
        return processURI(uri, overrideType);
    }

    /**
     * Process a base URI in the page. Not only is this filtered, it affects all
     * relative uri's on the page.
     */
    public String onBaseHref(String baseHref) {
        System.err.println("processBaseRef: " + baseHref);
        filterTripped();
        return null;
    }
    /**
     * Process plain-text. Notification only; can't modify.
     * Type can be null, or can correspond, for example to HTML tag name around text
     * (for example: "title").
     *
     * Note that the string will have been fed through the relevant decoder if
     * necessary (e.g. HTMLDecoder). It must be re-encoded if it is sent out as
     * text to a browser.
     */
    public void onText(String s, String type) {}

    /**
     * Process a form on the page.
     * @param method The form sending method. Normally GET or POST.
     * @param action The URI to send the form to.
     * @return The new action URI, or null if the form is not allowed.
     * @throws CommentException
     */
    public String processForm(String method, String action) throws CommentException {
        if (!(action.startsWith(mContainerPrefix) || action.startsWith(mFproxyPrefix))) {
            System.err.println("processForm: REJECTED URI");
            filterTripped();
            return null;
        }
        return action;
    }
    /**
     * Process a tag. If it needs changing, then return the changed
     * HTML, if not, then return null;
     * @param pt - The tag to be replaced
     * @return The new tag, or null, if it doesn't need changing
     * */
    public String processTag(ParsedTag pt) { return null; }
    ////////////////////////////////////////////////////////////

    // One off hacks to allow specific cases mangled by the filter.
    private final static String EXCEPTIONS[] = new String[] {
        // Mangled form
        "<input name=\"import\" type=\"submit\" value=\"Import Configuration\" />\n\n<hr>\n",
        // Allowed
        "<input name=\"import\" type=\"submit\" value=\"Import Configuration\"/>\n" +
        "<input type=\"file\" name=\"upload\" size=\"64\">\n<hr>\n",
        // Removed meta refresh
        "html><head>\n\n<title>",
        // Allowed.
        "html><head><meta http-equiv=\"refresh\" content=\"15\" /><title>\n"
    };

    // Allow a few safe, specific exceptions through the content filter.
    public String postProcess(String filtered, String unfiltered) {
        // System.err.println("--- unfiltered ---");
        // System.err.println(unfiltered);
        // System.err.println("--- filtered ---");
        // System.err.println(filtered);
        // System.err.println("---");
        int index = 0;
        while (index < EXCEPTIONS.length) {
            filtered = filtered.replace(EXCEPTIONS[index], EXCEPTIONS[index + 1]);
            index += 2;
        }
        return filtered;
    }

    public String filter(String html) throws ServerErrorException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            freenet.client.filter.ContentFilter.FilterStatus status =
                freenet.client.filter.ContentFilter.filter(new ByteArrayInputStream(html.getBytes(UTF8)),
                                                           baos, "text/html",
                                                           UTF8,
                                                           this);
            return postProcess(new String(baos.toByteArray(), UTF8), html);
        } catch (UnsafeContentTypeException ucte) {
            ucte.printStackTrace();
            throw new ServerErrorException("BUG: Generated dangerous html(0)??? But we caught it :-)");
        } catch (FilterTrippedException fte) {
            fte.printStackTrace();
            throw new ServerErrorException("BUG: Generated dangerous html(1)??? But we caught it :-)");
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new ServerErrorException("BUG: IOException validating page???");
        }
    }
}
