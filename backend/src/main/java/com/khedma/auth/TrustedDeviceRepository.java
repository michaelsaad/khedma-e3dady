package com.khedma.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    Optional<TrustedDevice> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from TrustedDevice d where d.userId = :userId")
    void deleteAllByUserId(Long userId);
}
