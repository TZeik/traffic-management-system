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
            System.out.printf("🚗 Vehículo %d (%s) desde %s se aproxima a la intersección.\n", this.id, this.type,
                    this.origin);
            Thread.sleep((long) (Math.random() * 3000) + 1000); // Tiempo de viaje aleatorio

            // 2. Llega a la intersección y registra su tiempo de llegada
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

    @Override
    public String toString() {
        return String.format("Vehículo %d [%s]", id, type);
    }
}