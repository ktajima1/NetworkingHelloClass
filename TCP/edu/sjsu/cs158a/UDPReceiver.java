package edu.sjsu.cs158a;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

public class UDPReceiver {
    public static void main(String[] arg) throws IOException {
        //A DatagramSocket is used to open up a portal to the network to send and receive datagrams (aka UDP packets)
        //This socket is kept alive and open during this entire process
        //This is a try-with-resources syntax where the DatagramSocket will be automatically closed once the try block finishes
        //execution
        try (var sock = new DatagramSocket(2323)) {
            System.out.println(sock.getLocalSocketAddress());
            System.out.println(sock.getRemoteSocketAddress()); //UDP does not have any connections, so there IS NO remote, only local
            sock.setSoTimeout(5000);
            //if the size of a packet is greater than 512, then the portion of the packet that doesnt fit inside the byte
            //array will be truncated and dropped. If it all fits, then everything is fine. UDP packets have a max size of
            //64k, if you want, you can set the array size to 64k thought memory will be sacrificed
            var bytes = new byte[512];
            var bb = ByteBuffer.wrap(bytes);
            var packet = new DatagramPacket(bytes, bytes.length);
            while (true) {
                try {
                    sock.receive(packet);
                } catch (SocketTimeoutException e) {
                    System.out.println("I'm bored");
                    System.exit(0);
                }
                //When the receiver receives a UDP packet, the packet specifies how long the message is
                //So using the len, we can specify length of message
                var len = packet.getLength();
                //must set the position of bb back to 0 to keep reading array
                bb.position(0);
                while (bb.position() < len) {
                    System.out.println("bb pos " + bb.position());
                    //this will read the first 4 bytes of the bytes[] array, but the position will not be reset when called again
                    //putInt() will also increment the position of the byteBuffer[]
                    var rand = bb.getInt();
                    System.out.println("bb pos " + bb.position());
//                System.out.println(packet.getSocketAddress() + " Got: " + new String(bytes, 0, len));
                    System.out.println(packet.getSocketAddress()+ " Got: " + rand);
                }
            }
        }
    }
}