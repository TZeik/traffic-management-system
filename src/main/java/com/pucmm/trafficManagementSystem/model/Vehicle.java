package com.pucmm.trafficManagementSystem.model;

import java.util.concurrent.atomic.AtomicInteger;

import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;

public class Vehicle implements Runnable {
    private static final AtomicInteger idCounter = new AtomicInteger(0); // Para generar IDs únicos

    private final int id;
    private final VehicleType type;
    private final Direction origin;
    private final Direction destination;
    private final Intersection intersection;
    private double x, y;
    private long arrivalTime;

    public Vehicle(VehicleType type, Direction origin, Direction destination, Intersection intersection) {
        this.id = idCounter.incrementAndGet();
        this.type = type;
        this.origin = origin;
        this.destination = destination;
        this.intersection = intersection;
    }

    @Override
    public void run() {
        try {
            // 1. Simula el viaje hacia la intersección
            moveToStopLine();
            System.out.printf("🚗 Vehículo %d (%s) desde %s se aproxima a la intersección.\n", this.id, this.type,
                    this.origin);
            Thread.sleep((long) (Math.random() * 3000) + 1000); // Tiempo de viaje aleatorio
            this.arrivalTime = System.currentTimeMillis();
            intersection.requestCross(this);
            // 2. Llega a la intersección y registra su tiempo de llegada
            moveOffScreen();
            this.arrivalTime = System.currentTimeMillis();
            System.out.printf("🛑 Vehículo %d (%s) ha LLEGADO a la intersección desde %s y está esperando.\n", this.id,
                    this.type, this.origin);

            // 3. Solicita cruzar y espera a que la intersección le dé permiso
            intersection.requestCross(this);

            // 4. Una vez que el método anterior termina, el vehículo ha cruzado
            System.out.printf("✅ Vehículo %d (%s) ha CRUZADO la intersección. Destino: %s\n", this.id, this.type,
                    this.destination);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.printf("El vehículo %d fue interrumpido.\n", this.id);
        }
    }

    private void moveToStopLine() throws InterruptedException {
        // Aquí iría la lógica de animación, moviendo el vehículo pixel por pixel
        // Por simplicidad, por ahora solo teletransportamos.
        // En una versión avanzada, harías un bucle que actualiza X/Y y duerme un poco.
        switch (this.origin) {
            case NORTH:
                setPosition(405, 200);
                break;
            case SOUTH:
                setPosition(405, 520);
                break;
            case EAST:
                setPosition(555, 360);
                break;
            case WEST:
                setPosition(265, 360);
                break;
            default:
                break;
        }
        Thread.sleep(1000); // Simula tiempo de viaje
    }

    private void moveOffScreen() throws InterruptedException {
        // Simula el vehículo saliendo de la vista
        // Por simplicidad, lo movemos a una coordenada lejana.
        setPosition(-100, -100);
        Thread.sleep(1000);
    }

    // Getters para que la Intersection pueda leer las propiedades del vehículo
    public int getId() {
        return id;
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

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    // Setters para recalibrar la posicion
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return String.format("Vehículo %d [%s]", id, type);
    }
}