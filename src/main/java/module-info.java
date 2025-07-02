module com.pucmm.trafficManagementSystem {
    requires transitive javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;

    opens com.pucmm.trafficManagementSystem to javafx.fxml;
    exports com.pucmm.trafficManagementSystem;

    opens com.pucmm.trafficManagementSystem.controller to javafx.fxml;
    exports com.pucmm.trafficManagementSystem.controller;

    opens com.pucmm.trafficManagementSystem.model to javafx.fxml;
    exports com.pucmm.trafficManagementSystem.model;

    exports com.pucmm.trafficManagementSystem.enums;
}