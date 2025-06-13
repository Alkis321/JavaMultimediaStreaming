package com.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatisticsManager {
    // Singleton instance
    private static StatisticsManager instance;
    
    // Thread-safe maps to store statistics
    private final ConcurrentHashMap<String, Integer> videoRequests;
    private final ConcurrentHashMap<String, Integer> protocolUsage;
    
    // Private constructor (singleton pattern)
    private StatisticsManager() {
        videoRequests = new ConcurrentHashMap<>();
        protocolUsage = new ConcurrentHashMap<>();
    }
    
    // Thread-safe getInstance method
    public static synchronized StatisticsManager getInstance() {
        if (instance == null) {
            instance = new StatisticsManager();
        }
        return instance;
    }
    
    // Record video request
    public void recordVideoRequest(String videoName) {
        videoRequests.compute(videoName, (_, count) -> (count == null) ? 1 : count + 1);
    }
    
    public void recordProtocolUsage(String protocol) {
        protocolUsage.compute(protocol, (_, count) -> (count == null) ? 1 : count + 1);
    }
    
    public Map<String, Integer> getVideoStatistics() {
        return new HashMap<>(videoRequests);
    }
    
    public Map<String, Integer> getProtocolStatistics() {
        return new HashMap<>(protocolUsage);
    }
    
    //formatted statistics for display
    public String getFormattedStatistics() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Video Request Statistics:\n");
        if (videoRequests.isEmpty()) {
            sb.append("  No videos requested yet\n");
        } else {
            videoRequests.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Sort by count (descending)
                .forEach(entry -> sb.append("  ").append(entry.getKey())
                    .append(": ").append(entry.getValue()).append("\n"));
        }
        
        sb.append("\nProtocol Usage Statistics:\n");
        if (protocolUsage.isEmpty()) {
            sb.append("  No protocols used yet\n");
        } else {
            protocolUsage.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Sort by count (descending)
                .forEach(entry -> sb.append("  ").append(entry.getKey())
                    .append(": ").append(entry.getValue()).append("\n"));
        }
        
        return sb.toString();
    }
}