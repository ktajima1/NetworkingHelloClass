package edu.sjsu.cs158a;

import java.io.IOException;
import java.net.*;

public class QotD {
    public static void main(String[] args) throws IOException {
        /* First, we set the address (which is the first arg[0]) and the port (which is 17 according to
        https://datatracker.ietf.org/doc/html/rfc865)

         */
        try (var sock = new DatagramSocket()) {
            sock.setSoTimeout(3000);

            var count = 3;
            if(args.length>2 && args[1].equals("--count")) {
                count = Integer.parseInt(args[2]);
            }
            InetAddress address = InetAddress.getByName(args[0]);
            int port = 17;
            var bytes = new byte[512];
            int quoteNumber=1;
            for(int i=0; i<count; i++) {
                var request = new DatagramPacket(bytes, bytes.length, address, port);
                sock.send(request);
                DatagramPacket response = new DatagramPacket(bytes, bytes.length);

                sock.receive(response);
                String quote = new String(bytes, 0, response.getLength());

                System.out.println("Quote " + quoteNumber + ":\n" + quote);
                quoteNumber++;
            }
        }
    }
}
