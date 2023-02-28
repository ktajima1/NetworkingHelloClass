package edu.sjsu.cs158a;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import java.util.concurrent.Callable;

@CommandLine.Command
public class HelloUDP implements Callable<Integer> {
    final static private short HELLO = 1;
    final static private short TRANSFER = 2;
    final static private short CHECKSUM = 3;
    final static private short ERROR = 5;
    private static final int retryLimit = 10;
    public static final String INTRO = "hello, i am ";

    static private void checkError(ByteBuffer bb) {
        if (bb.getShort() == ERROR) {
            // this runtime exception will bubble up and end the program
            throw new RuntimeException("error received: " + new String(bb.array(), bb.position(), bb.remaining()));
        }
        bb.position(0);
    }

    // send a packet and wait for a reply
    private static DatagramPacket sendRecv(DatagramSocket dsock, DatagramPacket toSend) throws IOException {
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
            try {
                dsock.send(toSend);
                while (true) {
                    var recvBuffer = new byte[512];
                    var received = new DatagramPacket(recvBuffer, recvBuffer.length);
                    dsock.receive(received);
                    bb = ByteBuffer.wrap(recvBuffer);
                    bb.limit(received.getLength());
                    checkError(bb);
                    short recvType = bb.getShort();
                    int recvConversation = -1;
                    int recvOffset = -1;
                    if (type == TRANSFER || type == CHECKSUM) {
                        recvConversation = bb.getInt();
                    }
                    if (type == TRANSFER) {
                        recvOffset = bb.getInt();
                    }
                    if (type != recvType || (conversation != recvConversation) || (offset != recvOffset)) {
                        System.out.printf("dup packet: %d ?= %d, %d ?= %d, %d ?= %d\n", type, recvType, conversation, recvConversation, offset, recvOffset);
                        continue;
                    }
                    // if we get here, we are done, so return the datagram
                    return received;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("timeout");
            }
        }
    }

    @Spec CommandSpec spec;
    @Parameters(description = "host:port of server to connect to")
    void setHostPort(String hostPort) {
        var lastColon = hostPort.lastIndexOf(':');
        try {
            dest = new InetSocketAddress(InetAddress.getByName(hostPort.substring(0, lastColon)), Integer.parseInt(hostPort.substring(lastColon+1)));
        } catch (UnknownHostException e) {
            throw new ParameterException(spec.commandLine(), e.getMessage());
        } catch (Exception e) {
            throw new ParameterException(spec.commandLine(), "port must be a number between 0 and 65535");
        }
    }
    SocketAddress dest;

    @Parameters(description = "file to transfer.")
    File file;

    public static void main(String[] args) {
        System.exit(new CommandLine(new HelloUDP()).execute(args));
    }

    public Integer call() {
        try {
            var startClock = System.currentTimeMillis();
            var dsock = new DatagramSocket();
            // max ping from my house was 13ms, so 8 times that should
            // be pretty safe
            dsock.setSoTimeout(100);
            // no packet should be bigger than 110, but perhaps a bigger error
            // packet could come back
            byte[] bytes = new byte[200];
            DatagramPacket dpack = new DatagramPacket(bytes, bytes.length);
            dpack.setSocketAddress(dest);
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            // just put your name. no SSID
            bb.putShort(HELLO).put(INTRO.getBytes()).put("Keigo Tajima".getBytes()).flip();
            dpack.setLength(bb.remaining());
            var helloRecv = sendRecv(dsock, dpack);
            var recvBB = ByteBuffer.wrap(helloRecv.getData(), helloRecv.getOffset(), helloRecv.getLength());
            checkError(recvBB);
            recvBB.getShort();
            int conversationId = recvBB.getInt();

            // extract out the name of who we are talking to
            String mess = new String(helloRecv.getData(), recvBB.position(), recvBB.remaining());
            String[] parts = mess.split(" ", 4);
            if (!mess.startsWith(INTRO)) {
                for (int i = 0; i < INTRO.length(); i++) {
                    if (mess.charAt(i) != INTRO.charAt(i)) {
                        System.out.println("Diff " + mess.charAt(i) + "!=" + INTRO.charAt(i) + " @ " + i);
                    }
                }
                throw new RuntimeException("got bad response: " + mess + " not " + INTRO);
            }
            System.out.println("Talkng with " + parts[3]);

            var buff = new byte[100];
            var fis = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("SHA-256"));
            int rc;
            int offset = 0;
            while ((rc = fis.read(buff)) > 0) {
                // System.out.println("sending offset: " + offset);
                bb.clear().putShort(TRANSFER).putInt(conversationId).putInt(offset).put(buff, 0, rc).flip();
                dpack.setLength(bb.remaining());
                var transferRecv = sendRecv(dsock, dpack);
                recvBB = ByteBuffer.wrap(transferRecv.getData(), transferRecv.getOffset(), transferRecv.getLength());
                checkError(recvBB);
                offset += rc;
            }

            bb.clear().putShort(CHECKSUM).putInt(conversationId).put(fis.getMessageDigest().digest(), 0, 8).flip();
            dpack.setLength(bb.remaining());
            var checksumRecv = sendRecv(dsock, dpack);
            recvBB = ByteBuffer.wrap(checksumRecv.getData(), checksumRecv.getOffset(), checksumRecv.getLength());
            checkError(recvBB);
            recvBB.getShort(); // skip the type
            recvBB.getInt(); // skip the conv id
            if (recvBB.get() == 0) {
                System.out.printf("success! Took about %dms\n", System.currentTimeMillis() - startClock);
                return 0;
            } else {
                System.out.println("failure: " + new String(bytes, bb.position(), bb.remaining()));
                return 1;
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return 1;
    }
}
