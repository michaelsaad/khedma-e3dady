package com.khedma.points.period;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointPeriodRepository extends JpaRepository<PointPeriod, Long> {

    List<PointPeriod> findAllByOrderByStartsOnAsc();
}
