module demons {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires com.github.kokorin.jaffree;
    requires org.slf4j;
    requires jspeedtest;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;


    exports com.client;
    
    //exports com.server;
    opens com.client to javafx.fxml, javafx.graphics;
}