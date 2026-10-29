package com.khedma.points.period;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.points.PointTransactionRepository;
import com.khedma.points.period.PeriodDtos.PeriodResponse;
import com.khedma.user.UserRepository;

/**
 * Point periods split the ledger by date so points can start again from zero (e.g. after a party)
 * while the all-time totals stay. A transaction belongs to the period that contains its effective date.
 */
@Service
public class PointPeriodService {

    /** A period as a half-open date range [from, toExclusive); toExclusive is null for the last period. */
    public record Range(Long id, String name, LocalDate from, LocalDate toExclusive) {}

    private final PointPeriodRepository periods;
    private final PointTransactionRepository transactions;
    private final UserRepository users;
    private final Clock clock;

    public PointPeriodService(PointPeriodRepository periods, PointTransactionRepository transactions,
                              UserRepository users, Clock clock) {
        this.periods = periods;
        this.transactions = transactions;
        this.users = users;
        this.clock = clock;
    }

    // ---- Reading -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PeriodResponse> list() {
        Range current = current();
        LocalDate today = LocalDate.now(clock);
        return ranges().stream()
                .map(r -> toResponse(r, current, today))
                .toList();
    }

    /** The period containing today (the newest one that has started). */
    @Transactional(readOnly = true)
    public Range current() {
        LocalDate today = LocalDate.now(clock);
        List<Range> all = ranges();
        Range current = all.get(0);
        for (Range range : all) {
            if (!range.from().isAfter(today)) current = range;
        }
        return current;
    }

    /** The requested period, or the current one when {@code periodId} is null. */
    @Transactional(readOnly = true)
    public Range range(Long periodId) {
        if (periodId == null) return current();
        return ranges().stream()
                .filter(r -> r.id().equals(periodId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PERIOD_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PeriodResponse describe(Range range) {
        return toResponse(range, current(), LocalDate.now(clock));
    }

    /** Points each student earned in the period (students with none are absent from the map). */
    @Transactional(readOnly = true)
    public Map<Long, Integer> studentPoints(Range range) {
        return toMap(range.toExclusive() == null
                ? transactions.sumByStudentFrom(range.from())
                : transactions.sumByStudentBetween(range.from(), range.toExclusive()));
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> groupPoints(Range range) {
        return toMap(range.toExclusive() == null
                ? transactions.sumByGroupFrom(range.from())
                : transactions.sumByGroupBetween(range.from(), range.toExclusive()));
    }

    @Transactional(readOnly = true)
    public int studentPoints(Long studentId, Range range) {
        Long sum = range.toExclusive() == null
                ? transactions.sumForStudentFrom(studentId, range.from())
                : transactions.sumForStudentBetween(studentId, range.from(), range.toExclusive());
        return sum == null ? 0 : sum.intValue();
    }

    @Transactional(readOnly = true)
    public int groupPoints(Long groupId, Range range) {
        Long sum = range.toExclusive() == null
                ? transactions.sumForGroupFrom(groupId, range.from())
                : transactions.sumForGroupBetween(groupId, range.from(), range.toExclusive());
        return sum == null ? 0 : sum.intValue();
    }

    // ---- Changing --------------------------------------------------------------------------

    /** Starts a new period; it must begin after the latest one (a future date schedules it). */
    @Transactional
    public PeriodResponse create(String name, LocalDate startsOn, Long actorId) {
        List<PointPeriod> all = periods.findAllByOrderByStartsOnAsc();
        if (!all.isEmpty() && !startsOn.isAfter(all.get(all.size() - 1).getStartsOn())) {
            throw new ApiException(ErrorCode.PERIOD_START_INVALID);
        }
        PointPeriod period = new PointPeriod();
        period.setName(name.trim());
        period.setStartsOn(startsOn);
        period.setCreatedBy(actorId == null ? null : users.getReferenceById(actorId));
        period.setCreatedAt(clock.instant());
        periods.save(period);
        return describe(range(period.getId()));
    }

    @Transactional
    public PeriodResponse rename(Long id, String name) {
        PointPeriod period = periods.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PERIOD_NOT_FOUND));
        period.setName(name.trim());
        return describe(range(id));
    }

    /**
     * Removes the latest period (e.g. started by mistake). Its points simply fall back into the
     * period before it; nothing in the ledger changes.
     */
    @Transactional
    public void delete(Long id) {
        List<PointPeriod> all = periods.findAllByOrderByStartsOnAsc();
        PointPeriod period = all.stream().filter(p -> p.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PERIOD_NOT_FOUND));
        if (all.size() == 1) throw new ApiException(ErrorCode.PERIOD_LAST_ONE);
        if (!all.get(all.size() - 1).getId().equals(id)) throw new ApiException(ErrorCode.PERIOD_NOT_LATEST);
        periods.delete(period);
    }

    // ---- internals -------------------------------------------------------------------------

    private List<Range> ranges() {
        List<PointPeriod> all = periods.findAllByOrderByStartsOnAsc();
        if (all.isEmpty()) throw new IllegalStateException("No point period configured");
        List<Range> out = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            PointPeriod p = all.get(i);
            LocalDate next = i + 1 < all.size() ? all.get(i + 1).getStartsOn() : null;
            out.add(new Range(p.getId(), p.getName(), p.getStartsOn(), next));
        }
        return out;
    }

    private static PeriodResponse toResponse(Range range, Range current, LocalDate today) {
        return new PeriodResponse(range.id(), range.name(), range.from(),
                range.toExclusive() == null ? null : range.toExclusive().minusDays(1),
                range.id().equals(current.id()), range.from().isAfter(today));
    }

    private static Map<Long, Integer> toMap(List<Object[]> rows) {
        Map<Long, Integer> out = new HashMap<>();
        for (Object[] row : rows) out.put((Long) row[0], ((Number) row[1]).intValue());
        return out;
    }
}
