package com.khedma.points.reason;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Service
public class PointReasonService {

    public record ReasonRequest(
            @NotBlank @Size(max = 200) String label,
            @Min(1) @Max(10000) Integer defaultAmount) {}

    public record ReasonResponse(Long id, String label, Integer defaultAmount, int usageCount) {
        static ReasonResponse of(PointReason r) {
            return new ReasonResponse(r.getId(), r.getLabel(), r.getDefaultAmount(), r.getUsageCount());
        }
    }

    private final PointReasonRepository reasons;

    public PointReasonService(PointReasonRepository reasons) {
        this.reasons = reasons;
    }

    /** Most used first. */
    @Transactional(readOnly = true)
    public List<ReasonResponse> list() {
        return reasons.findAllByOrderByUsageCountDescLabelAsc().stream().map(ReasonResponse::of).toList();
    }

    @Transactional
    public ReasonResponse create(ReasonRequest request) {
        String label = request.label().trim();
        if (reasons.existsByLabel(label)) throw new ApiException(ErrorCode.REASON_ALREADY_EXISTS);
        PointReason reason = new PointReason();
        reason.setLabel(label);
        reason.setDefaultAmount(request.defaultAmount());
        return ReasonResponse.of(reasons.save(reason));
    }

    @Transactional
    public ReasonResponse update(Long id, ReasonRequest request) {
        PointReason reason = reasons.findById(id).orElseThrow(() -> new ApiException(ErrorCode.REASON_NOT_FOUND));
        String label = request.label().trim();
        if (reasons.existsByLabelAndIdNot(label, id)) throw new ApiException(ErrorCode.REASON_ALREADY_EXISTS);
        reason.setLabel(label);
        reason.setDefaultAmount(request.defaultAmount());
        return ReasonResponse.of(reason);
    }

    @Transactional
    public void delete(Long id) {
        if (!reasons.existsById(id)) throw new ApiException(ErrorCode.REASON_NOT_FOUND);
        reasons.deleteById(id);
    }
}
