package com.khedma.points;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthUser;
import com.khedma.common.PageResponse;
import com.khedma.points.PointsDtos.ManualPointsRequest;
import com.khedma.points.PointsDtos.TransactionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PointsController {

    private final PointsService pointsService;

    public PointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @PostMapping("/students/{id}/points")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse studentPoints(@PathVariable Long id, @Valid @RequestBody ManualPointsRequest request,
                                             @AuthenticationPrincipal AuthUser actor) {
        return pointsService.manualForStudent(id, request, actor.id());
    }

    @PostMapping("/groups/{id}/points")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse groupPoints(@PathVariable Long id, @Valid @RequestBody ManualPointsRequest request,
                                           @AuthenticationPrincipal AuthUser actor) {
        return pointsService.manualForGroup(id, request, actor.id());
    }

    @GetMapping("/students/{id}/transactions")
    public PageResponse<TransactionResponse> studentTransactions(@PathVariable Long id,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "50") int size) {
        return pointsService.forStudent(id, page, size);
    }

    @GetMapping("/groups/{id}/transactions")
    public PageResponse<TransactionResponse> groupTransactions(@PathVariable Long id,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "50") int size) {
        return pointsService.forGroup(id, page, size);
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> recent(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return pointsService.recent(page, size);
    }
}
