package com.khedma.group;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.group.GroupDtos.GroupRequest;
import com.khedma.group.GroupDtos.GroupResponse;
import com.khedma.points.period.PointPeriodService;
import com.khedma.student.StudentRepository;

@Service
public class GroupService {

    private final GroupRepository groups;
    private final StudentRepository students;
    private final PointPeriodService periods;

    public GroupService(GroupRepository groups, StudentRepository students, PointPeriodService periods) {
        this.groups = groups;
        this.students = students;
        this.periods = periods;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list() {
        return groups.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public GroupResponse create(GroupRequest request) {
        String name = request.name().trim();
        if (groups.existsByNameIgnoreCase(name)) throw new ApiException(ErrorCode.GROUP_NAME_TAKEN);
        StudentGroup group = new StudentGroup();
        group.setName(name);
        return toResponse(groups.save(group));
    }

    @Transactional
    public GroupResponse rename(Long id, GroupRequest request) {
        StudentGroup group = groups.findById(id).orElseThrow(() -> new ApiException(ErrorCode.GROUP_NOT_FOUND));
        String name = request.name().trim();
        if (groups.existsByNameIgnoreCaseAndIdNot(name, id)) throw new ApiException(ErrorCode.GROUP_NAME_TAKEN);
        group.setName(name);
        return toResponse(group);
    }

    private GroupResponse toResponse(StudentGroup group) {
        int periodPoints = group.getId() == null ? 0 : periods.groupPoints(group.getId(), periods.current());
        return new GroupResponse(group.getId(), group.getName(), group.getPointsBalance(), periodPoints,
                students.countByGroupIdAndActiveTrue(group.getId()));
    }
}
