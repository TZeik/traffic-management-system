package com.pucmm.trafficManagementSystem.controller;

import com.pucmm.trafficManagementSystem.model.Intersection;
import com.pucmm.trafficManagementSystem.model.Vehicle;
import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import java.util.Random;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.util.Duration;

public class IntersectionController {
    @FXML
    private Pane simulationPane;
    @FXML
    private ComboBox<VehicleType> typeComboBox;
    @FXML
    private ComboBox<Direction> originComboBox;
    @FXML
    private ComboBox<Direction> destinationComboBox;
    @FXML
    private Button addVehicleButton;
    @FXML
    private Button addMultipleButton;

    private final Intersection intersection = new Intersection();
    private final Map<Vehicle, Circle> vehicleMap = new ConcurrentHashMap<>();
    private final Group streetGroup = new Group();

    @FXML
    public void initialize() {
        simulationPane.getChildren().add(streetGroup);

        typeComboBox.getItems().setAll(VehicleType.values());
        originComboBox.getItems().setAll(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
        destinationComboBox.getItems().setAll(Direction.STRAIGHT, Direction.RIGHT, Direction.LEFT, Direction.U_TURN);
        typeComboBox.getSelectionModel().selectFirst();
        originComboBox.getSelectionModel().selectFirst();
        destinationComboBox.getSelectionModel().selectFirst();

        simulationPane.widthProperty().addListener((obs, oldVal, newVal) -> redrawStreet());
        simulationPane.heightProperty().addListener((obs, oldVal, newVal) -> redrawStreet());

        startAnimationLoop();
    }

    private void redrawStreet() {
        streetGroup.getChildren().clear();

        double width = simulationPane.getWidth();
        double height = simulationPane.getHeight();
        if (width == 0 || height == 0)
            return;

        double streetWidth = Math.min(width, height) / 4.0;

        Rectangle hStreet = new Rectangle(0, height / 2 - streetWidth / 2, width, streetWidth);
        Rectangle vStreet = new Rectangle(width / 2 - streetWidth / 2, 0, streetWidth, height);
        hStreet.setFill(Color.GRAY);
        vStreet.setFill(Color.GRAY);
        hStreet.setStroke(Color.DARKGRAY);
        vStreet.setStroke(Color.DARKGRAY);
        streetGroup.getChildren().addAll(hStreet, vStreet);

        Line hLine = new Line(0, height / 2, width, height / 2);
        hLine.setStroke(Color.YELLOW);
        hLine.getStrokeDashArray().addAll(25d, 20d);

        Line vLine = new Line(width / 2, 0, width / 2, height);
        vLine.setStroke(Color.YELLOW);
        vLine.getStrokeDashArray().addAll(25d, 20d);
        streetGroup.getChildren().addAll(hLine, vLine);

        streetGroup.getChildren()
                .add(createStopSign(width / 2 + streetWidth / 2 + 45, height / 2 - streetWidth / 2 - 45, -90)); // East
        streetGroup.getChildren()
                .add(createStopSign(width / 2 - streetWidth / 2 - 45, height / 2 + streetWidth / 2 + 45, 90)); // West
        streetGroup.getChildren()
                .add(createStopSign(width / 2 - streetWidth / 2 - 45, height / 2 - streetWidth / 2 - 45, 180)); // North
        streetGroup.getChildren()
                .add(createStopSign(width / 2 + streetWidth / 2 + 45, height / 2 + streetWidth / 2 + 45, 0)); // South
    }

    private Group createStopSign(double x, double y, double angle) {
        double scale = 0.5;
        Polygon octagon = new Polygon(
                20 * scale, 0, 40 * scale, 0, 60 * scale, 20 * scale, 60 * scale, 40 * scale,
                40 * scale, 60 * scale, 20 * scale, 60 * scale, 0, 40 * scale, 0, 20 * scale);
        octagon.setFill(Color.RED);
        octagon.setStroke(Color.WHITE);
        octagon.setStrokeWidth(2);

        Text text = new Text("STOP");
        text.setFont(Font.font("Arial BOLD", 16 * scale));
        text.setFill(Color.WHITE);
        text.setX(10 * scale);
        text.setY(37 * scale);

        Rectangle pole = new Rectangle();
        pole.setX(28 * scale);
        pole.setY(60 * scale);
        pole.setWidth(4 * scale);
        pole.setHeight(40 * scale);
        pole.setFill(Color.LIGHTGRAY);

        Group sign = new Group(octagon, text, pole);
        sign.relocate(x - 30 * scale, y - 30 * scale);
        sign.getTransforms().add(new Rotate(angle, 30 * scale, 30 * scale));
        return sign;
    }

    @FXML
    private void addVehicle() {
        disableButtonsTemporarily();
        createAndStartVehicle(
                typeComboBox.getValue(),
                originComboBox.getValue(),
                destinationComboBox.getValue());
    }

    private void createAndStartVehicle(VehicleType type, Direction origin, Direction destination) {
        // 1. Crea el objeto lógico del vehículo
        Vehicle vehicle = new Vehicle(type, origin, destination, intersection);
        vehicle.setController(this);

        // 2. Crea su representación visual
        Circle vehicleCircle = new Circle(8,
                type == VehicleType.EMERGENCY ? Color.web("#e74c3c") : Color.web("#3498db"));
        vehicleCircle.setStroke(Color.BLACK);

        // 3. Obtiene su ruta y posición inicial
        List<Point2D> path = getPath(origin, destination);
        if (path.isEmpty())
            return; // No crea el vehículo si no hay una ruta válida

        Point2D startPos = path.get(0);
        vehicle.setPosition(startPos.getX(), startPos.getY());

        // 4. Lo añade a las colecciones y a la pantalla
        vehicleMap.put(vehicle, vehicleCircle);
        simulationPane.getChildren().add(vehicleCircle);
        vehicleCircle.toFront();

        // 5. Inicia el hilo del vehículo
        new Thread(vehicle).start();
    }

    @FXML
    private void addMultipleVehicles() {
        disableButtonsTemporarily();
        System.out.println("Iniciando adición de lote de vehículos...");
        final int numberOfVehiclesToAdd = 15; // El número de vehículos a generar
        final Random random = new Random();

        // Se crea un nuevo hilo para no congelar la UI
        new Thread(() -> {
            try {
                Direction[] origins = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
                Direction[] destinations = { Direction.STRAIGHT, Direction.LEFT, Direction.RIGHT };

                for (int i = 0; i < numberOfVehiclesToAdd; i++) {
                    // Genera propiedades aleatorias para el nuevo vehículo
                    Direction randomOrigin = origins[random.nextInt(origins.length)];
                    Direction randomDestination = destinations[random.nextInt(destinations.length)];

                    VehicleType randomType = (random.nextInt(200) == 0) ? VehicleType.EMERGENCY : VehicleType.NORMAL;

                    Platform.runLater(() -> {
                        createAndStartVehicle(randomType, randomOrigin, randomDestination);
                    });

                    Thread.sleep(250);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Adición de lote terminada.");
        }).start(); // Inicia el hilo de fondo
    }

    private void startAnimationLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                Iterator<Map.Entry<Vehicle, Circle>> iterator = vehicleMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Vehicle, Circle> entry = iterator.next();
                    if (entry.getKey().isFinished()) {
                        simulationPane.getChildren().remove(entry.getValue());
                        iterator.remove();
                    } else {
                        entry.getValue().relocate(entry.getKey().getX() - 8, entry.getKey().getY() - 8);
                    }
                }
            }
        };
        timer.start();
    }

    private void disableButtonsTemporarily() {
        addVehicleButton.setDisable(true);
        addMultipleButton.setDisable(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(2));

        pause.setOnFinished(event -> {
            addVehicleButton.setDisable(false);
            addMultipleButton.setDisable(false);
        });

        pause.play();
    }

    public List<Point2D> getPath(Direction origin, Direction destination) {
        double width = simulationPane.getWidth();
        double height = simulationPane.getHeight();
        if (width == 0 || height == 0)
            return List.of();

        double streetW = Math.min(width, height) / 4.0;
        final double STOP_GAP = 20.0;
        // Viniendo del NORTE: carril izquierdo de la pantalla (su derecha)
        double N_IN_X = width / 2 - streetW / 4;
        double N_OUT_X = width / 2 + streetW / 4;
        // Viniendo del SUR: carril derecho de la pantalla (su derecha)
        double S_IN_X = width / 2 + streetW / 4;
        double S_OUT_X = width / 2 - streetW / 4;
        // Viniendo del ESTE: carril superior de la pantalla (su derecha)
        double E_IN_Y = height / 2 - streetW / 4;
        double E_OUT_Y = height / 2 + streetW / 4;
        // Viniendo del OESTE: carril inferior de la pantalla (su derecha)
        double W_IN_Y = height / 2 + streetW / 4;
        double W_OUT_Y = height / 2 - streetW / 4;

        Point2D stopN = new Point2D(N_IN_X, height / 2 - streetW / 2 - STOP_GAP);
        Point2D stopS = new Point2D(S_IN_X, height / 2 + streetW / 2 + STOP_GAP);
        Point2D stopE = new Point2D(width / 2 + streetW / 2 + STOP_GAP, E_IN_Y);
        Point2D stopW = new Point2D(width / 2 - streetW / 2 - STOP_GAP, W_IN_Y);

        Point2D exitN = new Point2D(N_OUT_X, -50);
        Point2D exitS = new Point2D(S_OUT_X, height + 50);
        Point2D exitE = new Point2D(width + 50, E_OUT_Y);
        Point2D exitW = new Point2D(-50, W_OUT_Y);
        if (destination == Direction.U_TURN) {
            switch (origin) {
                case NORTH:
                    return List.of(new Point2D(N_IN_X, -50), stopN, new Point2D(N_OUT_X, stopN.getY() + STOP_GAP),
                            exitN);
                case SOUTH:
                    return List.of(new Point2D(S_IN_X, height + 50), stopS,
                            new Point2D(S_OUT_X, stopS.getY() - STOP_GAP), exitS);
                case EAST:
                    return List.of(new Point2D(width + 50, E_IN_Y), stopE,
                            new Point2D(stopE.getX() - STOP_GAP, E_OUT_Y), exitE);
                case WEST:
                    return List.of(new Point2D(-50, W_IN_Y), stopW, new Point2D(stopW.getX() + STOP_GAP, W_OUT_Y),
                            exitW);
                default:
                    break;
            }
        }

        switch (origin) {
            case NORTH:
                Point2D startN = new Point2D(N_IN_X, -50);
                Point2D enterN = new Point2D(N_IN_X, stopN.getY() + STOP_GAP);
                switch (destination) {
                    case STRAIGHT:
                        return List.of(startN, stopN, new Point2D(N_IN_X, stopS.getY()), exitS);
                    case RIGHT:
                        return List.of(startN, stopN, enterN, new Point2D(stopW.getX(), W_OUT_Y), exitW);
                    case LEFT:
                        return List.of(startN, stopN, new Point2D(N_IN_X, E_OUT_Y), new Point2D(stopE.getX(), E_OUT_Y),
                                exitE);
                    default:
                        break;
                }
                break;
            case SOUTH:
                Point2D startS = new Point2D(S_IN_X, height + 50);
                Point2D enterS = new Point2D(S_IN_X, stopS.getY() - STOP_GAP);
                switch (destination) {
                    case STRAIGHT:
                        return List.of(startS, stopS, new Point2D(S_IN_X, stopN.getY()), exitN);
                    case RIGHT:
                        return List.of(startS, stopS, enterS, new Point2D(stopE.getX(), E_OUT_Y), exitE);
                    case LEFT:
                        return List.of(startS, stopS, new Point2D(S_IN_X, W_OUT_Y), new Point2D(stopW.getX(), W_OUT_Y),
                                exitW);
                    default:
                        break;
                }
                break;
            case EAST:
                Point2D startE = new Point2D(width + 50, E_IN_Y);
                Point2D enterE = new Point2D(stopE.getX() - STOP_GAP, E_IN_Y);
                switch (destination) {
                    case STRAIGHT:
                        return List.of(startE, stopE, new Point2D(stopW.getX(), E_IN_Y), exitW);
                    case RIGHT:
                        return List.of(startE, stopE, new Point2D(N_OUT_X, E_IN_Y), new Point2D(N_OUT_X, stopN.getY()),
                                exitN);
                    case LEFT:

                        return List.of(startE, stopE, enterE, new Point2D(S_OUT_X, stopS.getY()), exitS);
                    default:
                        break;
                }
                break;
            case WEST:
                Point2D startW = new Point2D(-50, W_IN_Y);
                Point2D enterW = new Point2D(stopW.getX() + STOP_GAP, W_IN_Y);
                switch (destination) {
                    case STRAIGHT:
                        return List.of(startW, stopW, new Point2D(stopE.getX(), W_IN_Y), exitE);
                    case RIGHT:
                        return List.of(startW, stopW, new Point2D(S_OUT_X, W_IN_Y), new Point2D(S_OUT_X, stopS.getY()),
                                exitS);
                    case LEFT:

                        return List.of(startW, stopW, enterW, new Point2D(N_OUT_X, stopN.getY()), exitN);
                    default:
                        break;
                }
                break;
            default:
                break;
        }
        return List.of(new Point2D(0, 0));
    }
}