package com.pucmm.trafficManagementSystem.model;

import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.EnumMap;

public class Intersection {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Map<Direction, ConcurrentLinkedQueue<Vehicle>> waitingQueues;
    private final LinkedList<Direction> laneQueue = new LinkedList<>();
    private final Set<Vehicle> crossingVehicles = ConcurrentHashMap.newKeySet();
    private volatile boolean emergencyActive = false;

    public Intersection() {
        waitingQueues = new EnumMap<>(Direction.class);
        for (Direction dir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            waitingQueues.put(dir, new ConcurrentLinkedQueue<>());
        }
    }

    public void addToQueue(Vehicle vehicle) {
        waitingQueues.get(vehicle.getOrigin()).add(vehicle);
        System.out.printf("📋 Vehículo %d (%s) añadido a la cola de %s.\n", vehicle.getId(), vehicle.getType(), vehicle.getOrigin());

        lock.lock();
        try {
            Direction originLane = vehicle.getOrigin();

            if (vehicle.getType() == VehicleType.EMERGENCY) {
                // Si no hay una emergencia ya activa, esta nueva activa el modo.
                if (!this.emergencyActive) {
                    this.emergencyActive = true;
                    System.out.printf("🚨 ¡MODO DE EMERGENCIA ACTIVADO! Prioridad para el carril %s.\n", originLane);
                }
                // Se asegura de que el carril de la emergencia esté al frente de la cola.
                laneQueue.remove(originLane);
                laneQueue.addFirst(originLane);
            } else {
                if (!laneQueue.contains(originLane)) {
                    laneQueue.addLast(originLane);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void leaveIntersection(Vehicle vehicle) {
        crossingVehicles.remove(vehicle);
        lock.lock();
        try {
            // Si el vehículo que sale era una emergencia, revisamos si era la última.
            if (vehicle.getType() == VehicleType.EMERGENCY) {
                boolean anyOtherEmergency = crossingVehicles.stream().anyMatch(v -> v.getType() == VehicleType.EMERGENCY) ||
                                            waitingQueues.values().stream().flatMap(q -> q.stream()).anyMatch(v -> v.getType() == VehicleType.EMERGENCY);
                if (!anyOtherEmergency) {
                    this.emergencyActive = false; // Desactiva el modo si ya no quedan emergencias.
                    System.out.println("✅ Emergencia despejada. El tráfico vuelve a la normalidad.");
                }
            }

            if (waitingQueues.get(vehicle.getOrigin()).isEmpty() &&
                    crossingVehicles.stream().noneMatch(v -> v.getOrigin() == vehicle.getOrigin())) {
                laneQueue.remove(vehicle.getOrigin());
                System.out.printf("✅ Carril %s completamente libre. Cediendo el paso.\n", vehicle.getOrigin());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Permite a los vehículos saber si hay una emergencia activa.
     */
    public boolean isEmergencyActive() {
        return this.emergencyActive;
    }

    public boolean isMyTurn(Vehicle vehicle) {
        lock.lock();
        try {
            Direction activeLane = laneQueue.peek();
            if (!vehicle.getOrigin().equals(activeLane)) {
                return false;
            }
            return Objects.equals(waitingQueues.get(vehicle.getOrigin()).peek(), vehicle);
        } finally {
            lock.unlock();
        }
    }

    public void startCrossing(Vehicle vehicle) {
        lock.lock();
        try {
            Vehicle vehicleToCross = waitingQueues.get(vehicle.getOrigin()).poll();
            if (vehicleToCross != null) {
                crossingVehicles.add(vehicleToCross);
                System.out.printf("🚦 LUZ VERDE para vehículo %d. Empezando a cruzar...\n", vehicle.getId());
            }
        } finally {
            lock.unlock();
        }
    }

    public int getPositionInQueue(Vehicle vehicle) {
        Direction origin = vehicle.getOrigin();
        long vehiclesCrossingFromMyLane = crossingVehicles.stream()
                .filter(v -> v.getOrigin() == origin)
                .count();

        ConcurrentLinkedQueue<Vehicle> queue = waitingQueues.get(origin);
        if (queue == null)
            return (int) vehiclesCrossingFromMyLane;

        List<Vehicle> queueAsList = new ArrayList<>(queue);
        int indexInWaitingQueue = queueAsList.indexOf(vehicle);
        if (indexInWaitingQueue == -1) {
            indexInWaitingQueue = queueAsList.size();
        }
        return (int) vehiclesCrossingFromMyLane + indexInWaitingQueue;
    }
}