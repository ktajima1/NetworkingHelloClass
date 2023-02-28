package edu.sjsu.cs158a;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//ISTG IF SETPORT IS CONSIDERED AN UNEXPECTED ERROR REQUIREMENT AND NEEDS TO SEND ERROR CODE 5, THAT WILL REQUIRE REWORK

public class HelloUDPServer {
    final static private short HELLO = 1;
    final static private short TRANSFER = 2;
    final static private short CHECKSUM = 3;
    final static private short ERROR = 5;
    public static final String INTRO = "hello, i am ";

    private static int port;
    private static int convoNum;
    private static String clientName;
    private static File clientFile; //File to be transferred

    public HelloUDPServer(String port, int convoNum) {
        this.port = setPort(port);
        this.convoNum = convoNum;
    }

    public static void main(String[] args) throws IOException {
//        try {
//            throw new UnknownHostException();
//        } catch (UnknownHostException u) {
//            System.out.println(u.getMessage());
//        }
        if (args.length==0) {
            throw new UnknownHostException();
        }
        System.out.println("Listening on port " +args[0]);
        //Client conversation number (will increment as number of clients increase)
        int convoNum = 1;
        HelloUDPServer client = new HelloUDPServer(args[0], convoNum);
        convoNum++; //increment global conversation number

        try(var sock = new DatagramSocket(client.getPort())) {
//            System.out.println("I made it here " + client.getPort());
            while(true) {
                receiveAndSend(sock, client);
            }
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


    static void receiveAndSend(DatagramSocket dsock, HelloUDPServer client) throws IOException {
        var bytes = new byte[512];
        var bb = ByteBuffer.wrap(bytes);
        var packet = new DatagramPacket(bytes, bytes.length);   //Packet to be sent to client
        var transFile = new DatagramPacket(bytes, bytes.length); //File to be received from transfer
        short type=4; //Not a valid conversation type, will throw error if type is not changed or is invalid

        try {
            dsock.setSoTimeout(10000);
            dsock.receive(packet);
            //Get Type of conversation
            type = bb.getShort();
            System.out.println("From " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
        } catch (SocketTimeoutException e) {
            if(packet.getAddress()==null) {System.err.println("Took longer than 10 seconds"); }
            else {sendError(dsock, packet.getAddress(), packet.getPort(), "Took longer than 10 seconds");}
        }

        //Receive and respond to initial HELLO packet from client
        if(type==HELLO) {
            bb.clear();
            //Verify that the intro statement is correct and then extract client name
            String data = new String(bytes, 2, packet.getLength()-2);
            if (!data.split("hello, i am ")[0].equals("")) {
                sendError(dsock, packet.getAddress(), packet.getPort(), "Intro statement is invalid: \"" + data + "\" does not match \"hello, i am \"");
            }
            client.setClientName(data.split("hello, i am ")[1]);
            //Set server response packet's destination address and dest port using source address and src port
//                bb.clear();
            var response = new DatagramPacket(bytes, bytes.length);
            response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
            response.setPort(packet.getPort());         //Set destination port as the source port of received packet

            //Set content of server response packet
            bb.putShort(HELLO).putInt(client.getConvoNum()).put(INTRO.getBytes()).put(client.getClientName().getBytes()).flip();
            response.setLength(bb.remaining());

//                //Used for checking contents of server response packet, comment out later
//                String res = new String(bytes, 6, response.getLength()-6);
//                System.out.println("HELLO: "+bb.getShort()+" "+bb.getInt()+" "+res);

            //Send server response packet
            dsock.send(response);
            System.out.println("To " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
        }

        if(type==TRANSFER) {
            bb.clear();
            dsock.receive(transFile);

            try {
                client.setFile(new File(""+client.getClientName()+".txt"));
                FileWriter fw = new FileWriter((client.getFile()));

                fw.write(new String(transFile.getData(), 10, transFile.getLength()-10));
                fw.close();
            } catch (IOException e) {
                sendError(dsock, packet.getAddress(), packet.getPort(), "File transfer error occurred");
                System.exit(0);
            }

            //Set server response packet's destination address and dest port using source address and src port
            var response = new DatagramPacket(bytes, bytes.length);
            response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
            response.setPort(packet.getPort());         //Set destination port as the source port of received packet

            bb.putShort(TRANSFER).putInt(client.getConvoNum()).putInt(transFile.getOffset()).flip();
            response.setLength(bb.remaining());

            dsock.send(response);
            System.out.println("To " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
        }

        if(type==CHECKSUM) {
            bb.clear();
            bb.position(0);
            int typeNum = bb.getShort();    //skip the type
            int cNum = bb.getInt();         //skip the conversation number
            double clientCheckSum = bb.getDouble();
            try {
                byte[] fileData = Files.readAllBytes(Paths.get(client.getClientName().concat(".txt")));
                byte[] SHAHash = MessageDigest.getInstance("SHA-256").digest(fileData);
                var shaBuffer = ByteBuffer.wrap(SHAHash);
                var serverCheckSum = shaBuffer.getDouble();
                char responseByte = 1;
                if(clientCheckSum==serverCheckSum) {
                    responseByte=0;
                }

                var response = new DatagramPacket(bytes, bytes.length);
                response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
                response.setPort(packet.getPort());         //Set destination port as the source port of received packet
                bb.clear().putShort(CHECKSUM).putInt(convoNum).putChar(responseByte).flip();
                response.setLength(bb.remaining());

                dsock.send(response);
                System.out.println("To " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
            } catch(NoSuchAlgorithmException e) {
                sendError(dsock, packet.getAddress(), packet.getPort(), "Trouble occurred in calculating checksum");
                System.exit(0);
            }
        }
        bb.clear(); //Cleanup, probably not needed
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
    static void setClientName(String name) {
        clientName = name;
    }
    static String getClientName() {
        return clientName;
    }
    static void setFile(File file) {
        clientFile = file;
    }

    static File getFile() {
        return clientFile;
    }
}

