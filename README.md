# XIAOAI - Intelligent Data Q&A Platform

[English](README.md) | [简体中文](README_zh.md)

XIAOAI is an intelligent data Q&A and MCP service platform for enterprise business databases. It publishes databases as semantically enhanced, access-controlled MCP services so agents and MCP hosts can query, analyze, and maintain data within the authorized scope.

This repository is an independent downstream derivative of the open-source [DatI](https://github.com/yimindev/dati) project. Upstream copyright and license attribution are documented in [NOTICE](NOTICE) and [LICENSE](LICENSE).

## Core Capabilities

1. **Intelligent Data Q&A**: Improves NL2SQL with business terms, field aliases, enum values, and metric definitions
2. **Multi-Database Support**: Supports MySQL, PostgreSQL, ClickHouse, Doris, and other relational or analytical databases
3. **Semantic Governance**: Organizes tables by business subject and maintains business semantics
4. **MCP Publishing**: Publishes data services through standard [MCP](https://modelcontextprotocol.io/) Streamable HTTP
5. **Security & Governance**: Centrally managed credentials with user-level permission and data scope isolation

## Quick Start

```bash
git clone https://github.com/hxquan-q/datagroup.git
cd datagroup
cp .env.example .env
# Edit .env and configure at least JWT_SECRET and SPRING_ELASTICSEARCH_PASSWORD
docker compose up -d --build
```

Open `http://localhost:8085`. Register the first user with username `admin` to receive the super admin role. For production configuration, see the [Deployment Guide](docs/deployment.md).

## Tech Stack

- **Backend**: Spring Boot 3.5.x + Java 21 + JPA
- **Frontend**: Vue 3 + TypeScript + Vite + Element Plus + TailwindCSS 4
- **Database**: H2 (Development) / MySQL / PostgreSQL (Production)
- **Search Engine**: Elasticsearch (Semantic Retrieval)

## Documentation

- [Local Development Guide](docs/development.md): Environment setup, backend/frontend startup, and project conventions
- [Server Deployment Guide](docs/deployment.md): Architecture overview, Docker Compose deployment, and operations
- [Architecture Overview](docs/architecture/overview.md): System architecture, core modules, and interface conventions
- [User Guide](docs/user-guide/en/index.md): VitePress site, bilingual
- [API Specification](docs/api/openapi.json): Used by E2E test toolchains
- [AI Coding Assistant Guidelines](AGENTS.md): Backend, frontend, and design-system rules

## Examples

- [Enterprise BI & Retail Analytics (AdventureWorks DW)](examples/adventureworks-dw/README.md)
- [Family Finance Assistant](examples/family-finance/README.md)

## Agent Skills

- [dati-ops](skills/dati-ops/SKILL.md): Configure and operate the platform through HTTP APIs
- [e2e-tester](.agents/skills/e2e-tester/SKILL.md): E2E HTTP integration tests and API behavior validation

## Copyright and License

XIAOAI is a downstream derivative of DatI.

- Upstream project: [yimindev/dati](https://github.com/yimindev/dati)
- Upstream copyright: `Copyright 2026 DatI Contributors`
- Downstream modifications: `Copyright 2026 XIAOAI Contributors`
- License: [Apache License 2.0](LICENSE)

See [NOTICE](NOTICE) for the complete attribution statement. Use of the XIAOAI name or logo does not imply endorsement by or affiliation with the upstream DatI project or its contributors.
