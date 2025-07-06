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

    // Lineas del Highway
    LANE_1, // Carril izquierdo
    LANE_2, // Carril central
    LANE_3  // Carril derecho
}