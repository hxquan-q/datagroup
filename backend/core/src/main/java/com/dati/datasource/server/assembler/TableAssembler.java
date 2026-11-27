package com.dati.datasource.server.assembler;

import com.dati.base.BaseAssembler;
import com.dati.base.pojo.PageResponse;
import com.dati.datasource.domain.model.TableInfo;
import com.dati.datasource.server.pojo.AddedTableVO;
import com.dati.datasource.server.pojo.TableInfoVO;
import com.dati.datasource.server.pojo.request.UpdateTableRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TableAssembler extends BaseAssembler {

    private TableInfoVO mapFields(TableInfo tableInfo) {
        TableInfoVO tableInfoVO = new TableInfoVO();
        super.copyBaseInfo(tableInfo, tableInfoVO);
        tableInfoVO.setDatasourceId(tableInfo.getDatasourceId());
        tableInfoVO.setSchema(tableInfo.getSchema());
        tableInfoVO.setAliases(tableInfo.getAliases());
        tableInfoVO.setTimeColumn(tableInfo.getTimeColumn());
        tableInfoVO.setTimeWindowRequired(Boolean.TRUE.equals(tableInfo.getTimeWindowRequired()));
        tableInfoVO.setMaxRangeDays(tableInfo.getMaxRangeDays());
        tableInfoVO.setSqlBlocked(Boolean.TRUE.equals(tableInfo.getSqlBlocked()));
        return tableInfoVO;
    }

    public TableInfoVO toTableInfoVO(TableInfo tableInfo) {
        TableInfoVO vo = mapFields(tableInfo);
        super.fillUserInfo(List.of(vo));
        return vo;
    }

    public PageResponse<TableInfoVO> toPageResponse(Page<TableInfo> page) {
        List<TableInfoVO> vos = page.getContent().stream()
                .map(this::mapFields)
                .collect(Collectors.toList());
        super.fillUserInfo(vos);
        return PageResponse.of(new PageImpl<>(vos, page.getPageable(), page.getTotalElements()));
    }

    public TableInfo toTableInfo(UpdateTableRequest request) {
        TableInfo tableInfo = new TableInfo();
        tableInfo.setAliases(request.getAliases());
        tableInfo.setDescription(request.getDescription());
        tableInfo.setTimeColumn(request.getTimeColumn());
        tableInfo.setTimeWindowRequired(request.getTimeWindowRequired());
        tableInfo.setMaxRangeDays(request.getMaxRangeDays());
        tableInfo.setSqlBlocked(request.getSqlBlocked());
        return tableInfo;
    }

    public List<AddedTableVO> toAddedTables(List<TableInfo> tables) {
        return tables.stream()
                .map(table -> new AddedTableVO(table.getSchema(), table.getName()))
                .toList();
    }

}
