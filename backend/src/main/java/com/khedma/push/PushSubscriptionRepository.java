package com.khedma.push;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByUserIdIn(Collection<Long> userIds);

    long countByUserIdIn(Collection<Long> userIds);

    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.endpoint = :endpoint")
    int deleteByEndpoint(String endpoint);

    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.endpoint = :endpoint and s.user.id = :userId")
    int deleteByEndpointAndUserId(String endpoint, Long userId);

    @Transactional
    @Modifying
    @Query("update PushSubscription s set s.lastSuccessAt = :at where s.id = :id")
    int markDelivered(Long id, Instant at);
}
