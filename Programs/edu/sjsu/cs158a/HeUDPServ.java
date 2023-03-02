package edu.sjsu.cs158a;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class HeUDPServ {
    final static private short HELLO = 1;
    final static private short TRANSFER = 2;
    final static private short CHECKSUM = 3;
    final static private short ERROR = 5;
    public static final String INTRO = "hello, i am ";

    public static int clientCount = 1;
    public static HashMap<Integer, HeUDPServ> clientList = new HashMap<>();

    private int convoNum;
    private String clientName;
    private  File clientFile; //File to be transferred
    private int offset;
    private short discussionPointer;

    static String usageErrorMissingPort = "Missing required parameter: '<port>'\n" +
            "Usage: HelloUDPServer <port>\n" +
            "A UDP server that implements the HelloUDP protocol.\n" +
            "      <port>   port to listen on.";
    static String usageErrorInvalidPort = "port must be a number between 0 and 65535\n" +
            "Usage: HelloUDPServer <port>\n" +
            "A UDP server that implements the HelloUDP protocol.\n" +
            "      <port>   port to listen on.";

    public HeUDPServ(int convoNum, int discussionPointer) {
        this.convoNum = convoNum;
        this.discussionPointer = (short)discussionPointer;
        this.offset = -1;
    }

    public static void main(String[] args) throws IOException {
        //usageErrorMissingPort: Port number is unspecified, throw error
        if (args.length==0) {
            System.err.println(usageErrorMissingPort);
            System.exit(2);
        }
        //usageErrorUnmatchedArgs: There are extra arguments, throw error
        if (args.length>1) {
            StringBuilder str = new StringBuilder();
            str.append("Unmatched arguments from index 1:");
            for(int i=1; i<args.length;i++) {
                str.append(" '").append(args[i]).append("',");
            }
            str.deleteCharAt(str.length()-1); //Delete the comma at the end before appending
            str.append("\nUsage: HelloUDPServer <port>\n" +
                    "A UDP server that implements the HelloUDP protocol.\n" +
                    "      <port>   port to listen on.");
            System.err.println(str);
            System.exit(2);
        }

        try(var sock = new DatagramSocket(setPort(args[0]))) { //Sets port
            System.out.println("Listening on port " +args[0]);
            receiveAndSend(sock);
        } catch (SocketException e) {
            System.out.println("Something happened");
            e.printStackTrace();
        }
    }
    /** File transfer method using byte array output stream*/
//    static DatagramPacket fileTransfer(DatagramSocket sock, DatagramPacket toRead, HeUDPServ client, int type, ByteArrayOutputStream bo) throws IOException {
//        try {
//            bo.write(toRead.getData()); //Write data into byte array
//        } catch(IOException e) {
//            sendError(sock, toRead.getAddress(), toRead.getPort(), "File transfer error occurred");
//        }
//
//        //Set server response packet's destination address and dest port using source address and src port
//        var bytes = new byte[512];
//        var bb = ByteBuffer.wrap(bytes);
//        var response = new DatagramPacket(bytes, bytes.length);
//        response.setAddress(toRead.getAddress());   //Set response packet address to the source address of received packet
//        response.setPort(toRead.getPort());         //Set destination port as the source port of received packet
//
//        bb.putShort(TRANSFER).putInt(client.getConvoNum()).putInt(toRead.getOffset()).flip();
//        response.setLength(bb.remaining());
//
//        sock.send(response);
//        System.out.println("To " + toRead.getSocketAddress()+": "+ type + " " + client.getConvoNum());
//        /* Receive the next packet from client */
//        var nextBytes = new byte[512];
//        var nextPacket = new DatagramPacket(nextBytes, nextBytes.length);
//        try {
//            sock.setSoTimeout(3000);
//            sock.receive(nextPacket);
//        } catch (SocketTimeoutException e) {
//            sendError(sock, toRead.getAddress(), toRead.getPort(), "Trouble receiving next packet");
//        }
//        return nextPacket;
//    }
    /** File transfer method using file writer*/
static void fileTransfer(DatagramSocket sock, DatagramPacket toRead, HeUDPServ client, int type, String data) throws IOException {
    try {
        FileWriter fw = new FileWriter((client.getFile()), true);
        System.out.println("\nWriting: " +data);
        fw.write(data);
        fw.close();

        //Set server response packet's destination address and dest port using source address and src port
        var bytes = new byte[512];
        var bb = ByteBuffer.wrap(bytes);
        var response = new DatagramPacket(bytes, bytes.length);
        response.setAddress(toRead.getAddress());   //Set response packet address to the source address of received packet
        response.setPort(toRead.getPort());         //Set destination port as the source port of received packet

//        bb.putShort(TRANSFER).putInt(client.getConvoNum()).putInt(toRead.getOffset()).flip();
        bb.putShort(TRANSFER).putInt(client.getConvoNum()).putInt(toRead.getOffset()).flip();
        response.setLength(bb.remaining());

        sock.send(response);
        System.out.println("To " + toRead.getSocketAddress()+": "+ type + " " + client.getConvoNum());
    } catch(IOException e) {
        sendError(sock, toRead.getAddress(), toRead.getPort(), "File transfer error occurred");
    }
}

    static void receiveAndSend(DatagramSocket dsock) throws IOException {
        var bytes = new byte[512];
        var bb = ByteBuffer.wrap(bytes);
        var packet = new DatagramPacket(bytes, bytes.length);   //Packet to be sent to client
        var transFile = new DatagramPacket(bytes, bytes.length); //File to be received from transfer
        while(true) {
            short type=4; //Not a valid conversation type, will throw error if type is not changed or is invalid
            bb.clear();
            try {
                dsock.receive(packet);
                //Get Type of conversation
                type = bb.getShort();
            } catch (SocketTimeoutException e) {
//                if(packet.getAddress()==null) {
//                    System.err.println("Took longer than 3 seconds to receive client");
//                    System.exit(2);
//                }
//                else {sendError(dsock, packet.getAddress(), packet.getPort(), "Took longer than 10 seconds to receive client response, try again");}
            }

            //Receive and respond to initial HELLO packet from client
            if(type==HELLO) {
                System.out.println("IM IN HELLO");
                //Create new client with unique conversation ID, checks whether port # is valid as well
                int clientID = clientCount;
                HeUDPServ client = new HeUDPServ(clientID, 1);
                clientList.put(clientID, client);

                System.out.println("From " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
                clientCount++; //increment global conversation number

                bb.clear();
                //Verify that the intro statement is correct and then extract client name
                String data = new String(bytes, 2, packet.getLength()-2);
                if (!data.split("hello, i am ")[0].equals("")) {
                    sendError(dsock, packet.getAddress(), packet.getPort(), "Intro statement is invalid: \"" + data + "\" does not match \"hello, i am \"");
                }
                client.setClientName(data.split("hello, i am ")[1]);

                //Create a new file for client and clear contents of any pre-existing client file
                client.setFile(new File(client.getClientName()+".txt"));
                FileWriter fw = new FileWriter(client.getFile(), false);
                fw.write("");
                fw.close();

                //Set server response packet's destination address and dest port using source address and src port
                var response = new DatagramPacket(bytes, bytes.length);
                response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
                response.setPort(packet.getPort());         //Set destination port as the source port of received packet

                //Set content of server response packet
                bb.putShort(HELLO).putInt(client.getConvoNum()).put(INTRO.getBytes()).put(client.getClientName().getBytes()).flip();
                response.setLength(bb.remaining());

                //Send server response packet
                dsock.send(response);
                System.out.println("To " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
            }
            if(type==TRANSFER) {
                System.out.println("IM IN TRANSFER");
                //Fetch the client from the clientList using the conversation number in the packet.
                //If the client doesn't exist in the map (meaning Hello has not been sent, send an error)
                HeUDPServ client;
                int convNum = bb.getInt();
                if((client=clientList.get(convNum))==null) {
                    sendError(dsock, packet.getAddress(), packet.getPort(), "Client unknown: Transfer");
                }
                //Otherwise, continue with protocol
                else {
                    System.out.println("From " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
//                    bb.clear();
//                    dsock.receive(transFile);
                    bb.position(0);
//                    transFile = packet;
                    try {
//                        FileWriter fw = new FileWriter((client.getFile()));
//
//                        fw.write(new String(transFile.getData(), 10, transFile.getLength()-10));
//                        fw.close();

//                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        while(type==TRANSFER) {
                            String data = new String(packet.getData(), 10, packet.getLength()-10);
                            fileTransfer(dsock, packet, client, type, data);
//                                packet = fileTransfer(dsock, packet, client, type, data);
                            dsock.setSoTimeout(3000);
                            dsock.receive(packet);

                            bb.clear();
                            type = bb.getShort();
                            String currentData = new String(packet.getData(), 10, packet.getLength()-10);
                            System.out.println("Current data: " +currentData+"\nNext packet type is: " + type);
                            //call transfer method
                                //write to bo
                                //receive and return next packet to main method
                            //check type of response packet
                            //if type is transfer, loop again
                            //if type is not transfer, write bo to file and go to next conversation
                        }
//                        FileOutputStream fo = new FileOutputStream(client.getFile());
//                        bo.writeTo(fo);
//                        bo.close();
//                        fo.close();
                    } catch (IOException e) {
                        sendError(dsock, packet.getAddress(), packet.getPort(), "File transfer error occurred");
                        System.exit(2);
                    }

//                    //Set server response packet's destination address and dest port using source address and src port
//                    var response = new DatagramPacket(bytes, bytes.length);
//                    response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
//                    response.setPort(packet.getPort());         //Set destination port as the source port of received packet
//
//                    bb.putShort(TRANSFER).putInt(client.getConvoNum()).putInt(transFile.getOffset()).flip();
//                    response.setLength(bb.remaining());
//
//                    dsock.send(response);
//                    System.out.println("To " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
                }
            }
            if(type==CHECKSUM) {
                System.out.println("IM IN CHECKSUM");
                //Fetch the client from the clientList using the conversation number in the packet.
                //If the client doesn't exist in the map (meaning Hello has not been sent, send an error)
                HeUDPServ client;
                if((client=clientList.get(bb.getInt()))==null) {
                    sendError(dsock, packet.getAddress(), packet.getPort(), "Client unknown: Checksum");
                }
                //If client has not transferred a file before, send an error
                else if(!client.hasFile()) {
                    sendError(dsock, packet.getAddress(), packet.getPort(), "Client has not previously transferred a file");
                }
                //Otherwise, continue with protocol
                else {
                    System.out.println("From " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
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
                        int res=1;
                        char responseByte = 1;
                        if(clientCheckSum==serverCheckSum) {
                            res=0;
                            responseByte=0;
                        }

                        var response = new DatagramPacket(bytes, bytes.length);
                        response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
                        response.setPort(packet.getPort());         //Set destination port as the source port of received packet
                        bb.clear().putShort(CHECKSUM).putInt(client.getConvoNum()).putChar(responseByte).flip();
                        response.setLength(bb.remaining());

                        System.out.println("tomato "+res);
                        dsock.send(response);
                        System.out.println("To " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
                    } catch(NoSuchAlgorithmException e) {
                        sendError(dsock, packet.getAddress(), packet.getPort(), "Trouble occurred in calculating checksum");
                        System.exit(2);
                    }
                }
            }
            if(type!=HELLO && type!=TRANSFER && type!=CHECKSUM) {
                sendError(dsock, packet.getAddress(), packet.getPort(), "Invalid type was received");
            }
        }
    }
    static int setPort(String portString) {
        try {
            int portNum = Integer.parseInt(portString);
            if (portNum < 0 || portNum > 65535) throw new NumberFormatException();
            return portNum;
        } catch (Exception e) {
            System.err.println(usageErrorInvalidPort);
            System.exit(2);
        }
        return 0; //code will never reach here
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
    int getConvoNum() {return convoNum;}

    void setClientName(String name) {clientName = name;}

    String getClientName() {return clientName;}

    void setFile(File file) {clientFile = file;}

    File getFile() {return clientFile;}

    boolean hasFile() {return clientFile.exists();}
}

