package com.khedma.push;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.khedma.points.PointsGiven;
import com.khedma.push.PushService.Message;

/** Tells a kid with a linked account that they just got points. Runs only after the points are committed. */
@Component
class PointsPushListener {

    private final PushService pushService;

    PointsPushListener(PushService pushService) {
        this.pushService = pushService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPointsGiven(PointsGiven event) {
        pushService.sendToStudentAccount(event.studentId(), new Message(
                "نقط جديدة",
                signed(event.amount()) + " " + event.reason() + " · رصيدك " + ArabicDigits.of(event.balanceAfter()),
                "/me",
                "points"));
    }

    private static String signed(int amount) {
        return (amount > 0 ? "+" : "-") + ArabicDigits.of(Math.abs(amount));
    }
}
