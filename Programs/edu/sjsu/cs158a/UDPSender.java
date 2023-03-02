package edu.sjsu.cs158a;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Random;

public class UDPSender {
    public static void main(String[] arg) throws IOException {
        //For sender, we do not specify a port number because the operating system will pick an ephemeral port number for us
        //automatically; this is good because the operating systems know which ports are available, so it can choose one for us
        //a port number was specified for the receiver so that the sender knows which port to send packets to

        //When you send a datagram packet, it will either arrive intact, partially, not at all, or multiple times
        //it will also send other information, including the length of the packet
        var sock = new DatagramSocket();
        System.out.println(sock.getLocalSocketAddress()); //Instead of printing 127 local host address, this will print 0:0:0:0 as address
                                                          // 0:0:0:0 is a wild card address that can refer to ANY ADDRESS
        //Local addresses are our own addresses
        //Remote addresses are addresses were talking to
        System.out.println(sock.getRemoteSocketAddress()); //UDP does not have any connections, so there IS NO remote, only local

        //Attempt to send numbers; to send numbers, the number must be converted into bytes, sending multiple numbers
        var count = 3;
        var bytes = new byte[4*count];
        var bb = ByteBuffer.wrap(bytes);
        for (int i = 0; i < count; i++) {
            var rand = new Random().nextInt();
            //ByteBuffer has a convenient method that turns an integer into bytes and puts it into a bytesArray in
            // bigEndian form. putInt() will also increment the position of the byteBuffer[]
            bb.putInt(rand);
        }
//      //We cannot send strings, so we must convert the string to bytes and send it as bytes
//        var bytes = "hi!".getBytes();
        //this is the object used to send information in datagrams and we construct this object with the bytes array
        var packet = new DatagramPacket(bytes, bytes.length);
        //we then specify where the packets will go
        //we can specify a name and the name will get converted to a network address
        packet.setAddress(InetAddress.getByName("127.0.0.1"));
        packet.setPort(2323);
        sock.send(packet);
    }
}