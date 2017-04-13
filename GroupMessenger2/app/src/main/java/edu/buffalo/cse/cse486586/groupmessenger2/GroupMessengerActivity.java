package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.buffalo.cse.cse486586.groupmessenger2.Constants.DELIM;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.parseBoolean;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */

/*
* References
* 1) https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicInteger.html
* 2) https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html
* 3) https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/PriorityBlockingQueue.html
* 4) Class Notes
* */
public class GroupMessengerActivity extends Activity {

    private static final Boolean DEBUG = FALSE;

    private static final String TAG = GroupMessengerActivity.class.getSimpleName();
    private static final String KEY_COLUMN_NAME = "key";
    private static final String VALUE_COLUMN_NAME = "value";

    static final int[] REMOTE_PORTS = {11108, 11112, 11116, 11120, 11124};
    static final int SERVER_PORT = 10000;

    private TextView mTextView;
    private Button mButtonPTest, mSendButton;
    private EditText mEditText;

    private ContentResolver mContentResolver;

    private Uri mUri;

    private int MY_PORT;

    //Sequencing variables
    private AtomicInteger messageIdCounter = new AtomicInteger();
    private AtomicInteger sequenceCounter = new AtomicInteger();
    private AtomicInteger storeCounter = new AtomicInteger();

    private ConcurrentHashMap<Integer, Integer> rcvdMsgIdCountersFromPorts = new ConcurrentHashMap<Integer, Integer>();

    private ArrayList<Integer> availablePorts = new ArrayList<Integer>();
    private ArrayList<Integer> proposalsCount = new ArrayList<Integer>();
    private ArrayList<Float> highestProposalList = new ArrayList<Float>();

    private PriorityBlockingQueue<CustomMessage> holdBackPriorityQueue = new PriorityBlockingQueue<CustomMessage>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

        mContentResolver = getContentResolver();
        mTextView = (TextView) findViewById(R.id.textView1);
        mTextView.setMovementMethod(new ScrollingMovementMethod());

        mButtonPTest = (Button) findViewById(R.id.button1);
        mButtonPTest.setOnClickListener(new OnPTestClickListener(mTextView, getContentResolver()));

        mEditText = (EditText) findViewById(R.id.editText1);

        mSendButton = (Button) findViewById(R.id.button4);


        init();

        mSendButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = mEditText.getText().toString() + "\n";
                mEditText.setText("");
                sendMessage(msg, Constants.NEW);
            }
        });

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    // ******************************** HELPER FUNCTIONS START *************************************

    /**
     * buildUri() demonstrates how to build a URI for a ContentProvider.
     *
     * @param scheme
     * @param authority
     * @return the URI
     */
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    /**
     * Initialization of server and other variables
     */
    private void init() {
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        MY_PORT = Integer.parseInt(myPort);

        for (int i = 0; i < REMOTE_PORTS.length; i++) {
            availablePorts.add(REMOTE_PORTS[i]);
            rcvdMsgIdCountersFromPorts.put(REMOTE_PORTS[i], 0);
        }
    }

    /**
     * Iterates the priority queue to fetch an object
     *
     * @param message
     * @return CustomMessage
     */
    private CustomMessage iterateAndFetch(CustomMessage message) {

        Iterator<CustomMessage> itr = holdBackPriorityQueue.iterator();
        CustomMessage oldMessage = null;

        while (itr.hasNext()) {
            CustomMessage temp = itr.next();
            if (temp.equals(message)) {
                oldMessage = temp;
            }
        }
        return oldMessage;
    }

    /**
     * Send the message via client
     *
     * @param msg
     * @param messageType
     */
    private void sendMessage(String msg, String messageType) {
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, messageType);
    }

    /**
     * Decomposes the string to make a new CustomMessage Object
     *
     * @param msgFromClient
     * @return A new CustomMessage Object
     */
    private CustomMessage decomposeMessage(String msgFromClient) {

        String messageArr[] = msgFromClient.split(Constants.DELIM);

        String messageType = messageArr[0].trim();
        int senderPort = Integer.parseInt(messageArr[1].trim());
        String message = messageArr[2].trim();
        float priority = Float.parseFloat(messageArr[3].trim());
        int messageId = Integer.parseInt(messageArr[4].trim());
        boolean isDeliverable = parseBoolean(messageArr[5].trim());

        CustomMessage newMessage = new CustomMessage();
        newMessage.setMessage(message);
        newMessage.setMessageId(messageId);
        newMessage.setSenderPort(senderPort);
        newMessage.setDeliverable(isDeliverable);
        newMessage.setMessageType(messageType);
        newMessage.setPriority(priority);

        return newMessage;
    }

    /**
     * Creates a new CustomMessage object with message and message type
     *
     * @param messageToSend
     * @param messageType
     * @return A new CustomMessage
     */
    private CustomMessage getNewCustomMessage(String messageToSend, String messageType) {
        int newId = messageIdCounter.getAndIncrement();
        proposalsCount.add(newId, 0);
        highestProposalList.add(newId, 0.0f);
        return (new CustomMessage(MY_PORT, newId, messageToSend, 0.0f, messageType));
    }

    /**
     * A function when a new message is received
     *
     * @param newMessage
     */
    private void callNewMessageRecieved(CustomMessage newMessage) {

        float newPriority = sequenceCounter.incrementAndGet() + (availablePorts.indexOf(MY_PORT) / 10.0f);
        newMessage.setPriority(newPriority);
        newMessage.setMessageType(Constants.PROPOSAL);

        holdBackPriorityQueue.add(newMessage);
        sendMessage(newMessage.toString(), Constants.PROPOSAL);
    }

    /**
     * A function when a proposal is received
     *
     * @param newMessage
     */
    private void callProposalRecieved(CustomMessage newMessage) {
        int count = proposalsCount.get(newMessage.getMessageId());
        proposalsCount.set(newMessage.getMessageId(), count + 1);

        float priorPriority = highestProposalList.get(newMessage.getMessageId());

        if (newMessage.getPriority() > priorPriority) {
            priorPriority = newMessage.getPriority();
        }

        highestProposalList.set(newMessage.getMessageId(), priorPriority);

        if (proposalsCount.get(newMessage.getMessageId()) >= availablePorts.size()) {
            newMessage.setMessageType(Constants.AGREEMENT);
            newMessage.setDeliverable(true);
            newMessage.setPriority(highestProposalList.get(newMessage.getMessageId()));
            sendMessage(newMessage.toString(), Constants.AGREEMENT);
        }
    }

    /**
     * A function when an agreement is reached
     *
     * @param newMessage
     * @return A string of all messages which are deliverable
     */
    private String callAgreementRecieved(CustomMessage newMessage) {

        String deliverableMessages = "";

        if (holdBackPriorityQueue.contains(newMessage)) {
            CustomMessage oldMessage = iterateAndFetch(newMessage);
            holdBackPriorityQueue.remove(oldMessage);
            holdBackPriorityQueue.add(newMessage);
        }
        while (!holdBackPriorityQueue.isEmpty()) {

            CustomMessage tempCustomMessage = holdBackPriorityQueue.peek();

            int senderPort = tempCustomMessage.getSenderPort();

            int messageIdNeedToDeliver = rcvdMsgIdCountersFromPorts.get(senderPort);

            String messageToDeliver = tempCustomMessage.getMessage();

            if (messageIdNeedToDeliver == tempCustomMessage.getMessageId() && tempCustomMessage.isDeliverable()) {
                holdBackPriorityQueue.remove(tempCustomMessage);
                rcvdMsgIdCountersFromPorts.put(senderPort, messageIdNeedToDeliver + 1);
                deliverableMessages = deliverableMessages + DELIM + messageToDeliver;
            } else {
                break;
            }
        }
        return deliverableMessages;
    }

    /**
     * A Failure handler with respect to a given port
     *
     * @param errorPort
     */
    private void callFailureHandler(int errorPort) {

        if (availablePorts.contains(errorPort)) {
            availablePorts.remove(new Integer(errorPort));
            rcvdMsgIdCountersFromPorts.remove(new Integer(errorPort));
        }

        Iterator<CustomMessage> itr = holdBackPriorityQueue.iterator();

        while (itr.hasNext()) {
            CustomMessage temp = itr.next();
            if (temp.getSenderPort() == errorPort) {
                holdBackPriorityQueue.remove(temp);
            }
        }

        itr = holdBackPriorityQueue.iterator();

        while (itr.hasNext()) {
            CustomMessage temp = itr.next();
            if (temp.getSenderPort() == MY_PORT && temp.getMessageType().equals("PROPOSAL") &&
                    proposalsCount.get(temp.getMessageId()) >= availablePorts.size()) {
                sendMessage(temp.toString(), "PROPOSAL");
            }
        }

        // Log.v(TAG, "INSIDEFAILUREAFTER " + holdBackPriorityQueue.size());


    }

    // ******************************** HELPER FUNCTIONS ENDS *************************************


    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     * <p>
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            try {
                while (true) {
                    Socket clientSocket = serverSocket.accept();

                    OutputStream serverSocketOutputStream = clientSocket.getOutputStream();
                    InputStreamReader serverSocketInputStreamReader = new InputStreamReader(clientSocket.getInputStream());

                    PrintWriter serverOutputPrintWriter = new PrintWriter(serverSocketOutputStream, true);
                    BufferedReader serverInputBufferedReader = new BufferedReader(serverSocketInputStreamReader);

                    String commMessage;
                    String clientMessage = "";

                    while ((commMessage = serverInputBufferedReader.readLine()) != null) {
                        if (commMessage.equals(Constants.SYN)) {
                            serverOutputPrintWriter.println(Constants.SYNACK);
                        } else if (commMessage.equals(Constants.ACK)) {
                            serverOutputPrintWriter.println(Constants.ACK);
                        } else if (commMessage.equals(Constants.STOP)) {
                            serverOutputPrintWriter.println(Constants.STOPPED);
                            break;
                        } else {
                            if (commMessage.length() != 0) {
                                clientMessage = commMessage;
                                serverOutputPrintWriter.println(Constants.OK);
                            }
                        }
                    }
                    serverSocketOutputStream.close();
                    serverInputBufferedReader.close();
                    serverOutputPrintWriter.close();
                    serverSocketInputStreamReader.close();

                    // KeepAlive Feature ??? !!!
                    if (clientMessage == null || clientMessage.length() == 0) {
                        return null;
                    }

                    CustomMessage newMessage = decomposeMessage(clientMessage);

                    if (newMessage.getMessageType().equals(Constants.NEW)) {
                        callNewMessageRecieved(newMessage);

                    } else if (newMessage.getMessageType().equals(Constants.PROPOSAL)) {
                        callProposalRecieved(newMessage);

                    } else if (newMessage.getMessageType().equals(Constants.AGREEMENT)) {

                        String deliverableMessages = callAgreementRecieved(newMessage);
                        String messages[] = deliverableMessages.split(Constants.DELIM);
                        for (int i = 0; i < messages.length; i++) {
                            if (messages[i] != null && messages[i] != "" && messages[i].length() != 0) {
                                publishProgress(messages[i]);
                            }
                        }
                    } else {
                        Log.e(TAG, "Error Unknown Message ::: " + clientMessage);
                    }

                }
            } catch (IOException e) {
                Log.e(TAG, "ServerTask socket IOException : " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                Log.e(TAG, "ServerTask socket Exception : " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        protected void onProgressUpdate(String... strings) {
            String strReceived = strings[0].trim();
            mTextView.append(strReceived + "\n");
            ContentValues contentValues = new ContentValues();
            contentValues.put(KEY_COLUMN_NAME, Integer.toString(storeCounter.getAndIncrement()));
            contentValues.put(VALUE_COLUMN_NAME, strReceived);
            mContentResolver.insert(mUri, contentValues);
        }
    }


    /**
     * A simple message sending protocol
     *
     * @param message
     */
    private int sendMessageForServer(String message, String messageType, int port) {
        int errorPort = 0;
        try {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), port);

            OutputStream clientOutputStream = socket.getOutputStream();
            InputStreamReader clientInputStreamReader = new InputStreamReader(socket.getInputStream());

            PrintWriter clientOutputPrintWriter = new PrintWriter(clientOutputStream, true);
            BufferedReader clientInputBufferReader = new BufferedReader(clientInputStreamReader);

            String msgFromServer = "";

            clientOutputPrintWriter.println(Constants.SYN);

            while ((msgFromServer = clientInputBufferReader.readLine()) != null) {
                if (msgFromServer.equals(Constants.SYNACK)) {
                    clientOutputPrintWriter.println(Constants.ACK);
                } else if (msgFromServer.equals(Constants.ACK)) {
                    clientOutputPrintWriter.println(message);
                } else if (msgFromServer.equals(Constants.OK)) {
                    clientOutputPrintWriter.println(Constants.STOP);
                } else if (msgFromServer.equals(Constants.STOPPED)) {
                    break;
                }
            }

            if (msgFromServer == null || !msgFromServer.equals(Constants.STOPPED)) {
                errorPort = port;
            }
            clientOutputPrintWriter.close();
            clientInputBufferReader.close();
            socket.close();
        } catch (Exception e) {
            Log.e(TAG, "This is Client Sparta : " + e.getMessage());
            e.printStackTrace();
        }
        return errorPort;
    }


    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko and vkumar25
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String message = msgs[0];
            String messageType = msgs[1];
            int errorPort = 0;
            try {
                if (messageType.equals("NEW")) {
                    CustomMessage newMessage = getNewCustomMessage(message, messageType);
                    String msgToSend = newMessage.toString().replaceAll("\n", "");
                    for (int remotePort : availablePorts) {
                        if (sendMessageForServer(msgToSend, messageType, remotePort) != 0) {
                            errorPort = remotePort;
                        }
                    }
                } else if (messageType.equals("AGREEMENT")) {
                    String msgToSend = message;
                    for (int remotePort : availablePorts) {
                        if (sendMessageForServer(msgToSend, messageType, remotePort) != 0) {
                            errorPort = remotePort;
                        }
                    }
                } else if (messageType.equals("PROPOSAL")) {
                    String msgToSend = message;
                    String messageArr[] = message.split(Constants.DELIM);
                    int senderPort = Integer.parseInt(messageArr[1].trim());
                    if (sendMessageForServer(msgToSend, messageType, senderPort) != 0) {
                        errorPort = senderPort;
                    }
                }
                if (errorPort != 0) {

                    callFailureHandler(errorPort);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}