import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class Main {
    private static final ConcurrentHashMap<String, ExpiringValue> dataStore = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<Socket>> subscriptions = new ConcurrentHashMap<>();

    static class ExpiringValue {
        String value;
        long expirationTime;

        public ExpiringValue(String value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        public boolean isExpired() {
            if (expirationTime == 0) {
                return false; // No expiration
            }
            return System.currentTimeMillis() > expirationTime;
        }
    }
    public static void main(String[] args) {
        int port = 6379;
        
        System.out.println("Starting server on port " + port);

        Thread cleanerThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000); // Check every second
                    
                    for (String key : dataStore.keySet()) {
                        ExpiringValue value = dataStore.get(key);
                        if (value != null && value.isExpired()) {
                            dataStore.remove(key);
                            System.out.println("Removed expired key: " + key);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        cleanerThread.setDaemon(true);
        cleanerThread.start();

        ExecutorService executorService = Executors.newFixedThreadPool(10); // Thread pool for handling multiple clients
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is online! Waiting for a client to connect...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected from: " + clientSocket.getRemoteSocketAddress());
                executorService.submit(() -> handleClient(clientSocket));
            }
            
            
        } catch (Exception e) {
            System.out.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ){           
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                //parsing the RESP
                //Example: *3\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nAyaan\r\n
                if (inputLine.startsWith("*")) {
                    int arraySize = Integer.parseInt(inputLine.substring(1));
                    String[] commandParts = new String[arraySize];
                    for (int i = 0; i < arraySize; i++) {
                        in.readLine();
                        commandParts[i] = in.readLine();
                    }

                    System.out.println("Received command: " + String.join(" ", commandParts));

                    if (commandParts[0].equals("SET")) {
                        String key = commandParts[1];
                        String value = commandParts[2];
                        long expiryTime = 0;
                        // Check if the command has an expiration time 
                        // Example: *5\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nAyaan\r\n$2\r\nEX\r\n$2\r\n10\r\n
                        // SET name Ayaan EX 10
                        if (commandParts.length == 5 && commandParts[3].equals("EX")) {
                            long expirySeconds = Long.parseLong(commandParts[4]);
                            expiryTime = System.currentTimeMillis() + (expirySeconds * 1000);
                        }

                        dataStore.put(key, new ExpiringValue(value, expiryTime));
                        System.out.println("SET command executed: Key = " + key + ", Value = " + value + ", Expiry = " + (expiryTime == 0 ? "No expiry" : expiryTime));
                        //RESP format for simple string: +OK\r\n
                        out.print("+OK\r\n");
                        out.flush();
                    } else if (commandParts[0].equals("GET")) {
                        String key = commandParts[1];
                        ExpiringValue value = dataStore.get(key);
                        if (value != null && value.isExpired()) { // Checking for expiration if the background process hasn't removed it yet
                            dataStore.remove(key);
                            value = null;
                        }
                        if (value != null) {
                            //RESP format for bulk string: $<length>\r\n<value>\r\n
                            out.print("$" + value.value.length() + "\r\n" + value.value + "\r\n");
                        } else {
                            //RESP format for nil bulk string: $-1\r\n
                            out.print("$-1\r\n");
                        }
                        out.flush();
                    } else if (commandParts[0].equals("PING")) {
                        //RESP format for simple string: +PONG\r\n
                        out.print("+PONG\r\n");
                        out.flush();
                    } else if (commandParts[0].equals("DEL")) {
                        String key = commandParts[1];
                        if (dataStore.containsKey(key)) {
                            dataStore.remove(key);
                            //RESP format for integer: :<number>\r\n
                            out.print(":1\r\n");
                        } else {
                            out.print(":0\r\n");
                        }
                        out.flush();
                    } else if (commandParts[0].equals("INCR")) {
                        String key = commandParts[1];
                        ExpiringValue value = dataStore.get(key);
                        if (value != null && value.isExpired()) { // Checking for expiration if the background process hasn't removed it yet
                            dataStore.remove(key);
                            value = null;
                        }

                        
                        if (value != null) {
                            try {
                                int intValue = Integer.parseInt(value.value);
                                intValue++;
                                dataStore.put(key, new ExpiringValue(String.valueOf(intValue), value.expirationTime));
                                out.print(":" + intValue + "\r\n");
                            } catch (NumberFormatException e) {
                                out.print("-ERR value is not an integer\r\n");
                            }
                        } else { // If the key does not exist, set it to 1
                            dataStore.put(key, new ExpiringValue("1", 0));
                            out.print(":1\r\n");
                        }
                        out.flush();
                    } else if (commandParts[0].equals("DECR")) {
                        String key = commandParts[1];
                        ExpiringValue value = dataStore.get(key);
                        if (value != null && value.isExpired()) { // Checking for expiration if the background process hasn't removed it yet
                            dataStore.remove(key);
                            value = null;
                        }

                        if (value != null) {
                            try {
                                int intValue = Integer.parseInt(value.value);
                                intValue--;
                                dataStore.put(key, new ExpiringValue(String.valueOf(intValue), value.expirationTime));
                                out.print(":" + intValue + "\r\n");
                            } catch (NumberFormatException e) {
                                out.print("-ERR value is not an integer\r\n");
                            }
                        } else { // If the key does not exist, set it to -1
                            dataStore.put(key, new ExpiringValue("-1", 0));
                            out.print(":-1\r\n");
                        }
                        out.flush();
                    } else if (commandParts[0].equals("SUBSCRIBE")) {
                        String channel = commandParts[1];
                        subscriptions.putIfAbsent(channel, new CopyOnWriteArrayList<>());
                        subscriptions.get(channel).add(clientSocket);
                        System.out.println("Client" + clientSocket.getRemoteSocketAddress() + " subscribed to channel: " + channel);
                        out.print("+Subscribed to channel: " + channel + "\r\n");
                        out.flush();
                    } else if (commandParts[0].equals("PUBLISH")) {
                        String channel = commandParts[1];
                        String message = commandParts[2];
                        List<Socket> subscribers = subscriptions.get(channel);
                        if (subscribers != null) {
                            for (Socket subscriber : subscribers) {
                                try {
                                    PrintWriter subscriberOut = new PrintWriter(subscriber.getOutputStream(), true);
                                    //RESP format for message: +message\r\n
                                    subscriberOut.print("+Message from " + channel + ": " + message + "\r\n");
                                    subscriberOut.flush();
                                } catch (Exception e) {
                                    System.out.println("Failed to send message to subscriber: " + e.getMessage());
                                }
                            }
                        }
                        else {
                            System.out.println("No subscribers for channel: " + channel);
                        }
                        out.print("+Message published to channel: " + channel + "\r\n");
                        out.flush();
                    } else {
                        System.out.println("Unknown command: " + commandParts[0]);
                        //RESP format for error: -Error message\r\n
                        out.print("-ERR unknown command '" + commandParts[0] + "'\r\n");
                        out.flush();
                    }
                    
                } else {
                    System.out.println("Received: " + inputLine);
                }
            }
            
            System.out.println("Client disconnected.");
        } catch (Exception e) {
            System.out.println("Client handler exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}