package com.pucmm.trafficManagementSystem.controller;

import com.pucmm.trafficManagementSystem.model.Intersection;
import com.pucmm.trafficManagementSystem.model.Vehicle;
import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class IntersectionController {

    @FXML
    private Pane simulationPane;
    @FXML
    private ComboBox<VehicleType> typeComboBox;
    @FXML
    private ComboBox<Direction> originComboBox;
    @FXML
    private ComboBox<Direction> destinationComboBox;

    // Instancia del modelo de la intersección de la Fase 1
    private final Intersection intersection = new Intersection();

    // Mapa para vincular un objeto Vehículo (lógica) a un Círculo (vista)
    private final Map<Vehicle, Circle> vehicleMap = new ConcurrentHashMap<>();

    // El `initialize` se ejecuta automáticamente después de cargar el FXML
    @FXML
    public void initialize() {
        // Poblar los ComboBox con los valores de los enums
        typeComboBox.getItems().setAll(VehicleType.values());
        originComboBox.getItems().setAll(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
        destinationComboBox.getItems().setAll(Direction.STRAIGHT, Direction.LEFT, Direction.RIGHT, Direction.U_TURN);

        // Seleccionar valores por defecto
        typeComboBox.getSelectionModel().selectFirst();
        originComboBox.getSelectionModel().selectFirst();
        destinationComboBox.getSelectionModel().selectFirst();

        // Iniciar el bucle de animación para mover los vehículos
        startAnimationLoop();
    }

    // Este método se llama cuando se presiona el botón "Añadir Vehículo"
    @FXML
    private void addVehicle() {
        VehicleType type = typeComboBox.getValue();
        Direction origin = originComboBox.getValue();
        Direction destination = destinationComboBox.getValue();

        // 1. Crear el vehículo lógico
        // NOTA: Pasamos el controlador 'this' al vehículo para que pueda actualizarse.
        Vehicle vehicle = new Vehicle(type, origin, destination, intersection);

        // 2. Crear su representación visual (un círculo)
        Circle vehicleCircle = createVehicleCircle(vehicle);

        // 3. Vincular ambos en el mapa
        vehicleMap.put(vehicle, vehicleCircle);

        // 4. Añadir el círculo al panel de simulación
        simulationPane.getChildren().add(vehicleCircle);

        // 5. Iniciar el hilo del vehículo para que comience su lógica
        new Thread(vehicle).start();
    }

    // Método para crear el círculo con el color y posición inicial correctos
    private Circle createVehicleCircle(Vehicle vehicle) {
        Circle circle = new Circle(10, vehicle.getType() == VehicleType.EMERGENCY ? Color.RED : Color.BLUE);

        // Posiciones iniciales fuera de la pantalla visible, dependiendo del origen
        switch (vehicle.getOrigin()) {
            case NORTH:
                vehicle.setPosition(405, -20);
                break;
            case SOUTH:
                vehicle.setPosition(405, 740);
                break;
            case EAST:
                vehicle.setPosition(831, 360);
                break;
            case WEST:
                vehicle.setPosition(-20, 360);
                break;
            default:
                break;
        }
        circle.relocate(vehicle.getX(), vehicle.getY());
        return circle;
    }

    // Bucle principal para actualizar la GUI
    private void startAnimationLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // En cada frame, actualiza la posición de cada círculo en la pantalla
                for (Map.Entry<Vehicle, Circle> entry : vehicleMap.entrySet()) {
                    Vehicle v = entry.getKey();
                    Circle c = entry.getValue();
                    c.relocate(v.getX(), v.getY());
                }
            }
        };
        timer.start();
    }

    // Un método público para que el hilo del Vehículo pueda notificar su nueva
    // posición
    public void updateVehiclePosition(Vehicle vehicle) {
        // No se necesita `Platform.runLater` aquí porque la actualización
        // la lee el AnimationTimer, que ya corre en el hilo de JavaFX.
    }
}