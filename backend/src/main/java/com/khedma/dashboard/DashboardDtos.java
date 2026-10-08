package com.khedma.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.khedma.points.PointsDtos.TransactionResponse;
import com.khedma.student.Grade;

public final class DashboardDtos {

    private DashboardDtos() {}

    /**
     * One card per student. No contact details: the dashboard is visible to every approved account.
     * Rates are percentages (0-100) of the meetings held so far this season.
     */
    public record StudentCard(
            Long id,
            String name,
            Grade grade,
            Long groupId,
            String groupName,
            int points,
            int rank,
            int serviceCount,
            int massCount,
            int meetingsHeld,
            int serviceRate,
            int massRate) {}

    public record GroupStanding(Long id, String name, int points, int rank, long memberCount) {}

    public record LastMeeting(LocalDate date, int serviceCount, int massCount) {}

    public record Summary(
            int totalStudents,
            int totalGroups,
            int meetingsHeld,
            LastMeeting lastMeeting,
            int averageServiceRate,
            int averageMassRate,
            List<StudentCard> topStudents,
            List<GroupStanding> groups) {}

    public record MeetingRecord(LocalDate date, boolean service, boolean mass) {}

    public record StudentDetail(
            StudentCard card,
            List<MeetingRecord> attendance,
            List<TransactionResponse> recentTransactions) {}

    public enum SortBy { POINTS, NAME, ATTENDANCE }
}
