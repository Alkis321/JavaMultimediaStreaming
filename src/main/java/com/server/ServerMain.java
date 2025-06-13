package com.server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpServer;


public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);
    private static ServerGUI gui;

    
    public static void main(String[] args) {
        logger.info("Working dir: " + System.getProperty("user.dir"));
        logger.info("Server is starting");
        
        List<String> videos = VideoCatalog.getAvailableVideos();
        HlsCatalog.makeHlsSegments();
        ExecutorService threadPool = Executors.newFixedThreadPool(Config.THREAD_POOL_SIZE);

        gui = new ServerGUI(threadPool);
        gui.setVisible(true);
        gui.addLog("Server initialized at: "+ new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date()) );

        HttpServer httpServer = null;

        try{
            httpServer = HttpServer.create(new InetSocketAddress(Config.HTTP_PORT), 0);
            httpServer.createContext("/hls-output", new HlsHandler(Config.HLS_OUTPUT_DIR));
            httpServer.setExecutor(Executors.newFixedThreadPool(8));
            httpServer.start();
            gui.updateStatus(true);
            gui.addLog("HTTP server started on port " + Config.HTTP_PORT);
            logger.info("HTTP server started on port " + Config.HTTP_PORT);

            gui.setHttpServer(httpServer);

        } catch(IOException e) {
            gui.updateStatus(false);
            logger.error("Error creating HTTP server: " + e.getMessage());
            gui.addLog("Error creating HTTP server: " + e.getMessage());
            return;
        }


        try (ServerSocket serverSocket = new ServerSocket(Config.PORT)) {
            gui.setServerSocket(serverSocket);
            logger.info("Server listening on port " + Config.PORT);
            
            logger.info("AVAILABLE_VIDEOS:" + String.join(",", videos));
            logger.info("Type 'exit' to disconnect.");
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                    gui.addLog("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                    
                    // Submit client handling task to thread pool
                    threadPool.submit(new ClientHandler(clientSocket));
                    
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
            gui.addLog("Server stopped");
            gui.updateStatus(false);
        }
    }
}
