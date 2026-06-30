import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class Main {
    private static final HashMap<String, String> dataStore = new HashMap<>();
    public static void main(String[] args) {
        int port = 6379;
        
        System.out.println("Starting server on port " + port);
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is online! Waiting for a client to connect...");
            
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected from: " + clientSocket.getRemoteSocketAddress());
            
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            
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
                        dataStore.put(key, value);
                        System.out.println("SET command executed: Key = " + key + ", Value = " + value);
                        //RESP format for simple string: +OK\r\n
                        out.print("+OK\r\n");
                        out.flush();
                    } else if (commandParts[0].equals("GET")) {
                        String key = commandParts[1];
                        String value = dataStore.get(key);
                        if (value != null) {
                            //RESP format for bulk string: $<length>\r\n<value>\r\n
                            out.print("$" + value.length() + "\r\n" + value + "\r\n");
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
                    } else {
                        System.out.println("Unknown command: " + commandParts[0]);
                        //RESP format for error: -Error message\r\n
                        out.print("-ERR unknown command '" + commandParts[0] + "'\r\n");
                        out.flush();
                    }
                    
                } else {
                    System.out.println("Received: " + inputLine);
                }
                
                out.print("+OK\r\n");
                out.flush();
            }
            
            System.out.println("Client disconnected.");
            
        } catch (Exception e) {
            System.out.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}