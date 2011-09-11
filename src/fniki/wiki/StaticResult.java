/* Static implementation of ChildContainerResult.
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

public class StaticResult implements ChildContainerResult {
    private final String mEncoding;
    private final String mMimeType;
    private final byte[] mData;
    private final String mTitle;
    private final int mMetaRefreshSeconds;

    public StaticResult(String encoding, String mimeType, String title, int refreshSeconds,
                        byte[] data) {
        mEncoding = encoding;
        mMimeType = mimeType;
        mTitle = title;
        mMetaRefreshSeconds = refreshSeconds;
        mData = data;
    }

    public String getEncoding() { return mEncoding; }
    public String getMimeType() { return mMimeType; }
    public byte[] getData() { return mData; }
    public String getTitle() { return mTitle; }
    public int getMetaRefreshSeconds() { return mMetaRefreshSeconds; }
}

