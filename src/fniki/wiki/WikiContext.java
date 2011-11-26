/* Interface to represent the state of a jfniki wiki.
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

import java.io.IOException;

// Add wiki specific functionality.
public interface WikiContext extends Request {
    String getAction();
    String getTitle(); // hmmm

    WikiTextStorage getStorage() throws IOException;
    WikiTextChanges getRemoteChanges() throws IOException;
    FreenetWikiTextParser.ParserDelegate getParserDelegate();

    // Client must deal with url escaping.
    String makeLink(String containerRelativePath);

    String getString(String keyName, String defaultValue);
    int getInt(String keyName, int defaultValue);

    boolean isCreatingOuterHtml();

    Configuration getConfiguration();
    Configuration getDefaultConfiguration();

    // Returns a public fmsId.
    // e.g. djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
    //
    // fmsId is the human readable part, can be null
    // private SSK is the private SSK for the FMS identity.
    // This can return "???" on failure or if the args are null.
    String getPublicFmsId(String fmsId, String privateSSK);

    // throws unchecked Configuration.ConfigurationException
    void setConfiguration(Configuration config);

    // throwable can be null
    void logError(String msg, Throwable throwable);

    void raiseRedirect(String toLocation, String msg) throws RedirectException; // 302
    // LATER: Remove? this is not used
    void raiseNotFound(String msg) throws NotFoundException;  // 404
    void raiseAccessDenied(String msg) throws AccessDeniedException;  // 403
    void raiseServerError(String msg) throws ServerErrorException;  // 500

    // This is so we can play nice with the Freenet plugin API.
    // Force a download to disk of data.
    void raiseDownload(byte[] data, String filename, String mimeType) throws DownloadException;

    // Raise an AccessDeniedException if the form password
    // is not present and valid.
    void checkFormPassword() throws AccessDeniedException;

    // Reads a template file out of the jar and fills
    // in the values.
    String fillInTemplate(String templateName, String... values)
        throws ServerErrorException;
}