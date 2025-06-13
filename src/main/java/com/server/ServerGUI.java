package com.server;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpServer;

public class ServerGUI extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(ServerGUI.class);
    
    private JPanel statusPanel;
    private JLabel statusIcon;
    private JButton stopButton;
    private JButton statsButton;
    private JTextArea logArea;
    
    private HttpServer httpServer;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    
    public ServerGUI(ExecutorService threadPool) {
        this.threadPool = threadPool;
        logger.info("oop i got called");
        
        // Set up the frame
        setTitle("Streaming Server Control");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Status panel with icon
        statusPanel = new JPanel();
        statusIcon = new JLabel("●");
        statusIcon.setForeground(Color.RED);  // Initially red (not running)
        statusIcon.setFont(new Font("Dialog", Font.BOLD, 20));
        statusPanel.add(new JLabel("Server Status: "));
        statusPanel.add(statusIcon);
        
        // Stop button
        stopButton = new JButton("Stop Server");
        stopButton.addActionListener(_ -> stopServer());
        stopButton.setEnabled(false); // Initially disabled
        statusPanel.add(stopButton);

        statsButton = new JButton("Show Statistics");
        statsButton.addActionListener(_ -> showStatistics());
        statusPanel.add(statsButton);
        
        // Add status panel to the top
        add(statusPanel, BorderLayout.NORTH);
        
        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);
        
        // Center on screen
        setLocationRelativeTo(null);

        setVisible(true);
        
        // Add initial log
        addLog("Server GUI initialized");
    }
    
    public void updateStatus(boolean isRunning) {
        SwingUtilities.invokeLater(() -> {
            if (isRunning) {
                statusIcon.setForeground(Color.GREEN);
                statusIcon.setText("●");
                stopButton.setEnabled(true);
                addLog("Server is running");
            } else {
                statusIcon.setForeground(Color.RED);
                statusIcon.setText("●");
                stopButton.setEnabled(false);
                addLog("Server is stopped");
            }
        });
    }
    
    public void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showStatistics() {
        //get statistics from the singleton
        String stats = StatisticsManager.getInstance().getFormattedStatistics();
        
        // Show in a dialog
        JTextArea statsArea = new JTextArea(stats);
        statsArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(statsArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));
        
        JOptionPane.showMessageDialog(
            this,
            scrollPane,
            "Server Statistics",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
    
    public void setHttpServer(HttpServer server) {
        this.httpServer = server;
    }
    
    public void setServerSocket(ServerSocket socket) {
        this.serverSocket = socket;
    }
    
    private void stopServer() {
        int confirm = JOptionPane.showConfirmDialog(
            this, 
            "Are you sure you want to stop the server?", 
            "Confirm Stop", 
            JOptionPane.YES_NO_OPTION
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            addLog("Stopping server...");
            
            if (httpServer != null) {
                httpServer.stop(0);
                addLog("HTTP server stopped");
            }
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                    addLog("Server socket closed");
                } catch (IOException e) {
                    addLog("Error closing server socket: " + e.getMessage());
                }
            }
            
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
                addLog("Thread pool shutdown initiated");
            }
            
            updateStatus(false);
            
            // Exit application after a brief delay
            Timer timer = new Timer(1000, _ -> System.exit(0));
            timer.setRepeats(false);
            timer.start();
        }
    }
}