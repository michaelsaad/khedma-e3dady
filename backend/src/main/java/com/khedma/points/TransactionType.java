package com.khedma.points;

public enum TransactionType {
    MANUAL_ADD,
    MANUAL_DEDUCT,
    RESET,
    /** Created automatically when attendance (service or mass) is marked. */
    ATTENDANCE_AWARD,
    /** Created automatically when a marked attendance is un-marked; cancels one award. */
    ATTENDANCE_REVERSAL
}
