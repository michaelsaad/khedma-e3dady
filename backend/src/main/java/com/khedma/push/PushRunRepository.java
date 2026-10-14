package com.khedma.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Marker rows that make scheduled notifications run at most once per key. */
public interface PushRunRepository extends JpaRepository<PushRun, String> {

    /** @return true when this call claimed the key, false when it was already taken */
    default boolean claim(String runKey) {
        return insertIfAbsent(runKey) == 1;
    }

    @Transactional
    @Modifying
    @Query(value = "insert into push_runs (run_key) values (:runKey) on conflict do nothing", nativeQuery = true)
    int insertIfAbsent(String runKey);
}
