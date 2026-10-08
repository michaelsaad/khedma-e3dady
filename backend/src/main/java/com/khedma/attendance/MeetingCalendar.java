package com.khedma.attendance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.settings.AppSettings;

/** Which dates are valid meetings: the configured weekday, inside the season, not in the future. */
@Component
public class MeetingCalendar {

    private final Clock clock;

    public MeetingCalendar(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<LocalDate> pastMeetings(AppSettings settings) {
        LocalDate last = today().isBefore(settings.getSeasonEnd()) ? today() : settings.getSeasonEnd();
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = settings.getSeasonStart().with(TemporalAdjusters.nextOrSame(settings.getMeetingDay()));
             !date.isAfter(last);
             date = date.plusWeeks(1)) {
            dates.add(date);
        }
        return dates;
    }

    public void requireValidMeeting(LocalDate date, AppSettings settings) {
        if (date.getDayOfWeek() != settings.getMeetingDay()
                || date.isBefore(settings.getSeasonStart())
                || date.isAfter(settings.getSeasonEnd())
                || date.isAfter(today())) {
            throw new ApiException(ErrorCode.INVALID_MEETING_DATE);
        }
    }
}
