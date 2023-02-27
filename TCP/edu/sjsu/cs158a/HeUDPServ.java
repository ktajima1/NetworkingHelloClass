package edu.sjsu.cs158a;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.DigestInputStream;
import java.security.MessageDigest;

//ISTG IF SETPORT IS CONSIDERED AN UNEXPECTED ERROR REQUIREMENT, THAT WILL REQUIRE REWORK

public class HeUDPServ {
    final static private short HELLO = 1;
    final static private short TRANSFER = 2;
    final static private short CHECKSUM = 3;
    final static private short ERROR = 5;
    private static final int retryLimit = 10;
    public static final String INTRO = "hello, i am ";

    private static int port;
    private static int convoNum;

    public HeUDPServ(String port, int convoNum) {
        this.port = setPort(port);
        this.convoNum = convoNum;
    }

    public static void main(String[] args) throws IOException {
        if (args.length==0) {
            System.out.println("Missing required parameter: '<port>'");
            System.exit(0);
        }
        //Client conversation number (will increment as number of clients increase)
        int convoNum = 1;
        HeUDPServ client = new HeUDPServ(args[0], convoNum);
        convoNum++; //increment global conversation number

        try(var sock = new DatagramSocket(client.getPort())) {
//            System.out.println("I made it here " + client.getPort());
            receiveAndSend(sock, client);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    static void sendError(DatagramSocket dsock, InetAddress address, int port, String errMessage) throws IOException {
        var bytes = new byte[512];
        var bb = ByteBuffer.wrap(bytes);
        var response = new DatagramPacket(bytes, bytes.length);
        response.setAddress(address);   //Set response packet address to the source address of received packet
        response.setPort(port);         //Set destination port as the source port of received packet

        //Set content of server response packet
        bb.putShort(ERROR).put(errMessage.getBytes()).flip();
        response.setLength(bb.remaining());
        //Send server response packet
        dsock.send(response);
    }

    static DatagramPacket receiveAndSend(DatagramSocket dsock, HeUDPServ client) throws IOException {
        var bytes = new byte[512];
        var bb = ByteBuffer.wrap(bytes);
        var packet = new DatagramPacket(bytes, bytes.length);
        while (true) {
            try {
                dsock.setSoTimeout(10000);
                dsock.receive(packet);
            } catch (SocketTimeoutException e) {
                sendError(dsock, packet.getAddress(), packet.getPort(), "Took longer than 10 seconds");
                System.exit(0);
            }
            //Get type of message
            short type = bb.getShort();

            //Receive and respond to initial HELLO packet from client
            if(type==HELLO) {
                bb.clear();
                //Verify that the intro statement is correct and then extract client name
                String data = new String(bytes, 2, packet.getLength()-2);
                if (!data.split("hello, i am ")[0].equals("")) {
                    sendError(dsock, packet.getAddress(), packet.getPort(), "Intro statement is invalid: \"" + data + "\" does not match \"hello, i am \"");
                }
                String clientName = data.split("hello, i am ")[1];

                //Set server response packet's destination address and dest port using source address and src port
//                bb.clear();
                var response = new DatagramPacket(bytes, bytes.length);
                response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
                response.setPort(packet.getPort());         //Set destination port as the source port of received packet

                //Set content of server response packet
                bb.putShort(HELLO).putInt(client.getConvoNum()).put(INTRO.getBytes()).put(clientName.getBytes()).flip();
                response.setLength(bb.remaining());

//                //Used for checking contents of server response packet, comment out later
//                String res = new String(bytes, 6, response.getLength()-6);
//                System.out.println("HELLO: "+bb.getShort()+" "+bb.getInt()+" "+res);

                //Send server response packet
                dsock.send(response);
            }
            if(type==TRANSFER) {
                System.out.println("I've reached file transfer segment");

//                var buff = new byte[100];
//                var fis = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("SHA-256"));
//                int rc;
//                int offset = 0;
//                while ((rc = fis.read(buff)) > 0) {
//                    // System.out.println("sending offset: " + offset);
//                    bb.clear().putShort(TRANSFER).putInt(convoNum).putInt(offset).put(buff, 0, rc).flip();
//                    dpack.setLength(bb.remaining());
//                    var transferRecv = sendRecv(dsock, dpack);
//                    recvBB = ByteBuffer.wrap(transferRecv.getData(), transferRecv.getOffset(), transferRecv.getLength());
//                    checkError(recvBB);
//                    offset += rc;
//                }

//                //Verify that the intro statement is correct and then extract client name
//                String data = new String(bytes, 2, packet.getLength()-2);
//                if (!data.split("hello, i am ")[0].equals("")) {
//                    sendError(dsock, packet.getAddress(), packet.getPort(), "Intro statement is invalid: \"" + data + "\" does not match \"hello, i am \"");
//                }
//                String clientName = data.split("hello, i am ")[1];
//
//                //Set server response packet's destination address and dest port using source address and src port
//                bb.clear();
//                var response = new DatagramPacket(bytes, bytes.length);
//                response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
//                response.setPort(packet.getPort());         //Set destination port as the source port of received packet
//
//                //Set content of server response packet
//                bb.putShort(HELLO).putInt(client.getConvoNum()).put(INTRO.getBytes()).put(clientName.getBytes()).flip();
//                response.setLength(bb.remaining());
//
//                //Used for checking contents of server response packet, comment out later
//                String res = new String(bytes, 6, response.getLength()-6);
//                System.out.println("HELLO: "+bb.getShort()+" "+bb.getInt()+" "+res);
//
//                //Send server response packet
//                dsock.send(response);
            }

            if(type==CHECKSUM) {

            }

//          Idk if this is needed, but here just in case
            bb.position(0);
        }
    }
    static int setPort(String portString) {
        try {
            int portNum = Integer.parseInt(portString);
            if (portNum < 0 || portNum > 65535) throw new NumberFormatException();
            return portNum;
        } catch (Exception e) {
            System.out.println("port must be a number between 0 and 65535");
            e.printStackTrace();
        }
        return 0; //code will never reach here
    }

    static int getPort() {
        return port;
    }
    static int getConvoNum() {
        return convoNum;
    }
}

