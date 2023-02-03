package edu.sjsu.cs158a;

public class cmdline {
    public static void main(String[] args) {
        System.out.println("i'm going to connect to " + args[1] + ":" + args[2]);
        for (int i=0; i<args.length; i++) {
            System.out.println(args[i]);
        }
    }
}
