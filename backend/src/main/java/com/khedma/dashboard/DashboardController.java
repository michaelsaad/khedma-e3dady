package com.khedma.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.common.PageResponse;
import com.khedma.dashboard.DashboardDtos.SortBy;
import com.khedma.dashboard.DashboardDtos.StudentCard;
import com.khedma.dashboard.DashboardDtos.StudentDetail;
import com.khedma.dashboard.DashboardDtos.Summary;
import com.khedma.student.Grade;

/** Visible to every approved account (see SecurityConfig). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public Summary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/students")
    public PageResponse<StudentCard> cards(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Grade grade,
                                           @RequestParam(required = false) Long groupId,
                                           @RequestParam(defaultValue = "POINTS") SortBy sort,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "24") int size) {
        return dashboardService.cards(q, grade, groupId, sort, page, size);
    }

    @GetMapping("/students/{id}")
    public StudentDetail student(@PathVariable Long id) {
        return dashboardService.student(id);
    }
}
