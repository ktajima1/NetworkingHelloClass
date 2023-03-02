package edu.sjsu.cs158a;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.sjsu.cs158a.HelloUDP.CliUtil.debug;
import static edu.sjsu.cs158a.HelloUDP.CliUtil.error;
import static edu.sjsu.cs158a.HelloUDP.CliUtil.info;

@CommandLine.Command
public class HelloUDP implements Callable<Integer> {
    public static final String INTRO = "hello, i am ";
    final static private short HELLO = 1;
    final static private short TRANSFER = 2;
    final static private short CHECKSUM = 3;
    final static private short ERROR = 5;
    private static final int retryLimit = 10;
    AtomicInteger dupCounter = new AtomicInteger();
    AtomicInteger reorderCounter = new AtomicInteger();
    ThreadLocal<DatagramPacket> reorderPacket = new ThreadLocal<>();
    @Spec
    CommandSpec spec;
    SocketAddress dest;

    @Parameters(index = "0", description = "host:port of server to connect to")
    void setHostPort(String hostPort) {
        var lastColon = hostPort.lastIndexOf(':');
        try {
            dest = new InetSocketAddress(InetAddress.getByName(hostPort.substring(0, lastColon)), Integer.parseInt(hostPort.substring(lastColon + 1)));
        } catch (UnknownHostException e) {
            throw new ParameterException(spec.commandLine(), e.getMessage());
        } catch (Exception e) {
            throw new ParameterException(spec.commandLine(), "host:port must be a hostname or IP address and a port number between 0 and 65535");
        }
    }

    @Parameters(index = "1..*", arity = "1..*", description = "file(s) to transfer.")
    File[] files;
    @Option(names = "--dup-packets")
    boolean dupPackets;
    @Option(names = "--reorder")
    boolean reorderPackets;
    @Option(names = "--delay")
    int delay;
    @Option(names = "--show-time")
    boolean showTime;
    @Option(names = "--debug")
    boolean debug;

    @Option(names = "--badchecksum")
    boolean badChecksum;

    static private void checkError(ByteBuffer bb) {
        if (bb.getShort() == ERROR) {
            // this runtime exception will bubble up and end the program
            throw new RuntimeException("error received: " + new String(bb.array(), bb.position(), bb.remaining()));
        }
        bb.position(0);
    }

    public static void main(String[] args) {
        System.exit(new CliUtil(new HelloUDP()).execute(args));
    }

    // send a packet and wait for a reply
    private DatagramPacket sendRecv(DatagramSocket dsock, DatagramPacket toSend) throws IOException {
        int count = 0;

        // extract out key fields to make sure we get the correct reply
        ByteBuffer bb = ByteBuffer.wrap(toSend.getData(), toSend.getOffset(), toSend.getLength());
        int conversation = -1;
        int offset = -1;
        short type = bb.getShort();
        if (type == TRANSFER || type == CHECKSUM) {
            // TRANSFER and CHECKSUM will have the conversation id
            conversation = bb.getInt();
        }
        if (type == TRANSFER) {
            // only TRANSFER has an offset
            offset = bb.getInt();
        }

        while (true) {
            if (count++ > retryLimit) {
                throw new IOException(String.format("More than %d retries", retryLimit));
            }
            long startTime = 0;
            try {
                dsock.send(toSend);
                if (reorderPacket.get() != null) {
                    dsock.send(reorderPacket.get());
                    reorderPacket.remove();
                }
                if (type != HELLO && dupPackets && (dupCounter.getAndIncrement() % 3) == 0) {
                    dsock.send(toSend);
                }
                if (type != HELLO && reorderPackets && (reorderCounter.getAndIncrement() % 3) == 0) {
                    reorderPacket.set(new DatagramPacket(toSend.getData().clone(), toSend.getOffset(), toSend.getLength(), toSend.getSocketAddress()));
                }
                while (true) {
                    var recvBuffer = new byte[512];
                    var received = new DatagramPacket(recvBuffer, recvBuffer.length);
                    startTime = System.currentTimeMillis();
                    dsock.receive(received);
                    debug("recv {0} ms", System.currentTimeMillis() - startTime);
                    bb = ByteBuffer.wrap(recvBuffer);
                    bb.limit(received.getLength());
                    checkError(bb);
                    short recvType = bb.getShort();
                    int recvConversation = -1;
                    int recvOffset = -1;
                    if (recvType == TRANSFER || recvType == CHECKSUM) {
                        recvConversation = bb.getInt();
                    }
                    if (recvType == TRANSFER) {
                        recvOffset = bb.getInt();
                    }
                    if (type != recvType || (conversation != recvConversation) || (offset != recvOffset)) {
                        debug("dup packet: %d ?= %d, %d ?= %d, %d ?= %d\n", type, recvType, conversation, recvConversation, offset, recvOffset);
                        continue;
                    }
                    // if we get here, we are done, so return the datagram
                    return received;
                }
            } catch (SocketTimeoutException e) {
                debug("timeout {0} ms", System.currentTimeMillis() - startTime);
            }
        }
    }

    public Integer call() throws InterruptedException {
        Random rand = new Random();
        CliUtil.enableDebug(debug);
        final var baseName = "ottoTA";
        var threads = new ArrayList<Thread>();


        for (int i = 0; i < files.length; i++) {
            final var file = files[i];
            var name = baseName + i;
            var t = new Thread(() -> {
                try (var dsock = new DatagramSocket()) {
                    // max ping from my house was 13ms, so 5 times that should
                    // be pretty safe
                    dsock.setSoTimeout(60);

                    // no packet should be bigger than 110, but perhaps a bigger error
                    // packet could come back
                    byte[] bytes = new byte[200];
                    var startClock = System.currentTimeMillis();
                    DatagramPacket dpack = new DatagramPacket(bytes, bytes.length);
                    dpack.setSocketAddress(dest);
                    ByteBuffer bb = ByteBuffer.wrap(bytes);
                    // just put your name. no SSID
                    bb.putShort(HELLO).put(INTRO.getBytes()).put(name.getBytes()).flip();
                    dpack.setLength(bb.remaining());
                    var helloRecv = sendRecv(dsock, dpack);
                    var recvBB = ByteBuffer.wrap(helloRecv.getData(), helloRecv.getOffset(), helloRecv.getLength());
                    checkError(recvBB);
                    recvBB.getShort();
                    int conversationId = recvBB.getInt();

                    // extract out the name of who we are talking to
                    String mess = new String(helloRecv.getData(), recvBB.position(), recvBB.remaining());
                    if (!mess.startsWith(INTRO)) {
                        for (int j = 0; j < INTRO.length(); j++) {
                            if (mess.charAt(j) != INTRO.charAt(j)) {
                                CliUtil.fatal("Diff " + mess.charAt(j) + "!=" + INTRO.charAt(j) + " @ " + j);
                            }
                        }
                        throw new RuntimeException("got bad response: " + mess + " not " + INTRO);
                    }

                    var buff = new byte[100];
                    var fis = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("SHA-256"));
                    int rc;
                    int offset = 0;
                    while ((rc = fis.read(buff)) > 0) {
                        bb.clear().putShort(TRANSFER).putInt(conversationId).putInt(offset).put(buff, 0, rc).flip();
                        dpack.setLength(bb.remaining());
                        Thread.sleep(delay);
                        var transferRecv = sendRecv(dsock, dpack);
                        recvBB = ByteBuffer.wrap(transferRecv.getData(), transferRecv.getOffset(), transferRecv.getLength());
                        checkError(recvBB);
                        offset += rc;
                    }

                    byte[] digest = fis.getMessageDigest().digest();
                    if (badChecksum) {
                        //  mix the digest up if we want a bad checksum
                        rand.nextBytes(digest);
                    }
                    bb.clear().putShort(CHECKSUM).putInt(conversationId).put(digest, 0, 8).flip();
                    dpack.setLength(bb.remaining());
                    var checksumRecv = sendRecv(dsock, dpack);
                    recvBB = ByteBuffer.wrap(checksumRecv.getData(), checksumRecv.getOffset(), checksumRecv.getLength());
                    checkError(recvBB);
                    recvBB.getShort(); // skip the type
                    recvBB.getInt(); // skip the conv id
                    rc = recvBB.get();
                    if (rc == 0) {
//                        System.out.printf("success! Took about %dms\n", System.currentTimeMillis() - startClock);
                        if (showTime) {
                            info("success! Took about {0}ms\n", (System.currentTimeMillis() - startClock));
                        }
                    } else {
                        error("failure: {0}", rc);
                    }
                } catch (IOException | NoSuchAlgorithmException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
            threads.add(t);
            t.start();
        }
        for (var t : threads) {
            t.join();
        }
        return 0;
    }

    public static class CliUtil extends CommandLine {
        static private boolean debugEnabled;
        static private boolean timestampEnabled;
        static private CliUtil cli;

        public CliUtil(Object command) {
            super(command);
            cli = this;
        }

        static public void enableDebug(boolean enable) {
            debugEnabled = enable;
        }

        static public void enableTimestamp(boolean enabled) {
            timestampEnabled = enabled;
        }

        static private int getScreenWidth() {
            return cli.getCommandSpec().usageMessage().width();
        }

        static private void coloredOut(String color, String format, Object... args) {
            var rawMessage = MessageFormat.format(format, args);
            var stylizedMessage = Help.Ansi.AUTO.string(MessageFormat.format("{0} @|{1} {2}|@", timestampEnabled ? new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date()) : "", color, rawMessage));
            var line = new Help.Column(getScreenWidth(), 0, Help.Column.Overflow.WRAP);
            var txtTable = Help.TextTable.forColumns(Help.defaultColorScheme(Help.Ansi.AUTO), line);
            txtTable.indentWrappedLines = 0;
            txtTable.addRowValues(stylizedMessage);
            System.out.print(txtTable);
            System.out.flush();
        }

        static public void fatal(String format, Object... args) {
            error(format, args);
            System.exit(2);
        }

        static public void error(String format, Object... args) {
            coloredOut("red", format, args);
        }

        static public void simpleError(String err) {
            error("{0}", err);
        }

        static public void warn(String format, Object... args) {
            coloredOut("yellow", format, args);
        }

        static public void info(String format, Object... args) {
            coloredOut("blue", format, args);
        }

        static public void debug(String format, Object... args) {
            if (!debugEnabled) return;
            coloredOut("magenta", format, args);
        }
    }
}
