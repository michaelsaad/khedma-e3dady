package com.khedma.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface GroupRepository extends JpaRepository<StudentGroup, Long> {

    List<StudentGroup> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from StudentGroup g where g.id = :id")
    Optional<StudentGroup> findByIdForUpdate(Long id);
}
