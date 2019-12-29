package chat_group;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class Client2 extends Thread {
    public static String xyz;
    private int user_id = -1;
    public String username;
    private String cmd_result = null;
    private String login = null;
    private String response;

    private Map<String, Set<String[]>> TopicMessages = new HashMap<String, Set<String[]>>();
    private HashSet<String> topicSet = new HashSet<>();
    public static ArrayList<Client2> Client2_List = new ArrayList<>();

    private HashSet<String> getTopics() {
        return this.topicSet;
    }

    private ArrayList<MessageListener> messageListener = new ArrayList<>();
    private ArrayList<UserStatusListener> userStatusListeners = new ArrayList<>();

    public Client2() {
        Client2_List.add(this);
    }

    ///////////////////////
    // login process

    public boolean login(String name, String password) throws IOException {
        String cmd1 = "login " + name + " " + password + "\n";
        handleClient(cmd1);
        String response = this.cmd_result;
        System.out.println("Response : " + response);
        if (!this.cmd_result.trim().equalsIgnoreCase("Ok login")) {

            return false;
        }

        // startMessageReader();

        return true;
    }

    private void handleClient(String line) throws IOException {

        String[] tokens = line.split(" ");
        // if(tokens==null || tokens.length <= 0){continue;}

        String cmd = tokens[0];


        if ("logoff".equalsIgnoreCase(cmd) || "quit".equalsIgnoreCase(cmd)) {
            handleLogoff();
            return;
        } else if ("login".equalsIgnoreCase(cmd)) {
            this.handleLogin(tokens);

        } else if ("register".equalsIgnoreCase(cmd)) {
            String[] tokensMsg = line.split(" ", 3);
            this.handleRegister(tokensMsg);

        } else if ("join".equalsIgnoreCase(cmd)) {
            handleJoin(tokens);
        } else if ("msg".equalsIgnoreCase(cmd)) {
            String[] tokensMsg = line.split(" ", 3);
            handleMessage(tokensMsg);
            System.out.println("in handle client");
        } else {
            String msg = "unknown " + cmd + "\n";
            write(msg);
        }
    }


    private boolean isAuthenticated(String username, String password) {
        dbOperations dbOp = new dbOperations();
        try {

            List<Map<String, Object>> users = dbOp.auth(username, password);

            if (users.size() == 1) {
                Map<String, Object> user = users.get(0);
                this.user_id = (int) user.get("user_id");

                return true;
            }

        } catch (SQLException e) {
            // e.printStackTrace();
        }

        return false;
    }

    private void handleLogin(String[] tokens) throws IOException {

        String msg = null;
        if (tokens.length == 3) {
            String login = tokens[1];
            String password = tokens[2];

            if (
                    this.isAuthenticated(login, password)
            ) {
                this.cmd_result = "Ok login\n";
                msg = "Ok login\n";

                this.login = login;
                System.out.println("user logged in successfully: " + login);
                String onlineMsg = "online " + login + "\n";

            } else {
                msg = "error login\n";
            }
        }

    }


    ///////////////////////
    // Regestration process
    public boolean register(String name, String password) throws IOException {
        String cmd1 = "register " + name + " " + password + "\n";

        handleClient(cmd1);
        String response = this.cmd_result;
        System.out.println("Response : " + response);
        if (!this.cmd_result.trim().equalsIgnoreCase("ok register")) {
            return false;
        }

        return true;
    }

    private void handleRegister(String[] tokens) throws IOException {
        String msg;
        if (tokens.length != 3) {
            return;
        }

        String username = tokens[1];
        String password = tokens[2];

        if (this.exists(username)) {

            this.cmd_result = "error username exists\n";

            return;
        }

        int user_id = this.createUser(username, password);

        if (user_id == -1) {
            this.cmd_result = "error register failure\n";
        } else {
            this.cmd_result = "ok register\n";
        }

    }

    private int createUser(String username, String password) {
        dbOperations dbOp = new dbOperations();

        try {
            int user_id = dbOp.newAccount(username, password);
            return user_id;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private boolean exists(String username) {
        dbOperations dbOp = new dbOperations();
        try {
            List<Map<String, Object>> users = dbOp.getUsers();
            for (Map<String, Object> user : users) {
                if (username.equalsIgnoreCase((String) user.get("username"))) {
                    return true;
                }
            }
        } catch (SQLException e) {
            // e.printStackTrace();
        }

        return false;
    }


////////////////
    // get_topics to client

    public String[] getAllTopics() throws IOException {
        String cmd = "getTopics\n";
        this.response = handleGetTopics();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String response = this.response;


        String[] ss = response.split(" #");
        System.out.println("ss : " + ss);
        return ss;
    }

    private String handleGetTopics() throws IOException {
        List<Client2> clientList = Client2.getClientList();

        HashSet<String> topics, uniqueTopics = new HashSet<>();

        for (Client2 c_worker : clientList) {
            topics = c_worker.getTopics();
            System.out.println("topics=" + topics);
            for (String topic : topics) {
                uniqueTopics.add(topic);
            }
        }

        String msg = "Topics";

        for (String topic : uniqueTopics) {
            msg = msg + " " + topic;
        }
        msg = msg + "\n";
        System.out.println("msg" + msg);
        return msg;
        //this.outputStream.write(msg.getBytes());
    }

    public static List<Client2> getClientList() {
        return Client2_List;
    }

    private void handleJoin(String[] tokens) {
        if (tokens.length <= 1) {
            return;
        }

        String topic = tokens[1];
        topicSet.add(topic);
    }

    private void handleLeave(String[] tokens) {
        if (tokens.length <= 1) {
            return;
        }

        String topic = tokens[1];
        topicSet.remove(topic);
    }

    public boolean isMemberOfTopic(String topic) {
        return topicSet.contains(topic);
    }

    public HashSet<String> getSubscribedTopics() {
        return this.topicSet;
    }

    public void joinTopic(String text) throws IOException {
        String cmd = "join #" + text + "\n";

       // handleClient(cmd);

        topicSet.add(text);

    }

    ////////////////////////
    ///// client logoff
    private void handleLogoff() throws IOException {

        String offlineMsg = "offline " + login + "\n";
        List<Client2> cliet_List = Client2.getClientList();

        // notify users on logoff user
        for (Client2 worker : cliet_List) {
            if (!login.equals(worker.getLogin())) {
                worker.send(offlineMsg);

            }
        }

    }

    public String getLogin() {
        return this.login;
    }

    public void logoff() throws IOException {
        String cmd = "logoff" + "\n";

        write(cmd);
    }

    private void send(String Msg) throws IOException {
        String[] tokens = new String[0];
        if (login != null)
          //  handleClient(Msg);
            tokens=Msg.split(" ",3);
            handleMessage1(tokens);
    }


    private void write(String msg) {
        System.out.println(msg);
    }

    private void startMessageReader() {

        multi_client t = new multi_client(this);
        t.start();
    }


    //////////////////
// Listeners
    public void addMessageListener(MessageListener messageListener) {
        this.messageListener.add(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        this.messageListener.remove(messageListener);
    }


    ///////////////
    /// send messages
    public void sendMessage(String to, String message) throws IOException {
        String cmd = "msg " + "#" + to + " " + message + "\n";
        //String[] tokens=cmd.split(" ",3);
        rabbit_client_sender(to, message);
        handleClient(cmd);
    }

    public Set<String[]> getMessagesFromTopic(String topic) {
        Set<String[]> messageSet;
        if (!TopicMessages.containsKey(topic)) {
            messageSet = new HashSet<>();
        } else {
            messageSet = TopicMessages.get(topic);
        }
        return messageSet;
    }

    private void handleMessage(String[] tokens) throws IOException {
        System.out.println("in handle message");

        String sendTo = tokens[1];
        String body = tokens[2];

        boolean isTopic = sendTo.charAt(0) == '#';
         String send2=sendTo.replace("#","");
        String[] tokens1 = new String[0];
        List<Client2> workerList = Client2.getClientList();
        System.out.println("workerList.toString()"+workerList.get(0).username);
        for (Client2 worker : workerList) {
            if (isTopic) {
                System.out.println(worker.topicSet.toString()+"   to send "+ sendTo);
                if (worker.isMemberOfTopic(send2)) {
                    tokens1=("msg " + sendTo + ":" + login + " " + body + "\n").split(" ",3);
                    handleMessage1(tokens1);

                }
                continue;
            }

            if (worker.getLogin().equalsIgnoreCase(sendTo)) {
                tokens=("msg " + login + " " + body + "\n").split(" ",3);
                handleMessage1(tokens1);
                System.out.println("token 2 "+tokens1);
                break;
            }
        }
    }


    private void handleMessage1(String[] tokens) {
        System.out.println("in handle Message1");

        String login = tokens[1];
        String msBody = tokens[2];
        String[] topicUser = login.split(":");

        if (topicUser.length == 1) {
            System.out.println("in listener 1 ");
            for (MessageListener listener : messageListener) {
                listener.onMessage(login, msBody);
            }
            return;
        }
        System.out.println("in listener 2 ");
        String topic = topicUser[0].substring(1);
        String user = topicUser[1];

        String[] message = new String[2];
        message[0] = user;
        message[1] = msBody;

        Set<String[]> newSet;

        if (!TopicMessages.containsKey(topic)) {

            newSet = new HashSet<>();
            newSet.add(message);
            TopicMessages.put(topic, newSet);
        } else {
            newSet = TopicMessages.get(topic);

            newSet.add(message);

            TopicMessages.replace(topic, newSet);
        }
        System.out.println("in listener 4");
        for (MessageListener listener : messageListener) {
            listener.onMessage(user, msBody);
        }
    }
    private static final String EXCHANGE_NAME = "topic_logs";
    void rabbit_client_sender(String topic, String msg) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();
           /* channel.exchangeDeclare(EXCHANGE_NAME, "topic");

            String routingKey = topic;
            String message = msg;

            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
            //System.out.println(" [x] Sent '" + routingKey + "':'" + message + "'");
        }
*/
            //channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
            channel.queueDeclare("QUEUE_NAME", false, false, false,
                    null);

            channel.basicPublish("", "QUEUE_NAME", null,
                    msg.getBytes("UTF-8"));

        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    String tabbit_receiver(String topic) throws IOException, TimeoutException {
        Client2.xyz="";
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection=factory.newConnection();

        Channel channel = connection.createChannel();
        //channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
        channel.queueDeclare("QUEUE_NAME", false, false, false,
                null);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            Client2.xyz+=message+"\n";
            System.out.println("in rabbit receive"+Client2.xyz);
        };
        channel.basicConsume("QUEUE_NAME", true, deliverCallback,
                consumerTag -> {
                });
         channel.close();
         connection.close();
            return Client2.xyz;

    }
}


  class multi_client extends Thread {

    Client2 client;

    public multi_client(Client2 client) {
        this.client = client;
    }

    public void run() {
        System.out.println("");

    }

}