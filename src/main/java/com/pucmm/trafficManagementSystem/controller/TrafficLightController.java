package com.pucmm.trafficManagementSystem.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrafficLightController {
    // Mapa para el estado visual de los semáforos (true = verde, false = rojo)
    private final Map<Integer, AtomicBoolean> lightStates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public TrafficLightController() {
        // Inicializar los 6 semáforos, todos en rojo al principio.
        for (int i = 1; i <= 6; i++) {
            lightStates.put(i, new AtomicBoolean(false));
        }
        startCycle();
    }

    private void startCycle() {
        // Ciclo de cambio de luces cada 12 segundos.
        scheduler.scheduleAtFixedRate(() -> {
            // Semáforos 1 (izq) y 6 (der) son independientes y opuestos
            toggleLight(1);
            toggleLight(6);

            // Semáforos 2 y 3 (intersección 2) son opuestos
            boolean is2Green = lightStates.get(2).get();
            lightStates.get(2).set(!is2Green);
            lightStates.get(3).set(is2Green);

            // Semáforos 4 y 5 (intersección 3) son opuestos
            boolean is4Green = lightStates.get(4).get();
            lightStates.get(4).set(!is4Green);
            lightStates.get(5).set(is4Green);

        }, 0, 10, TimeUnit.SECONDS);
    }

    private void toggleLight(int lightId) {
        lightStates.get(lightId).set(!lightStates.get(lightId).get());
    }

    public boolean isGreen(int lightId) {
        if (lightId < 1 || lightId > 6) return false;
        return lightStates.get(lightId).get();
    }
    
    // Método para que los vehículos de emergencia fuercen el verde
    public void setEmergencyGreen(int lightId, boolean green) {
        if (lightId >= 1 && lightId <= 6) {
             lightStates.get(lightId).set(green);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}