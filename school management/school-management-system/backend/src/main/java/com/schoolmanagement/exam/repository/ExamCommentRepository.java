package com.schoolmanagement.exam.repository;

import com.schoolmanagement.exam.entity.ExamComment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamCommentRepository extends JpaRepository<ExamComment, UUID> {

    Optional<ExamComment> findByExamIdAndStudentId(UUID examId, UUID studentId);

    List<ExamComment> findAllByExamIdAndStudentIdIn(UUID examId, Collection<UUID> studentIds);

    List<ExamComment> findAllByExamId(UUID examId);
}
