#!/usr/bin/env python3
"""Seed a demo MES environment for local development and UI review.

Idempotent: safe to run repeatedly. Steps:
  1. Start elasticsearch and MySQL docker containers (created if missing).
  2. Create MES demo tables and rows in MySQL.
  3. Register the admin user (ignored when it already exists) and log in.
  4. Find-or-create the demo datasource, import its tables, sync columns.
  5. Find-or-create the demo subject and link missing tables.
  6. Find-or-create the demo MCP service scoped to the subject and publish it.

Prerequirements: docker running; the backend already serving on
SEED_BACKEND_URL (default http://localhost:8085).

Usage: scripts/seed_demo.py
Env overrides: SEED_BACKEND_URL, SEED_ADMIN_NAME, SEED_ADMIN_PASSWORD,
  SEED_ES_PASSWORD, SEED_ES_PORT, SEED_MYSQL_PASSWORD, SEED_MYSQL_PORT,
  SEED_PREFIX (resource name prefix, default "mes")
"""

import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

BACKEND = os.environ.get("SEED_BACKEND_URL", "http://localhost:8085")
ADMIN_NAME = os.environ.get("SEED_ADMIN_NAME", "admin")
ADMIN_PASSWORD = os.environ.get("SEED_ADMIN_PASSWORD", "Admin123456")
ES_PASSWORD = os.environ.get("SEED_ES_PASSWORD", "xiaoai-demo-es-pass")
ES_PORT = int(os.environ.get("SEED_ES_PORT", "9200"))
MYSQL_PASSWORD = os.environ.get("SEED_MYSQL_PASSWORD", "rootpass")
MYSQL_PORT = int(os.environ.get("SEED_MYSQL_PORT", "3306"))
PREFIX = os.environ.get("SEED_PREFIX", "mes")

ES_CONTAINER = "xiaoai-es"
MYSQL_CONTAINER = "xiaoai-mysql"
ES_IMAGE = "davyinsa/elasticsearch-ik:8.18.7"
MYSQL_IMAGE = "mysql:8.0"

DEMO_TABLES = ["production_order", "work_center", "quality_inspection_result_record", "device"]

SEED_SQL = """
CREATE TABLE IF NOT EXISTS production_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64), product_name VARCHAR(128), quantity INT,
  status VARCHAR(32), created_at DATETIME
);
CREATE TABLE IF NOT EXISTS work_center (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32), name VARCHAR(128), workshop VARCHAR(64)
);
CREATE TABLE IF NOT EXISTS quality_inspection_result_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64), inspector_name VARCHAR(64), result VARCHAR(32),
  defect_count INT, inspected_at DATETIME
);
CREATE TABLE IF NOT EXISTS device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_code VARCHAR(64), device_name VARCHAR(128), status VARCHAR(32)
);
INSERT INTO production_order (order_no, product_name, quantity, status, created_at)
SELECT * FROM (SELECT 'PO202611001','servo motor housing',1200,'IN_PROGRESS',NOW()
UNION ALL SELECT 'PO202611002','gear reducer set',560,'DONE',NOW()) seed
WHERE NOT EXISTS (SELECT 1 FROM production_order);
INSERT INTO work_center (code, name, workshop)
SELECT * FROM (SELECT 'WC01','CNC center','workshop-1'
UNION ALL SELECT 'WC02','assembly line A','workshop-2') seed
WHERE NOT EXISTS (SELECT 1 FROM work_center);
INSERT INTO device (device_code, device_name, status)
SELECT * FROM (SELECT 'CNC-01','machining center 01','RUNNING') seed
WHERE NOT EXISTS (SELECT 1 FROM device);
"""


def log(msg):
    print(f"[seed] {msg}", flush=True)


def sh(cmd, **kw):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, **kw)


def docker_state(name):
    r = sh(f"docker inspect -f '{{{{.State.Status}}}}' {name} 2>/dev/null")
    return r.stdout.strip() if r.returncode == 0 else None


def container_on_port(port):
    r = sh(f"docker ps --filter publish={port} --format '{{{{.Names}}}}'")
    names = r.stdout.split()
    return names[0] if names else None


def port_open(port):
    with socket.socket() as s:
        s.settimeout(1)
        return s.connect_ex(("127.0.0.1", port)) == 0


def ensure_containers():
    """Start demo containers and return the name of the mysql container to seed.

    A port already served by another docker container (e.g. one started with
    docker compose per docs/development.md) is reused instead of failing."""
    if docker_state(ES_CONTAINER) is None:
        owner = container_on_port(ES_PORT)
        if owner:
            log(f"port {ES_PORT} already served by container {owner}, reusing it")
        elif port_open(ES_PORT):
            sys.exit(f"port {ES_PORT} is in use by a non-docker service; set SEED_ES_PORT or stop it first")
        else:
            log(f"creating {ES_CONTAINER} ({ES_IMAGE}, first pull may take minutes)")
            r = sh(
                f"docker run -d --name {ES_CONTAINER} -p 127.0.0.1:{ES_PORT}:9200"
                " -e discovery.type=single-node -e xpack.security.enabled=true"
                f" -e ELASTIC_PASSWORD={ES_PASSWORD}"
                ' -e "ES_JAVA_OPTS=-Xms512m -Xmx512m"'
                f" {ES_IMAGE}"
            )
            if r.returncode != 0:
                sys.exit(f"failed to start elasticsearch container: {r.stderr.strip()}")
    elif docker_state(ES_CONTAINER) != "running":
        sh(f"docker start {ES_CONTAINER}")

    mysql = MYSQL_CONTAINER
    if docker_state(MYSQL_CONTAINER) is None:
        owner = container_on_port(MYSQL_PORT)
        if owner and not port_open(MYSQL_PORT):
            sys.exit(f"container {owner} publishes port {MYSQL_PORT} but not on localhost; the backend cannot reach it — set SEED_MYSQL_PORT")
        if owner:
            log(f"port {MYSQL_PORT} already served by container {owner}, reusing it")
            mysql = owner
        elif port_open(MYSQL_PORT):
            sys.exit(f"port {MYSQL_PORT} is in use by a non-docker service; set SEED_MYSQL_PORT or stop it first")
        else:
            log(f"creating {MYSQL_CONTAINER} ({MYSQL_IMAGE})")
            r = sh(
                f"docker run -d --name {MYSQL_CONTAINER} -p 127.0.0.1:{MYSQL_PORT}:3306"
                f" -e MYSQL_ROOT_PASSWORD={MYSQL_PASSWORD} -e MYSQL_DATABASE={PREFIX}_prod"
                f" {MYSQL_IMAGE} --default-authentication-plugin=mysql_native_password"
            )
            if r.returncode != 0:
                sys.exit(f"failed to start mysql container: {r.stderr.strip()}")
    elif docker_state(MYSQL_CONTAINER) != "running":
        sh(f"docker start {MYSQL_CONTAINER}")

    for attempt in range(90):
        if sh(f"docker exec {mysql} mysqladmin ping -uroot -p{MYSQL_PASSWORD} 2>/dev/null").returncode == 0:
            break
        time.sleep(2)
    else:
        sys.exit("mysql did not become ready in time")
    log("mysql is ready")

    for attempt in range(90):
        r = sh(f"curl -s -o /dev/null -w '%{{http_code}}' --max-time 3 -u elastic:{ES_PASSWORD} localhost:{ES_PORT}")
        if r.stdout.strip() == "200":
            break
        time.sleep(2)
    else:
        sys.exit("elasticsearch did not become ready in time")
    log("elasticsearch is ready")
    return mysql


def seed_mysql(mysql):
    db = f"{PREFIX}_prod"
    r = sh(
        f"docker exec -i {mysql} mysql -uroot -p{MYSQL_PASSWORD} -N -B"
        f" -e \"SELECT table_name FROM information_schema.tables WHERE table_schema='{db}'\" 2>/dev/null"
    )
    if r.returncode == 0:
        foreign = sorted(set(r.stdout.split()) - set(DEMO_TABLES))
        if foreign:
            sys.exit(
                f"database {db} already contains non-demo tables ({', '.join(foreign[:5])});"
                " refusing to seed demo data into it — use a different SEED_PREFIX"
            )
    sql = f"CREATE DATABASE IF NOT EXISTS `{db}`;\nUSE `{db}`;\n" + SEED_SQL
    fd, sql_file = tempfile.mkstemp(suffix=".sql", prefix="seed_demo_")
    try:
        with os.fdopen(fd, "w") as f:
            f.write(sql)
        r = sh(f"docker exec -i {mysql} mysql -uroot -p{MYSQL_PASSWORD} < {sql_file} 2>/dev/null")
        if r.returncode != 0:
            sys.exit(f"failed to seed mysql tables: {r.stderr.strip()}")
    finally:
        os.unlink(sql_file)
    log(f"mysql demo tables seeded in {db}")


def api(method, path, body=None, token=None, tolerate=(400, 404, 409)):
    req = urllib.request.Request(
        BACKEND + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode()
            return json.loads(raw) if raw else {}, None
    except urllib.error.HTTPError as e:
        detail = e.read().decode()[:200]
        if e.code in tolerate:
            return None, (e.code, detail)
        sys.exit(f"{method} {path} failed: {e.code} {detail}")
    except urllib.error.URLError:
        sys.exit(
            f"backend unreachable at {BACKEND} — start it first, e.g.\n"
            f"  JWT_SECRET=dev-secret-key-for-local-demo-seed-0123456789 SPRING_ELASTICSEARCH_PASSWORD={ES_PASSWORD}"
            f" SPRING_ELASTICSEARCH_URIS=http://localhost:{ES_PORT} ADMIN_USERS={ADMIN_NAME}"
            " mvn -q -pl app spring-boot:run   (from backend/)"
        )


def paged_get(path, token):
    data, _ = api("GET", path, token=token)
    return data or {}


def main():
    mysql = ensure_containers()
    seed_mysql(mysql)

    # User registration errors (already exists) are fine.
    api("POST", "/v1/auth/register", {"name": ADMIN_NAME, "password": ADMIN_PASSWORD, "display_name": ADMIN_NAME})
    login, err = api("POST", "/v1/auth/login", {"type": "local", "name": ADMIN_NAME, "password": ADMIN_PASSWORD})
    if not login or not login.get("token"):
        sys.exit(f"login failed: {err}")
    token = login["token"]
    log(f"logged in as {ADMIN_NAME}")

    jdbc = f"jdbc:mysql://localhost:{MYSQL_PORT}/{PREFIX}_prod"
    ds_name = f"{PREFIX}-prod-mysql"

    existing = paged_get("/v1/data-sources?page=1&page_size=50", token).get("data", [])
    ds = next((d for d in existing if d.get("name") == ds_name), None)
    if ds:
        log(f"datasource {ds_name} already exists, reusing")
    else:
        ds = api("POST", "/v1/data-sources", {
            "name": ds_name, "type": "MYSQL", "jdbc_url": jdbc,
            "username": "root", "password": MYSQL_PASSWORD,
            "description": "MES production database (read-only demo): orders, work centers, quality, devices",
        }, token=token)[0]
        if not ds:
            sys.exit("failed to create datasource")
        log(f"datasource {ds_name} created")
    ds_id = ds["id"]

    api("POST", f"/v1/data-sources/{ds_id}/tables/batch",
        [{"name": t, "schema": f"{PREFIX}_prod"} for t in DEMO_TABLES], token=token)

    tables = paged_get(f"/v1/data-sources/{ds_id}/tables?page=1&page_size=100", token).get("data", [])
    for t in tables:
        api("POST", f"/v1/data-sources/{ds_id}/tables/{t['id']}/columns/sync", token=token)
    log(f"{len(tables)} tables imported and columns synced")

    subjects = paged_get("/v1/subjects?page=1&page_size=50", token).get("data", [])
    subject = next((s for s in subjects if s.get("name") == "Production Execution"), None)
    if subject:
        log("subject already exists, reusing")
    else:
        subject = api("POST", "/v1/subjects", {
            "datasource_id": ds_id, "name": "Production Execution",
            "description": "Core MES domain covering production orders, work centers, quality inspection and device ledger",
            "aliases": ["production domain", "MES production"],
        }, token=token)[0]
        if not subject:
            sys.exit("failed to create subject")
        log("subject created")
    sub_id = subject["id"]

    linked = paged_get(f"/v1/subjects/{sub_id}/tables?page=1&page_size=100", token).get("data", [])
    linked_ids = {t.get("id") or t.get("table_id") for t in linked} if isinstance(linked, list) else set()
    added = 0
    for t in tables:
        if t["id"] not in linked_ids:
            _, err = api("POST", f"/v1/subjects/{sub_id}/tables", {"table_id": t["id"]}, token=token)
            if err is None:
                added += 1
    log(f"subject tables linked ({added} new)")

    services = paged_get("/v1/mcp-services?page=1&page_size=50", token).get("data", [])
    svc = next((s for s in services if s.get("code") == f"{PREFIX}-data-qa"), None)
    if svc:
        log("mcp service already exists, reusing")
    else:
        svc = api("POST", "/v1/mcp-services", {
            "code": f"{PREFIX}-data-qa", "name": "MES Production QA",
            "description": "MES data Q&A service for shop floor and management, covering orders, quality and devices",
            "data_scopes": [{"scope_type": "SUBJECT", "reference_id": sub_id, "access_mode": "IMPORTED_TABLES"}],
        }, token=token)[0]
        if not svc:
            sys.exit("failed to create mcp service")
        log("mcp service created")
    svc_id = svc["id"]

    detail = paged_get(f"/v1/mcp-services/{svc_id}", token)
    if detail.get("status") == "DRAFT":
        _, err = api("POST", f"/v1/mcp-services/{svc_id}/publish", {"release_note": "initial release"}, token=token)
        log("mcp service published" if err is None else f"publish skipped: {err}")
    else:
        log(f"mcp service already {detail.get('status', 'present')}")

    log(f"done — open the console: datasource {ds_name}, subject 'Production Execution', service '{PREFIX}-data-qa'")


if __name__ == "__main__":
    main()
