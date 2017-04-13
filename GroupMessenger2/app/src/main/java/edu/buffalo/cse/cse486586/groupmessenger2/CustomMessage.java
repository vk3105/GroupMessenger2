package edu.buffalo.cse.cse486586.groupmessenger2;

/**
 * Created by vipin on 3/15/17.
 */

public class CustomMessage implements Comparable<CustomMessage> {
    private boolean isDeliverable;
    private int messageId;
    private float priority;
    private int senderPort;
    private String message;
    private String messageType;

    public CustomMessage() {

    }

    public CustomMessage(int senderPort, int messageId, String message, float priority,
                         String msgType) {
        this.senderPort = senderPort;
        this.message = message.trim().replaceAll("\n", "");
        this.priority = priority;
        this.messageType = msgType.replaceAll("\n", "");
        this.isDeliverable = false;
        this.messageId = messageId;
    }

    @Override
    public String toString() {
        String messageToSend = this.messageType + Constants.DELIM +
                this.senderPort + Constants.DELIM +
                this.message + Constants.DELIM +
                this.priority + Constants.DELIM +
                this.messageId + Constants.DELIM +
                this.isDeliverable;
        return messageToSend.replaceAll("\n", "");
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public String getMessage() {
        return message.replaceAll("\n", "");
    }

    public int getSenderPort() {
        return senderPort;
    }

    public void setSenderPort(int senderPort) {
        this.senderPort = senderPort;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setPriority(float priority) {
        this.priority = priority;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setDeliverable(boolean deliverable) {
        isDeliverable = deliverable;
    }

    public float getPriority() {
        return priority;
    }

    public String getMessageType() {
        return messageType.replaceAll("\n", "");
    }

    public boolean isDeliverable() {
        return isDeliverable;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof CustomMessage)) {
            return false;
        }

        CustomMessage secondMessage = (CustomMessage) object;

        if (this.senderPort == secondMessage.getSenderPort()
                && this.message.equals(secondMessage.getMessage())
                && this.messageId == secondMessage.getMessageId()) {
            return true;
        }

        return false;
    }

    @Override
    public int compareTo(CustomMessage obj) {
        if (this.senderPort == obj.getSenderPort()) {
            if (this.messageId > obj.getMessageId()) {
                return 1;
            } else {
                return -1;
            }
        } else {
            if (this.priority > obj.getPriority()) {
                return 1;
            } else if (this.priority < obj.getPriority()) {
                return -1;
            } else {
                if (this.senderPort > obj.getSenderPort()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }
    }
}
