package com.pucmm.trafficManagementSystem.model;

import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.EnumMap;

public class Intersection implements TrafficManager {
    private final Map<Direction, ConcurrentLinkedQueue<Vehicle>> waitingQueues;
    private final ConcurrentLinkedQueue<Vehicle> globalArrivalQueue = new ConcurrentLinkedQueue<>();
    private final Set<Vehicle> crossingVehicles = ConcurrentHashMap.newKeySet();

    public Intersection() {
        waitingQueues = new EnumMap<>(Direction.class);
        for (Direction dir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            waitingQueues.put(dir, new ConcurrentLinkedQueue<>());
        }
    }

    @Override
    public void addToQueue(Vehicle vehicle) {
        waitingQueues.get(vehicle.getOrigin()).add(vehicle);
        globalArrivalQueue.add(vehicle);
        System.out.printf("📋 Vehículo %d (%s) añadido a la cola de %s.\n", vehicle.getId(), vehicle.getType(),
                vehicle.getOrigin());
    }

    @Override
    public boolean isMyTurn(Vehicle vehicle) {
        Vehicle activeEmergency = findActiveEmergency();

        // --- LÓGICA DE EMERGENCIA ---
        if (activeEmergency != null) {
            Direction emergencyLane = activeEmergency.getOrigin();

            Vehicle headOfEmergencyLane = waitingQueues.get(emergencyLane).peek();
            if (crossingVehicles.isEmpty()) {
                return vehicle == headOfEmergencyLane;
            } else {
                return false;
            }
        }

        // --- LÓGICA FIFO NORMAL ---
        if (!crossingVehicles.isEmpty()) {
            return false;
        }
        return globalArrivalQueue.peek() == vehicle;
    }

    private Vehicle findActiveEmergency() {
        for (ConcurrentLinkedQueue<Vehicle> queue : waitingQueues.values()) {
            for (Vehicle v : queue) {
                if (v.getType() == VehicleType.EMERGENCY) {
                    return v;
                }
            }
        }
        return null;
    }

    @Override
    public void startCrossing(Vehicle vehicle) {
        globalArrivalQueue.remove(vehicle);
        waitingQueues.get(vehicle.getOrigin()).remove(vehicle);
        crossingVehicles.add(vehicle);
        System.out.printf("🚦 Vehículo %d empieza a cruzar.\n", vehicle.getId());
    }

    @Override
    public void leaveIntersection(Vehicle vehicle) {
        crossingVehicles.remove(vehicle);
        System.out.printf("✅ Vehículo %d ha salido del cruce.\n", vehicle.getId());
    }

    @Override
    public int getPositionInQueue(Vehicle vehicle) {
        return new ArrayList<>(waitingQueues.get(vehicle.getOrigin())).indexOf(vehicle);
    }

    @Override
    public boolean isEmergencyActive() {
        return findActiveEmergency() != null;
    }

    @Override
    public Direction getOrigin() {
        return null;
    }
}