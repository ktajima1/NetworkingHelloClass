package edu.sjsu.cs158a;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class HelloUDPServer {
    final static private short HELLO = 1;
    final static private short TRANSFER = 2;
    final static private short CHECKSUM = 3;
    final static private short ERROR = 5;
    public static final String INTRO = "hello, i am ";

    public static int clientCount = 1;
    public static HashMap<Integer, HelloUDPServer> clientList = new HashMap<>();

    private int convoNum;
    private String clientName;
    private  File clientFile; //File to be transferred

    static String usageErrorMissingPort = "Missing required parameter: '<port>'\n" +
            "Usage: HelloUDPServer <port>\n" +
            "A UDP server that implements the HelloUDP protocol.\n" +
            "      <port>   port to listen on.";
    static String usageErrorInvalidPort = "port must be a number between 0 and 65535\n" +
            "Usage: HelloUDPServer <port>\n" +
            "A UDP server that implements the HelloUDP protocol.\n" +
            "      <port>   port to listen on.";

    public HelloUDPServer(int convoNum) {
        this.convoNum = convoNum;
    }

    public static void main(String[] args) throws IOException {
        //Check whether arguments are valid
        checkArgs(args);

        try(var sock = new DatagramSocket(setPort(args[0]))) { //Set port
            System.out.println("Listening on port " +args[0]);
            receiveAndSend(sock);
        } catch (SocketException e) {
            System.out.println("Something happened");
            e.printStackTrace();
        }
    }

    static void receiveAndSend(DatagramSocket dsock) throws IOException {
        var bytes = new byte[512];
        var bb = ByteBuffer.wrap(bytes);
        var packet = new DatagramPacket(bytes, bytes.length);   //Packet to be sent to client
        var transFile = new DatagramPacket(bytes, bytes.length); //File to be received from transfer

        int count = 1;

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
                //Create new client with unique conversation ID, checks whether port # is valid as well
                int clientID = clientCount;
                HelloUDPServer client = new HelloUDPServer(clientID);
                clientList.put(clientID, client);

                System.out.println("From " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
                clientCount++; //increment global conversation number

                bb.clear();
                //Verify that the intro statement is correct and then extract client name
                String data = new String(bytes, 2, packet.getLength()-2);
                if (!data.split(INTRO)[0].equals("")) {
                    sendError(dsock, packet.getAddress(), packet.getPort(), "Intro statement is invalid: \"" + data + "\" does not match \"hello, i am \"");
                }
                client.setClientName(data.split(INTRO)[1]);

                //Create a new file for client or clear a pre-existing client file
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
            else if(type==TRANSFER) {
                System.out.println("Packet "+count++);
                //Fetch the client from the clientList using the conversation number in the packet.
                //If the client doesn't exist in the map (meaning Hello has not been sent, send an error)

                HelloUDPServer client;
                if((client=clientList.get(bb.getInt()))==null) {
                    sendError(dsock, packet.getAddress(), packet.getPort(), "Client unknown: Transfer");
                }
                //Otherwise, continue with protocol
                else {
                    System.out.println("From " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
                    bb.clear();
                    dsock.receive(transFile);

                    try {
//                        client.setFile(new File(""+client.getClientName()+".txt"));
                        FileWriter fw = new FileWriter((client.getFile()), true);

                        fw.write(new String(transFile.getData(), 10, transFile.getLength()-10));
                        fw.close();
                    } catch (IOException e) {
                        sendError(dsock, packet.getAddress(), packet.getPort(), "File transfer error occurred");
                        System.exit(2);
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
            }

            else if(type==CHECKSUM) {
                //Fetch the client from the clientList using the conversation number in the packet.
                //If the client doesn't exist in the map (meaning Hello has not been sent, send an error)
                HelloUDPServer client;
                if((client=clientList.get(bb.getInt()))==null) {
                    sendError(dsock, packet.getAddress(), packet.getPort(), "Client unknown: Checksum");
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
                        char responseByte = 1;
                        if(clientCheckSum==serverCheckSum) {
                            responseByte=0;
                        }

                        var response = new DatagramPacket(bytes, bytes.length);
                        response.setAddress(packet.getAddress());   //Set response packet address to the source address of received packet
                        response.setPort(packet.getPort());         //Set destination port as the source port of received packet
                        bb.clear().putShort(CHECKSUM).putInt(client.getConvoNum()).putChar(responseByte).flip();
                        response.setLength(bb.remaining());

                        dsock.send(response);
                        System.out.println("To " + packet.getSocketAddress()+": "+ type + " " + client.getConvoNum());
                    } catch(NoSuchAlgorithmException e) {
                        sendError(dsock, packet.getAddress(), packet.getPort(), "Trouble occurred in calculating checksum");
                        System.exit(2);
                    }
                }
            }
            else {
                sendError(dsock, packet.getAddress(), packet.getPort(), "Invalid type was received");
            }
        }
//        bb.clear(); //Cleanup, probably not needed
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
    static void checkArgs(String[] arguments) {
        //usageErrorMissingPort: Port number is unspecified, throw error
        if (arguments.length==0) {
            System.err.println(usageErrorMissingPort);
            System.exit(2);
        }
        //usageErrorUnmatchedArgs: There are extra arguments, throw error
        if (arguments.length>1) {
            StringBuilder str = new StringBuilder();
            str.append("Unmatched arguments from index 1:");
            for(int i=1; i<arguments.length;i++) {
                str.append(" '").append(arguments[i]).append("',");
            }
            str.deleteCharAt(str.length()-1); //Delete the comma at the end before appending
            str.append("\nUsage: HelloUDPServer <port>\n" +
                    "A UDP server that implements the HelloUDP protocol.\n" +
                    "      <port>   port to listen on.");
            System.err.println(str);
            System.exit(2);
        }
    }
     int getConvoNum() {
        return convoNum;
    }
     void setClientName(String name) {
        clientName = name;
    }
     String getClientName() {
        return clientName;
    }
     void setFile(File file) {
        clientFile = file;
    }

     File getFile() {
        return clientFile;
    }
}

