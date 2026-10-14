package com.khedma.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByGoogleSubject(String googleSubject);

    Optional<AppUser> findByStudentId(Long studentId);

    List<AppUser> findAllByOrderByStatusAscDisplayNameAsc();

    /** Approved accounts holding one of these roles. */
    List<AppUser> findAllByStatusAndRoleIn(UserStatus status, Collection<Role> roles);
}
