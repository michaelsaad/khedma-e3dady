package com.khedma.followup;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthUser;
import com.khedma.common.PageResponse;
import com.khedma.followup.FollowupDtos.FollowupRequest;
import com.khedma.followup.FollowupDtos.FollowupResponse;
import com.khedma.followup.FollowupDtos.ResolveRequest;
import com.khedma.followup.FollowupDtos.TypeRequest;
import com.khedma.followup.FollowupDtos.TypeResponse;

import jakarta.validation.Valid;

/** Route access is set in SecurityConfig: writing and listing needs SERVANT; handling reports and editing types needs ADMIN. */
@RestController
@RequestMapping("/api")
public class FollowupController {

    private final FollowupService service;

    public FollowupController(FollowupService service) {
        this.service = service;
    }

    // ---- Reports ----

    @PostMapping("/followups")
    @ResponseStatus(HttpStatus.CREATED)
    public FollowupResponse create(@Valid @RequestBody FollowupRequest request, @AuthenticationPrincipal AuthUser actor) {
        return service.create(request, actor);
    }

    @GetMapping("/followups")
    public PageResponse<FollowupResponse> search(@RequestParam(required = false) Long studentId,
                                                 @RequestParam(required = false) FollowupStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "30") int size,
                                                 @AuthenticationPrincipal AuthUser actor) {
        return service.search(studentId, status, actor, page, size);
    }

    @PatchMapping("/followups/{id}/resolve")
    public FollowupResponse resolve(@PathVariable Long id, @Valid @RequestBody(required = false) ResolveRequest request,
                                    @AuthenticationPrincipal AuthUser actor) {
        return service.resolve(id, request == null ? null : request.note(), actor);
    }

    @PatchMapping("/followups/{id}/reopen")
    public FollowupResponse reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    @DeleteMapping("/followups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ---- Types ----

    @GetMapping("/followup-types")
    public List<TypeResponse> types(@AuthenticationPrincipal AuthUser actor) {
        return service.types(actor);
    }

    @PostMapping("/followup-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TypeResponse createType(@Valid @RequestBody TypeRequest request) {
        return service.createType(request);
    }

    @PutMapping("/followup-types/{id}")
    public TypeResponse updateType(@PathVariable Long id, @Valid @RequestBody TypeRequest request) {
        return service.updateType(id, request);
    }

    @DeleteMapping("/followup-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteType(@PathVariable Long id) {
        service.deleteType(id);
    }
}
