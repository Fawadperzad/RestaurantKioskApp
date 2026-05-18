module com.RestaurantKiokAppMavenProject {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // DB
    requires java.sql;

    // jBCrypt (automatic module from dependency org.mindrot:jbcrypt)
    requires jbcrypt;

    // Allow reflective access from FXMLLoader to controller classes and main package
    opens controller to javafx.fxml;
    opens com.RestaurantKiokAppMavenProject to javafx.fxml;
    opens models to javafx.fxml;
    opens utils to javafx.fxml;

    // Export packages so other modules (and frameworks) can access public types if needed
    exports controller;
    exports com.RestaurantKiokAppMavenProject;
    exports models;
    exports utils;
}
