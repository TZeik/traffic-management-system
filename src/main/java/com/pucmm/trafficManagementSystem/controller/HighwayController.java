package com.pucmm.trafficManagementSystem.controller;

import com.pucmm.trafficManagementSystem.App;
import com.pucmm.trafficManagementSystem.enums.Direction;
import com.pucmm.trafficManagementSystem.enums.VehicleType;
import com.pucmm.trafficManagementSystem.model.HighwayIntersection;
import com.pucmm.trafficManagementSystem.model.Vehicle;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HighwayController {
    @FXML
    private Pane simulationPane;
    @FXML
    private ComboBox<VehicleType> typeComboBox;
    @FXML
    private ComboBox<Direction> originComboBox;
    @FXML
    private ComboBox<Direction> actionComboBox;
    @FXML
    private ComboBox<Integer> intersectionComboBox;
    @FXML
    private Button addVehicleButton, addMultipleButton, backButton;
    @FXML
    private Label laneLabel, intersectionLabel;

    private final List<HighwayIntersection> intersections = new ArrayList<>();
    private final Map<Vehicle, Circle> vehicleMap = new ConcurrentHashMap<>();
    private final Group highwayGroup = new Group();
    private AnimationTimer animationTimer;
    private final double laneHeight = 60;
    private final double intersectionWidth = 120;

    private final Map<Integer, Set<Vehicle>> approachingVehicles = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        for (int i = 1; i <= 4; i++) {
            intersections.add(new HighwayIntersection(i));
            approachingVehicles.put(i, ConcurrentHashMap.newKeySet());
        }
        simulationPane.getChildren().add(highwayGroup);
        typeComboBox.getItems().setAll(VehicleType.values());
        originComboBox.getItems().setAll(Direction.WEST, Direction.EAST);
        actionComboBox.getItems().setAll(Direction.STRAIGHT, Direction.LEFT, Direction.RIGHT, Direction.U_TURN);
        typeComboBox.getSelectionModel().selectFirst();
        originComboBox.getSelectionModel().selectFirst();
        actionComboBox.getSelectionModel().selectFirst();
        backButton.setGraphic(new FontIcon(FontAwesomeSolid.ARROW_LEFT));
        actionComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, newAction) -> updateIntersectionSelectorVisibility(newAction));
        originComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, newOrigin) -> updateAvailableIntersections(newOrigin));
        updateAvailableIntersections(originComboBox.getValue());
        updateIntersectionSelectorVisibility(actionComboBox.getValue());
        simulationPane.widthProperty().addListener((obs, o, n) -> redrawHighway());
        simulationPane.heightProperty().addListener((obs, o, n) -> redrawHighway());
        startAnimationLoop();
    }

    private void updateIntersectionSelectorVisibility(Direction action) {
        boolean isTurn = action == Direction.LEFT || action == Direction.RIGHT || action == Direction.U_TURN;
        intersectionLabel.setVisible(isTurn);
        intersectionLabel.setManaged(isTurn);
        intersectionComboBox.setVisible(isTurn);
        intersectionComboBox.setManaged(isTurn);
    }

    private void updateAvailableIntersections(Direction origin) {
        Integer previouslySelected = intersectionComboBox.getValue();
        intersectionComboBox.getItems().clear();
        if (origin == Direction.WEST) {
            intersectionComboBox.getItems().setAll(2, 3, 4);
        } else {
            intersectionComboBox.getItems().setAll(1, 2, 3);
        }
        if (previouslySelected != null && intersectionComboBox.getItems().contains(previouslySelected)) {
            intersectionComboBox.setValue(previouslySelected);
        } else {
            intersectionComboBox.getSelectionModel().selectFirst();
        }
    }

    public double getIntersectionCenterX(int id, double totalWidth) {
        double gapFromCenter = totalWidth / 8.0;
        switch (id) {
            case 1:
                return intersectionWidth / 2.0;
            case 2:
                return totalWidth / 2.0 - gapFromCenter;
            case 3:
                return totalWidth / 2.0 + gapFromCenter;
            case 4:
                return totalWidth - intersectionWidth / 2.0;
            default:
                return 0;
        }
    }

    public double getIntersectionWidth() {
        return this.intersectionWidth;
    }

    private void redrawHighway() {
        highwayGroup.getChildren().clear();
        double width = simulationPane.getWidth();
        double height = simulationPane.getHeight();
        if (width == 0 || height == 0)
            return;

        double totalHighwayHeight = laneHeight * 6;
        double highwayY = (height - totalHighwayHeight) / 2;

        Rectangle highwayBackground = new Rectangle(0, highwayY, width, totalHighwayHeight);
        highwayBackground.setFill(Color.GRAY);
        highwayGroup.getChildren().add(highwayBackground);
        for (int i = 1; i <= 4; i++) {
            double centerX = getIntersectionCenterX(i, width);
            Rectangle vStreet = new Rectangle(centerX - intersectionWidth / 2, 0, intersectionWidth, height);
            vStreet.setFill(Color.GRAY);
            highwayGroup.getChildren().add(vStreet);
        }

        for (int i = 1; i < 6; i++) {
            if (i == 3)
                continue;
            Line laneLine = new Line(0, highwayY + i * laneHeight, width, highwayY + i * laneHeight);
            laneLine.setStroke(Color.WHITE);
            laneLine.getStrokeDashArray().addAll(25d, 20d);
            highwayGroup.getChildren().add(laneLine);
        }

        double wallY = highwayY + 3 * laneHeight;
        double lastX = 0;
        for (int i = 1; i <= 4; i++) {
            double centerX = getIntersectionCenterX(i, width);
            double gapStart = centerX - intersectionWidth / 2;
            Rectangle wallSegment = new Rectangle(lastX, wallY - 5, gapStart - lastX, 10);
            wallSegment.setFill(Color.DARKSLATEGRAY);
            highwayGroup.getChildren().add(wallSegment);
            lastX = centerX + intersectionWidth / 2;
        }
        Rectangle finalWallSegment = new Rectangle(lastX, wallY - 5, width - lastX, 10);
        finalWallSegment.setFill(Color.DARKSLATEGRAY);
        highwayGroup.getChildren().add(finalWallSegment);

        for (int i = 1; i <= 4; i++) {
            double centerX = getIntersectionCenterX(i, width);
            highwayGroup.getChildren().add(createStopSign(
                    centerX + intersectionWidth / 2 + 15,
                    highwayY + (laneHeight * 3),
                    180));
            highwayGroup.getChildren().add(createStopSign(
                    centerX - intersectionWidth / 2 - 15,
                    highwayY + (laneHeight * 3),
                    0));
        }

        highwayGroup.toBack();
    }

    private Group createStopSign(double x, double y, double angle) {
        double scale = 0.4;
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

        Group sign = new Group(octagon, text);
        sign.relocate(x - 30 * scale, y - 30 * scale);
        sign.getTransforms().add(new Rotate(angle, 30 * scale, 30 * scale));
        return sign;
    }

    @FXML
    private void addVehicle() {
        disableButtonsTemporarily();
        VehicleType type = typeComboBox.getValue();
        Direction origin = originComboBox.getValue();
        Direction action = actionComboBox.getValue();
        Direction lane;
        if (action == Direction.LEFT || action == Direction.U_TURN)
            lane = Direction.LANE_1;
        else if (action == Direction.RIGHT)
            lane = Direction.LANE_3;
        else
            lane = Direction.LANE_2;
        Integer intersectionId = intersectionComboBox.isVisible() ? intersectionComboBox.getValue() : null;
        createAndStartVehicle(type, origin, lane, action, intersectionId);
    }

    @FXML
    private void addMultipleVehicles() {
        disableButtonsTemporarily();
        final int numVehicles = 30;
        final Random random = new Random();
        Direction[] actions = { Direction.STRAIGHT, Direction.LEFT, Direction.RIGHT, Direction.U_TURN };
        new Thread(() -> {
            try {
                for (int i = 0; i < numVehicles; i++) {
                    Direction origin = random.nextBoolean() ? Direction.WEST : Direction.EAST;
                    Direction action = actions[random.nextInt(actions.length)];
                    VehicleType type = (random.nextInt(200) == 0) ? VehicleType.EMERGENCY : VehicleType.NORMAL;

                    Direction lane;
                    if (action == Direction.LEFT || action == Direction.U_TURN)
                        lane = Direction.LANE_1;
                    else if (action == Direction.RIGHT)
                        lane = Direction.LANE_3;
                    else
                        lane = Direction.LANE_2;

                    Integer intersectionId = null;
                    if (action != Direction.STRAIGHT) {
                        List<Integer> possibleIntersections = new ArrayList<>();
                        if (origin == Direction.WEST)
                            possibleIntersections.addAll(Arrays.asList(2, 3, 4));
                        else
                            possibleIntersections.addAll(Arrays.asList(1, 2, 3));
                        intersectionId = possibleIntersections.get(random.nextInt(possibleIntersections.size()));
                    }

                    final Integer finalIntersectionId = intersectionId;
                    final Direction finalLane = lane;
                    Platform.runLater(
                            () -> createAndStartVehicle(type, origin, finalLane, action, finalIntersectionId));
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void createAndStartVehicle(VehicleType type, Direction origin, Direction lane, Direction action,
            Integer intersectionId) {
        HighwayIntersection targetIntersection = (intersectionId != null) ? intersections.get(intersectionId - 1)
                : null;
        Vehicle vehicle = new Vehicle(type, origin, action, targetIntersection);
        vehicle.setLane(lane);
        vehicle.setController(this);
        Circle vehicleCircle = new Circle(10,
                type == VehicleType.EMERGENCY ? Color.web("#e74c3c") : Color.web("#3498db"));
        vehicleCircle.setStroke(Color.BLACK);
        List<Point2D> path = getPath(vehicle);
        if (path.isEmpty())
            return;
        Point2D startPos = path.get(0);
        vehicle.setPosition(startPos.getX(), startPos.getY());
        vehicleMap.put(vehicle, vehicleCircle);
        simulationPane.getChildren().add(vehicleCircle);
        new Thread(vehicle).start();
    }

    public List<Point2D> getPath(Vehicle vehicle) {
        double width = simulationPane.getWidth();
        double height = simulationPane.getHeight();
        if (width == 0 || height == 0)
            return List.of();

        double highwayY = (height - (laneHeight * 6)) / 2;
        Direction origin = vehicle.getOrigin();
        Direction action = vehicle.getDestination();
        Direction lane = vehicle.getLane();
        double startY = getLaneY(origin, lane, highwayY);

        if (action == Direction.STRAIGHT) {
            Point2D start = new Point2D(origin == Direction.WEST ? -50 : width + 50, startY);
            Point2D end = new Point2D(origin == Direction.WEST ? width + 50 : -50, startY);
            return List.of(start, end);
        }

        HighwayIntersection intersection = vehicle.getTargetIntersection();
        if (intersection == null)
            return List.of();

        double intersectionCenterX = getIntersectionCenterX(intersection.getId(), width);
        List<Point2D> path = new ArrayList<>();
        Point2D startPoint, stopPoint, turnPoint;

        if (origin == Direction.WEST) {
            startPoint = new Point2D(-50, startY);
            stopPoint = new Point2D(intersectionCenterX - intersectionWidth / 2 - 20, startY);
            turnPoint = new Point2D(intersectionCenterX, startY);
            path.addAll(Arrays.asList(startPoint, stopPoint, turnPoint));

            switch (action) {
                case RIGHT:
                    path.add(new Point2D(intersectionCenterX, height + 50));
                    break;
                case LEFT:
                    path.add(new Point2D(intersectionCenterX, -50));
                    break;
                case U_TURN:
                    path.add(new Point2D(intersectionCenterX, getLaneY(Direction.EAST, Direction.LANE_2, highwayY)));
                    path.add(new Point2D(-50, getLaneY(Direction.EAST, Direction.LANE_2, highwayY)));
                    break;
                default:
                    break;
            }
        } else {
            startPoint = new Point2D(width + 50, startY);
            stopPoint = new Point2D(intersectionCenterX + intersectionWidth / 2 + 20, startY);
            turnPoint = new Point2D(intersectionCenterX, startY);
            path.addAll(Arrays.asList(startPoint, stopPoint, turnPoint));

            switch (action) {
                case RIGHT:
                    path.add(new Point2D(intersectionCenterX, -50));
                    break;
                case LEFT:
                    path.add(new Point2D(intersectionCenterX, height + 50));
                    break;
                case U_TURN:
                    path.add(new Point2D(intersectionCenterX, getLaneY(Direction.WEST, Direction.LANE_2, highwayY)));
                    path.add(new Point2D(width + 50, getLaneY(Direction.WEST, Direction.LANE_2, highwayY)));
                    break;
                default:
                    break;
            }
        }
        return path;
    }

    private double getLaneY(Direction origin, Direction lane, double highwayY) {
        double laneOffset = 0.5;
        if (lane == Direction.LANE_2)
            laneOffset = 1.5;
        if (lane == Direction.LANE_3)
            laneOffset = 2.5;
        return origin == Direction.EAST ? highwayY + (3 - laneOffset) * laneHeight
                : highwayY + (3 * laneHeight) + (laneOffset * laneHeight);
    }

    public Vehicle findLeaderFor(Vehicle follower) {
        Vehicle leader = null;
        double minDistance = Double.MAX_VALUE;

        for (Vehicle potentialLeader : vehicleMap.keySet()) {
            if (follower.equals(potentialLeader)) {
                continue;
            }

            if (follower.getOrigin() == potentialLeader.getOrigin()
                    && follower.getLane() == potentialLeader.getLane()) {
                double distance;
                boolean isInFront;

                if (follower.getOrigin() == Direction.WEST) {
                    isInFront = potentialLeader.getX() > follower.getX();
                    distance = potentialLeader.getX() - follower.getX();
                } else {
                    isInFront = potentialLeader.getX() < follower.getX();
                    distance = follower.getX() - potentialLeader.getX();
                }

                if (isInFront && distance < minDistance) {
                    minDistance = distance;
                    leader = potentialLeader;
                }
            }
        }
        return leader;
    }

    public void registerApproachingVehicle(int intersectionId, Vehicle vehicle) {
        approachingVehicles.get(intersectionId).add(vehicle);
    }

    public void deregisterApproachingVehicle(int intersectionId, Vehicle vehicle) {
        approachingVehicles.get(intersectionId).remove(vehicle);
    }

    public boolean isIntersectionApproachedByStraightVehicle(int intersectionId, Vehicle vehicleToCheck) {
        Set<Vehicle> approaching = approachingVehicles.get(intersectionId);
        if (approaching.isEmpty()) {
            return false;
        }

        if (vehicleToCheck.getType() == VehicleType.EMERGENCY) {
            return approaching.stream().anyMatch(v -> v.getType() == VehicleType.EMERGENCY);
        }

        return !approaching.isEmpty();
    }

    public boolean shouldYieldForTurningEmergency(Vehicle straightVehicle, int intersectionId) {
        HighwayIntersection intersection = intersections.get(intersectionId - 1);
        return intersection.hasEmergencyVehicleWaiting();
    }

    public double getSimulationPaneWidth() {
        return simulationPane.getWidth();
    }

    private void startAnimationLoop() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                Iterator<Map.Entry<Vehicle, Circle>> iterator = vehicleMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Vehicle, Circle> entry = iterator.next();
                    if (entry.getKey().isFinished()) {
                        simulationPane.getChildren().remove(entry.getValue());
                        iterator.remove();
                    } else {
                        entry.getValue().relocate(entry.getKey().getX() - 10, entry.getKey().getY() - 10);
                    }
                }
            }
        };
        animationTimer.start();
    }

    private void disableButtonsTemporarily() {
        addVehicleButton.setDisable(true);
        addMultipleButton.setDisable(true);
        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(event -> {
            addVehicleButton.setDisable(false);
            addMultipleButton.setDisable(false);
        });
        pause.play();
    }

    @FXML
    private void goBackToMenu() {
        if (animationTimer != null)
            animationTimer.stop();
        for (Vehicle vehicle : vehicleMap.keySet())
            vehicle.stop();
        vehicleMap.clear();
        simulationPane.getChildren().clear();
        try {
            App.setRoot("MenuView");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}