package com.dati.mcp.domain.service;

import com.dati.base.exception.DatiException;
import com.dati.base.exception.ErrorCode;
import com.dati.common.JsonUtils;
import com.dati.datasource.repository.dao.TableInfoDAO;
import com.dati.datasource.repository.po.TableInfoPO;
import com.dati.mcp.domain.model.McpDataScopeAccessMode;
import com.dati.mcp.domain.model.McpDataScopeType;
import com.dati.mcp.domain.model.McpPrebuiltToolConfig;
import com.dati.mcp.domain.model.McpResolvedTable;
import com.dati.mcp.domain.model.McpServiceSnapshot;
import com.dati.mcp.domain.model.McpServiceStatus;
import com.dati.mcp.repository.dao.McpServiceDAO;
import com.dati.mcp.repository.dao.McpServiceSnapshotDAO;
import com.dati.mcp.repository.mapper.McpServiceSnapshotMapper;
import com.dati.mcp.repository.po.McpServicePO;
import com.dati.mcp.repository.po.McpServiceSnapshotPO;
import com.dati.mcp.server.pojo.McpServiceDiffVO;
import com.dati.permission.domain.model.Permission;
import com.dati.permission.domain.model.ResourceType;
import com.dati.permission.domain.service.PermissionService;
import com.dati.semantic.domain.service.MetricService;
import com.dati.semantic.repository.dao.SubjectTableDAO;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class McpServicePublishService {

    private final McpServiceDAO mcpServiceDAO;
    private final McpServiceSnapshotDAO snapshotDAO;
    private final McpServiceDataScopeService dataScopeService;
    private final McpToolService toolService;
    private final McpPromptService promptService;
    private final PermissionService permissionService;
    private final TableInfoDAO tableInfoDAO;
    private final SubjectTableDAO subjectTableDAO;
    private final MetricService metricService;

    public McpServicePublishService(McpServiceDAO mcpServiceDAO,
                                     McpServiceSnapshotDAO snapshotDAO,
                                     McpServiceDataScopeService dataScopeService,
                                     McpToolService toolService,
                                     McpPromptService promptService,
                                     PermissionService permissionService,
                                     TableInfoDAO tableInfoDAO,
                                     SubjectTableDAO subjectTableDAO,
                                     MetricService metricService) {
        this.mcpServiceDAO = mcpServiceDAO;
        this.snapshotDAO = snapshotDAO;
        this.dataScopeService = dataScopeService;
        this.toolService = toolService;
        this.promptService = promptService;
        this.permissionService = permissionService;
        this.tableInfoDAO = tableInfoDAO;
        this.subjectTableDAO = subjectTableDAO;
        this.metricService = metricService;
    }

    @Transactional
    public McpServiceSnapshot publish(String serviceId, String releaseNote) {
        McpServicePO servicePO = mcpServiceDAO.findById(serviceId)
                .orElseThrow(() -> new DatiException(ErrorCode.MS_SERVICE_NOT_FOUND, serviceId));
        permissionService.requireCurrentUser(ResourceType.MCP_SERVICE, serviceId, Permission.EDIT, servicePO.getCreatedBy());

        dataScopeService.validateScopePermission(dataScopeService.getDataScope(serviceId));

        if (dataScopeService.getDataScope(serviceId).isEmpty()) {
            throw new DatiException(ErrorCode.MS_SERVICE_DATA_SCOPE_EMPTY, serviceId);
        }

        return doPublish(servicePO, releaseNote);
    }

    /**
     * 发布核心：不做数据范围校验。
     * 回滚内部发布目标快照可能是 MS018 规则前的空范围历史版本，恢复该状态是合法操作。
     */
    private McpServiceSnapshot doPublish(McpServicePO servicePO, String releaseNote) {
        return doPublish(servicePO, releaseNote, buildCurrentSnapshotContent(servicePO));
    }

    private McpServiceSnapshot doPublish(McpServicePO servicePO, String releaseNote,
                                         McpServiceSnapshot.SnapshotContent content) {
        String serviceId = servicePO.getId();

        Integer maxVersion = snapshotDAO.findMaxVersionNumberByServiceId(serviceId);
        int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;

        McpServiceSnapshot snapshot = new McpServiceSnapshot();
        snapshot.setServiceId(serviceId);
        snapshot.setVersionNumber(nextVersion);
        snapshot.setReleaseNote(releaseNote != null ? releaseNote.trim() : null);
        snapshot.setContent(content);

        McpServiceSnapshotPO snapshotPO = McpServiceSnapshotMapper.toPO(snapshot);
        snapshotPO = snapshotDAO.save(snapshotPO);

        // 仅首次发布（DRAFT）时置为 PUBLISHED；DISABLED 状态下发布保持停用（发布 ≠ 上线）
        if (servicePO.getStatus() == McpServiceStatus.DRAFT) {
            servicePO.setStatus(McpServiceStatus.PUBLISHED);
        }
        servicePO.setActiveVersionId(snapshotPO.getId());
        servicePO.setActiveVersionNumber(nextVersion);
        mcpServiceDAO.save(servicePO);

        return McpServiceSnapshotMapper.toModel(snapshotPO);
    }

    @Transactional
    public void disable(String serviceId) {
        McpServicePO servicePO = mcpServiceDAO.findById(serviceId)
                .orElseThrow(() -> new DatiException(ErrorCode.MS_SERVICE_NOT_FOUND, serviceId));
        permissionService.requireCurrentUser(ResourceType.MCP_SERVICE, serviceId, Permission.EDIT, servicePO.getCreatedBy());
        if (servicePO.getStatus() != McpServiceStatus.PUBLISHED) {
            throw new DatiException(ErrorCode.MS_SERVICE_STATUS_INVALID,
                    "Only PUBLISHED service can be disabled, current: " + servicePO.getStatus());
        }
        servicePO.setStatus(McpServiceStatus.DISABLED);
        mcpServiceDAO.save(servicePO);
    }

    @Transactional
    public void enable(String serviceId) {
        McpServicePO servicePO = mcpServiceDAO.findById(serviceId)
                .orElseThrow(() -> new DatiException(ErrorCode.MS_SERVICE_NOT_FOUND, serviceId));
        permissionService.requireCurrentUser(ResourceType.MCP_SERVICE, serviceId, Permission.EDIT, servicePO.getCreatedBy());
        if (servicePO.getStatus() != McpServiceStatus.DISABLED) {
            throw new DatiException(ErrorCode.MS_SERVICE_STATUS_INVALID,
                    "Only DISABLED service can be enabled, current: " + servicePO.getStatus());
        }
        servicePO.setStatus(McpServiceStatus.PUBLISHED);
        mcpServiceDAO.save(servicePO);
    }

    public List<McpServiceSnapshot> getSnapshots(String serviceId) {
        McpServicePO servicePO = mcpServiceDAO.findById(serviceId)
                .orElseThrow(() -> new DatiException(ErrorCode.MS_SERVICE_NOT_FOUND, serviceId));
        permissionService.requireCurrentUser(ResourceType.MCP_SERVICE, serviceId, Permission.VIEW, servicePO.getCreatedBy());
        return snapshotDAO.findAllByServiceIdOrderByVersionNumberDesc(serviceId).stream()
                .map(McpServiceSnapshotMapper::toModel)
                .collect(Collectors.toList());
    }

    public McpServiceDiffVO getDiff(String serviceId) {
        McpServicePO servicePO = mcpServiceDAO.findById(serviceId)
                .orElseThrow(() -> new DatiException(ErrorCode.MS_SERVICE_NOT_FOUND, serviceId));
        permissionService.requireCurrentUser(ResourceType.MCP_SERVICE, serviceId, Permission.VIEW, servicePO.getCreatedBy());

        McpServiceDiffVO diff = new McpServiceDiffVO();
        diff.setActiveVersionNumber(servicePO.getActiveVersionNumber());

        if (servicePO.getActiveVersionId() == null) {
            diff.setHasChanges(true);
            diff.getModifiedComponents().add("INITIAL_RELEASE");
            return diff;
        }

        McpServiceSnapshotPO activeSnapshotPO = snapshotDAO.findById(servicePO.getActiveVersionId())
                .orElse(null);

        if (activeSnapshotPO == null) {
            diff.setHasChanges(true);
            return diff;
        }

        McpServiceSnapshot activeSnapshot = McpServiceSnapshotMapper.toModel(activeSnapshotPO);
        McpServiceSnapshot.SnapshotContent activeContent = activeSnapshot.getContent();
        McpServiceSnapshot.SnapshotContent currentContent = buildCurrentSnapshotContent(servicePO);

        if (activeContent == null) {
            diff.setHasChanges(true);
            return diff;
        }

        // 1. Basic Info Diff
        if (!Objects.equals(activeContent.getServiceInfo().getName(), currentContent.getServiceInfo().getName())
                || !Objects.equals(activeContent.getServiceInfo().getDescription(), currentContent.getServiceInfo().getDescription())) {
            diff.setBasicInfoChanged(true);
            diff.getModifiedComponents().add("BASIC_INFO");
        }

        // 2. Data Scope Diff（仅比较业务字段，忽略 id/审计字段）
        if (!Objects.equals(dataScopeKeys(activeContent.getDataScopes()), dataScopeKeys(currentContent.getDataScopes()))) {
            diff.setDataScopeChanged(true);
            diff.getModifiedComponents().add("DATA_SCOPE");
        }

        // 3. Custom Tools Diff
        Map<String, McpServiceSnapshot.CustomToolDraft> activeCustomTools = activeContent.getCustomTools() != null
                ? activeContent.getCustomTools().stream().collect(Collectors.toMap(McpServiceSnapshot.CustomToolDraft::name, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, McpServiceSnapshot.CustomToolDraft> currentCustomTools = currentContent.getCustomTools() != null
                ? currentContent.getCustomTools().stream().collect(Collectors.toMap(McpServiceSnapshot.CustomToolDraft::name, Function.identity(), (a, b) -> a))
                : Map.of();

        List<String> addedTools = new ArrayList<>();
        List<String> modifiedTools = new ArrayList<>();
        List<String> deletedTools = new ArrayList<>();

        for (Map.Entry<String, McpServiceSnapshot.CustomToolDraft> entry : currentCustomTools.entrySet()) {
            if (!activeCustomTools.containsKey(entry.getKey())) {
                addedTools.add(entry.getKey());
            } else {
                if (!Objects.equals(customToolKey(activeCustomTools.get(entry.getKey())), customToolKey(entry.getValue()))) {
                    modifiedTools.add(entry.getKey());
                }
            }
        }
        for (String activeName : activeCustomTools.keySet()) {
            if (!currentCustomTools.containsKey(activeName)) {
                deletedTools.add(activeName);
            }
        }

        // Prebuilt tools check（仅比较业务字段，忽略 id/审计字段）
        List<McpServiceSnapshot.PrebuiltToolDraft> activePrebuilt = activeContent.getPrebuiltTools();
        List<McpServiceSnapshot.PrebuiltToolDraft> currentPrebuilt = currentContent.getPrebuiltTools();
        boolean prebuiltChanged = !Objects.equals(prebuiltKeys(activePrebuilt), prebuiltKeys(currentPrebuilt));
        if (prebuiltChanged) {
            // prebuilt 变更没有单独的增删改列表，将具体工具名并入 modified_tools
            modifiedTools.addAll(changedPrebuiltTypes(activePrebuilt, currentPrebuilt));
        }

        if (!addedTools.isEmpty() || !modifiedTools.isEmpty() || !deletedTools.isEmpty() || prebuiltChanged) {
            diff.setToolsChanged(true);
            diff.setAddedTools(addedTools);
            diff.setModifiedTools(modifiedTools);
            diff.setDeletedTools(deletedTools);
            diff.getModifiedComponents().add("TOOLS");
        }

        // 4. Metrics Diff
        Map<String, McpServiceSnapshot.MetricDraft> activeMetrics = activeContent.getMetrics() != null
                ? activeContent.getMetrics().stream().collect(Collectors.toMap(McpServiceSnapshot.MetricDraft::metricId, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, McpServiceSnapshot.MetricDraft> currentMetrics = currentContent.getMetrics() != null
                ? currentContent.getMetrics().stream().collect(Collectors.toMap(McpServiceSnapshot.MetricDraft::metricId, Function.identity(), (a, b) -> a))
                : Map.of();
        List<String> addedMetrics = new ArrayList<>();
        List<String> modifiedMetrics = new ArrayList<>();
        List<String> deletedMetrics = new ArrayList<>();
        for (Map.Entry<String, McpServiceSnapshot.MetricDraft> entry : currentMetrics.entrySet()) {
            if (!activeMetrics.containsKey(entry.getKey())) {
                addedMetrics.add(entry.getValue().code());
            } else if (!Objects.equals(metricKey(activeMetrics.get(entry.getKey())), metricKey(entry.getValue()))) {
                modifiedMetrics.add(entry.getValue().code());
            }
        }
        for (Map.Entry<String, McpServiceSnapshot.MetricDraft> entry : activeMetrics.entrySet()) {
            if (!currentMetrics.containsKey(entry.getKey())) {
                deletedMetrics.add(entry.getValue().code());
            }
        }
        if (!addedMetrics.isEmpty() || !modifiedMetrics.isEmpty() || !deletedMetrics.isEmpty()) {
            diff.setMetricsChanged(true);
            diff.setAddedMetrics(addedMetrics);
            diff.setModifiedMetrics(modifiedMetrics);
            diff.setDeletedMetrics(deletedMetrics);
            diff.getModifiedComponents().add("METRICS");
        }

        // 5. Prompts Diff
        Map<String, McpServiceSnapshot.PromptDraft> activePrompts = activeContent.getPrompts() != null
                ? activeContent.getPrompts().stream().collect(Collectors.toMap(McpServiceSnapshot.PromptDraft::name, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, McpServiceSnapshot.PromptDraft> currentPrompts = currentContent.getPrompts() != null
                ? currentContent.getPrompts().stream().collect(Collectors.toMap(McpServiceSnapshot.PromptDraft::name, Function.identity(), (a, b) -> a))
                : Map.of();

        List<String> addedPrompts = new ArrayList<>();
        List<String> modifiedPrompts = new ArrayList<>();
        List<String> deletedPrompts = new ArrayList<>();

        for (Map.Entry<String, McpServiceSnapshot.PromptDraft> entry : currentPrompts.entrySet()) {
            if (!activePrompts.containsKey(entry.getKey())) {
                addedPrompts.add(entry.getKey());
            } else {
                if (!Objects.equals(promptKey(activePrompts.get(entry.getKey())), promptKey(entry.getValue()))) {
                    modifiedPrompts.add(entry.getKey());
                }
            }
        }
        for (String activeName : activePrompts.keySet()) {
            if (!currentPrompts.containsKey(activeName)) {
                deletedPrompts.add(activeName);
            }
        }

        if (!addedPrompts.isEmpty() || !modifiedPrompts.isEmpty() || !deletedPrompts.isEmpty()) {
            diff.setPromptsChanged(true);
            diff.setAddedPrompts(addedPrompts);
            diff.setModifiedPrompts(modifiedPrompts);
            diff.setDeletedPrompts(deletedPrompts);
            diff.getModifiedComponents().add("PROMPTS");
        }

        boolean hasChanges = diff.isBasicInfoChanged() || diff.isDataScopeChanged() || diff.isToolsChanged()
                || diff.isMetricsChanged() || diff.isPromptsChanged();
        diff.setHasChanges(hasChanges);

        return diff;
    }

    @Transactional
    public McpServiceSnapshot rollback(String serviceId, Integer targetVersionNumber, String releaseNote) {
        McpServicePO servicePO = mcpServiceDAO.findById(serviceId)
                .orElseThrow(() -> new DatiException(ErrorCode.MS_SERVICE_NOT_FOUND, serviceId));
        permissionService.requireCurrentUser(ResourceType.MCP_SERVICE, serviceId, Permission.EDIT, servicePO.getCreatedBy());

        McpServiceSnapshotPO targetSnapshotPO = snapshotDAO.findByServiceIdAndVersionNumber(serviceId, targetVersionNumber)
                .orElseThrow(() -> new DatiException(ErrorCode.MS_SERVICE_VERSION_NOT_FOUND,
                        "v" + targetVersionNumber + " of service " + serviceId));

        McpServiceSnapshot targetSnapshot = McpServiceSnapshotMapper.toModel(targetSnapshotPO);
        McpServiceSnapshot.SnapshotContent content = targetSnapshot.getContent();
        if (content == null) {
            throw new DatiException(ErrorCode.MS_SERVICE_VERSION_NOT_FOUND,
                    "v" + targetVersionNumber + " of service " + serviceId + " has no content");
        }

        // 1. 基础信息写回草稿
        McpServiceSnapshot.ServiceInfo info = content.getServiceInfo();
        if (info != null) {
            servicePO.setName(info.getName());
            servicePO.setDescription(info.getDescription());
            servicePO.setCode(info.getCode());
            mcpServiceDAO.save(servicePO);
        }

        // 2. 数据范围写回草稿（Draft → Model 重建，无 id，先删后存安全）
        dataScopeService.saveDataScope(serviceId,
                content.getDataScopes() == null ? List.of()
                        : content.getDataScopes().stream().map(McpServiceSnapshotMapper::toDataScope).toList());

        // 3. Tools 写回草稿
        toolService.replaceCustomTools(serviceId,
                content.getCustomTools() == null ? List.of()
                        : content.getCustomTools().stream().map(McpServiceSnapshotMapper::toCustomTool).toList());
        if (content.getPrebuiltTools() != null) {
            content.getPrebuiltTools().forEach(cfg -> {
                McpPrebuiltToolConfig tool = McpServiceSnapshotMapper.toPrebuiltTool(cfg);
                toolService.updatePrebuiltTool(serviceId, cfg.toolType(), tool.getConfig(), tool.isEnabled());
            });
        }

        // 4. Prompts 写回草稿
        promptService.replacePrompts(serviceId,
                content.getPrompts() == null ? List.of()
                        : content.getPrompts().stream().map(McpServiceSnapshotMapper::toPrompt).toList());

        // 5. 重新发布生成新快照 vN+1（保留回滚审计痕迹）
        // 不走 publish()：目标快照可能是空数据范围的历史版本，恢复该状态是合法操作（MS019 仅约束主动发布）
        String note = "Rollback to v" + targetVersionNumber
                + (releaseNote != null && !releaseNote.isBlank() ? ": " + releaseNote : "");
        return doPublish(servicePO, note, content);
    }

    /**
     * 业务字段比较 key：diff 只关注配置内容差异。
     * 快照内容为 Draft（纯业务字段，无 id/审计字段），key 天然只含业务字段。
     */
    private List<String> dataScopeKeys(List<McpServiceSnapshot.DataScopeDraft> scopes) {
        return scopes == null ? List.of() : scopes.stream()
                .map(s -> s.scopeType() + "|" + s.referenceId() + "|" + s.accessMode()
                        + "|" + String.join(",", sortedSchemas(s.allowedSchemas()))
                        + "|" + resolvedTableKeys(s))
                .toList();
    }

    private String resolvedTableKeys(McpServiceSnapshot.DataScopeDraft scope) {
        return scope.resolvedTables().stream()
                .map(t -> t.dataSourceId() + "." + t.schema() + "." + t.tableName())
                .sorted()
                .collect(Collectors.joining(","));
    }

    private List<String> sortedSchemas(List<String> schemas) {
        return schemas == null ? List.of() : schemas.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private List<String> prebuiltKeys(List<McpServiceSnapshot.PrebuiltToolDraft> tools) {
        return tools == null ? List.of() : tools.stream()
                .map(t -> t.toolType() + "|" + t.enabled() + "|" + JsonUtils.toJson(t.config()))
                .toList();
    }

    /** 返回配置发生变化的 prebuilt 工具名（toolType），用于 diff 明细展示 */
    private List<String> changedPrebuiltTypes(List<McpServiceSnapshot.PrebuiltToolDraft> active,
                                              List<McpServiceSnapshot.PrebuiltToolDraft> current) {
        List<String> changed = new ArrayList<>();
        List<String> activeKeys = prebuiltKeys(active);
        List<String> currentKeys = prebuiltKeys(current);
        int max = Math.max(activeKeys.size(), currentKeys.size());
        for (int i = 0; i < max; i++) {
            String a = i < activeKeys.size() ? activeKeys.get(i) : null;
            String c = i < currentKeys.size() ? currentKeys.get(i) : null;
            if (!Objects.equals(a, c)) {
                McpServiceSnapshot.PrebuiltToolDraft cfg = i < current.size() ? current.get(i) : (i < active.size() ? active.get(i) : null);
                if (cfg != null && cfg.toolType() != null) {
                    changed.add(cfg.toolType().name());
                }
            }
        }
        return changed;
    }

    private String customToolKey(McpServiceSnapshot.CustomToolDraft t) {
        return t.toolType() + "|" + t.name() + "|" + t.title() + "|" + t.description()
                + "|" + t.enabled() + "|" + JsonUtils.toJson(t.config());
    }

    private String metricKey(McpServiceSnapshot.MetricDraft metric) {
        return metric.code() + "|" + metric.name() + "|" + metric.description()
                + "|" + JsonUtils.toJson(metric.aliases()) + "|" + metric.owner()
                + "|" + metric.grain() + "|" + metric.timeField() + "|" + metric.timeZone()
                + "|" + JsonUtils.toJson(metric.allowedDimensions()) + "|" + metric.sqlTemplate()
                + "|" + JsonUtils.toJson(metric.parameters()) + "|" + metric.timeout()
                + "|" + metric.maxRows() + "|" + metric.revision();
    }

    private String promptKey(McpServiceSnapshot.PromptDraft p) {
        return p.name() + "|" + p.description() + "|" + p.content()
                + "|" + JsonUtils.toJson(p.parameters()) + "|" + p.enabled();
    }

    private McpServiceSnapshot.SnapshotContent buildCurrentSnapshotContent(McpServicePO servicePO) {
        String serviceId = servicePO.getId();

        McpServiceSnapshot.ServiceInfo serviceInfo = new McpServiceSnapshot.ServiceInfo();
        serviceInfo.setId(servicePO.getId());
        serviceInfo.setName(servicePO.getName());
        serviceInfo.setDescription(servicePO.getDescription());
        serviceInfo.setCode(servicePO.getCode());

        List<McpServiceSnapshot.DataScopeDraft> dataScopes = dataScopeService.getDataScope(serviceId).stream()
                .map(McpServiceSnapshotMapper::toDataScopeDraft)
                .map(this::withResolvedTables)
                .toList();

        ToolsResult toolsResult = toolService.listTools(serviceId);
        List<McpServiceSnapshot.PrebuiltToolDraft> prebuiltTools = toolsResult.prebuilt() != null
                ? toolsResult.prebuilt().stream().map(McpServiceSnapshotMapper::toPrebuiltToolDraft).toList()
                : List.of();
        List<McpServiceSnapshot.CustomToolDraft> customTools = toolsResult.custom() != null
                ? toolsResult.custom().stream().map(McpServiceSnapshotMapper::toCustomToolDraft).toList()
                : List.of();

        List<McpServiceSnapshot.PromptDraft> prompts = promptService.listPrompts(serviceId).stream()
                .map(McpServiceSnapshotMapper::toPromptDraft)
                .toList();

        List<String> subjectIds = dataScopes.stream()
                .filter(scope -> scope.scopeType() == McpDataScopeType.SUBJECT)
                .map(McpServiceSnapshot.DataScopeDraft::referenceId)
                .distinct()
                .toList();
        List<McpServiceSnapshot.MetricDraft> metrics = metricService.getMetricsBySubjectIds(subjectIds).stream()
                .map(McpServiceSnapshotMapper::toMetricDraft)
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();

        McpServiceSnapshot.SnapshotContent content = new McpServiceSnapshot.SnapshotContent();
        content.setServiceInfo(serviceInfo);
        content.setDataScopes(dataScopes);
        content.setPrebuiltTools(prebuiltTools);
        content.setCustomTools(customTools);
        content.setPrompts(prompts);
        content.setMetrics(metrics);
        return content;
    }


    private McpServiceSnapshot.DataScopeDraft withResolvedTables(McpServiceSnapshot.DataScopeDraft draft) {
        if (draft.accessMode() == McpDataScopeAccessMode.SCHEMA_SCOPE) {
            return draft.withResolvedTables(List.of());
        }

        List<TableInfoPO> tables;
        if (draft.scopeType() == McpDataScopeType.DATA_SOURCE) {
            tables = tableInfoDAO.findByDataSourceId(draft.referenceId());
        } else if (draft.scopeType() == McpDataScopeType.SUBJECT) {
            tables = subjectTableDAO.findTablesBySubjectId(draft.referenceId(), Pageable.unpaged()).getContent();
        } else {
            tables = List.of();
        }

        List<McpResolvedTable> resolved = tables.stream()
                .map(t -> new McpResolvedTable(t.getDataSourceId(), t.getSchema(), t.getName()))
                .distinct()
                .sorted((a, b) -> {
                    int byDataSource = a.dataSourceId().compareTo(b.dataSourceId());
                    if (byDataSource != 0) return byDataSource;
                    int bySchema = String.valueOf(a.schema()).compareTo(String.valueOf(b.schema()));
                    if (bySchema != 0) return bySchema;
                    return a.tableName().compareTo(b.tableName());
                })
                .toList();
        return draft.withResolvedTables(resolved);
    }

}
