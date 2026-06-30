import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        int port = 6379; // The standard Redis port
        
        System.out.println("Starting server on port " + port);
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is online! Waiting for a client to connect...");
            
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected from: " + clientSocket.getRemoteSocketAddress());
            
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from client: " + inputLine);
                
                out.println("Echo: " + inputLine);
            }
            
            System.out.println("Client disconnected.");
            
        } catch (Exception e) {
            System.out.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}