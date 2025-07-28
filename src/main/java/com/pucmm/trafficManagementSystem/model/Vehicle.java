package com.pucmm.trafficManagementSystem.model;

import com.pucmm.trafficManagementSystem.controller.HighwayController;
import com.pucmm.trafficManagementSystem.controller.IntersectionController;
import com.pucmm.trafficManagementSystem.controller.TrafficLightController;
import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Vehicle implements Runnable {
    private volatile boolean running = true;
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    private static final double SAFE_DISTANCE = 50.0;
    private final int id;
    private final VehicleType type;
    private final Direction origin;
    private final Direction destination;
    private Direction lane;
    private final TrafficManager trafficManager;
    private HighwayController highwayController;
    private IntersectionController intersectionController;
    private long arrivalTime;
    private double x, y;
    private volatile boolean finished = false;
    private final double normalSpeed = 1.4;
    private final double emergencyClearSpeed = 1.8;

    private List<Integer> trafficLightPath;
    private int nextTrafficLightIndex = 0;
    private int lastKnownIntersectionId = -1;

    private TrafficLightController trafficLightController;
    private IntersectionStateManager intersectionStateManager;

    public Vehicle(VehicleType type, Direction origin, Direction destination, Intersection intersection) {
        this.id = idCounter.incrementAndGet();
        this.type = type;
        this.origin = origin;
        this.destination = destination;
        this.trafficManager = intersection;
    }

    public Vehicle(VehicleType type, Direction origin, Direction action, HighwayIntersection targetIntersection) {
        this.id = idCounter.incrementAndGet();
        this.type = type;
        this.origin = origin;
        this.destination = action;
        this.trafficManager = targetIntersection;
    }

    public void setTrafficLightController(TrafficLightController tlc) {
        this.trafficLightController = tlc;
    }

    public void setIntersectionStateManager(IntersectionStateManager ism) {
        this.intersectionStateManager = ism;
    }

    @Override
    public void run() {
        if (isFinished())
            return;

        if (intersectionController != null) {
            runSimpleIntersectionLogic();
        } else if (highwayController != null) {
            runHighwayLogic();
        } else {
            this.finished = true;
        }
    }

    private void runSimpleIntersectionLogic() {
        try {
            List<Point2D> path = getPathFromController();
            if (path.isEmpty()) {
                this.finished = true;
                return;
            }

            trafficManager.addToQueue(this);
            int currentPathSegment = 1;
            boolean crossingStarted = false;

            while (running && currentPathSegment < path.size()) {
                Point2D target = path.get(currentPathSegment);

                if (!crossingStarted) {
                    if (trafficManager.isMyTurn(this)) {
                        crossingStarted = true;
                        trafficManager.startCrossing(this);
                        target = path.get(currentPathSegment);
                    } else {
                        target = getDynamicStopPoint(path.get(1));
                    }
                }

                moveTo(target, trafficManager.isEmergencyActive() || this.type == VehicleType.EMERGENCY);

                if (distanceTo(target) < 1.5) {
                    if (crossingStarted) {
                        currentPathSegment++;
                    }
                }

                Thread.sleep(16);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (intersectionStateManager != null) {
                for (int i = 1; i <= 4; i++) {
                    intersectionStateManager.vehicleExitsStraightZone(i, this);
                }
            }

            if (trafficManager != null)
                trafficManager.leaveIntersection(this);
            this.finished = true;
        }
    }

    private void runHighwayLogic() {
        try {
            List<Point2D> path = getPathFromController();
            if (path.isEmpty()) {
                this.finished = true;
                return;
            }

            calculateTrafficLightPath();
            int currentPathSegment = 1;

            while (running && currentPathSegment < path.size()) {
                
                Vehicle leader = highwayController.findLeaderFor(this);
                if (leader != null && distanceTo(new Point2D(leader.getX(), leader.getY())) < SAFE_DISTANCE) {
                    updateIntersectionState(); 
                    Thread.sleep(16);
                    continue; 
                }

                if (isApproachingTrafficLight()) {
                    int lightId = trafficLightPath.get(nextTrafficLightIndex);
                    Point2D stopLine = highwayController.getStopLineForLight(lightId, origin, lane, highwayController.getSimulationPaneWidth(), highwayController.getSimulationPane().getHeight());

                    // Comprobación clave: ¿La línea de parada está todavía en frente de mí?
                    boolean stopLineIsInFront = (origin == Direction.WEST && getX() < stopLine.getX()) || (origin == Direction.EAST && getX() > stopLine.getX());

                    if (stopLineIsInFront && distanceTo(stopLine) > 2.0) {
                        moveTo(stopLine, this.type == VehicleType.EMERGENCY);
                        updateIntersectionState();
                        Thread.sleep(16);
                        continue;
                    }
                    
                    if (stopLineIsInFront) { // Solo evaluar el semáforo si estamos en su línea de parada
                        boolean canGo = false;
                        if (this.type == VehicleType.EMERGENCY) {
                            canGo = true; 
                            if ((destination == Direction.LEFT || destination == Direction.U_TURN) && isAtFinalTurn(lightId)) {
                                if (intersectionStateManager.isOpposingTrafficCrossing(getTargetIntersection().getId(), this)) {
                                    canGo = false;
                                }
                            }
                        } else { 
                            boolean isLightGreen = trafficLightController.isGreen(lightId);
                            canGo = isLightGreen;
                            if (!isLightGreen) {
                                if (highwayController.findEmergencyFollower(this) != null) {
                                    canGo = true; 
                                }
                            }
                            if (canGo && (destination == Direction.LEFT || destination == Direction.U_TURN) && isAtFinalTurn(lightId)) {
                                if (intersectionStateManager.isOpposingTrafficCrossing(getTargetIntersection().getId(), this)) {
                                    canGo = false;
                                }
                            }
                        }

                        if (!canGo) {
                            updateIntersectionState();
                            Thread.sleep(16);
                            continue;
                        }
                    }
                    
                    // Si llegamos aquí, significa que podemos pasar la luz (o que ya la pasamos).
                    nextTrafficLightIndex++;
                }

                Point2D currentTarget = path.get(currentPathSegment);
                moveTo(currentTarget, this.type == VehicleType.EMERGENCY);
                
                if (distanceTo(currentTarget) < 2.0) {
                    currentPathSegment++;
                }
                
                updateIntersectionState();
                Thread.sleep(16);
            }

            if (running && this.destination == Direction.U_TURN) {
                final Vehicle self = this;
                Platform.runLater(() -> highwayController.spawnStraightVehicleFromUTurn(self));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (intersectionStateManager != null) {
                if (lastKnownIntersectionId != -1) {
                    intersectionStateManager.vehicleExitsStraightZone(lastKnownIntersectionId, this);
                }
            }
            if (trafficManager != null) trafficManager.leaveIntersection(this);
            this.finished = true;
        }
    }

    private boolean isAtFinalTurn(int lightId) {
        if (getTargetIntersection() == null)
            return false;
        int targetIntersectionId = getTargetIntersection().getId();

        if ((lightId == 1 && targetIntersectionId == 1) ||
                ((lightId == 2 || lightId == 3) && targetIntersectionId == 2) ||
                ((lightId == 4 || lightId == 5) && targetIntersectionId == 3) ||
                (lightId == 6 && targetIntersectionId == 4)) {
            return true;
        }
        return false;
    }

    private void updateIntersectionState() {
        int currentIntersectionId = getMyCurrentIntersectionId();

        // Si estábamos en una intersección y ahora ya no (o estamos en una diferente),
        // significa que hemos salido de la anterior.
        if (lastKnownIntersectionId != -1 && lastKnownIntersectionId != currentIntersectionId) {
            intersectionStateManager.vehicleExitsStraightZone(lastKnownIntersectionId, this);
        }

        // Si ahora estamos dentro de una intersección, anunciamos nuestra presencia.
        if (currentIntersectionId != -1) {
            intersectionStateManager.vehicleEntersStraightZone(currentIntersectionId, this);
        }

        // Actualizamos nuestra última posición conocida para la siguiente iteración.
        lastKnownIntersectionId = currentIntersectionId;
    }

    private int getMyCurrentIntersectionId() {
        for (int i = 1; i <= 4; i++) {
            double centerX = highwayController.getIntersectionCenterX(i, highwayController.getSimulationPaneWidth());
            double width = highwayController.getIntersectionWidth();
            if (this.x > centerX - width / 2 && this.x < centerX + width / 2) {
                return i;
            }
        }
        return -1;
    }

    private boolean isApproachingTrafficLight() {
        return trafficLightPath != null && nextTrafficLightIndex < trafficLightPath.size();
    }

    private void calculateTrafficLightPath() {
        trafficLightPath = new ArrayList<>();
        int finalIntersectionId = (getTargetIntersection() != null) ? getTargetIntersection().getId() : 0;

        if (origin == Direction.WEST) {
            if (destination == Direction.STRAIGHT || destination == Direction.U_TURN_CONTINUATION) {
                if (getX() < highwayController.getIntersectionCenterX(2, highwayController.getSimulationPaneWidth()))
                    trafficLightPath.add(3);
                if (getX() < highwayController.getIntersectionCenterX(3, highwayController.getSimulationPaneWidth()))
                    trafficLightPath.add(5);
                if (getX() < highwayController.getIntersectionCenterX(4, highwayController.getSimulationPaneWidth()))
                    trafficLightPath.add(6);
            } else {
                if (finalIntersectionId >= 2)
                    trafficLightPath.add(3);
                if (finalIntersectionId >= 3)
                    trafficLightPath.add(5);
                if (finalIntersectionId >= 4)
                    trafficLightPath.add(6);
            }
        } else {
            if (destination == Direction.STRAIGHT || destination == Direction.U_TURN_CONTINUATION) {
                if (getX() > highwayController.getIntersectionCenterX(3, highwayController.getSimulationPaneWidth()))
                    trafficLightPath.add(4);
                if (getX() > highwayController.getIntersectionCenterX(2, highwayController.getSimulationPaneWidth()))
                    trafficLightPath.add(2);
                if (getX() > highwayController.getIntersectionCenterX(1, highwayController.getSimulationPaneWidth()))
                    trafficLightPath.add(1);
            } else {
                if (finalIntersectionId <= 3)
                    trafficLightPath.add(4);
                if (finalIntersectionId <= 2)
                    trafficLightPath.add(2);
                if (finalIntersectionId <= 1)
                    trafficLightPath.add(1);
            }
        }
    }

    private List<Point2D> getPathFromController() {
        if (highwayController != null) {
            return highwayController.getPath(this);
        } else if (intersectionController != null) {
            return intersectionController.getPath(origin, destination);
        }
        return new ArrayList<>();
    }

    private void moveTo(Point2D target, boolean emergency) {
        double currentSpeed = emergency ? this.emergencyClearSpeed : this.normalSpeed;

        if (distanceTo(target) < currentSpeed) {
            this.x = target.getX();
            this.y = target.getY();
        } else {
            double angle = Math.atan2(target.getY() - y, target.getX() - x);
            x += currentSpeed * Math.cos(angle);
            y += currentSpeed * Math.sin(angle);
        }
    }

    private Point2D getDynamicStopPoint(Point2D baseStopLine) {
        if (trafficManager == null)
            return baseStopLine;
        int positionInQueue = trafficManager.getPositionInQueue(this);
        double vehicleSpacing = 30.0;
        double offset = positionInQueue * vehicleSpacing;

        switch (origin) {
            case NORTH:
                return new Point2D(baseStopLine.getX(), baseStopLine.getY() - offset);
            case SOUTH:
                return new Point2D(baseStopLine.getX(), baseStopLine.getY() + offset);
            case EAST:
                return new Point2D(baseStopLine.getX() + offset, baseStopLine.getY());
            case WEST:
                return new Point2D(baseStopLine.getX() - offset, baseStopLine.getY());
            default:
                return baseStopLine;
        }
    }

    public double distanceTo(Point2D target) {
        return Math.sqrt(Math.pow(target.getX() - x, 2) + Math.pow(target.getY() - y, 2));
    }

    public void setController(IntersectionController controller) {
        this.intersectionController = controller;
    }

    public void setController(HighwayController controller) {
        this.highwayController = controller;
    }

    public int getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public long getArrivalTime() {
        return arrivalTime;
    }

    public boolean isFinished() {
        return finished;
    }

    public VehicleType getType() {
        return type;
    }

    public Direction getOrigin() {
        return origin;
    }

    public Direction getDestination() {
        return destination;
    }

    public void stop() {
        this.running = false;
    }

    public void setLane(Direction lane) {
        this.lane = lane;
    }

    public Direction getLane() {
        return this.lane;
    }

    public HighwayIntersection getTargetIntersection() {
        if (trafficManager instanceof HighwayIntersection) {
            return (HighwayIntersection) trafficManager;
        }
        return null;
    }
}