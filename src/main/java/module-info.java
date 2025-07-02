module com.pucmm.trafficManagementSystem {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.pucmm.trafficManagementSystem to javafx.fxml;
    exports com.pucmm.trafficManagementSystem;
}
