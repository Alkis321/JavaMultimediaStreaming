package com.server;

import java.io.*;
import java.net.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class clientHandler implements Runnable {
    private static final String EXIT_MESSAGE = "exit";
    private final Socket clientSocket;
    private static final Logger logger = LoggerFactory.getLogger(clientHandler.class);
    
    public clientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }
    
    @Override
    public void run() {
        try (
            //Handling of client
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            logger.info("Client connected: {}", clientSocket.getRemoteSocketAddress());
            out.println("Available Videos: " + String.join(", ",  videoCatalog.listConvertedVideos()));
            //out.println("Welcome to the server! Type 'exit' to disconnect.");
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                logger.info("Received from client: " + inputLine);
                
                // Check for exit message
                if (EXIT_MESSAGE.equalsIgnoreCase(inputLine.trim())) {
                    out.println("Goodbye!");
                    break;
                }
                if (inputLine.startsWith("SPEED ")) {
                    logger.info("Client reported download speed: " + inputLine.substring(6) + " Mbps");
                    continue; 
                }
              /*   if (inputLine.startsWith("STREAM")) {
                    // STREAM <movie> <resolution> <format> <proto> <clientIP> <port>
                    String[] p = inputLine.split("\\s+");
                    String movie = p[1];           // e.g. input_fish
                    String res   = p[2];           // 480
                    String fmt   = p[3];           // avi
                    var proto    = StreamLauncher.Protocol.valueOf(p[4].toUpperCase());
                    String ip    = p[5];
                    int port     = Integer.parseInt(p[6]);
                
                    String rel   = movie + "-" + res + "p." + fmt;                // input_fish-480p.avi
                    String full  = Paths.get(Config.CONVERTED_VIDEOS_DIR, rel)
                                         .toString();                              // ./videos/converted/…
                
                    StreamLauncher.launchSender(full, proto, ip, port);
                } */
                
                // Echo message back with server acknowledgment
                out.println("Server received: " + inputLine);
            }
            
            //Here streaming capabilities will be handled
            

            System.out.println("Client disconnecting: " + clientSocket.getInetAddress().getHostAddress());
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
}
