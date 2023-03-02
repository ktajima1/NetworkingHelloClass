//package edu.sjsu.cs158a;
//
//import picocli.CommandLine;
//import picocli.CommandLine.Command;
//import picocli.CommandLine.Option;
//import picocli.CommandLine.Parameters;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//import java.net.InetSocketAddress;
//
//@Command
//public class Example {
//    public static void main(String[] args) {
//        System.exit(new CommandLine(new Example()).execute(args));
//    }
//
//    @Command(name = "client", description = "simple UDP client that reads lines and sends to server")
//    public void clientCli(@Parameters(paramLabel = "host:port", description = "destination host and UDP port") String hostPort,
//                          @Option(names = "--max-size", defaultValue = "512",
//                                  description = "maximum UDP message to send. larger messages will be sent as smaller pieces") int maxSize) throws IOException {
//        var lastColon = hostPort.lastIndexOf(':');
//        var host = hostPort.substring(0, lastColon);
//        var port = Integer.parseInt(hostPort.substring(lastColon + 1));
//        InetSocketAddress dest = new InetSocketAddress(host, port);
//        try (var sock = new DatagramSocket()) {
//            var br = new BufferedReader(new InputStreamReader(System.in));
//            String line;
//            while ((line = br.readLine()) != null) {
//                var bytes = line.getBytes();
//                int off = 0;
//                int len = bytes.length;
//                while (len > 0) {
//                    var sendSize = len;
//                    if (sendSize > maxSize) sendSize = maxSize;
//                    sock.send(new DatagramPacket(bytes, off, sendSize, dest));
//                    off += sendSize;
//                    len -= sendSize;
//                }
//            }
//        }
//    }
//
//    @Command(name = "server", description = "simple UDP server that receives messages and prints them")
//    public void serverCli(@Parameters(paramLabel = "port", description = "UDP port to listen on") int port) throws IOException {
//        try (var sock = new DatagramSocket(port)) {
//            while (true) {
//                var bytes = new byte[512];
//                var packet = new DatagramPacket(bytes, bytes.length);
//                sock.receive(packet);
//                System.out.printf("from %s: %s\n", packet.getSocketAddress(), new String(bytes, 0, packet.getLength()));
//                packet.setData("hello!".getBytes());
//                sock.send(packet);
//                System.out.printf("to %s: %s\n", packet.getSocketAddress(), new String(bytes, 0, packet.getLength()));
//            }
//        }
//    }
//}