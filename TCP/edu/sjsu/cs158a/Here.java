package edu.sjsu.cs158a;

import java.io.IOException;
import java.net.Socket;

public class Here {

    public static void main(String[] args) throws IOException {
        //Create connection to server
        try {
//          If "cs-reed-02.class.homeofcode.com" does not work, try "172.27.19.2" for the Socket
            //This Socket will connect to the server and receive a message from it
//            var sock = new Socket("cs-reed-02.class.homeofcode.com", 3333);

            var sock = new Socket(args[1], Integer.parseInt(args[2]));

            var out = sock.getOutputStream();
            var in = sock.getInputStream();
            //The first byte of the message is the length of message, length needed to read entire message
            var len = in.read();
            // The welcome message comes in as a string of bytes
            // in.readBytes() will only read 4 bytes at a time, readNBytes(length) needed to read entire message
            var bytes = in.readNBytes(len);

            //Print out welcome message
            System.out.printf("%s\n", new String(bytes, 0, len));
//            System.out.println(new String(bytes)); not sure what this line was for

            // Student ID
            var myid = 15537405;
            //Convert Student ID to bytes in big-endian format
            //The decimal ID is converted to bit-format and then shifted accordingly to its position.
            //The first byte (the MSB) is shifted to the right by 24 bits and then
            var myidBytes = new byte[4];
            myidBytes[0] = (byte)((myid >> 24) & 0xff);
            myidBytes[1] = (byte)((myid >> 16) & 0xff);
            myidBytes[2] = (byte)((myid >> 8) & 0xff);
            myidBytes[3] = (byte)((myid) & 0xff);
            //Full Name and convert to Bytes
            var name = "Keigo Tajima";
            var nameBytes = name.getBytes();
            out.write(myidBytes);
            out.write(nameBytes.length);
            out.write(nameBytes);
            var pin = in.readNBytes(4);
//            System.out.println(new String(pin));
            out.write(pin);
            var successMessage = in.readNBytes(in.read());
            System.out.println(new String(successMessage));
        } catch(Error e) {
            e.printStackTrace();
        }
    }
}
