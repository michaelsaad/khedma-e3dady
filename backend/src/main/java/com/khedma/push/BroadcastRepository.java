package com.khedma.push;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    @EntityGraph(attributePaths = "sentBy")
    List<Broadcast> findTop20ByOrderByIdDesc();
}
