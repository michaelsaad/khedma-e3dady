package com.khedma.user;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.attendance.AttendanceRepository;
import com.khedma.common.ApiException;
import com.khedma.common.PageResponse;
import com.khedma.config.AppProperties;
import com.khedma.points.PointsDtos.TransactionResponse;
import com.khedma.points.PointsService;
import com.khedma.student.Student;
import com.khedma.student.StudentDtos.StudentResponse;
import com.khedma.student.StudentRepository;
import com.khedma.user.UserDtos.AttendanceDay;
import com.khedma.user.UserDtos.MyStudentResponse;
import com.khedma.user.UserDtos.UpdateUserRequest;

@Service
public class UserService {

    private final UserRepository users;
    private final StudentRepository students;
    private final AttendanceRepository attendance;
    private final PointsService pointsService;
    private final AppProperties properties;

    public UserService(UserRepository users, StudentRepository students, AttendanceRepository attendance,
                       PointsService pointsService, AppProperties properties) {
        this.users = users;
        this.students = students;
        this.attendance = attendance;
        this.pointsService = pointsService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.of(require(userId));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByStatusAscDisplayNameAsc().stream().map(UserResponse::of).toList();
    }

    @Transactional
    public UserResponse update(Long targetId, UpdateUserRequest request, Long actorId) {
        AppUser target = require(targetId);
        boolean changesAccess = (request.role() != null && request.role() != target.getRole())
                || (request.status() != null && request.status() != target.getStatus());
        if (changesAccess && Objects.equals(targetId, actorId)) {
            throw ApiException.badRequest("لا يمكنك تغيير صلاحية حسابك الحالي");
        }
        if (changesAccess && properties.normalizedSuperAdminEmails().contains(target.getEmail())) {
            throw ApiException.badRequest("لا يمكن تغيير صلاحية حساب المالك");
        }
        if (request.role() != null) target.setRole(request.role());
        if (request.status() != null) target.setStatus(request.status());
        return UserResponse.of(target);
    }

    @Transactional
    public UserResponse linkStudent(Long targetId, Long studentId) {
        AppUser target = require(targetId);
        if (studentId == null) {
            target.setStudent(null);
        } else {
            Student student = students.findByIdAndActiveTrue(studentId)
                    .orElseThrow(() -> ApiException.notFound("المخدوم غير موجود"));
            users.findByStudentId(studentId)
                    .filter(other -> !other.getId().equals(targetId))
                    .ifPresent(other -> { throw ApiException.conflict("المخدوم مربوط بحساب آخر"); });
            target.setStudent(student);
        }
        return UserResponse.of(target);
    }

    @Transactional(readOnly = true)
    public MyStudentResponse myStudent(Long userId) {
        Student student = linkedStudent(userId);
        List<AttendanceDay> days = attendance.findAllByStudentIdOrderByMeetingDateDesc(student.getId()).stream()
                .map(a -> new AttendanceDay(a.getMeetingDate(), a.isService(), a.isMass()))
                .toList();
        return new MyStudentResponse(StudentResponse.of(student), days);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> myTransactions(Long userId, int page, int size) {
        return pointsService.forStudent(linkedStudent(userId).getId(), page, size);
    }

    private Student linkedStudent(Long userId) {
        Student student = require(userId).getStudent();
        if (student == null || !student.isActive()) {
            throw ApiException.notFound("حسابك غير مربوط بمخدوم بعد");
        }
        return student;
    }

    private AppUser require(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("المستخدم غير موجود"));
    }
}
