package com.pucmm.trafficManagementSystem.enums;

public enum Direction {
    // Starting points
    NORTH,
    SOUTH,
    EAST,
    WEST,

    // Cross actions
    STRAIGHT,
    RIGHT,
    LEFT,
    U_TURN,
    U_TURN_CONTINUATION,

    // Lanes for Highway Scenario
    LANE_1, // Carril derecho
    LANE_2, // Carril central
    LANE_3  // Carril izquierdo
}