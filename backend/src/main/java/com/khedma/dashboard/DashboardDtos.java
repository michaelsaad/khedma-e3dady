package com.khedma.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.khedma.points.PointsDtos.TransactionResponse;
import com.khedma.points.period.PeriodDtos.PeriodResponse;
import com.khedma.student.Grade;

public final class DashboardDtos {

    private DashboardDtos() {}

    /**
     * One card per student. Includes the phone so servants (USER role) can call or message families;
     * they still can't change anything.
     * Rates are percentages (0-100) of the meetings held so far this season.
     */
    public record StudentCard(
            Long id,
            String name,
            Grade grade,
            String phone,
            Long groupId,
            String groupName,
            /** Points in the chosen period; rank is by these. */
            int points,
            int rank,
            /** All-time total, across every period. */
            int totalPoints,
            int serviceCount,
            int massCount,
            int meetingsHeld,
            int serviceRate,
            int massRate) {}

    public record GroupStanding(Long id, String name, int points, int rank, int totalPoints, long memberCount) {}

    public record LastMeeting(LocalDate date, int serviceCount, int massCount) {}

    public record Summary(
            int totalStudents,
            int totalGroups,
            int meetingsHeld,
            LastMeeting lastMeeting,
            int averageServiceRate,
            int averageMassRate,
            List<StudentCard> topStudents,
            List<GroupStanding> groups,
            PeriodResponse period) {}

    public record MeetingRecord(LocalDate date, boolean service, boolean mass) {}

    public record StudentDetail(
            StudentCard card,
            List<MeetingRecord> attendance,
            List<TransactionResponse> recentTransactions,
            PeriodResponse period) {}

    public enum SortBy { POINTS, NAME, ATTENDANCE }
}
