// Attribution: Derived from WikiParserDemo by Yaroslav Stavnichiy.
// Changes Copyright (c) 2010, 2011 Darrell Karbott
// Changes licensed under the GPL2 (or later).
// Original Copywright notice follows.
/*
 * Copyright (c) 2007 Yaroslav Stavnichiy, yarosla@gmail.com
 *
 * Latest version of this software can be obtained from:
 *   http://web-tec.info/WikiParser/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * If you make use of this code, I'd appreciate hearing about it.
 * Comments, suggestions, and bug reports welcome: yarosla@gmail.com
 */

package fniki.wiki;

import java.io.UnsupportedEncodingException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;


import static ys.wikiparser.Utils.*;
import ys.wikiparser.WikiParser;

public class FreenetWikiTextParser extends WikiParser {
    public interface ParserDelegate {
        // Return false to invoke the base class hander, true otherwise.
        boolean processedMacro(StringBuilder sb, String text);

        // We never want to call the base class.
        void appendLink(StringBuilder sb, String text);

        // We never want to call the base class.
        void appendImage(StringBuilder sb, String text);
    }

    private final ParserDelegate mParserDelegate;

    public FreenetWikiTextParser(String wikiText,
                                 ParserDelegate parserDelegate) {
        super();
        HEADING_LEVEL_SHIFT=0;
        mParserDelegate = parserDelegate;
        parse(wikiText);
    }

    @Override
    protected void appendImage(String text) {
        if (mParserDelegate == null) {
            return;
        }
        mParserDelegate.appendImage(sb, text);
    }

    public static String renderXHTML(String wikiText) {
        throw new RuntimeException("Use the constructor so you can pass in a ParserDelegate.");
    }

    @Override
    protected void appendLink(String text) {
        if (mParserDelegate == null) {
            return;
        }
        mParserDelegate.appendLink(sb, text);
    }

    @Override
    protected void appendMacro(String text) {
        if (mParserDelegate != null && mParserDelegate.processedMacro(sb, text)) {
            return;
        }
        super.appendMacro(text);
    }

    public static String escapeURL(String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }
}