package edu.sjsu.cs158a;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

public class HelloUDPServer {
    static InetAddress clientIPAddress = null;
    static int clientPortAddress = -1;
    static DatagramSocket sock;

    public static void main(String[] args) throws IOException {
        //Common UsageErrorString
        String usageMsg = "Usage: HelloUDPServer <port>\n" +
                "A UDP server that implements the HelloUDP protocol.\n" +
                "      <port>   port to listen on.";

        //usageErrorMissingPort: Port number is unspecified, throw error
        if (args.length<1) {
            System.err.println("Missing required parameter: '<port>'\n"+usageMsg);
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
            str.append(usageMsg);
            System.err.println(str);
            System.exit(2);
        }
        /* Set port of socket */
        try {
            int portNum = Integer.parseInt(args[0]);
            if (portNum < 0 || portNum > 65535) throw new NumberFormatException();
            sock = new DatagramSocket(portNum);
        } catch (Exception e) {
            System.err.println("port must be a number between 0 and 65535\n"+usageMsg);
            System.exit(2);
        }
        //print listening on port
        System.out.println("Listening on port " +args[0]);

        HashMap<Integer, Client> clientList = new HashMap<>();
        //integer = clientID(ConversationNUM), client = client Object

        int conversationNum = 100;

        while(true) {
            DatagramPacket packet = receivePacket();
            if(packet==null) {
                //socketimeout error, do nothing and let client resend another packet
            } else {
                byte[] message = packet.getData();
                message = Arrays.copyOf(message, packet.getLength());
                //truncate message (shrink the byte[512] to the length of the data
                //message = arrays.copyOf(message, packets.getLength());
                ByteBuffer bb = ByteBuffer.wrap(message);
                //wrap message[] in byteBuffer to ease retrieval
                short type = bb.getShort();
                //short type = bb.getShort();

                /* First 2 bytes of message will be the type, remaining bytes will be "hello, i am*/
                if (type == 1) {
                    String intro = new String(message, 2, message.length-2);
//                    byte[] intro = new byte[bb.array().length - bb.position()];


                    //get the name of client
                    //loop through clientList.getValues() to check for duplicates
                    //      if a duplicate is found, resend confirmation
                    //      break;
                    //increment conversation number
                    String clientName = intro.split("hello, i am ")[1];
                    System.out.println("Name: "+clientName);
                    boolean clientIsDuplicate=false;
                    for (Client client: clientList.values()) {
                        if(client.getClientName().equals(clientName)) {
                            bb.putShort((short)1);
                            bb.putInt(client.getConversationNumber());
                            bb.put("hello, i am ".getBytes());
                            System.out.println(client.getClientName());
                            bb.put(client.getClientName().getBytes());
                            bb.flip();
                            sendPacket(bb);
                            clientIsDuplicate=true;
                            break;
                        }
                    }
                    if(!clientIsDuplicate) {
                        clientList.put(conversationNum, new Client(clientName, (short)1));
                        System.out.println(type + " " + conversationNum);
                        conversationNum++;
                    }
                }
                if (type == 2) {
                    System.out.println("Reached transfer");
                    //grab conversation number
                    //find the client using id
                    //if client does not exist, return an error and exit.
                    //client.setDiscussionPoint to 2
                    //print out where the packet came from

                    //grab the offset
                    //if received offset is less than current offset, then resend confirmation with the received offset
                    //if offset difference is greater than 100, send back a confirmation with your current offset

                    //at this point you should be at the correct offset (start of file byte chunk)
                    //change current offset (client.setOffSet());

                    //grab the file contents
//                    byte[] fileContents = new byte[message.length-- bb.position()];
//                    bb.get(fileContents); //put remaining bb into fileContents

                    //add to client.bytearrayoutputstream
                    //send confirmation message

                }
                if (type == 3) {
                    //grab convo num
                    //find the client using number
                    //if null -> return error
                    //set discussion pointer to 3

                    //write to fileoutputstream
                    byte[] receivedHash = new byte[8];

                }
            }
        }
    }

    public static DatagramPacket receivePacket() {
        try {
            sock.setSoTimeout(3000);
            byte[] bytes = new byte[512];
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            sock.receive(packet);
            clientIPAddress = packet.getAddress();
            clientPortAddress = packet.getPort();
            System.out.print("From "+clientIPAddress+": ");
            return packet;
        } catch (IOException e) {
            return null;
        }
    }

//    public static void sendPacket(ByteBuffer bb, int port, int type, int convoNum) throws IOException {
    public static void sendPacket(ByteBuffer bb) throws IOException {
        //if no ip address, exit
        if(clientIPAddress==null) {
            System.err.println("Client does not have an address set");
            System.exit(2);
        }
        byte[] bytes = bb.array();
        //allocate bytes using bb.getData
        var packet = new DatagramPacket(bytes, bytes.length);
        packet.setAddress(clientIPAddress);   //Set response packet address to the source address of received packet
        packet.setPort(clientPortAddress);
        //send packet
        packet.setLength(bb.remaining());
        sock.send(packet);
        //print out where it's going to
        System.out.print("To "+clientIPAddress+": ");

    }
    public static void sendError(DatagramSocket dsock, InetAddress address, int port, String errMessage) throws IOException {
        var bytes = new byte[512];
        var bb = ByteBuffer.wrap(bytes);
        var response = new DatagramPacket(bytes, bytes.length);
        response.setAddress(address);   //Set response packet address to the source address of received packet
        response.setPort(port);         //Set destination port as the source port of received packet

        //Set content of server response packet
        bb.putShort((short)5).put(errMessage.getBytes()).flip();
        response.setLength(bb.remaining());
        //Send server response packet
        dsock.send(response);
    }
}
