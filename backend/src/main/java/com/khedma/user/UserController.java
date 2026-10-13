package com.khedma.user;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthUser;
import com.khedma.dashboard.DashboardDtos.StudentDetail;
import com.khedma.dashboard.DashboardService;
import com.khedma.user.UserDtos.LinkStudentRequest;
import com.khedma.user.UserDtos.StudentSuggestion;
import com.khedma.user.UserDtos.UpdateUserRequest;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final DashboardService dashboardService;

    public UserController(UserService userService, DashboardService dashboardService) {
        this.userService = userService;
        this.dashboardService = dashboardService;
    }

    /** Available to any signed-in account, including PENDING ones (to show the waiting screen). */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return userService.me(user.id());
    }

    /**
     * The account's own student page (points, attendance, history). The id always comes from the account's
     * link, never from the request, so a MAKHDOUM can't read anyone else.
     */
    @GetMapping("/me/student")
    public StudentDetail myStudent(@AuthenticationPrincipal AuthUser user) {
        return dashboardService.student(userService.linkedStudentId(user.id()));
    }

    // ---- SUPER_ADMIN only (see SecurityConfig) ----

    @GetMapping("/users")
    public List<UserResponse> list() {
        return userService.list();
    }

    @PatchMapping("/users/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal AuthUser actor) {
        return userService.update(id, request, actor.id());
    }

    /** Likely students for an account that signed up, to link it to its existing record. */
    @GetMapping("/users/{id}/student-suggestions")
    public List<StudentSuggestion> suggestions(@PathVariable Long id) {
        return userService.suggestStudents(id);
    }

    @PutMapping("/users/{id}/student")
    public UserResponse linkStudent(@PathVariable Long id, @RequestBody LinkStudentRequest request) {
        return userService.linkStudent(id, request.studentId());
    }
}
