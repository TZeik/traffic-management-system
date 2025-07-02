package com.pucmm.trafficManagementSystem.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;

public class Intersection {
    // Un Lock para controlar el acceso a la zona crítica (el cruce).
    private final ReentrantLock lock = new ReentrantLock(true); // 'true' para modo justo
    // Una "condition variable" para que los hilos esperen eficientemente.
    private final Condition canCross = lock.newCondition();

    // Colas concurrentes para cada punto de entrada. Son seguras para hilos.
    private final Map<Direction, ConcurrentLinkedQueue<Vehicle>> waitingQueues;

    public Intersection() {
        waitingQueues = new EnumMap<>(Direction.class);
        for (Direction dir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            waitingQueues.put(dir, new ConcurrentLinkedQueue<>());
        }
    }

    /**
     * El método principal que los vehículos llaman para cruzar.
     */
    public void requestCross(Vehicle vehicle) throws InterruptedException {
        // El vehículo se añade a su cola correspondiente.
        waitingQueues.get(vehicle.getOrigin()).add(vehicle);

        lock.lock(); // Adquiere el lock para entrar a la sección crítica.
        try {
            // El vehículo espera MIENTRAS no sea su turno.
            while (!isMyTurn(vehicle)) {
                canCross.await(); // Espera eficientemente sin consumir CPU.
            }

            // Cuando sale del `while`, es su turno de cruzar.
            // Se elimina de la cola.
            waitingQueues.get(vehicle.getOrigin()).poll();

            System.out.printf("🚦 LUZ VERDE para %s. Cruzando...\n", vehicle);
            Thread.sleep(2000); // Simula el tiempo que tarda en cruzar.

            // Despierta a todos los demás hilos en espera para que re-evalúen quién es el
            // siguiente.
            canCross.signalAll();

        } finally {
            lock.unlock(); // ¡Importante! Siempre liberar el lock.
        }
    }

    /**
     * La lógica de negocio para decidir quién tiene el derecho de paso.
     * Este método SÓLO debe ser llamado desde un bloque 'lockeado'.
     */
    private boolean isMyTurn(Vehicle vehicle) {
        // Regla 1: ¿Es el vehículo que solicita el primero en su propia cola? Si no,
        // debe esperar.
        if (!Objects.equals(waitingQueues.get(vehicle.getOrigin()).peek(), vehicle)) {
            return false;
        }

        // Regla 2: Prioridad de Emergencia.
        Vehicle emergencyVehicle = findFirstEmergencyVehicle();
        if (emergencyVehicle != null) {
            // Si hay una emergencia, solo puede pasar si es ESTE vehículo.
            return Objects.equals(emergencyVehicle, vehicle);
        }

        // Regla 3: Orden de llegada (First-Come, First-Served).
        Vehicle firstArrived = findFirstArrivedVehicle();
        // Solo puede pasar si ESTE vehículo es el que llegó primero globalmente.
        return Objects.equals(firstArrived, vehicle);
    }

    private Vehicle findFirstEmergencyVehicle() {
        return waitingQueues.values().stream()
                .map(ConcurrentLinkedQueue::peek) // Obtiene el primer vehículo de cada cola
                .filter(Objects::nonNull)
                .filter(v -> v.getType() == VehicleType.EMERGENCY)
                .findFirst()
                .orElse(null);
    }

    private Vehicle findFirstArrivedVehicle() {
        return waitingQueues.values().stream()
                .map(ConcurrentLinkedQueue::peek) // Obtiene el primer vehículo de cada cola
                .filter(Objects::nonNull)
                .min((v1, v2) -> Long.compare(v1.getArrivalTime(), v2.getArrivalTime()))
                .orElse(null);
    }
}