package com.dati.semantic.server.assembler;

import com.dati.base.BaseAssembler;
import com.dati.base.pojo.PageResponse;
import com.dati.semantic.domain.model.ValidationCase;
import com.dati.semantic.server.pojo.vo.ValidationCaseVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ValidationCaseAssembler extends BaseAssembler {
    public ValidationCaseVO toVO(ValidationCase item) {
        ValidationCaseVO vo = mapFields(item);
        super.fillUserInfo(List.of(vo));
        return vo;
    }
    public PageResponse<ValidationCaseVO> toPageResponse(Page<ValidationCase> page) {
        List<ValidationCaseVO> vos = page.getContent().stream().map(this::mapFields).collect(Collectors.toList());
        super.fillUserInfo(vos);
        return PageResponse.of(new PageImpl<>(vos, page.getPageable(), page.getTotalElements()));
    }
    private ValidationCaseVO mapFields(ValidationCase item) {
        ValidationCaseVO vo = new ValidationCaseVO();
        super.copyBaseInfo(item, vo);
        vo.setSubjectId(item.getSubjectId());
        vo.setMetricId(item.getMetricId());
        vo.setQuestion(item.getQuestion());
        vo.setArguments(item.getArguments());
        vo.setDimensions(item.getDimensions());
        vo.setAssertionType(item.getAssertionType());
        vo.setExpectedValue(item.getExpectedValue());
        vo.setOwner(item.getOwner());
        vo.setEnabled(item.getEnabled());
        vo.setLastStatus(item.getLastStatus());
        vo.setLastMessage(item.getLastMessage());
        vo.setLastRunAt(item.getLastRunAt());
        vo.setLastDurationMs(item.getLastDurationMs());
        vo.setLastMetricRevision(item.getLastMetricRevision());
        vo.setLastEvidence(item.getLastEvidence());
        return vo;
    }
}
