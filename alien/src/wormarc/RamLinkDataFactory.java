/* A LinkDataFactory which makes LinkData instances that are stored in RAM.
 *
 *  Copyright (C) 2010, 2011 Darrell Karbott
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.0 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 *  Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package wormarc;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.EOFException;
import java.security.MessageDigest;

class RamLinkData implements LinkData {
    private final byte[] mData;

    RamLinkData(byte[] data) {
        mData = data;
    }

    public InputStream openInputStream() throws IOException {
        return new ByteArrayInputStream(mData);
    }

    public void copyTo(OutputStream destination) throws IOException {
        destination.write(mData);
    }

    public byte[] copyTo(byte[] destination) throws IOException {
        System.arraycopy(mData, 0, destination, 0, destination.length);
        return destination;
    }
}

// INTENT: decouple rep of link data from presentation implementation (BinaryRep).
//        e.g. RAM storage, single file storage, multiple file storage.
public class RamLinkDataFactory implements  LinkDataFactory {
    private static LinkDataFactory mInstance;
    public LinkData makeLinkData(InputStream source, long length, MessageDigest messageDigest) throws IOException {
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Can't allocate a buffer that big. Sorry :-(");
        }
        byte[] data = new byte[(int)length];
        int count = 0;
        while (count < data.length) {
            int bytesRead = source.read(data, count, data.length - count);
            if (bytesRead == -1) {
                throw new EOFException("Unexpected EOF reading RamLinkData.");
            }
            messageDigest.update(data, count, bytesRead);
            count += bytesRead;
        }
        return new RamLinkData(data);
    }

    public final static LinkDataFactory instance() {
        if (mInstance == null) {
            mInstance = new RamLinkDataFactory();
        }
        return mInstance;
    }
}
