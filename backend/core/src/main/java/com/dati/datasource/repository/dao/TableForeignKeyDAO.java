package com.dati.datasource.repository.dao;

import com.dati.datasource.repository.po.TableForeignKeyPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TableForeignKeyDAO extends JpaRepository<TableForeignKeyPO, String> {
    List<TableForeignKeyPO> findByTableId(String tableId);
    List<TableForeignKeyPO> findByTableIdIn(Collection<String> tableIds);
    void deleteByTableId(String tableId);
    void deleteByTableIdIn(Collection<String> tableIds);
}
