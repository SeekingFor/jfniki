/* A collection of IO utility functions.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;

import java.util.Random;

public class IOUtil {
    private final static Random sRandom = new Random();
    private final static int BUF_LEN = 1024 * 32;

    // Don't change without reviewing code.
    public final static String UTF8 = "utf8";

    public static void silentlyClose(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ioe) {
            // NOP
        }
    }

    public final static void copyAndClose(InputStream fromStream, OutputStream toStream) throws IOException {
        if (fromStream == null || toStream == null) {
            throw new IllegalArgumentException();
        }

        try {
            byte[] buffer = new byte[BUF_LEN];
            while (true) {
                int bytesRead = fromStream.read(buffer);
                if (bytesRead == -1) {
                    return;
                }
                toStream.write(buffer, 0, bytesRead);
            }
        }
        finally {
            try {
                fromStream.close();
            }
            finally {
                toStream.close();
            }
        }
    }

    public final static DigestInputStream getSha1DigestInputStream(InputStream fromStream)
        throws IOException {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA");
            return new DigestInputStream(fromStream, sha1);
        }
        catch (NoSuchAlgorithmException nsae) {
            fromStream.close();
            throw new IOException("Couldn't load SHA1 algorithm.");
        }
    }

    private static class NullOutputStream extends OutputStream {
        public void write(int b) throws IOException {}
        public void write(byte[] b,
                          int off,
                          int len)
            throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || off + len > b.length) {
                throw new IndexOutOfBoundsException();
            }
        }
    }

    // Hmmm... old code was probably faster.
    // Closes stream.
    public final static LinkDigest getFileDigest(InputStream fromStream) throws IOException {
        DigestInputStream inputStream = getSha1DigestInputStream(fromStream);
        try {
            copyAndClose(inputStream, new NullOutputStream());
            return new LinkDigest(inputStream.getMessageDigest().digest());
        }
        finally {
            fromStream.close();
        }
    }

    public final static void copyAndClose(InputStream fromStream, String toFileName) throws IOException {
        copyAndClose(fromStream, new FileOutputStream(toFileName));
    }

    public final static byte[] readAndClose(InputStream fromStream) throws IOException {
        if (fromStream == null) {
            throw new IllegalArgumentException();
        }

        ByteArrayOutputStream toStream = new ByteArrayOutputStream();
        copyAndClose(fromStream, toStream);

        byte[] bytes = toStream.toByteArray();
        return bytes;
    }

    public final static byte[] readFully(String inputFile) throws IOException {
        return readAndClose(new FileInputStream(inputFile));
    }

    public final static void writeFully(byte[] data, String outputFile) throws IOException {
        copyAndClose(new ByteArrayInputStream(data), new FileOutputStream(outputFile));
    }

    public final static String readUtf8StringAndClose(InputStream fromStream) throws IOException {
        ByteArrayOutputStream toStream = new ByteArrayOutputStream();
        copyAndClose(fromStream, toStream);
        return new String(toStream.toByteArray(), UTF8);
    }

    public final static InputStream toStreamAsUtf8(String value) throws IOException {
        return new ByteArrayInputStream(value.getBytes(UTF8));
    }

    // ATTRIBUTION: mmyers, SO
    // http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
    public final static byte[] fromHexString(final String encoded) {
        if ((encoded.length() % 2) != 0)
            throw new IllegalArgumentException("Input string must contain an even number of characters");

        final byte result[] = new byte[encoded.length()/2];
        final char enc[] = encoded.toCharArray();
        for (int i = 0; i < enc.length; i += 2) {
            StringBuilder curr = new StringBuilder(2);
            curr.append(enc[i]).append(enc[i + 1]);
            result[i/2] = (byte) Integer.parseInt(curr.toString(), 16);
        }
        return result;
    }

    // ATTRIBUTION: Peter Lawrey, SO
    // http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-le
    public final static String toHexString(byte[] bytes, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%1$02x", b));
            maxBytes -= 1;
            if (maxBytes == 0) {
                break;
            }
        }
        return sb.toString();
    }

    public final static String randomHexString(int length) {
        if (length < 1) {
            throw new IllegalArgumentException();
        }
        byte[] bytes = new byte[length];
        sRandom.nextBytes(bytes);
        return toHexString(bytes, bytes.length);
    }

    // ATTRIBUTION: erikson, SO
    // http://stackoverflow.com/questions/779519/delete-files-recursively-in-java
    public final static void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            throw new FileNotFoundException("Failed to delete file: " + f);
        }
    }
    public final static void delete(String fileOrDirectory) throws IOException { delete( new File(fileOrDirectory) ); }

    ////////////////////////////////////////////////////////////
    // Binary IO
    ////////////////////////////////////////////////////////////

    public static byte[] readBytes(DataInputStream source, int numberOfBytes) throws IOException {
        byte[] data = new byte[numberOfBytes];
        int count = 0;
        while (count < data.length) {
            int bytesRead = source.read(data, count, data.length - count);
            if (bytesRead == -1) {
                throw new IOException("Unexpected EOF reading bytes");
            }
            count += bytesRead;
        }
        return data;
    }

    // DCI: remove? is there other code that should be using this?
    // public static void writeString(DataOutputStream outputStream, String value) throws IOException {
    //     byte[] bytes = value.getBytes(UTF8);
    //     if (bytes.length > 32767) { // DCI: is this required?
    //         throw new IOException("Length doesn't fit in a signed short");
    //     }

    //     outputStream.writeShort(bytes.length);
    //     outputStream.write(bytes);
    // }

    public static String readString(DataInputStream source) throws IOException {
        int length = source.readShort();
        if (length > 32767) { // DCI: is this required?
            throw new IOException("Length doesn't fit in a signed short");
        }
        return new String(readBytes(source, length), UTF8);
    }
}
