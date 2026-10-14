package com.khedma.points;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.attendance.Attendance;
import com.khedma.attendance.AttendanceKind;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.common.PageResponse;
import com.khedma.group.GroupRepository;
import com.khedma.group.StudentGroup;
import com.khedma.points.PointsDtos.ManualKind;
import com.khedma.points.PointsDtos.ManualPointsRequest;
import com.khedma.points.PointsDtos.TransactionResponse;
import com.khedma.points.reason.PointReasonRepository;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.UserRepository;

/**
 * The only place balances change. Every change writes one immutable ledger row and
 * updates the cached balance on the student/group, both under a row lock.
 * Groups only get manual transactions; attendance points go to students.
 */
@Service
public class PointsService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

    private final PointTransactionRepository transactions;
    private final StudentRepository students;
    private final GroupRepository groups;
    private final UserRepository users;
    private final PointReasonRepository reasons;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public PointsService(PointTransactionRepository transactions, StudentRepository students,
                         GroupRepository groups, UserRepository users, PointReasonRepository reasons, Clock clock,
                         ApplicationEventPublisher events) {
        this.transactions = transactions;
        this.students = students;
        this.groups = groups;
        this.users = users;
        this.reasons = reasons;
        this.clock = clock;
        this.events = events;
    }

    // ---- Manual transactions ------------------------------------------------------------

    @Transactional
    public TransactionResponse manualForStudent(Long studentId, ManualPointsRequest request, Long actorId) {
        Student student = students.findByIdForUpdate(studentId)
                .filter(Student::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.STUDENT_NOT_FOUND));
        int delta = manualDelta(request, student.getPointsBalance());
        PointTransaction tx = newTransaction(manualType(request), delta, request.notes().trim(), actorId);
        tx.setStudent(student);
        tx.setMeetingDate(request.meetingDate());
        student.setPointsBalance(student.getPointsBalance() + delta);
        tx.setBalanceAfter(student.getPointsBalance());
        reasons.incrementUsage(tx.getNotes());
        TransactionResponse saved = TransactionResponse.of(transactions.save(tx));
        if (request.kind() == ManualKind.ADD) {
            events.publishEvent(new PointsGiven(student.getId(), delta, tx.getNotes(), student.getPointsBalance()));
        }
        return saved;
    }

    @Transactional
    public TransactionResponse manualForGroup(Long groupId, ManualPointsRequest request, Long actorId) {
        StudentGroup group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.GROUP_NOT_FOUND));
        int delta = manualDelta(request, group.getPointsBalance());
        PointTransaction tx = newTransaction(manualType(request), delta, request.notes().trim(), actorId);
        tx.setGroup(group);
        group.setPointsBalance(group.getPointsBalance() + delta);
        tx.setBalanceAfter(group.getPointsBalance());
        // Saved reasons are for student points only, so group notes don't affect their ranking.
        return TransactionResponse.of(transactions.save(tx));
    }

    /**
     * Undoes one meeting bonus (given from the attendance page) with a linked deduction of the same
     * amount and reason. Like attendance reversals it may take the balance below zero.
     */
    @Transactional
    public TransactionResponse undoMeetingBonus(Long studentId, Long transactionId, Long actorId) {
        Student student = students.findByIdForUpdate(studentId)
                .orElseThrow(() -> new ApiException(ErrorCode.STUDENT_NOT_FOUND));
        PointTransaction bonus = transactions.findById(transactionId)
                .filter(t -> t.getStudent() != null && t.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        if (bonus.getType() != TransactionType.MANUAL_ADD || bonus.getMeetingDate() == null
                || transactions.existsByReversesId(bonus.getId())) {
            throw new ApiException(ErrorCode.TRANSACTION_NOT_UNDOABLE);
        }
        PointTransaction undo = newTransaction(TransactionType.MANUAL_DEDUCT, -bonus.getAmount(), bonus.getNotes(), actorId);
        undo.setStudent(student);
        undo.setReverses(bonus);
        undo.setMeetingDate(bonus.getMeetingDate());
        student.setPointsBalance(student.getPointsBalance() + undo.getAmount());
        undo.setBalanceAfter(student.getPointsBalance());
        return TransactionResponse.of(transactions.save(undo));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> meetingBonuses(Long studentId, LocalDate date) {
        if (!students.existsById(studentId)) throw new ApiException(ErrorCode.STUDENT_NOT_FOUND);
        return transactions.findActiveMeetingBonuses(studentId, date).stream().map(TransactionResponse::of).toList();
    }

    private static int manualDelta(ManualPointsRequest request, int balance) {
        return switch (request.kind()) {
            case ADD -> requireAmount(request);
            case DEDUCT -> {
                int amount = requireAmount(request);
                if (amount > balance) throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
                yield -amount;
            }
            case RESET -> {
                if (balance == 0) throw new ApiException(ErrorCode.BALANCE_ALREADY_ZERO);
                yield -balance;
            }
        };
    }

    private static int requireAmount(ManualPointsRequest request) {
        if (request.amount() == null) throw new ApiException(ErrorCode.AMOUNT_REQUIRED);
        return request.amount();
    }

    private static TransactionType manualType(ManualPointsRequest request) {
        return switch (request.kind()) {
            case ADD -> TransactionType.MANUAL_ADD;
            case DEDUCT -> TransactionType.MANUAL_DEDUCT;
            case RESET -> TransactionType.RESET;
        };
    }

    // ---- Automatic attendance distribution ------------------------------------------------

    /**
     * Makes the ledger match the attendance flag for one kind (service or mass):
     * marked and not yet awarded -> award {@code points}; un-marked -> reverse every active award.
     * Idempotent, so saving the same attendance twice changes nothing.
     * The caller must hold the student's row lock (see {@link StudentRepository#findByIdForUpdate}).
     *
     * @return the net change applied to the student's balance
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int syncAttendancePoints(Student lockedStudent, Attendance attendance, AttendanceKind kind,
                                    int points, Long actorId) {
        return syncAttendancePoints(lockedStudent, attendance, kind, points, actorId, false);
    }

    /**
     * Same as above; with {@code meetingSkipped} every active award is reversed regardless of the
     * attendance flags, because a meeting that didn't happen earns no points.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int syncAttendancePoints(Student lockedStudent, Attendance attendance, AttendanceKind kind,
                                    int points, Long actorId, boolean meetingSkipped) {
        var active = transactions.findActiveAttendanceAwards(attendance.getId(), kind);
        String note = kind.label() + " on " + attendance.getMeetingDate() + (meetingSkipped ? " (meeting skipped)" : "");
        int net = 0;

        if (attendance.isPresent(kind) && !meetingSkipped) {
            if (active.isEmpty() && points > 0) {
                PointTransaction award = newTransaction(TransactionType.ATTENDANCE_AWARD, points,
                        note, actorId);
                net += applyToStudent(lockedStudent, award, attendance, kind);
            }
        } else {
            for (PointTransaction award : active) {
                // Reverse exactly what was awarded, even if the setting changed since.
                PointTransaction reversal = newTransaction(TransactionType.ATTENDANCE_REVERSAL, -award.getAmount(),
                        "Reversal: " + note, actorId);
                reversal.setReverses(award);
                net += applyToStudent(lockedStudent, reversal, attendance, kind);
            }
        }
        return net;
    }

    private int applyToStudent(Student student, PointTransaction tx, Attendance attendance, AttendanceKind kind) {
        tx.setStudent(student);
        tx.setAttendance(attendance);
        tx.setAttendanceKind(kind);
        // Reversals may take the balance below zero if awarded points were already spent;
        // that is intentional so the ledger always stays correct.
        student.setPointsBalance(student.getPointsBalance() + tx.getAmount());
        tx.setBalanceAfter(student.getPointsBalance());
        transactions.save(tx);
        return tx.getAmount();
    }

    // ---- Queries ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> forStudent(Long studentId, int page, int size) {
        if (!students.existsById(studentId)) throw new ApiException(ErrorCode.STUDENT_NOT_FOUND);
        return PageResponse.of(transactions.findAllByStudentId(studentId, pageOf(page, size)), TransactionResponse::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> forGroup(Long groupId, int page, int size) {
        if (!groups.existsById(groupId)) throw new ApiException(ErrorCode.GROUP_NOT_FOUND);
        return PageResponse.of(transactions.findAllByGroupId(groupId, pageOf(page, size)), TransactionResponse::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> recent(int page, int size) {
        return PageResponse.of(transactions.findAllBy(pageOf(page, size)), TransactionResponse::of);
    }

    private static PageRequest pageOf(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), NEWEST_FIRST);
    }

    private PointTransaction newTransaction(TransactionType type, int amount, String notes, Long actorId) {
        PointTransaction tx = new PointTransaction();
        tx.setType(type);
        tx.setAmount(amount);
        tx.setNotes(notes);
        tx.setCreatedBy(actorId == null ? null : users.getReferenceById(actorId));
        tx.setCreatedAt(clock.instant());
        return tx;
    }
}
