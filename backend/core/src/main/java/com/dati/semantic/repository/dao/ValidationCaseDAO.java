package com.dati.semantic.repository.dao;

import com.dati.semantic.repository.po.ValidationCasePO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationCaseDAO extends JpaRepository<ValidationCasePO, String> {
    Page<ValidationCasePO> findBySubjectId(String subjectId, Pageable pageable);
    List<ValidationCasePO> findBySubjectId(String subjectId);
    List<ValidationCasePO> findBySubjectIdIn(List<String> subjectIds);
    void deleteBySubjectId(String subjectId);
    void deleteByMetricId(String metricId);
}
