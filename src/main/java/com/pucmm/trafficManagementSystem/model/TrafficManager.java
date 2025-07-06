package com.pucmm.trafficManagementSystem.model;

import com.pucmm.trafficManagementSystem.enums.Direction;

public interface TrafficManager {
    void addToQueue(Vehicle vehicle);
    void leaveIntersection(Vehicle vehicle);
    boolean isMyTurn(Vehicle vehicle);
    void startCrossing(Vehicle vehicle);
    int getPositionInQueue(Vehicle vehicle);
    boolean isEmergencyActive();
    Direction getOrigin();
}