/* An Archive.IO implementation for byte arrays.
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

package wormarc.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.List;

import wormarc.Archive;
import wormarc.Block;
import wormarc.HistoryLinkMap;
import wormarc.LinkDataFactory;

public class ByteArrayIO extends StreamIO {
    private byte[] mBlob;
    private ByteArrayOutputStream mBuffer;

   public ByteArrayIO() {
        super(new StreamFactoryImpl(), "");
        ((StreamFactoryImpl)mStreamFactory).setTarget(this);
    }

    public void setData(byte[] data) {
        mBlob = data;
    }

    public byte[] getData() {
        return mBlob;
    }

    // Clients should call this in a finally block after
    // reading or writing.
    public void release() {
        mBlob = null;
        mBuffer = null;
    }

    ////////////////////////////////////////////////////////////
    protected byte[] giveAwayBlob() {
        byte[] data = mBlob;
        mBlob = null;
        return data;
    }

    protected OutputStream createOutputStream() {
        mBuffer = new ByteArrayOutputStream();
        return mBuffer;
    }

    private static class StreamFactoryImpl implements StreamFactory {
        private ByteArrayIO mTarget;

        public void setTarget(ByteArrayIO io) {
            mTarget = io;
        }

        public InputStream getInputStream() throws IOException {
            byte[] data = mTarget.giveAwayBlob();
            if (data == null) {
                throw new IOException("Data not set.");
            }
            return new ByteArrayInputStream(data);
        }
        public OutputStream getOutputStream() throws IOException {
            return mTarget.createOutputStream();
        }
        public boolean shouldCloseInputStream() { return true; }
        public boolean shouldCloseOutputStream() { return true;}
    }

    ////////////////////////////////////////////////////////////
    public void write(HistoryLinkMap linkMap, List<Block> blocks, List<Archive.RootObject> rootObjects)
        throws IOException {
        mBlob = null;
        super.write(linkMap, blocks, rootObjects);
        mBlob = mBuffer.toByteArray(); // Hmmm 2x RAM.
        mBuffer = null; // 1x RAM
    }

    public Archive.ArchiveData read(HistoryLinkMap linkMap, LinkDataFactory linkFactory)
        throws IOException {
        return super.read(linkMap, linkFactory);
    }
}