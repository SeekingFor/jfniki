/* An implementation helper class for reading and writing data into Freenet.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import java.security.DigestInputStream;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.pterodactylus.fcp.AllData;
import net.pterodactylus.fcp.ClientGet;
import net.pterodactylus.fcp.ClientHello;
import net.pterodactylus.fcp.ClientPut;
import net.pterodactylus.fcp.CloseConnectionDuplicateClientName;
import net.pterodactylus.fcp.FcpAdapter;
import net.pterodactylus.fcp.FcpConnection;
import net.pterodactylus.fcp.GetFailed;
import net.pterodactylus.fcp.NodeHello;
import net.pterodactylus.fcp.PutFailed;
import net.pterodactylus.fcp.Priority;
import net.pterodactylus.fcp.ProtocolError;
import net.pterodactylus.fcp.PutSuccessful;
import net.pterodactylus.fcp.SimpleProgress;
import net.pterodactylus.fcp.Verbosity;

import net.pterodactylus.fcp.FcpMessage;

import wormarc.Block;
import wormarc.HistoryLinkMap;
import wormarc.IOUtil;
import wormarc.LinkDigest;

// Christ on a bike! is jfcplib really this complicated?
public class FCPCommandRunner {
    private final static Verbosity VERBOSITY = Verbosity.ALL;
    private final static Priority PRIORITY = Priority.interactive;
    private final static int MAX_RETRIES = 6;
    private final static boolean DONT_COMPRESS = true;
    private final static String REAL_TIME_FIELD = "RealTimeFlag";
    private final static String REAL_TIME_VALUE = "true";

    // If a request takes longer than this it is assumed to have timed out.
    private final static int FOREVER_MS = 1000 * 60 * 60;

    private static PrintStream sDebugOut = System.err;

    protected static void debug(String msg) {
        sDebugOut.println(msg);
    }

    public static void setDebugOutput(PrintStream out) {
        synchronized (FCPCommandRunner.class) {
            sDebugOut = out;
        }
    }

    static abstract class Command extends FcpAdapter {
        protected String mName;
        protected String mUri;
        protected FCPCommandRunner mRunner;
        protected String mFcpId;

        protected boolean mDone = false;
        protected String mFailureMsg = "";

        protected abstract void handleData(long length, InputStream data) throws IOException;
        protected abstract FcpMessage getStartMessage();

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

        protected Command(String name, String uri, FCPCommandRunner runner) {
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
            //debug("handleDone: " + failureMsg);

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
        protected HelloCommand(String clientName, FCPCommandRunner runner) {
            super("client_hello", "", runner);
            mClientName = clientName;
        }

        protected void handleData(long length, InputStream data) throws IOException {
            handleDone("Not expecting AllData");
        }

        protected FcpMessage getStartMessage() {
            mFcpId = "only_client_hello";
            return new ClientHello(mClientName);
        }
    }

    static class GetBlock extends Command {
        private long mLength;
        private Block mBlock;
        private String mHexDigest;
        private FreenetIO mIO;

        protected GetBlock(String name, String uri, long length, FreenetIO io, FCPCommandRunner runner) {
            super(name, uri, runner);
            mIO = io;
            mLength = length;
        }

        protected void handleData(long length, InputStream data) throws IOException {
            debug(String.format("[%s]:handleData received %d bytes", mName, length));
            if (mLength != length) {
                System.err.println("BLOCK IS WRONG LENGTH.");
                throw new IOException("Block is wrong length!");
            }
            if (data == null) {
                throw new IllegalArgumentException("data == null");
            }

            DigestInputStream digestInput = IOUtil.getSha1DigestInputStream(data);
            mBlock = mIO.readLinks(digestInput);
            LinkDigest digest = new LinkDigest(digestInput.getMessageDigest().digest());
            mHexDigest = digest.toString();
        }

        protected FcpMessage getStartMessage() {
            ClientGet msg = new ClientGet(mUri, mFcpId);
            msg.setVerbosity(VERBOSITY);
            msg.setPriority(PRIORITY);
            msg.setMaxSize(mLength); // DCI: test?
            msg.setField(REAL_TIME_FIELD, REAL_TIME_VALUE);
            msg.setMaxRetries(MAX_RETRIES);
            return msg;
        }

        public Block getBlock() { return mBlock; }
        public String getHexDigest() { return mHexDigest; }
    }

    static class PutBlock extends Command { // DCI: sleazy. How does stream get closed in failure cases?
        private long mLength;
        private DigestInputStream mData;
        private String mHexDigest;

        public PutBlock(String name, long length, InputStream data, FCPCommandRunner runner) throws IOException {
            super(name, "CHK@", runner);
            mLength = length;
            mData = IOUtil.getSha1DigestInputStream(data);
        }

        protected void handleData(long length, InputStream data) throws IOException {
            handleDone("Not expecting AllData");
        }

	public void receivedPutSuccessful(FcpConnection fcpConnection, PutSuccessful putSuccessful) {
            // BUG: DCI: why  does it hash "" when chk cache is disabled for re-insert.
            // is this a race condition???
            LinkDigest digest = new LinkDigest(mData.getMessageDigest().digest());
            mHexDigest = digest.toString();

            // Order important. This posts handleDone.
            super.receivedPutSuccessful(fcpConnection, putSuccessful);
        }

        protected FcpMessage getStartMessage() {
            ClientPut msg = new ClientPut(mUri, mFcpId);
            msg.setDataLength(mLength);
            msg.setPayloadInputStream(mData);
            msg.setVerbosity(VERBOSITY);
            msg.setDontCompress(DONT_COMPRESS);
            msg.setPriority(PRIORITY);
            msg.setMaxRetries(MAX_RETRIES);
            msg.setField(REAL_TIME_FIELD, REAL_TIME_VALUE);
            return msg;
        }
        public long getLength() { return mLength; }
        public String getHexDigest() { return mHexDigest; }
    }

    static class GetBlockChk extends Command { // DCI: sleazy. How does stream get closed in failure cases?
        private long mLength;
        private InputStream mData;
        public GetBlockChk(String name, long length, InputStream data, FCPCommandRunner runner) {
            super(name, "CHK@", runner);
            mLength = length;
            mData = data;
        }

        public long getLength() { return mLength; }

        protected void handleData(long length, InputStream data) throws IOException {
            handleDone("Not expecting AllData");
        }

        protected FcpMessage getStartMessage() {
            ClientPut msg = new ClientPut(mUri, mFcpId);
            msg.setDataLength(mLength);
            msg.setPayloadInputStream(mData);
            msg.setVerbosity(VERBOSITY);
            msg.setDontCompress(DONT_COMPRESS);
            msg.setPriority(PRIORITY);
            msg.setGetCHKOnly(true);
            msg.setMaxRetries(MAX_RETRIES);
            msg.setField(REAL_TIME_FIELD, REAL_TIME_VALUE);
            return msg;
        }
    }

    static class GetTopKey extends Command {
        private FreenetTopKey mTopKey;
        protected GetTopKey(String name, String uri, FCPCommandRunner runner) {
            super(name, uri, runner);
        }

        protected void handleData(long length, InputStream data) throws IOException {
            debug(String.format("[%s]:handleData received %d bytes", mName, length));
            mTopKey = FreenetTopKey.fromBytes(data);
        }

        protected FcpMessage getStartMessage() {
            ClientGet msg = new ClientGet(mUri, mFcpId);
            msg.setVerbosity(VERBOSITY);
            msg.setPriority(PRIORITY);
            msg.setMaxSize(FreenetTopKey.MAX_LENGTH);
            msg.setMaxRetries(MAX_RETRIES);
            msg.setField(REAL_TIME_FIELD, REAL_TIME_VALUE);
            return msg;
        }

        public FreenetTopKey getTopKey() { return mTopKey; }
    }

    static class PutTopKey extends Command {
        private FreenetTopKey mTopKey;

        protected PutTopKey(String name, String uri, FreenetTopKey topKey, FCPCommandRunner runner)
            throws IOException {
            super(name,  uri, runner);
            mTopKey = topKey;
        }

        protected void handleData(long length, InputStream data) throws IOException {
            handleDone("Not expecting AllData");
        }

        protected FcpMessage getStartMessage() {
            try {
                ClientPut msg = new ClientPut(mUri, mFcpId);
                // Hmmm... double read. Ok. it's small.
                long length = IOUtil.readAndClose(mTopKey.toBytes()).length;
                msg.setDataLength(length);
                msg.setPayloadInputStream(mTopKey.toBytes());
                msg.setVerbosity(VERBOSITY);
                msg.setDontCompress(DONT_COMPRESS);
                msg.setPriority(PRIORITY);
                msg.setMaxRetries(MAX_RETRIES);
                msg.setField(REAL_TIME_FIELD, REAL_TIME_VALUE);
                return msg;
            } catch (IOException ioe) {
                // Should never happen.
                throw new RuntimeException("Assertion Failure: Read of topkey bytes failed.", ioe);
            }
        }
    }

    static class InvertPrivateKey extends Command {
        private long mLength;
        private InputStream mData;

        public InvertPrivateKey(String name, String privateSSK, FCPCommandRunner runner) {
            super(name, privateSSK + "inverting", runner);
            byte[] raw = new byte[] {'d', 'u','m', 'm', 'y'};
            mLength = raw.length;
            mData = new ByteArrayInputStream(raw);
        }

        public long getLength() { return mLength; }

        // Can return null.
        public String getPublicSSK() {
            // SSK@/kRM~jJVREwnN2qnA8R0Vt8HmpfRzBZ0j4rHC2cQ-0hw,2xcoQVdQLyqfTpF2DpkdUIbHFCeL4W~2X1phUYymnhM,AQACAAE/
            final String KEY_START = "SSK@";
            final String KEY_END = ",AQACAAE/";
            final int pos = mUri.indexOf(KEY_END);
            if (mUri == null || !mUri.startsWith(KEY_START) || mUri.indexOf(KEY_END) == -1) {
                return null;
            }
            return mUri.substring(0, pos + KEY_END.length());
        }

        protected void handleData(long length, InputStream data) throws IOException {
            handleDone("Not expecting AllData");
        }

        protected FcpMessage getStartMessage() {
            ClientPut msg = new ClientPut(mUri, mFcpId);
            msg.setDataLength(mLength);
            msg.setPayloadInputStream(mData);
            msg.setVerbosity(VERBOSITY);
            msg.setDontCompress(DONT_COMPRESS);
            msg.setPriority(PRIORITY);
            msg.setGetCHKOnly(true); // Also works for SSKs.
            msg.setField(REAL_TIME_FIELD, REAL_TIME_VALUE);
            msg.setMaxRetries(MAX_RETRIES);
            return msg;
        }
    }

    ////////////////////////////////////////////////////////////
    private List<Command> mPending = new ArrayList<Command>();
    private FcpConnection mConnection;

    public FCPCommandRunner(String host, int port, String clientName)
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

    public synchronized GetBlock sendGetBlock(String uri, long length, int ordinal, FreenetIO io) throws IOException  {
        GetBlock cmd = new GetBlock(String.format("get_block_%d", ordinal),
                                    uri, length, io, this);
        start(cmd);
        return cmd;
    }

    public synchronized PutBlock sendPutBlock(int ordinal, HistoryLinkMap linkMap, Block block) throws IOException  {
        PutBlock cmd = new PutBlock(String.format("put_block_%d", ordinal),
                                    linkMap.getLength(block),  // Hmmm... Expensive?
                                    linkMap.getBinaryRep(block),
                                    this);
        start(cmd);
        return cmd;
    }

    public synchronized GetBlockChk sendGetBlockChk(int ordinal, HistoryLinkMap linkMap, Block block) throws IOException  {
        GetBlockChk cmd = new GetBlockChk(String.format("get_block_chk_%d", ordinal),
                                          linkMap.getLength(block),  // Hmmm... Expensive?
                                          linkMap.getBinaryRep(block),
                                          this);
        start(cmd);
        return cmd;
    }


    public synchronized GetTopKey sendGetTopKey(String uri) throws IOException  {
        GetTopKey cmd = new GetTopKey("get_top_key", uri, this);
        start(cmd);
        return cmd;
    }

    public synchronized PutTopKey sendPutTopKey(String uri, FreenetTopKey topKey) throws IOException  {
        PutTopKey cmd = new PutTopKey("put_top_key", uri, topKey, this);
        start(cmd);
        return cmd;
    }

    public synchronized InvertPrivateKey sendInvertPrivateKey(String privateSSKKey) throws IOException  {
        InvertPrivateKey cmd = new InvertPrivateKey("invert_private_ssk", privateSSKKey, this);
        start(cmd);
        return cmd;
    }

    public synchronized int getPendingCount() {
        return mPending.size();
    }

    // DCI: make this call raiseOnFailure for each request?
    public synchronized void waitUntilAllFinished(int timeoutMs) throws InterruptedException {
        long maxTimeMs = System.currentTimeMillis() + timeoutMs;
        while (mPending.size() > 0) {
            if (System.currentTimeMillis() > maxTimeMs) {
                // DCI:, LATER: Is this 100% correct. Should I be setting the interrupted flag too?
                throw new InterruptedException("Timed out before all requests finished.");
            }
            wait(250);
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

    public static void dump(FcpMessage msg) {
        debug(msg.getName());
        Iterator<String> itr = msg.iterator();
        while(itr.hasNext()) {
            String key = itr.next();
            debug(key + "=" + msg.getFields().get(key));
        }
        debug("EndMessage");
        // DCI: way to see if payloadinputstream is set without dorking FcpMessage?
    }
}
