package com.khedma.points;

/**
 * Published after a manual bonus was added to a student, so other parts (push notifications) can react once the
 * points are safely committed, without the points code knowing about them.
 */
public record PointsGiven(Long studentId, int amount, String reason, int balanceAfter) {}
