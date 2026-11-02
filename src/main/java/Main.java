import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Map;
import java.io.*;

public class Main {
    private static final ConcurrentHashMap<String, ExpiringValue> dataStore = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<Socket>> subscriptions = new ConcurrentHashMap<>();
    private static final int maxKeys = 10000;
    private static final int backupFreq = 300000; // ms between backups
    private static final File snapshotFile = new File("dump.rdb");
    private static BufferedWriter aofWriter;

    public static void main(String[] args) {
        int port = 6379;
        
        try {
            aofWriter = new BufferedWriter(new FileWriter("appendonly.aof", true));
        } catch (FileNotFoundException e) {
            System.out.println("FileNotFoundException: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Starting server on port " + port);

        loadRDBSnapshot();
        readAOF();

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

        Thread RDBsnapshot = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(backupFreq);
                    createRDBSnapshot();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        RDBsnapshot.start();

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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    try {
        if (aofWriter != null) {
            aofWriter.close();
        }
    } catch (IOException e) {
        System.out.println("Failed to close AOF writer: " + e.getMessage());
        e.printStackTrace();
    }
}));
    }

    private static void loadRDBSnapshot() {
        if (!snapshotFile.exists()) {
            System.out.println("No snapshot file found; starting with an empty database.");
            return;
        }
        try (DataInputStream dis = new DataInputStream(new FileInputStream(snapshotFile))) {
            if (!(dis.readUTF().equals("RDB"))) {
                System.out.println("Corrupted RDB Snapshot file header");
                return;
            }

            int datasize = dis.readInt();
            long now = System.currentTimeMillis();

            for (int i = 0; i < datasize; i++) {
                String key = dis.readUTF();
                String value = dis.readUTF();
                long ets = dis.readLong();
                long la = dis.readLong();
                if (ets == 0 || ets > now)
                    dataStore.put(key, new ExpiringValue(value, ets, la));
            }

            System.out.println("Successfully restored " + dataStore.size() + " key(s) through RDB Snapshot.");
        } catch (IOException e) {
            System.out.println("Failed to parse RDB Snapshot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createRDBSnapshot() {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(snapshotFile))) {
            dos.writeUTF("RDB");
            dos.writeInt(dataStore.size());

            for (Map.Entry<String, ExpiringValue> entry : dataStore.entrySet()) {
                if (entry.getValue().isExpired())
                    continue;
                dos.writeUTF(entry.getKey());
                dos.writeUTF(entry.getValue().getValue());
                dos.writeLong(entry.getValue().getExpirationTimeSystem());
                dos.writeLong(entry.getValue().lastAccessed());
            }
            dos.flush();
            System.out.println("RDB successfully saved to " + snapshotFile.getAbsolutePath());
        } catch (FileNotFoundException e) {
            System.out.println("FileNotFoundException: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            new PrintWriter("appendonly.aof").close();
        } catch (FileNotFoundException e) {
            System.out.println("FileNotFoundException: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void AppendAOF(String[] commandParts) throws IOException {
        if (commandParts[0].equals("SET") && commandParts.length == 5 && commandParts[3].equals("EX"))
        {
            commandParts[3] = "PXAT";
            long seconds = Long.parseLong(commandParts[4]);
            commandParts[4] = String.valueOf(System.currentTimeMillis() + (seconds * 1000));
        }
        if (commandParts[0].equals("EXPIRE")) 
        {
            commandParts[0] = "PEXPIREAT";
            long seconds = Long.parseLong(commandParts[2]);
            commandParts[2] = String.valueOf(System.currentTimeMillis() + (seconds * 1000));
        }
        aofWriter.write("*" + commandParts.length + "\r\n");
        for (String commandPart : commandParts)
        {
            aofWriter.write("$" + commandPart.length() + "\r\n" + commandPart + "\r\n");
        }
        aofWriter.flush();
    }

    private static void readAOF() {
        try (BufferedReader aofReader = new BufferedReader(new FileReader("appendonly.aof"))) {
            String inputLine;
            int commandCount = 0;
            while ((inputLine = aofReader.readLine()) != null) {
                //parsing the RESP
                //Example: *3\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nAyaan\r\n
                if (inputLine.startsWith("*")) {
                    int arraySize = Integer.parseInt(inputLine.substring(1));
                    String[] commandParts = new String[arraySize];
                    for (int i = 0; i < arraySize; i++) {
                        aofReader.readLine();
                        commandParts[i] = aofReader.readLine();
                    }
                    if (executeCommand(commandParts)) {
                        commandCount++;
                    }
                }
            }
            System.out.println("Replayed " + commandCount + " command(s) through AOF Log.");
            aofReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("FileNotFoundException: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void LRUEviction() {
        if (dataStore.size() < maxKeys)
            return;
        String oldestKey = null;
        long oldestUsage = Long.MAX_VALUE;

        for (Map.Entry<String, ExpiringValue> entry : dataStore.entrySet()) { //iterate through hashmap to find key that been used least recently
            if (entry.getValue().lastAccessed() < oldestUsage) {
                oldestUsage = entry.getValue().lastAccessed();
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            dataStore.remove(oldestKey);
            System.out.println("[LRU Eviction] Removed key: " + oldestKey);
        }
        
        
    }

    private static boolean executeCommand(String[] commandParts) { // for restoring key-value pairs through aof file
        if (commandParts == null || commandParts.length == 0) {
            return false;
        }
        if (commandParts[0].equals("SET")) {
            LRUEviction();
            String key = commandParts[1];
            String value = commandParts[2];
            long expiryTime = 0;
            // Check if the command has an expiration time 
            // Example: *5\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nAyaan\r\n$2\r\nEX\r\n$2\r\n10\r\n
            // SET name Ayaan EX 10
            if (commandParts.length == 5 && commandParts[3].equals("PXAT")) {
                expiryTime = Long.parseLong(commandParts[4]);
            }

            dataStore.put(key, new ExpiringValue(value, expiryTime));
            return true;
        } else if (commandParts[0].equals("PEXPIREAT")) {
            String key = commandParts[1];
            ExpiringValue value = dataStore.get(key);
            if (value != null && value.isExpired()) {
                dataStore.remove(key);
                value = null;
            }

            if (value != null) {
                try {
                    long expiryTime = (Long.parseLong(commandParts[2]));
                    dataStore.put(key, new ExpiringValue(value.getValue(), expiryTime));
                } catch (NumberFormatException e) {

                }
            }
            return true;
        } else if (commandParts[0].equals("DEL")) {
            String key = commandParts[1];
            if (dataStore.containsKey(key)) {
                dataStore.remove(key);
            }
            return true;
        } else if (commandParts[0].equals("INCR")) {
            String key = commandParts[1];
            ExpiringValue value = dataStore.get(key);
            if (value != null && value.isExpired()) { // Checking for expiration if the background process hasn't removed it yet
                dataStore.remove(key);
                value = null;
            }

            if (value != null) {
                try {
                    int intValue = Integer.parseInt(value.getValue());
                    intValue++;
                    dataStore.put(key, new ExpiringValue(String.valueOf(intValue), value.getExpirationTimeSystem()));
                } catch (NumberFormatException e) {
                }
            } else { // If the key does not exist, set it to 1
                dataStore.put(key, new ExpiringValue("1", 0));
            }
            return true;
        } else if (commandParts[0].equals("DECR")) {
            String key = commandParts[1];
            ExpiringValue value = dataStore.get(key);
            if (value != null && value.isExpired()) { // Checking for expiration if the background process hasn't removed it yet
                dataStore.remove(key);
                value = null;
            }

            if (value != null) {
                try {
                    int intValue = Integer.parseInt(value.getValue());
                    intValue--;
                    dataStore.put(key, new ExpiringValue(String.valueOf(intValue), value.getExpirationTimeSystem()));
                } catch (NumberFormatException e) {
                    
                }
            } else { // If the key does not exist, set it to -1
                dataStore.put(key, new ExpiringValue("-1", 0));
            }
            return true;
        }
        return false;
    }

    private static void executeCommand(String[] commandParts, PrintWriter out, Socket clientSocket) {
        if (commandParts[0].equals("SET")) {
            LRUEviction();
            String key = commandParts[1];
            String value = commandParts[2];
            long expiryTime = 0;
            // Check if the command has an expiration time 
            // Example: *5\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nAyaan\r\n$2\r\nEX\r\n$2\r\n10\r\n
            // SET name Ayaan EX 10
            if (commandParts.length == 5 && commandParts[3].equals("EX")) {
                long expirySeconds = Long.parseLong(commandParts[4]);
                expiryTime = System.currentTimeMillis() + (expirySeconds * 1000);
            } else if (commandParts.length == 5 && commandParts[3].equals("PXAT")) {
                expiryTime = Long.parseLong(commandParts[4]);
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
                out.print("$-1\r\n");
            }
            if (value != null) {
                //RESP format for bulk string: $<length>\r\n<value>\r\n
                value.Accessed();
                out.print("$" + value.getValue().length() + "\r\n" + value.getValue() + "\r\n");
            } else {
                //RESP format for nil bulk string: $-1\r\n
                out.print("$-1\r\n");
            }
            out.flush();
        } else if (commandParts[0].equals("KEYS")) {
            if (commandParts[1].equals("*")) {
                for (Map.Entry<String, ExpiringValue> entry : dataStore.entrySet()) {
                    if (entry.getValue().isExpired())
                        continue;
                    out.print(entry.getKey() + "\r\n");
                }
                out.flush();
            }
        } else if (commandParts[0].equals("EXPIRE")) {
            String key = commandParts[1];
            ExpiringValue value = dataStore.get(key);
            if (value != null && value.isExpired()) {
                dataStore.remove(key);
                value = null;
                out.print("$-1\r\n");
            }

            if (value != null) {
                try {
                    long expirySeconds = (Long.parseLong(commandParts[2]) * 1000);
                    long expiryTime = System.currentTimeMillis() + expirySeconds;
                    dataStore.put(key, new ExpiringValue(value.getValue(), expiryTime));
                } catch (NumberFormatException e) {
                    out.print("-ERR expiration value is not an integer\r\n");
                }
            }
        } else if (commandParts[0].equals("PEXPIREAT")) {
            String key = commandParts[1];
            ExpiringValue value = dataStore.get(key);
            if (value != null && value.isExpired()) {
                dataStore.remove(key);
                value = null;
                out.print("$-1\r\n");
            }

            if (value != null) {
                try {
                    long expiryTime = (Long.parseLong(commandParts[2]));
                    dataStore.put(key, new ExpiringValue(value.getValue(), expiryTime));
                } catch (NumberFormatException e) {
                    out.print("-ERR expiration value is not an integer\r\n");
                }
            }
        } else if (commandParts[0].equals("TTL")) {
            String key = commandParts[1];
            ExpiringValue value = dataStore.get(key);

            if (value != null && value.isExpired()) {
                dataStore.remove(key);
                value = null;
                out.print("$-1\r\n");
            }

            if (value != null) {
                long ms = (value.getExpirationTimeSystem()) - (System.currentTimeMillis());
                out.print(String.valueOf(ms / 1000) + "\r\n");
                out.flush();
            } else {
                //RESP format for nil bulk string: $-1\r\n
                out.print("$-1\r\n");
            }
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
                out.print("$-1\r\n");
            }

            if (value != null) {
                try {
                    int intValue = Integer.parseInt(value.getValue());
                    intValue++;
                    dataStore.put(key, new ExpiringValue(String.valueOf(intValue), value.getExpirationTimeSystem()));
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
                out.print("$-1\r\n");
            }

            if (value != null) {
                try {
                    int intValue = Integer.parseInt(value.getValue());
                    intValue--;
                    dataStore.put(key, new ExpiringValue(String.valueOf(intValue), value.getExpirationTimeSystem()));
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
        } else if (commandParts[0].equals("UNSUBSCRIBE")) {
            String channel = commandParts[1];
            List<Socket> subscribers = subscriptions.get(channel);
            if (subscribers != null) {
                subscribers.remove(clientSocket);
                System.out.println("Client" + clientSocket.getRemoteSocketAddress() + " unsubscribed from channel: " + channel);
                out.print("+Unsubscribed from channel: " + channel + "\r\n");
            } else {
                out.print("-ERR not subscribed to channel: " + channel + "\r\n");
            }
            out.flush();
        } else {
            System.out.println("Unknown command: " + commandParts[0]);
            //RESP format for error: -Error message\r\n
            out.print("-ERR unknown command '" + commandParts[0] + "'\r\n");
            out.flush();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ){           
            String inputLine;
            List<String> multiQueue = new ArrayList<>(); // For MULTI/EXEC
            boolean inQueue = false; // Flag to check if we are in a MULTI block
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
                    if (inQueue && commandParts[0].equals("EXEC")) {
                        inQueue = false;
                        for (String queuedCommand : multiQueue) {
                            String[] queuedCommandParts = queuedCommand.split(" ");
                            executeCommand(queuedCommandParts, out, clientSocket);
                            AppendAOF(queuedCommandParts);
                        }
                        multiQueue.clear();
                    } else if (commandParts[0].equals("DISCARD")) {
                        multiQueue.clear();
                        inQueue = false;
                        out.print("+OK\r\n");
                        out.flush();
                    } else if (commandParts[0].equals("MULTI")) {
                        multiQueue.clear();
                        inQueue = true;
                        out.print("+OK\r\n");
                        out.flush();
                    } else if (inQueue) {
                        multiQueue.add(String.join(" ", commandParts));
                        out.print("+QUEUED\r\n");
                        out.flush();
                    } else {
                        AppendAOF(commandParts);
                        executeCommand(commandParts, out, clientSocket);
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