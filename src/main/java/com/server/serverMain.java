package com.server;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class serverMain {
    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 10;
    private static final Logger logger = LoggerFactory.getLogger(serverMain.class);

    
    public static void main(String[] args) {
        logger.info("Working dir: " + System.getProperty("user.dir"));
        logger.info("Server is starting");
        //logger.
        
        List<String> videos = videoCatalog.getAvailableVideos();
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server listening on port " + PORT);
            
            logger.info("AVAILABLE_VIDEOS:" + String.join(",", videos));
            logger.info("Type 'exit' to disconnect.");
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                    
                    // Submit client handling task to thread pool
                    threadPool.submit(new clientHandler(clientSocket));
                    
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
            logger.info("Server stopped");
        }
    }
}
