package com.khedma.user;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.config.AppProperties;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.UserDtos.UpdateUserRequest;

@Service
public class UserService {

    private final UserRepository users;
    private final StudentRepository students;
    private final AppProperties properties;

    public UserService(UserRepository users, StudentRepository students, AppProperties properties) {
        this.users = users;
        this.students = students;
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
            throw new ApiException(ErrorCode.CANNOT_CHANGE_OWN_ACCESS);
        }
        if (changesAccess && properties.normalizedSuperAdminEmails().contains(target.getEmail())) {
            throw new ApiException(ErrorCode.CANNOT_CHANGE_OWNER_ACCESS);
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
                    .orElseThrow(() -> new ApiException(ErrorCode.STUDENT_NOT_FOUND));
            users.findByStudentId(studentId)
                    .filter(other -> !other.getId().equals(targetId))
                    .ifPresent(other -> { throw new ApiException(ErrorCode.STUDENT_ALREADY_LINKED); });
            target.setStudent(student);
        }
        return UserResponse.of(target);
    }

    private AppUser require(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
