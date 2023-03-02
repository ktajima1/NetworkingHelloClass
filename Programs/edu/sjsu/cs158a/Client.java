package edu.sjsu.cs158a;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Client {

    private String clientName;
    private int conversationNumber;
    private short discussionPointer;
    private ByteArrayOutputStream bo;
    private int offSet;

    public Client(String clientName, short discussionPointer) {
        this.clientName = clientName;
        this.discussionPointer = discussionPointer;
        this.bo = new ByteArrayOutputStream();
        this.offSet = -1;
    }

    public void writeToOutputStream(byte[] bytes) throws IOException {
        bo.write(bytes);
    }

    public ByteArrayOutputStream getByteArrayOutputStream() {
        return bo;
    }
    public String getClientName() {
        return clientName;
    }

    public short getDiscussionPointer() {
        return discussionPointer;
    }

    public void setDiscussionPointer(int discussionPointer) {
        this.discussionPointer = (short)discussionPointer;
    }

    public int getOffSet() {
        return offSet;
    }
    public void setOffSet(int offSet) {
        this.offSet = offSet;
    }

    public int getConversationNumber() {
        return conversationNumber;
    }
}
