import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
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
                        String commandLine = in.readLine();
                        commandParts[i] = commandLine;
                    }
                    System.out.println("RESP Protocol: " + String.join(" ", commandParts));
                    System.out.print("Parsed Command Array: [");
                    for (String cmd : commandParts) {
                        System.out.print(" \"" + cmd + "\" ");
                    }
                    System.out.println("]");
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