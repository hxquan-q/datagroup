package com.dati.semantic.repository.dao;

import com.dati.semantic.repository.po.TableRelationPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableRelationDAO extends JpaRepository<TableRelationPO, String> {
    Page<TableRelationPO> findBySubjectId(String subjectId, Pageable pageable);
    List<TableRelationPO> findBySubjectId(String subjectId);
    void deleteBySubjectId(String subjectId);
    void deleteByFactTableIdOrDimensionTableId(String factTableId, String dimensionTableId);
}
