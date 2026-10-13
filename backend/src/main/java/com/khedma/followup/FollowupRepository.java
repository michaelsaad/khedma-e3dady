package com.khedma.followup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FollowupRepository extends JpaRepository<Followup, Long>, JpaSpecificationExecutor<Followup> {

    @Override
    @EntityGraph(attributePaths = {"student", "author", "resolvedBy"})
    Page<Followup> findAll(Specification<Followup> spec, Pageable pageable);
}
