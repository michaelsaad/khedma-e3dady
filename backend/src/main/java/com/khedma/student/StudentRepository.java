package com.khedma.student;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    @EntityGraph(attributePaths = "group")
    List<Student> findAllByActiveTrue();

    Optional<Student> findByNameAndGrade(String name, Grade grade);

    @EntityGraph(attributePaths = "group")
    Optional<Student> findByIdAndActiveTrue(Long id);

    long countByGroupIdAndActiveTrue(Long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(Long id);
}
