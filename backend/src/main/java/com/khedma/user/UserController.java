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
import com.khedma.user.UserDtos.LinkStudentRequest;
import com.khedma.user.UserDtos.UpdateUserRequest;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Available to any signed-in account, including PENDING ones (to show the waiting screen). */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return userService.me(user.id());
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

    @PutMapping("/users/{id}/student")
    public UserResponse linkStudent(@PathVariable Long id, @RequestBody LinkStudentRequest request) {
        return userService.linkStudent(id, request.studentId());
    }
}
