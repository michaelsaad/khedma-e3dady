package com.khedma.points.reason;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PointReasonRepository extends JpaRepository<PointReason, Long> {

    List<PointReason> findAllByOrderByUsageCountDescLabelAsc();

    boolean existsByLabel(String label);

    boolean existsByLabelAndIdNot(String label, Long id);

    @Modifying
    @Query("update PointReason r set r.usageCount = r.usageCount + 1 where r.label = :label")
    int incrementUsage(String label);
}
