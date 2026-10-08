package com.khedma.points.reason;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.points.reason.PointReasonService.ReasonRequest;
import com.khedma.points.reason.PointReasonService.ReasonResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/point-reasons")
public class PointReasonController {

    private final PointReasonService reasonService;

    public PointReasonController(PointReasonService reasonService) {
        this.reasonService = reasonService;
    }

    @GetMapping
    public List<ReasonResponse> list() {
        return reasonService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReasonResponse create(@Valid @RequestBody ReasonRequest request) {
        return reasonService.create(request);
    }

    @PutMapping("/{id}")
    public ReasonResponse update(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return reasonService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        reasonService.delete(id);
    }
}
