package com.assu.server.domain.student.converter;

import com.assu.server.domain.student.dto.StudentResponseDTO;
import com.assu.server.domain.student.entity.Student;

public class StudentConverter {
        public static StudentResponseDTO.CheckStampResponseDTO checkStampResponseDTO(Student student, String message) {
            return StudentResponseDTO.CheckStampResponseDTO.builder()
                    .userId(student.getId())
                    .stamp(student.getStamp())
                    .message(message)
                    .build();
        }
}
