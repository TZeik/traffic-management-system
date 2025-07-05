package com.pucmm.trafficManagementSystem.model;

import com.pucmm.trafficManagementSystem.controller.IntersectionController;
import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;
import javafx.geometry.Point2D;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Vehicle implements Runnable {
    private volatile boolean running = true;

    private static final AtomicInteger idCounter = new AtomicInteger(0);
    private final int id;
    private final VehicleType type;
    private final Direction origin;
    private final Direction destination;
    private final Intersection intersection;
    private long arrivalTime;
    private double x, y;
    private volatile boolean finished = false;

    // Velocidades
    private final double normalSpeed = 1.5;
    private final double emergencyClearSpeed = 1.5;

    private IntersectionController controller;

    public Vehicle(VehicleType type, Direction origin, Direction destination, Intersection intersection) {
        this.id = idCounter.incrementAndGet();
        this.type = type;
        this.origin = origin;
        this.destination = destination;
        this.intersection = intersection;
    }

    @Override
    public void run() {
        while (running && !isFinished()) {
            try {
                intersection.addToQueue(this);

                List<Point2D> path = controller.getPath(origin, destination);
                if (path.isEmpty()) {
                    this.finished = true;
                    return;
                }

                Point2D baseStopPoint = path.get(1);
                this.arrivalTime = System.currentTimeMillis();

                // Bucle de espera activa
                while (true) {
                    if (intersection.isMyTurn(this)) {
                        break;
                    }
                    Point2D currentTarget = getDynamicStopPoint(baseStopPoint);
                    if (distanceTo(currentTarget) > 1.0) {
                        moveTo(currentTarget);
                    }
                    Thread.sleep(200);
                }

                intersection.startCrossing(this);

                // Recorre el resto del camino
                for (int i = 1; i < path.size(); i++) {
                    moveTo(path.get(i));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                intersection.leaveIntersection(this);
                this.finished = true;
                System.out.printf("🏁 Vehículo %d ha salido de la intersección.\n", this.id);
            }
        }

    }

    /**
     * La velocidad ahora depende del estado de emergencia y del tipo de vehículo.
     */
    private void moveTo(Point2D target) throws InterruptedException {
        double currentSpeed;

        // Si hay una emergencia activa Y este vehículo NO es de emergencia, debe
        // acelerar para despejar.
        if (intersection.isEmergencyActive() && this.type != VehicleType.EMERGENCY) {
            currentSpeed = this.emergencyClearSpeed;
        } else {
            currentSpeed = this.normalSpeed;
        }

        while (distanceTo(target) > currentSpeed) {
            double angle = Math.atan2(target.getY() - y, target.getX() - x);
            x += currentSpeed * Math.cos(angle);
            y += currentSpeed * Math.sin(angle);
            Thread.sleep(16);
        }
        x = target.getX();
        y = target.getY();
    }

    private Point2D getDynamicStopPoint(Point2D baseStopLine) {
        int positionInQueue = intersection.getPositionInQueue(this);
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
                break;
        }
        return baseStopLine;
    }

    private double distanceTo(Point2D target) {
        return Math.sqrt(Math.pow(target.getX() - x, 2) + Math.pow(target.getY() - y, 2));
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

    public boolean isFinished() {
        return finished;
    }

    public VehicleType getType() {
        return type;
    }

    public Direction getOrigin() {
        return origin;
    }

    public long getArrivalTime() {
        return arrivalTime;
    }

    public void setController(IntersectionController controller) {
        this.controller = controller;
    }

    public void stop() {
        this.running = false;
    }
}