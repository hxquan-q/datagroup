# XIAOAI - 小爱智能问数平台

[English](README.md) | [简体中文](README_zh.md)

XIAOAI（小爱智能问数平台）是面向企业业务数据库的智能问数与 MCP 服务平台。它把数据库发布为语义增强、权限可控的 MCP 服务，让 Agent 或任意 MCP Host 可以在授权范围内查询、分析和维护数据。

本仓库是基于上游开源项目 [DatI](https://github.com/yimindev/dati) 二次开发的独立下游项目；上游版权与许可信息见 [NOTICE](NOTICE) 和 [LICENSE](LICENSE)。

## 核心能力

1. **智能问数**：通过业务术语、字段别名、枚举值和指标口径增强 NL2SQL 问答效果
2. **多数据库**：支持 MySQL、PostgreSQL、ClickHouse、Doris 等关系型与分析型数据库
3. **语义治理**：按业务主题组织表范围，维护业务术语和字段语义
4. **MCP 发布**：基于标准 [MCP](https://modelcontextprotocol.io/) Streamable HTTP 发布数据服务
5. **安全管控**：集中托管数据库凭据，支持用户级权限和数据范围隔离

## 快速开始

```bash
git clone https://github.com/hxquan-q/datagroup.git
cd datagroup
cp .env.example .env
# 编辑 .env，至少配置 JWT_SECRET 与 SPRING_ELASTICSEARCH_PASSWORD
docker compose up -d --build
```

访问 `http://localhost:8085`。首个注册用户填写 `admin` 即自动成为超级管理员。详细配置与生产部署见 [部署手册](docs/deployment_zh.md)。

## 技术栈

- **后端**：Spring Boot 3.5.x + Java 21 + JPA
- **前端**：Vue 3 + TypeScript + Vite + Element Plus + TailwindCSS 4
- **数据库**：H2（开发）/ MySQL / PostgreSQL（生产）
- **搜索引擎**：Elasticsearch（语义检索）

## 文档导航

- [本地开发指南](docs/development_zh.md)：环境准备、前后端启动与开发约定
- [服务器部署手册](docs/deployment_zh.md)：架构组成、Docker Compose 部署与运维排障
- [架构总览](docs/architecture/overview_zh.md)：系统分层架构、核心模块设计与接口约定
- [用户帮助中心](docs/user-guide/index.md)：VitePress 站点，中英双语
- [API 契约](docs/api/openapi.json)：E2E 测试工具链使用
- [AI 编码助手规范](AGENTS.md)：后端、前端与设计系统规则

## 实战案例

- [企业级零售 BI 分析 (AdventureWorks DW)](examples/adventureworks-dw/README.md)
- [家庭共享记账助手](examples/family-finance/README.md)

## Agent Skills

- [dati-ops](skills/dati-ops/SKILL.md)：通过 HTTP API 完成平台配置与操作（数据源、主题术语、MCP 服务）
- [e2e-tester](.agents/skills/e2e-tester/SKILL.md)：E2E HTTP 集成测试与 API 行为验证

## 版权与许可

XIAOAI / 小爱智能问数平台是 DatI 的下游衍生项目。

- 上游项目：[yimindev/dati](https://github.com/yimindev/dati)
- 上游版权：`Copyright 2026 DatI Contributors`
- 下游修改版权：`Copyright 2026 XIAOAI Contributors`
- 许可证：[Apache License 2.0](LICENSE)

完整的 attribution 说明见 [NOTICE](NOTICE)。使用 XIAOAI 名称或标识不代表上游 DatI 项目或其贡献者的认可或隶属关系。
