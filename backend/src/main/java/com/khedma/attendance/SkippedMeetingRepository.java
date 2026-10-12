package com.khedma.attendance;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SkippedMeetingRepository extends JpaRepository<SkippedMeeting, LocalDate> {

    default Set<LocalDate> allDates() {
        return findAll().stream().map(SkippedMeeting::getMeetingDate).collect(Collectors.toSet());
    }
}
