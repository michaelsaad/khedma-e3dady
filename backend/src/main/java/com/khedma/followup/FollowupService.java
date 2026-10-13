package com.khedma.followup;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.auth.AuthUser;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.common.PageResponse;
import com.khedma.followup.FollowupDtos.FollowupRequest;
import com.khedma.followup.FollowupDtos.FollowupResponse;
import com.khedma.followup.FollowupDtos.TypeRequest;
import com.khedma.followup.FollowupDtos.TypeResponse;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * Servants write follow-up reports and read their own; admins read everyone's and mark them handled.
 * Reports are never reachable from a MAKHDOUM account (see SecurityConfig).
 */
@Service
public class FollowupService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final FollowupRepository followups;
    private final FollowupTypeRepository types;
    private final StudentRepository students;
    private final UserRepository users;
    private final Clock clock;

    public FollowupService(FollowupRepository followups, FollowupTypeRepository types, StudentRepository students,
                           UserRepository users, Clock clock) {
        this.followups = followups;
        this.types = types;
        this.students = students;
        this.users = users;
        this.clock = clock;
    }

    // ---- Reports ------------------------------------------------------------------------

    @Transactional
    public FollowupResponse create(FollowupRequest request, AuthUser actor) {
        Student student = students.findByIdAndActiveTrue(request.studentId())
                .orElseThrow(() -> new ApiException(ErrorCode.STUDENT_NOT_FOUND));
        FollowupType type = types.findById(request.typeId())
                .filter(FollowupType::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.FOLLOWUP_TYPE_NOT_FOUND));

        Followup followup = new Followup();
        followup.setStudent(student);
        followup.setType(type);
        followup.setTypeLabel(type.getLabel());
        followup.setNote(request.note() == null ? "" : request.note().trim());
        followup.setAuthor(users.getReferenceById(actor.id()));
        followup.setCreatedAt(clock.instant());
        return FollowupResponse.of(followups.save(followup));
    }

    /** Servants only ever see their own reports; admins see all. */
    @Transactional(readOnly = true)
    public PageResponse<FollowupResponse> search(Long studentId, FollowupStatus status, AuthUser actor, int page, int size) {
        Specification<Followup> spec = (root, cq, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (studentId != null) where.add(cb.equal(root.get("student").get("id"), studentId));
            if (status != null) where.add(cb.equal(root.get("status"), status));
            if (!isAdmin(actor)) where.add(cb.equal(root.get("author").get("id"), actor.id()));
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), NEWEST_FIRST);
        return PageResponse.of(followups.findAll(spec, pageable), FollowupResponse::of);
    }

    @Transactional
    public FollowupResponse resolve(Long id, String note, AuthUser actor) {
        Followup followup = require(id);
        followup.setStatus(FollowupStatus.DONE);
        followup.setResolvedBy(users.getReferenceById(actor.id()));
        followup.setResolvedNote(note == null ? "" : note.trim());
        followup.setResolvedAt(clock.instant());
        return FollowupResponse.of(followup);
    }

    @Transactional
    public FollowupResponse reopen(Long id) {
        Followup followup = require(id);
        followup.setStatus(FollowupStatus.OPEN);
        followup.setResolvedBy(null);
        followup.setResolvedNote("");
        followup.setResolvedAt(null);
        return FollowupResponse.of(followup);
    }

    @Transactional
    public void delete(Long id) {
        followups.delete(require(id));
    }

    // ---- Types (the editable list) --------------------------------------------------------

    /** Servants pick from active types only; admins also see the inactive ones. */
    @Transactional(readOnly = true)
    public List<TypeResponse> types(AuthUser actor) {
        var list = isAdmin(actor) ? types.findAllByOrderBySortOrderAscIdAsc() : types.findAllByActiveTrueOrderBySortOrderAscIdAsc();
        return list.stream().map(TypeResponse::of).toList();
    }

    @Transactional
    public TypeResponse createType(TypeRequest request) {
        String label = request.label().trim();
        if (types.existsByLabel(label)) throw new ApiException(ErrorCode.FOLLOWUP_TYPE_ALREADY_EXISTS);
        FollowupType type = new FollowupType();
        type.setLabel(label);
        type.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        type.setActive(request.active() == null || request.active());
        return TypeResponse.of(types.save(type));
    }

    @Transactional
    public TypeResponse updateType(Long id, TypeRequest request) {
        FollowupType type = types.findById(id).orElseThrow(() -> new ApiException(ErrorCode.FOLLOWUP_TYPE_NOT_FOUND));
        String label = request.label().trim();
        if (types.existsByLabelAndIdNot(label, id)) throw new ApiException(ErrorCode.FOLLOWUP_TYPE_ALREADY_EXISTS);
        type.setLabel(label);
        if (request.sortOrder() != null) type.setSortOrder(request.sortOrder());
        if (request.active() != null) type.setActive(request.active());
        return TypeResponse.of(type);
    }

    /** Old reports keep their own copy of the label, so removing a type never rewrites history. */
    @Transactional
    public void deleteType(Long id) {
        if (!types.existsById(id)) throw new ApiException(ErrorCode.FOLLOWUP_TYPE_NOT_FOUND);
        types.deleteById(id);
    }

    private Followup require(Long id) {
        return followups.findById(id).orElseThrow(() -> new ApiException(ErrorCode.FOLLOWUP_NOT_FOUND));
    }

    private static boolean isAdmin(AuthUser actor) {
        return actor.role() == Role.ADMIN || actor.role() == Role.SUPER_ADMIN;
    }
}
