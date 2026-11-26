package com.dati.semantic.repository.dao;

import com.dati.semantic.repository.po.TableRelationPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TableRelationDAO extends JpaRepository<TableRelationPO, String> {
    Page<TableRelationPO> findBySubjectId(String subjectId, Pageable pageable);
    List<TableRelationPO> findBySubjectId(String subjectId);
    void deleteBySubjectId(String subjectId);
    @Modifying
    @Query("delete from TableRelationPO r where r.factTableId in :tableIds or r.dimensionTableId in :tableIds")
    void deleteByAnyTableIdIn(@Param("tableIds") Collection<String> tableIds);

    @Modifying
    @Query("delete from TableRelationPO r where (r.factTableId = :tableId and r.factColumn in :columns) or (r.dimensionTableId = :tableId and r.dimensionColumn in :columns)")
    void deleteByAnyColumnIn(@Param("tableId") String tableId, @Param("columns") Collection<String> columns);
}
