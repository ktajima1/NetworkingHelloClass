package edu.sjsu.cs158a;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

public class HeUDPServ {

    private static int port;
    private static String clientName;

    public HeUDPServ(String port) {
        this.port = setPort(port);
    }

    public static void main(String[] args) throws IOException {
        if (args.length==0) {
            System.out.println("Missing required parameter: '<port>'");
            System.exit(0);
        }
        HeUDPServ client = new HeUDPServ(args[0]);

        try(var sock = new DatagramSocket(client.getPort())) {
            System.out.println("I made it here " + client.getPort());
            var bytes = new byte[512];
            var bb = ByteBuffer.wrap(bytes);
            var packet = new DatagramPacket(bytes, bytes.length);
            while (true) {
                sock.setSoTimeout(8000);
                try {
                    sock.receive(packet);
                } catch (SocketTimeoutException e) {
                    System.out.println("I'm bored");
                    System.exit(0);
                }
                String data = new String(bytes, 2, packet.getLength()-2);
//                System.out.println("I got a packet: " + data);
                System.out.println("I got a packet: " + bb.getShort() + " " + data);
                bb.position(0);
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
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
}
