package com.khedma.followup;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowupTypeRepository extends JpaRepository<FollowupType, Long> {

    List<FollowupType> findAllByOrderBySortOrderAscIdAsc();

    List<FollowupType> findAllByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByLabel(String label);

    boolean existsByLabelAndIdNot(String label, Long id);
}
