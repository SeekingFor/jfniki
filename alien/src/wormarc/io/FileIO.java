/* An Archive.IO implementation for files.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileIO extends StreamIO {
    private File mFile;

    protected File getFile() throws IOException {
        if (mFile == null) {
            throw new IOException("File not set!");
        }
        return mFile;
    }

    private static class StreamFactoryImpl implements StreamFactory {
        FileIO mFio;
        public void setTarget(FileIO fio) {
            mFio = fio;
        }
        public InputStream getInputStream() throws IOException { return new FileInputStream(mFio.getFile()); }
        public OutputStream getOutputStream() throws IOException { return new FileOutputStream(mFio.getFile()); }
        public boolean shouldCloseInputStream() { return true; }
        public boolean shouldCloseOutputStream() { return true;}
    }

    public FileIO() {
        super(new StreamFactoryImpl(), "");
        ((StreamFactoryImpl)mStreamFactory).setTarget(this);
    }

    public void setFile(File file) {
        mFile = file;
    }

    public void setFile(String fileName) {
        setFile(new File(fileName));
    }
}