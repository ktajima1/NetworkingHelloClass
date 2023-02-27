package edu.sjsu.cs158a;

import java.io.IOException;
import java.net.*;
import java.util.HashSet;

/* This quote of the day (QOTD) protocol program will use networking principles learned in CS158A to send UDP packets to
   a target server (in this program, the target server is djxmmx.net) and receive a response back from the server (in
   the form of a packet). We send a UDP packet (with or without data, doesn't matter) to signal to the QOTD server to
   respond with a packet containing the QOTD as data.

   Terminal command for this program is:
        java qotd.java djxmmx.net [--count {#}]

   The number of quotes we request from the djxmmx server is by default, 3, but the user may also specify the number of
   quote requested using the parameter "--count {#}" where if --count is the second argument, then the following argument
   (which must be an integer) will be the number of quotes requested from the server.
        Ex: java qotd.java djxmmx.net
         - will request and receive from the djxmmx server the default number of quotes (3) and print them
        Ex: java qotd.java djxmmx.net --count 5
         - will reuqest and receive from the djmmx.net server 5 QOTDs and print them

   Note: If IP Address is not specified as the first argument, the program will terminate. If IP Address is invalid, the
         program will throw a HostNotFound exception and terminate. If there is a typo in the second argument, --count,
         the program will request the default number of quotes. If the argument following "--count" is not a valid integer,
         the program will request the default number of quotes.
 */
public class QotD {
    public static void main(String[] args) throws IOException {
        /* Default # of quotes = 3 */
        var count = 3;
        /* If user specifies a desired number of quotes, update count to desired count */
        if(args.length>2 && args[1].equals("--count")) {
            try {
                count = Integer.parseInt(args[2]);
            } catch(NumberFormatException e) {
                System.out.println("Unable to process \"" + args[2] + "\" as a valid number, defaulting to three quotes of the day\n\n");
            }
        }
        /* QOTD Protocol
        * Create a DataGram Socket to send and receive packets from the djxmmx.net server. With the try-with resources
        * technique, the datagram socket will be automatically closed at the end of the try block.
        */
        try (var sock = new DatagramSocket()) {
            /* Set destination address using first argument, port to 17 (QOTD Protocol), and byte array to size 512 */
            if (args.length==0) {
                System.out.println("No IP Address given, Exiting System");
                System.exit(0);
            }
            //Set address to first argument (djxmmx.net)
            InetAddress address = InetAddress.getByName(args[0]);
            //Port number specified in https://datatracker.ietf.org/doc/html/rfc865
            int port = 17;
            var bytes = new byte[512];

            //quoNum is used as the quoteList[] index variable
            int quoNum=1;
            //HashSet used to check for duplicate quotes. If received quote is a duplicate, quote will not be added to
            //the quoteList[] array
            HashSet<String> duplicateChecker = new HashSet<>();
            String[] quoteList = new String[count];

            //While loop runs until the desired number of QOTDs have been added to the duplicateChecker.
            while(duplicateChecker.size()<count) {
                //Create a datagram packet with the desired address and port number and send the packet to djxmmx server
                var request = new DatagramPacket(bytes, bytes.length, address, port);
                sock.send(request);
                //Try block is here to deal with SocketTimeoutExceptions that may occur if receiving packets from
                //the server takes too long. If SocketTimeoutException occurs, program will ignore the dropped packet
                //and proceed to request another packet.
                try {
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                    sock.setSoTimeout(3000);
                    sock.receive(packet);
                    String quote = new String(bytes, 0, packet.getLength());

                    //If the received QOTD is not a duplicate, then add the QOTD to the quoteList[] array and increment
                    //index variable quoNum by 1.
                    if(duplicateChecker.add(quote)) {
                        quoteList[quoNum-1]=quote;
                        quoNum++;
                    }
                } catch (SocketTimeoutException e) {
                }
            }
            //Print out the contents of quoteList
            for (int i=0; i<quoteList.length; i++) {
                System.out.println("Quote " + (i+1) + ":\n" + quoteList[i]);
            }
        }
    }
}