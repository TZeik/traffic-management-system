package com.pucmm.trafficManagementSystem.controller;

import com.pucmm.trafficManagementSystem.App;
import java.io.IOException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class MenuController {

    @FXML
    void startIntersectionSimulation(ActionEvent event) {
        try {
            App.setRoot("IntersectionView");
        } catch (IOException e) {
            System.err.println("Error al cargar la vista de la intersección.");
            e.printStackTrace();
        }
    }

    @FXML
    void startHighwaySimulation(ActionEvent event) {
        try {
            App.setRoot("HighwayView");
        } catch (IOException e) {
            System.err.println("Error al cargar la vista de la autopista.");
            e.printStackTrace();
        }
    }
}