package com.pucmm.trafficManagementSystem.model;

import com.pucmm.trafficManagementSystem.controller.HighwayController;
import com.pucmm.trafficManagementSystem.controller.IntersectionController;
import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.Collections;
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

    private List<Integer> straightPathIntersections;
    private int nextStraightIntersectionIndex = 0;

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
            if (trafficManager != null) {
                trafficManager.leaveIntersection(this);
            }
            this.finished = true;
            System.out.printf("🏁 Vehículo %d ha completado su ruta.\n", this.id);
        }
    }

    private void runHighwayLogic() {
        try {
            List<Point2D> path = getPathFromController();
            if (path.isEmpty()) {
                this.finished = true;
                return;
            }

            calculateStraightPathIntersections();
            if (!straightPathIntersections.isEmpty()) {
                highwayController.registerApproachingVehicle(straightPathIntersections.get(0), this);
            }

            if (trafficManager != null) {
                trafficManager.addToQueue(this);
            }

            int currentPathSegment = 1;
            while (running && currentPathSegment < path.size()) {
                boolean shouldMove = true;

                Vehicle leader = highwayController.findLeaderFor(this);
                if (leader != null && distanceTo(new Point2D(leader.getX(), leader.getY())) < SAFE_DISTANCE) {
                    shouldMove = false;
                } else {
                    boolean isYieldingSituation = (trafficManager != null && this.lane == Direction.LANE_1
                            && currentPathSegment == 1);
                    if (isYieldingSituation) {
                        if (highwayController.isIntersectionApproachedByStraightVehicle(getTargetIntersection().getId(),
                                this)) {
                            Point2D stopPoint = getDynamicStopPoint(path.get(1));
                            moveTo(stopPoint, this.type == VehicleType.EMERGENCY);
                            if (distanceTo(stopPoint) < 1.5) {
                                shouldMove = false;
                            }
                        }
                    }
                }

                if (this.destination != Direction.STRAIGHT && !isYieldingSituation() && currentPathSegment == 1) {
                    if (amIApproachingAnIntersection()
                            && highwayController.shouldYieldForTurningEmergency(this, getNextIntersectionId())) {
                        shouldMove = false;
                    }
                }

                if (shouldMove) {
                    Point2D currentTarget = path.get(currentPathSegment);
                    moveTo(currentTarget, this.type == VehicleType.EMERGENCY);

                    if (distanceTo(currentTarget) < 1.5) {
                        currentPathSegment++;
                    }
                }

                updateIntersectionFlags();

                Thread.sleep(16);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (highwayController != null && straightPathIntersections != null) {
                for (int i = nextStraightIntersectionIndex; i < straightPathIntersections.size(); i++) {
                    highwayController.deregisterApproachingVehicle(straightPathIntersections.get(i), this);
                }
            }
            if (trafficManager != null) {
                trafficManager.leaveIntersection(this);
            }
            this.finished = true;
            System.out.printf("🏁 Vehículo %d ha completado su ruta.\n", this.id);
        }
    }

    private boolean isYieldingSituation() {
        return trafficManager != null && this.lane == Direction.LANE_1
                && getPathFromController().indexOf(getDynamicStopPoint(getPathFromController().get(1))) == 1;
    }

    private int getNextIntersectionId() {
        if (straightPathIntersections != null && !straightPathIntersections.isEmpty()
                && nextStraightIntersectionIndex < straightPathIntersections.size()) {
            return straightPathIntersections.get(nextStraightIntersectionIndex);
        }
        if (getTargetIntersection() != null) {
            return getTargetIntersection().getId();
        }
        return -1;
    }

    private boolean amIApproachingAnIntersection() {
        int nextIntersectionId = getNextIntersectionId();
        if (nextIntersectionId != -1) {
            double intersectionCenterX = highwayController.getIntersectionCenterX(nextIntersectionId,
                    highwayController.getSimulationPaneWidth());
            double dangerZone = 120.0;
            return Math.abs(this.x - intersectionCenterX) < dangerZone;
        }
        return false;
    }

    private void calculateStraightPathIntersections() {
        straightPathIntersections = new ArrayList<>();
        int finalDestinationId = (getTargetIntersection() != null) ? getTargetIntersection().getId() : 0;

        for (int i = 1; i <= 4; i++) {
            if (this.destination == Direction.STRAIGHT) {
                straightPathIntersections.add(i);
            } else if (finalDestinationId != 0) {
                if (this.origin == Direction.WEST && i < finalDestinationId) {
                    straightPathIntersections.add(i);
                } else if (this.origin == Direction.EAST && i > finalDestinationId) {
                    straightPathIntersections.add(i);
                }
            }
        }
        if (this.origin == Direction.EAST) {
            Collections.reverse(straightPathIntersections);
        }
    }

    private void updateIntersectionFlags() {
        if (highwayController == null || straightPathIntersections == null || straightPathIntersections.isEmpty()
                || nextStraightIntersectionIndex >= straightPathIntersections.size()) {
            return;
        }

        int currentTargetIntersectionId = straightPathIntersections.get(nextStraightIntersectionIndex);
        double intersectionCenterX = highwayController.getIntersectionCenterX(currentTargetIntersectionId,
                highwayController.getSimulationPaneWidth());

        double iWidth = highwayController.getIntersectionWidth();

        boolean hasPassed = (this.origin == Direction.WEST && this.x > intersectionCenterX + iWidth / 2) ||
                (this.origin == Direction.EAST && this.x < intersectionCenterX - iWidth / 2);

        if (hasPassed) {
            highwayController.deregisterApproachingVehicle(currentTargetIntersectionId, this);
            nextStraightIntersectionIndex++;
            if (nextStraightIntersectionIndex < straightPathIntersections.size()) {
                int nextIntersectionId = straightPathIntersections.get(nextStraightIntersectionIndex);
                highwayController.registerApproachingVehicle(nextIntersectionId, this);
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
        if (highwayController != null) {
            currentSpeed = this.normalSpeed + 0.2;
        }

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

    private double distanceTo(Point2D target) {
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