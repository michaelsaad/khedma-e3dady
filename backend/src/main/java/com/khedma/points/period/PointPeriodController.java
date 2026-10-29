package com.khedma.points.period;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthUser;
import com.khedma.points.period.PeriodDtos.CreatePeriodRequest;
import com.khedma.points.period.PeriodDtos.PeriodResponse;
import com.khedma.points.period.PeriodDtos.RenamePeriodRequest;

import jakarta.validation.Valid;

/** Listing is open to every approved account (for the period picker); changes are ADMIN (see SecurityConfig). */
@RestController
@RequestMapping("/api/point-periods")
public class PointPeriodController {

    private final PointPeriodService service;

    public PointPeriodController(PointPeriodService service) {
        this.service = service;
    }

    @GetMapping
    public List<PeriodResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodResponse create(@Valid @RequestBody CreatePeriodRequest request, @AuthenticationPrincipal AuthUser actor) {
        return service.create(request.name(), request.startsOn(), actor.id());
    }

    @PutMapping("/{id}")
    public PeriodResponse rename(@PathVariable Long id, @Valid @RequestBody RenamePeriodRequest request) {
        return service.rename(id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
