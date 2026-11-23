package com.schoolmanagement.exam.repository;

import com.schoolmanagement.exam.entity.ExamSubject;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSubjectRepository extends JpaRepository<ExamSubject, UUID> {

    @EntityGraph(attributePaths = {"subject"})
    List<ExamSubject> findAllByExamIdInOrderByCreatedAtAscIdAsc(Collection<UUID> examIds);

    @EntityGraph(attributePaths = {"subject", "exam"})
    List<ExamSubject> findAllByExamIdOrderByCreatedAtAscIdAsc(UUID examId);

    boolean existsBySubjectId(UUID subjectId);
}
