/* A UI subcomponent which display static wikitext from the .jar.
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

package fniki.wiki.child;

import java.io.IOException;

import static ys.wikiparser.Utils.*;

import fniki.wiki.ChildContainer;
import fniki.wiki.ChildContainerException;
import fniki.wiki.ServerErrorException;
import fniki.wiki.WikiContext;

public class StaticWikiText implements ChildContainer {
    private final String mResourcePath;
    private final String mTitle;

    public StaticWikiText(String resourcePath, String title) {
        mResourcePath = resourcePath;
        mTitle = title;
    }

    protected String getViewSourcePage(WikiContext context) throws ChildContainerException {
        String template = context.getString("/view_source.html", null);
        if (template == null) {
            throw new ServerErrorException("view_source.html template not in the .jar.");
        }

        String title = escapeHTML(mTitle);
        String source = escapeHTML(context.getString(mResourcePath, "Couldn't find requested resource in jar!"));
        return String.format(template, title, source);
    }

    public String handle(WikiContext context) throws ChildContainerException {
        try {
            if (context.getAction().equals("viewsrc")) {
                return getViewSourcePage(context);
            }
            String wikiText = context.getString(mResourcePath, "Couldn't find requested resource in jar!");
            return new WikiContainer().renderExternalWikiText(context, mTitle, context.getPath(), wikiText);
        } catch (IOException ioe) {
            throw new ServerErrorException("Couldn't render the requested static wikitext from the .jar.");
        }
    }
}