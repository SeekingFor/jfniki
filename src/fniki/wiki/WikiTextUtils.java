/* Helper methods for manipulating wikitext.
 *
 * Copyright (C) 2010, 2011, 2012 Darrell Karbott
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

public class WikiTextUtils {
    private final static String FOOTER_PAGE = "__meta__footer__";
    private final static String HEADER_PAGE = "__meta__header__";
    private static String getTemplateWikiText(WikiTextStorage storage,
                                              String templatePage,
                                              String name)
        throws IOException {
        if (name.startsWith("__meta__")) {
            // I am not sure this is right, but not doing
            // it is definitely wrong.
            // i.e. don't show the headers / footers as you are
            // editing them.
            return "";
        }
        if (!storage.hasPage(templatePage)) {
            return "";
        }
        return storage.getPage(templatePage);
    }

    public static String getPageWithHeaderAndFooter(WikiTextStorage storage,
                                                    String name)
        throws IOException {
        return
            // Header
            getTemplateWikiText(storage,
                                HEADER_PAGE,
                                name) +
            // WikiText
            storage.getPage(name) +

            // Footer
            getTemplateWikiText(storage,
                                FOOTER_PAGE,
                                name);
    }
}

