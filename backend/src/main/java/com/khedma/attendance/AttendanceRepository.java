package com.khedma.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByStudentIdAndMeetingDate(Long studentId, LocalDate meetingDate);

    List<Attendance> findAllByMeetingDate(LocalDate meetingDate);


    List<Attendance> findAllByMeetingDateBetween(LocalDate from, LocalDate to);
}
