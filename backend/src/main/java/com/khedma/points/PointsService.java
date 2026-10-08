package com.khedma.points;

import java.time.Clock;

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

    public PointsService(PointTransactionRepository transactions, StudentRepository students,
                         GroupRepository groups, UserRepository users, PointReasonRepository reasons, Clock clock) {
        this.transactions = transactions;
        this.students = students;
        this.groups = groups;
        this.users = users;
        this.reasons = reasons;
        this.clock = clock;
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
        student.setPointsBalance(student.getPointsBalance() + delta);
        tx.setBalanceAfter(student.getPointsBalance());
        reasons.incrementUsage(tx.getNotes());
        return TransactionResponse.of(transactions.save(tx));
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
        reasons.incrementUsage(tx.getNotes());
        return TransactionResponse.of(transactions.save(tx));
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
        var active = transactions.findActiveAttendanceAwards(attendance.getId(), kind);
        String note = kind.label() + " on " + attendance.getMeetingDate();
        int net = 0;

        if (attendance.isPresent(kind)) {
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
