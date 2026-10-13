package com.khedma.student;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.common.PageResponse;
import com.khedma.group.GroupRepository;
import com.khedma.group.StudentGroup;
import com.khedma.student.StudentDtos.StudentRequest;
import com.khedma.student.StudentDtos.StudentResponse;
import com.khedma.user.UserRepository;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
public class StudentService {

    private static final Sort DEFAULT_SORT = Sort.by("grade", "name");

    private final StudentRepository students;
    private final GroupRepository groups;
    private final UserRepository users;

    public StudentService(StudentRepository students, GroupRepository groups, UserRepository users) {
        this.students = students;
        this.groups = groups;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> search(String query, Grade grade, Long groupId, int page, int size) {
        Specification<Student> spec = (root, cq, cb) -> {
            if (cq.getResultType() != Long.class && cq.getResultType() != long.class) {
                root.fetch("group", JoinType.LEFT);
            }
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("active")));
            if (grade != null) where.add(cb.equal(root.get("grade"), grade));
            if (groupId != null) where.add(cb.equal(root.get("group").get("id"), groupId));
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(root.get("phone"), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), DEFAULT_SORT);
        return PageResponse.of(students.findAll(spec, pageable), StudentResponse::of);
    }

    /** Creating a student that was removed before (same name + grade) restores them with their history. */
    @Transactional
    public StudentResponse create(StudentRequest request) {
        String name = request.name().trim();
        StudentGroup group = resolveGroup(request.groupId());
        Student student = students.findByNameAndGrade(name, request.grade())
                .map(existing -> {
                    if (existing.isActive()) throw new ApiException(ErrorCode.STUDENT_ALREADY_EXISTS);
                    existing.setActive(true);
                    return existing;
                })
                .orElseGet(Student::new);
        student.setName(name);
        student.setGrade(request.grade());
        student.setPhone(normalizePhone(request.phone()));
        student.setGroup(group);
        return StudentResponse.of(students.save(student));
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = requireActive(id);
        String name = request.name().trim();
        students.findByNameAndGrade(name, request.grade())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new ApiException(ErrorCode.STUDENT_ALREADY_EXISTS); });
        student.setName(name);
        student.setGrade(request.grade());
        student.setPhone(normalizePhone(request.phone()));
        student.setGroup(resolveGroup(request.groupId()));
        return StudentResponse.of(student);
    }

    /** Soft delete: keeps attendance and points history. */
    @Transactional
    public void remove(Long id) {
        Student student = requireActive(id);
        student.setActive(false);
        student.setGroup(null);
        // A removed student can't be the "own page" of an account any more.
        users.findByStudentId(id).ifPresent(account -> account.setStudent(null));
    }

    private Student requireActive(Long id) {
        return students.findByIdAndActiveTrue(id).orElseThrow(() -> new ApiException(ErrorCode.STUDENT_NOT_FOUND));
    }

    private StudentGroup resolveGroup(Long groupId) {
        if (groupId == null) return null;
        return groups.findById(groupId).orElseThrow(() -> new ApiException(ErrorCode.GROUP_NOT_FOUND));
    }

    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[\\s-]", "");
    }
}
