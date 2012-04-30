/* Helper class to do freesite insertion an USK updating.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.util.ArrayList;
import java.util.List;

import net.pterodactylus.fcp.AllData;
import net.pterodactylus.fcp.ClientGet;
import net.pterodactylus.fcp.ClientHello;
import net.pterodactylus.fcp.ClientPutComplexDir;
import net.pterodactylus.fcp.CloseConnectionDuplicateClientName;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.FcpMessage;
import net.pterodactylus.fcp.FileEntry;
import net.pterodactylus.fcp.GetFailed;
import net.pterodactylus.fcp.NodeHello;
import net.pterodactylus.fcp.PutFailed;
import net.pterodactylus.fcp.Priority;
import net.pterodactylus.fcp.ProtocolError;
import net.pterodactylus.fcp.PutSuccessful;
import net.pterodactylus.fcp.ReturnType;
import net.pterodactylus.fcp.SimpleProgress;
import net.pterodactylus.fcp.Verbosity;

import wormarc.IOUtil;

class FcpTools {
    ////////////////////////////////////////////////////////////
    // C&P from wormarc.io.FCPCommandRunner
    // because I didn't want to make it public in wormarc.
    ////////////////////////////////////////////////////////////

    private final static Verbosity VERBOSITY = Verbosity.ALL;
    private final static Priority PRIORITY = Priority.interactive;
    private final static int MAX_RETRIES = 6;
    private final static boolean DONT_COMPRESS = true;
    private final static String REAL_TIME_FIELD = "RealTimeFlag";
    private final static String REAL_TIME_VALUE = "true";

    // If a request takes longer than this it is assumed to have timed out.
    private final static int FOREVER_MS = 1000 * 60 * 60;

    private final static int WAIT_TIMEOUT_MS = 250;

    private static PrintStream sDebugOut = System.err;

    protected static void debug(String msg) {
        sDebugOut.println(msg);
    }

    public static void setDebugOutput(PrintStream out) {
        synchronized (FcpTools.class) {
            sDebugOut = out;
        }
    }

    static abstract class Command extends FcpAdapter {
        protected String mName;
        protected String mUri;
        protected FcpTools mRunner;
        protected String mFcpId;

        protected boolean mDone = false;
        protected String mFailureMsg = "";

        protected abstract void handleData(long length, InputStream data) throws IOException;
        protected abstract FcpMessage getStartMessage() throws IOException;

        protected void handleProgress(SimpleProgress simpleProgress) {
            debug(String.format("[%s]:(%d, %d, %d, %d, %d, %s)",
                                mName,
                                simpleProgress.getTotal(),
                                simpleProgress.getRequired(),
                                simpleProgress.getFailed(),
                                simpleProgress.getFatallyFailed(),
                                simpleProgress.getSucceeded(),
                                simpleProgress.isFinalizedTotal() ? "true" : "false"
                                ));
        }

        protected String makeFcpId() {
            return IOUtil.randomHexString(16);
        }

        protected Command(String name, String uri, FcpTools runner) {
            mName = name;
            mUri = uri;
            mRunner = runner;
            mFcpId = makeFcpId();
        }

        public synchronized String getUri() {
            return mUri;
        }

        public synchronized boolean finished() {
            return mDone;
        }

        public synchronized boolean succeeded() {
            if (!mDone) {
                throw new IllegalStateException("Not finished.");
            }
            return mFailureMsg.equals("");
        }

        public synchronized void raiseOnFailure() throws IOException {
            if (!mDone) {
                throw new IllegalStateException("Not finished.");
            }
            if (mFailureMsg.equals("")) {
                return;
            }
            if (mName == null) {
                mName = "unknown";
            }
            throw new IOException(String.format("%s:%s", mName, mFailureMsg));
        }

        protected void handleDone(String failureMsg) {
            if (failureMsg == null) {
                throw new IllegalArgumentException("failureMsg == null");
            }
            debug("handleDone: " + failureMsg);

            synchronized (this) {
                mFailureMsg = failureMsg;
                mDone = true;
            }
            synchronized (mRunner) {
                mRunner.commandFinished(this);
            }
        }

        public void receivedSimpleProgress(FcpConnection fcpConnection, SimpleProgress simpleProgress) {
            if (!simpleProgress.getIdentifier().equals(mFcpId)) {
                return;
            }
            handleProgress(simpleProgress);
        }

        public void receivedGetFailed(FcpConnection fcpConnection, GetFailed getFailed) {
            if (!getFailed.getIdentifier().equals(mFcpId)) {
                return;
            }

            // DCI: Handle too big! (code == 21). It means the
            handleDone(String.format("ClientGet failed: [%d]: %s",
                                     getFailed.getCode(),
                                     getFailed.getShortCodeDescription()));
        }

	public void receivedPutFailed(FcpConnection fcpConnection, PutFailed putFailed) {
            if (!putFailed.getIdentifier().equals(mFcpId)) {
                return;
            }

            handleDone(String.format("ClientPut failed: [%d]: %s",
                                     putFailed.getCode(),
                                     putFailed.getShortCodeDescription()));
        }

        public void receivedAllData(FcpConnection fcpConnection, AllData allData) {
            if (!allData.getIdentifier().equals(mFcpId)) {
                return;
            }
            String msg = "";
            try {
                handleData(allData.getDataLength(), allData.getPayloadInputStream());
            } catch (IOException ioe) {
                msg = "Failed processing downloaded data: " + ioe.getMessage();;
            } finally {
                handleDone(msg);
            }
        }

	public void receivedPutSuccessful(FcpConnection fcpConnection, PutSuccessful putSuccessful) {
            if (!putSuccessful.getIdentifier().equals(mFcpId)) {
                return;
            }
            mUri = putSuccessful.getURI();
            handleDone("");
        }

        public void receivedNodeHello(FcpConnection fcpConnection, NodeHello nodeHello) {
            handleDone("");
        }

        public void
            receivedCloseConnectionDuplicateClientName(FcpConnection fcpConnection,
                                                       CloseConnectionDuplicateClientName
                                                       closeConnectionDuplicateClientName) {
            handleDone("Duplicate Connection");
	}

        public void receivedProtocolError(FcpConnection fcpConnection, ProtocolError protocolError) {
            handleDone(String.format("Protocol Error[%d]: %s, %s",
                                     protocolError.getCode(),
                                     protocolError.getCodeDescription(),
                                     protocolError.getExtraDescription()
                                     ));

	}

	public void connectionClosed(FcpConnection fcpConnection, Throwable throwable) {
            handleDone("Connection Closed");
	}
    }

    static class HelloCommand extends Command {
        private String mClientName;
        protected HelloCommand(String clientName, FcpTools runner) {
            super("client_hello", "", runner);
            mClientName = clientName;
        }

        protected void handleData(long length, InputStream data) throws IOException {
            handleDone("Not expecting AllData");
        }

        protected FcpMessage getStartMessage() throws IOException {
            mFcpId = "only_client_hello";
            return new ClientHello(mClientName);
        }
    }

    private List<Command> mPending = new ArrayList<Command>();
    private FcpConnection mConnection;

    public FcpTools(String host, int port, String clientName)
        throws IOException, InterruptedException  {
        mConnection = new FcpConnection(host, port);
        mConnection.connect();
        sendClientHello(clientName);
        waitUntilAllFinished();
    }

    public synchronized void disconnect() {
        mConnection.disconnect();
    }

    protected synchronized HelloCommand sendClientHello(String clientName) throws IOException  {
        HelloCommand cmd = new HelloCommand(clientName, this);
        start(cmd);
        return cmd;
    }

    public synchronized void waitUntilAllFinished(int timeoutMs) throws InterruptedException {
        long maxTimeMs = System.currentTimeMillis() + timeoutMs;
        while (mPending.size() > 0) {
            if (System.currentTimeMillis() > maxTimeMs) {
                throw new InterruptedException("Timed out before all requests finished.");
            }
            wait(WAIT_TIMEOUT_MS); // Hmmmm...
        }
    }

    public synchronized void waitUntilAllFinished() throws InterruptedException {
        waitUntilAllFinished(FOREVER_MS);
    }

    protected synchronized void start(Command cmd) throws IOException {
        if (mPending.contains(cmd)) {
            throw new IllegalStateException("Command already started!");
        }
        mPending.add(cmd);
        mConnection.addFcpListener(cmd);
        boolean raised = true;
        try {
            debug("Starting: " + cmd.mName);
            FcpMessage fcpMsg = cmd.getStartMessage();
            mConnection.sendMessage(fcpMsg);
            raised = false;
        } finally {
            if (raised) {
                if (cmd.mFailureMsg.equals("")) {
                    cmd.mFailureMsg = "Aborted before start. Maybe the connection dropped?";
                }
                commandFinished(cmd);
            }
        }
    }

    protected void commandFinished(Command cmd) {
        synchronized (this) { // Can wait on runner
            if (!mPending.contains(cmd)) {
                return;
            }
            debug("Finished: " + cmd.mName + (cmd.mFailureMsg.equals("") ? ": SUCCEEDED" : ": FAILED: " + cmd.mFailureMsg));
            mPending.remove(cmd);
            mConnection.removeFcpListener(cmd);
            notifyAll();
        }
        synchronized (cmd) { // or individual commands.
            cmd.notifyAll();
        }
    }

    ////////////////////////////////////////////////////////////
    // Added code for site insertion.
    static class InsertFreesite extends Command {
        private final Iterable <FileInfo> mDataSource;
        private final String mDefaultName;

        public InsertFreesite(String name, String insertUri, Iterable <FileInfo> dataSource,
                              String defaultName, FcpTools runner) {
            super(name, insertUri, runner);
            mDataSource = dataSource;
            mDefaultName = defaultName;
            if (defaultName == null) {
                throw new IllegalArgumentException("defaultName is null");
            }
            for (FileInfo info : dataSource) {
                if (info.getName().equals(defaultName)) {
                    return;
                }
            }
            throw new IllegalArgumentException("Freesite default page not found:" + defaultName);
        }

        protected void handleData(long length, InputStream data) throws IOException {
            handleDone("Not expecting AllData");
        }

        protected FcpMessage getStartMessage() throws IOException {
            String uri = mUri;
            String siteName = null;
            if (uri.startsWith("CHK@")) {
                uri = "CHK@";
                // I tried setting the target file name for CHKs, but it doesn't work.
                //String[] fields = mUri.split("/");
                //siteName = fields[fields.length - 1];
                //System.err.println("sitename: " + siteName);
            }
            ClientPutComplexDir msg = new ClientPutComplexDir(mFcpId, mUri);
            // if (siteName != null) {
            //     msg.setTargetFilename(siteName);
            // }
            msg.setVerbosity(VERBOSITY);
            //msg.setDontCompress(DONT_COMPRESS);
            msg.setPriority(PRIORITY);
            msg.setField(REAL_TIME_FIELD, REAL_TIME_VALUE);
            msg.setMaxRetries(MAX_RETRIES);
            if (mDefaultName != null) {
                msg.setDefaultName(mDefaultName);
            }
            for (FileInfo info : mDataSource) {
                // BITCH:
                // This is horrible. jfcplib forces me to open a stream for every single
                // file at message construction time instead of taking an iterator / enumerable.
                msg.addFileEntry(FileEntry.createDirectFileEntry(info.getName(),
                                                                 info.getMimeType(),
                                                                 info.getLength(),
                                                                 info.getInputStream()));
            }

            return msg;
        }
    }


    static class CheckUsk extends Command {
        public CheckUsk(String name, String usk, FcpTools runner) {
            super(name, usk, runner);
        }

        protected void handleData(long length, InputStream data) throws IOException {
            handleDone("Not expecting AllData");
        }

        public void receivedGetFailed(FcpConnection fcpConnection, GetFailed getFailed) {
            if (!getFailed.getIdentifier().equals(mFcpId)) {
                return;
            }

            if (getFailed.getCode() == 27) {
                // Stash the updated URI in the redirect.
                mUri = getFailed.getRedirectURI();
            }

            // NOTE:
            // We expect code 29 for locally available data.
            // The request "fails" after checking for the redirect
            // because we gave it a fake mime type.
            // This is success.

            // We don't care how we failed (DNF, RNF, etc.),
            // just that we are done.
            handleDone("");
        }

        protected FcpMessage getStartMessage() throws IOException {
            ClientGet msg = new ClientGet(mUri, mFcpId, ReturnType.none);

            msg.setVerbosity(VERBOSITY);
            msg.setPriority(PRIORITY);
            msg.setField(REAL_TIME_FIELD, REAL_TIME_VALUE);
            msg.setMaxRetries(0);
            msg.setAllowedMimeTypes("fakemimetypetoforcefailure");

            // DON'T request data.
            msg.setDataStoreOnly(true);
            // NOTE: Also notice ReturnType.none above.

            return msg;
        }
    }

    public synchronized InsertFreesite sendInsertFreesite(String insertUri,
                                                          Iterable <FileInfo> dataSource,
                                                          String defaultName) throws IOException  {
        InsertFreesite cmd = new InsertFreesite("insert_freesite", insertUri,
                                                dataSource, defaultName, this);
        start(cmd);
        return cmd;
    }

    public synchronized CheckUsk sendCheckUsk(String usk) throws IOException  {
        CheckUsk cmd = new CheckUsk("check_usk", usk, this);
        start(cmd);
        return cmd;
    }

    public static FileInfo makeFileInfo(String name, byte[] data, String mimeType) {
        return new RAMFileInfo(name, data, mimeType);
    }
}