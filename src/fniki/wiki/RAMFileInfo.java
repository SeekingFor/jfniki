/* A FileInfo implementation that keeps all data in RAM.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.Serializable;

import wormarc.IOUtil;

public class RAMFileInfo implements FileInfo, Serializable {
    static final long serialVersionUID = 6418124706846251906L;
    private final String mName;
    private final byte[] mData;
    private final String mMimeType;

    public RAMFileInfo(String name, byte[] data, String mimeType) {
        mName = name;
        mData = data;
        mMimeType = mimeType;
    }

    // Sleazy. Freeze an info. Only works if FileInfo
    // is static.  I.e. always returns the same data.
    public RAMFileInfo(FileInfo info) throws IOException {
        mName = info.getName();
        mMimeType = info.getMimeType();
        mData = IOUtil.readAndClose(info.getInputStream());
    }

    public String getName() { return mName; }
    public String getMimeType() { return mMimeType; }
    public int getLength() { return mData.length; }
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(mData);
    }
}

