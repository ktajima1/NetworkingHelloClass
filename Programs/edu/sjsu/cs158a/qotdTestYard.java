package edu.sjsu.cs158a;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

public class qotdTestYard {
    public static void main(String[] args) throws IOException {
    /*
    Network byte order definition:
    Ex. Say we have 4 bytes: _ _ _ _
    If the conversation number is 65538, then we have to take that number and put it into 4 byte format in hex format?
    65538 = 00 0x01 00 02. So the 4 bytes is: [00][01][00][02]

    We have conversation numbers so we can keep track of which message belongs to which client
    */

        /* First, we set the address (which is the first arg[0]) and the port (which is 17 according to
        https://datatracker.ietf.org/doc/html/rfc865)

         */
        try (var sock = new DatagramSocket()) {
            sock.setSoTimeout(3000);

            var count = 3;
            InetAddress address = InetAddress.getByName(args[0]);
            int port = 17;
            var bytes = new byte[512];
//            var bb = ByteBuffer.wrap(bytes);
//            System.out.println(address.getHostName());
//            var packet = new DatagramPacket(bytes, bytes.length);
//            var packet = new DatagramPacket(bytes, 0, bytes.length, address, 17);
            for(int i=0; i<count; i++) {
                var request = new DatagramPacket(bytes, bytes.length, address, port);
//            System.out.println(address + " | " + request.getSocketAddress());
//            SocketAddress sockaddr = new InetSocketAddress(address, port);
//            request.setAddress(address);
//            request.setPort(17);

                sock.send(request);
                DatagramPacket response = new DatagramPacket(bytes, bytes.length);

                sock.receive(response);
                String quote = new String(bytes, 0, response.getLength());

                System.out.println(quote + '\n');
            }
//            var packet = new DatagramPacket(bytes, 0, bytes.length, address, 17);
//
////            sock.receive(packet);
//            System.out.println("AH");
//            System.out.println(packet.getData().toString());
//            try {
//                sock.receive(packet);
//                System.out.println("I odiajowdj done something");
//                System.out.println(packet.getData().toString());
//                System.out.println("I shouldve done something");
//            } catch(SocketTimeoutException e) {
//                System.out.println("DIUWHIU");
//                System.exit(0);
//            }

//            for (int i = 0; i < count; i++) {
//                var packet = new DatagramPacket(bytes, 0, bytes.length, address, 17);
//                sock.receive(packet);
//                packet.getData().toString();
//
//            }
//            while (true) {
//                try {
//                    System.out.println("Checkpoint 1");
//                    sock.receive(packet);
//                } catch (SocketTimeoutException e) {
//                    System.out.println("I'm bored");
//                    System.exit(0);
//                }
//            }
//            for (int i = 0; i < count; i++) {
//
//                System.out.println("Checkpoint 2");
//                //When the receiver receives a UDP packet, the packet specifies how long the message is
//                //So using the len, we can specify length of message
//                var len = packet.getLength();
//                //must set the position of bb back to 0 to keep reading array
//                bb.position(0);
//                while (bb.position() < len) {
//                    //this will read the first 4 bytes of the bytes[] array, but the position will not be reset when called again
//
//                    System.out.println(packet.getSocketAddress() + " Got: " + new String(bytes, 0, len));
//                }
//            }
        }
    /*
    SHA-256 Digest: a complicated hash function used to
    Can be used via JAVA by:
     */
    }
}
