package com.server;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

public class serverMain {
    private static final Logger logger = LoggerFactory.getLogger(serverMain.class);

    
    public static void main(String[] args) {
        logger.info("Working dir: " + System.getProperty("user.dir"));
        logger.info("Server is starting");
        
        List<String> videos = videoCatalog.getAvailableVideos();
        ExecutorService threadPool = Executors.newFixedThreadPool(Config.THREAD_POOL_SIZE);

        try{
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(Config.HTTP_PORT), 0);
            httpServer.createContext("/hls-output", new HLSHandler(Config.HLS_OUTPUT_DIR));
            httpServer.setExecutor(Executors.newFixedThreadPool(8));
            httpServer.start();
            logger.info("HTTP server started on port " + Config.HTTP_PORT);
        } catch(IOException e) {
            logger.error("Error creating HTTP server: " + e.getMessage());
            return;
        }
        try (ServerSocket serverSocket = new ServerSocket(Config.PORT)) {
            logger.info("Server listening on port " + Config.PORT);
            
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
