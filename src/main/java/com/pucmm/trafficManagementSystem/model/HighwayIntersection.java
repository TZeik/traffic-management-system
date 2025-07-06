package com.pucmm.trafficManagementSystem.model;

import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class HighwayIntersection implements TrafficManager {
    private final int id;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Map<Direction, Map<Direction, ConcurrentLinkedQueue<Vehicle>>> waitingLanes;
    private final LinkedList<Direction> laneQueue = new LinkedList<>();
    private final Set<Vehicle> crossingVehicles = ConcurrentHashMap.newKeySet();
    private volatile boolean emergencyActive = false;

    public HighwayIntersection(int id) {
        this.id = id;
        this.waitingLanes = new EnumMap<>(Direction.class);
        for (Direction dir : new Direction[] { Direction.EAST, Direction.WEST }) {
            Map<Direction, ConcurrentLinkedQueue<Vehicle>> lanes = new EnumMap<>(Direction.class);
            lanes.put(Direction.LANE_1, new ConcurrentLinkedQueue<>());
            lanes.put(Direction.LANE_2, new ConcurrentLinkedQueue<>());
            lanes.put(Direction.LANE_3, new ConcurrentLinkedQueue<>());
            waitingLanes.put(dir, lanes);
        }
    }

    @Override
    public void addToQueue(Vehicle vehicle) {
        waitingLanes.get(vehicle.getOrigin()).get(vehicle.getLane()).add(vehicle);
        System.out.printf("📋 AUTOPISTA-%d: Vehículo %d añadido a la cola %s del carril %s.\n",
                id, vehicle.getId(), vehicle.getOrigin(), vehicle.getLane());

        lock.lock();
        try {
            Direction originLane = vehicle.getOrigin();
            if (vehicle.getType() == VehicleType.EMERGENCY) {
                if (!this.emergencyActive) {
                    this.emergencyActive = true;
                    System.out.printf("🚨 ¡MODO EMERGENCIA AUTOPISTA-%d! Carril %s tiene prioridad.\n", id, originLane);
                }
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

    @Override
    public boolean isMyTurn(Vehicle vehicle) {
        lock.lock();
        try {

            boolean otherDirectionIsCrossing = crossingVehicles.stream()
                    .anyMatch(v -> v.getOrigin() != vehicle.getOrigin());
            if (otherDirectionIsCrossing) {
                return false;
            }

            Direction activeDirection = laneQueue.peek();
            if (!vehicle.getOrigin().equals(activeDirection)) {
                return false;
            }

            boolean vehicleFromMyLaneIsCrossing = crossingVehicles.stream()
                    .anyMatch(v -> v.getOrigin() == vehicle.getOrigin() && v.getLane() == vehicle.getLane());
            if (vehicleFromMyLaneIsCrossing) {
                return false;
            }

            return Objects.equals(waitingLanes.get(vehicle.getOrigin()).get(vehicle.getLane()).peek(), vehicle);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void startCrossing(Vehicle vehicle) {
        lock.lock();
        try {
            if (waitingLanes.get(vehicle.getOrigin()).get(vehicle.getLane()).peek() == vehicle) {
                crossingVehicles.add(vehicle);
                System.out.printf("🚦 AUTOPISTA-%d: LUZ VERDE para vehículo %d. Cruzando...\n", id, vehicle.getId());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void leaveIntersection(Vehicle vehicle) {
        lock.lock();
        try {
            crossingVehicles.remove(vehicle);
            waitingLanes.get(vehicle.getOrigin()).get(vehicle.getLane()).remove(vehicle);

            System.out.printf("✅ AUTOPISTA-%d: Vehículo %d ha salido. Cediendo el paso.\n", id, vehicle.getId());

            if (vehicle.getType() == VehicleType.EMERGENCY) {
                boolean anyOtherEmergency = crossingVehicles.stream()
                        .anyMatch(v -> v.getType() == VehicleType.EMERGENCY) ||
                        waitingLanes.values().stream()
                                .flatMap(lanes -> lanes.values().stream())
                                .flatMap(Queue::stream)
                                .anyMatch(v -> v.getType() == VehicleType.EMERGENCY);

                if (!anyOtherEmergency) {
                    this.emergencyActive = false;
                    System.out.println("✅ AUTOPISTA-" + id + ": Emergencia despejada. Tráfico normal.");
                }
            }

            if (isDirectionCompletelyClear(vehicle.getOrigin())) {
                laneQueue.remove(vehicle.getOrigin());
                System.out.printf("✅ AUTOPISTA-%d: Dirección %s completamente libre.\n", id, vehicle.getOrigin());
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean hasEmergencyVehicleWaiting() {
        boolean westQueueHasEmergency = waitingLanes.get(Direction.WEST).get(Direction.LANE_1).stream()
                .anyMatch(v -> v.getType() == VehicleType.EMERGENCY);
        boolean eastQueueHasEmergency = waitingLanes.get(Direction.EAST).get(Direction.LANE_1).stream()
                .anyMatch(v -> v.getType() == VehicleType.EMERGENCY);
        return westQueueHasEmergency || eastQueueHasEmergency;
    }

    private boolean isDirectionCompletelyClear(Direction origin) {
        boolean crossingEmpty = crossingVehicles.stream().noneMatch(v -> v.getOrigin() == origin);
        boolean waitingEmpty = waitingLanes.get(origin).values().stream().allMatch(Queue::isEmpty);
        return crossingEmpty && waitingEmpty;
    }

    @Override
    public int getPositionInQueue(Vehicle vehicle) {
        return new ArrayList<>(waitingLanes.get(vehicle.getOrigin()).get(vehicle.getLane())).indexOf(vehicle);
    }

    @Override
    public boolean isEmergencyActive() {
        return this.emergencyActive;
    }

    @Override
    public Direction getOrigin() {
        return null;
    }

    public int getId() {
        return id;
    }
}