package com.khedma.group;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.group.GroupDtos.GroupRequest;
import com.khedma.group.GroupDtos.GroupResponse;
import com.khedma.student.StudentRepository;

@Service
public class GroupService {

    private final GroupRepository groups;
    private final StudentRepository students;

    public GroupService(GroupRepository groups, StudentRepository students) {
        this.groups = groups;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list() {
        return groups.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public GroupResponse create(GroupRequest request) {
        String name = request.name().trim();
        if (groups.existsByNameIgnoreCase(name)) throw ApiException.conflict("اسم الجروب موجود بالفعل");
        StudentGroup group = new StudentGroup();
        group.setName(name);
        return toResponse(groups.save(group));
    }

    @Transactional
    public GroupResponse rename(Long id, GroupRequest request) {
        StudentGroup group = groups.findById(id).orElseThrow(() -> ApiException.notFound("الجروب غير موجود"));
        String name = request.name().trim();
        if (groups.existsByNameIgnoreCaseAndIdNot(name, id)) throw ApiException.conflict("اسم الجروب موجود بالفعل");
        group.setName(name);
        return toResponse(group);
    }

    private GroupResponse toResponse(StudentGroup group) {
        return new GroupResponse(group.getId(), group.getName(), group.getPointsBalance(),
                students.countByGroupIdAndActiveTrue(group.getId()));
    }
}
