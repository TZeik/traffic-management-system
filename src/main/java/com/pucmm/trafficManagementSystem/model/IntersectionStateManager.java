package com.pucmm.trafficManagementSystem.model;

import com.pucmm.trafficManagementSystem.enums.Direction;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IntersectionStateManager {

    private final Map<Integer, Set<Vehicle>> crossingStraightVehicles = new ConcurrentHashMap<>();

    public IntersectionStateManager() {
        for (int i = 1; i <= 4; i++) {
            crossingStraightVehicles.put(i, ConcurrentHashMap.newKeySet());
        }
    }

    public void vehicleEntersStraightZone(int intersectionId, Vehicle vehicle) {
        crossingStraightVehicles.get(intersectionId).add(vehicle);
    }

    public void vehicleExitsStraightZone(int intersectionId, Vehicle vehicle) {
        crossingStraightVehicles.get(intersectionId).remove(vehicle);
    }

    public boolean isOpposingTrafficCrossing(int intersectionId, Vehicle turningVehicle) {
        Direction opposingDirection = turningVehicle.getOrigin() == Direction.WEST ? Direction.EAST : Direction.WEST;
        
        return crossingStraightVehicles.get(intersectionId).stream()
                .anyMatch(v -> v.getOrigin() == opposingDirection);
    }
}