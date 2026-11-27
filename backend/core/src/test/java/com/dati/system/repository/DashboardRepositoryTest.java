package com.dati.system.repository;

import com.dati.datasource.repository.dao.DataSourceDAO;
import com.dati.datasource.repository.po.DataSourcePO;
import com.dati.mcp.repository.dao.McpServiceDAO;
import com.dati.mcp.repository.po.McpServicePO;
import com.dati.semantic.repository.dao.SubjectDAO;
import com.dati.semantic.repository.po.SubjectPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Dashboard repository tests")
class DashboardRepositoryTest {

    @Autowired
    private DataSourceDAO dataSourceDAO;

    @Autowired
    private SubjectDAO subjectDAO;

    @Autowired
    private McpServiceDAO mcpServiceDAO;

    @Test
    @DisplayName("Accessible creation timestamp queries return owned resources")
    void findAccessibleCreatedAt_shouldReturnOwnedResources() {
        String userId = "user-a";
        Instant createdAt = Instant.now();

        DataSourcePO dataSource = new DataSourcePO();
        dataSource.setName("owned datasource");
        dataSource.setCreatedBy(userId);
        dataSource.setCreatedAt(createdAt);
        dataSourceDAO.save(dataSource);

        DataSourcePO otherDataSource = new DataSourcePO();
        otherDataSource.setName("other datasource");
        otherDataSource.setCreatedBy("user-b");
        otherDataSource.setCreatedAt(createdAt);
        dataSourceDAO.save(otherDataSource);

        SubjectPO subject = new SubjectPO();
        subject.setName("owned subject");
        subject.setDatasourceId(dataSource.getId());
        subject.setCreatedBy(userId);
        subject.setCreatedAt(createdAt);
        subjectDAO.save(subject);

        McpServicePO service = new McpServicePO();
        service.setName("owned service");
        service.setCode("owned-service");
        service.setCreatedBy(userId);
        service.setCreatedAt(createdAt);
        mcpServiceDAO.save(service);

        assertThat(dataSourceDAO.findAccessibleCreatedAt(userId, Set.of())).hasSize(1);
        assertThat(subjectDAO.findAccessibleCreatedAt(userId, Set.of())).hasSize(1);
        assertThat(mcpServiceDAO.findAccessibleCreatedAt(userId, Set.of())).hasSize(1);
        assertThat(dataSourceDAO.findAllCreatedAt()).hasSize(2);
    }
}
