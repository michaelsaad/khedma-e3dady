package com.khedma.attendance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.khedma.common.ApiException;
import com.khedma.settings.AppSettings;

/** Which dates are valid meetings: the configured weekday, inside the season, not in the future. */
@Component
public class MeetingCalendar {

    private static final DateTimeFormatter ARABIC_DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.forLanguageTag("ar-EG"));

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
            throw ApiException.badRequest("اختر يوم اجتماع فات داخل السنة الحالية");
        }
    }

    /** e.g. "الجمعة 9 أكتوبر 2026" — used in transaction notes. */
    public static String formatArabic(LocalDate date) {
        return ARABIC_DATE.format(date);
    }
}
