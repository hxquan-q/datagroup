package com.dati.semantic.repository.dao;

import com.dati.semantic.repository.po.MetricPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetricDAO extends JpaRepository<MetricPO, String> {

    Optional<MetricPO> findBySubjectIdAndCode(String subjectId, String code);

    Page<MetricPO> findBySubjectId(String subjectId, Pageable pageable);

    List<MetricPO> findBySubjectId(String subjectId);

    List<MetricPO> findBySubjectIdIn(Collection<String> subjectIds);

    void deleteBySubjectId(String subjectId);

    @Query("""
            SELECT m FROM MetricPO m
            WHERE m.subjectId = :subjectId
            AND (locate(lower(:keyword), lower(m.name)) > 0
                 OR locate(lower(:keyword), lower(m.code)) > 0
                 OR locate(lower(:keyword), lower(cast(m.aliases as String))) > 0)
            """)
    Page<MetricPO> findBySubjectIdAndKeyword(@Param("subjectId") String subjectId,
                                             @Param("keyword") String keyword,
                                             Pageable pageable);
}
