#!/usr/bin/env python3
"""Local-only MySQL + verified fixture auth + callback-capable Agent stub + API/Worker.
Secrets are generated at runtime and written only to an owner-readable temporary directory.
Ctrl-C shuts down only the processes/container created by this invocation.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import signal
import subprocess
import tempfile
import threading
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid

SERVICE = Path(__file__).resolve().parents[1]


def http(base, path, token, method="GET", body=None, internal_key=None):
    headers = {"Authorization": "Bearer " + token, "X-Request-Id": str(uuid.uuid4())}
    if internal_key:
        headers["X-Internal-Service-Key"] = internal_key
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body, ensure_ascii=False).encode()
    if method == "POST" and path.startswith("/api/v1/sql-optimization/"):
        headers["Idempotency-Key"] = str(uuid.uuid4())
    with urlopen(Request(base + path, data=data, headers=headers, method=method), timeout=15) as response:
        return json.load(response)


def private_json(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)


def serve(args):
    jar = SERVICE / "target/zorth-web-sql-service-0.1.0-SNAPSHOT.jar"
    if not jar.is_file():
        raise SystemExit("Build service first with JDK17 mvn -Dapi.version=1.44 verify")
    runtime = Path(tempfile.mkdtemp(prefix="sql-optimizer-backend-"))
    token, auth_key, callback_key, password = (secrets.token_urlsafe(32) for _ in range(4))
    db_key, vault_key, cursor_key = (base64.b64encode(secrets.token_bytes(32)).decode() for _ in range(3))
    expiry = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat().replace("+00:00", "Z")
    base = f"http://127.0.0.1:{args.api_port}"
    container = "sql-optimizer-backend-fixture-" + uuid.uuid4().hex[:10]
    client = {"baseUrl": base, "token": token, "runtime": str(runtime)}
    private_json(runtime / "client.json", client)
    stop = threading.Event()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *unused):
            pass  # Do not log SQL, credentials or bodies.

        def respond(self, status, data):
            payload = json.dumps(data, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def authorized(self):
            return secrets.compare_digest(self.headers.get("Authorization", ""), "Bearer " + token)

        def do_GET(self):
            if self.path != "/fixture/auth" or not self.authorized() or not secrets.compare_digest(self.headers.get("X-Internal-Service-Key", ""), auth_key):
                return self.respond(401, {"code": "UNAUTHENTICATED"})
            self.respond(200, {"userId": "optimizer-fixture-user", "username": "optimizer-fixture", "displayName": "Local fixture", "product": {"id": "optimizer-fixture-product", "name": "Local fixture"}, "tokenExpiresAt": expiry})

        def do_POST(self):
            if self.path != "/api/v1/agents/sql-lite-optimizer/chat" or not self.authorized():
                return self.respond(401, {"code": "UNAUTHENTICATED"})
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if size < 1 or size > 262144:
                    return self.respond(400, {"code": "INVALID"})
                payload = json.loads(self.rfile.read(size))
                first, second, sql = payload["message"].split("\n", 2)
                datasource = first.removeprefix("datasourceId: ")
                database = second.removeprefix("database: ")
                if "fixture:fail" in sql:
                    return self.respond(503, {"code": "FIXTURE_UNAVAILABLE"})
                if "fixture:limitations" in sql:
                    report = {"verdict": "本地 Stub：证据不足", "issues": [], "suggestions": [], "limitations": [{"ruleId": None, "reason": "Stub 场景模拟 EXPLAIN 不支持或索引信息不完整，未运行真实模型"}]}
                else:
                    route = "/internal/api/v1/agent/data-sources/" + datasource + "/sql/"
                    validated = http(base, route + "validate", token, "POST", {"sql": sql, "database": database}, callback_key)
                    if not validated.get("valid", False):
                        return self.respond(400, {"code": "FIXTURE_SQL_INVALID"})
                    plan = http(base, route + "explain", token, "POST", {"sql": sql, "database": database, "executionId": str(uuid.uuid4())}, callback_key)
                    report = {"verdict": "本地 Stub：已读取真实数据库计划，未运行真实模型", "issues": [], "suggestions": [], "limitations": [{"ruleId": None, "reason": "仅用于本地认证、EXPLAIN 回调与异步生命周期验收；无真实 Agent 质量结论"}]}
                    private_json(runtime / "last-callback.json", {"validate": validated, "explain": plan})
                self.respond(200, {"sessionId": payload["sessionId"], "structuredOutput": report})
            except Exception:
                self.respond(500, {"code": "FIXTURE_CALLBACK_FAILED"})

    fixture = ThreadingHTTPServer(("127.0.0.1", args.fixture_port), Handler)
    thread = threading.Thread(target=fixture.serve_forever, daemon=True)
    thread.start()
    java = None
    container_started = False
    old_handlers = {}
    for sig in (signal.SIGINT, signal.SIGTERM):
        old_handlers[sig] = signal.signal(sig, lambda *_: stop.set())
    try:
        envfile = runtime / "mysql.env"
        fd = os.open(envfile, os.O_WRONLY | os.O_CREAT, 0o600)
        with os.fdopen(fd, "w") as stream:
            stream.write(f"MYSQL_ROOT_PASSWORD={password}\nMYSQL_DATABASE=optimizer_fixture\nMYSQL_USER=editor\nMYSQL_PASSWORD={password}\n")
        subprocess.run(["docker", "run", "--rm", "-d", "--name", container, "--env-file", str(envfile), "-p", "127.0.0.1::3306", "mysql:8.0"], check=True, stdout=subprocess.DEVNULL)
        container_started = True
        db_port = subprocess.check_output(["docker", "port", container, "3306/tcp"], text=True).strip().rsplit(":", 1)[1]
        for _ in range(90):
            if stop.is_set():
                return
            probe = subprocess.run(["docker", "exec", container, "mysqladmin", "-h127.0.0.1", "ping", "--silent"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if probe.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("Fixture MySQL startup timed out")
        # Keep fixture service configuration independent of personal/production SQL_EDITOR overrides.
        env = {k: v for k, v in os.environ.items() if not k.startswith("SQL_EDITOR_") and not k.startswith("SPRING_")}

        env.update({"SERVER_PORT": str(args.api_port), "SQL_EDITOR_MANAGEMENT_PORT": "-1", "SPRING_PROFILES_ACTIVE": "local-optimization", "SQL_EDITOR_METADATA_URL": f"jdbc:mysql://127.0.0.1:{db_port}/optimizer_fixture", "SQL_EDITOR_METADATA_USERNAME": "editor", "SQL_EDITOR_METADATA_PASSWORD": password, "SQL_EDITOR_AUTH_CONTEXT_URL": f"http://127.0.0.1:{args.fixture_port}/fixture/auth", "SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY": auth_key, "SQL_EDITOR_AUTH_CACHE_TTL_SECONDS": "0", "SQL_EDITOR_CREDENTIAL_CURRENT_KEY": db_key, "SQL_EDITOR_CREDENTIAL_CURRENT_VERSION": "v1", "SQL_EDITOR_OPTIMIZATION_ENABLED": "true", "SQL_EDITOR_OPTIMIZATION_INTEGRATION_READY": "true", "SQL_EDITOR_OPTIMIZATION_ALLOWED_USERS": "optimizer-fixture-user", "SQL_EDITOR_OPTIMIZATION_ALLOWED_PRODUCTS": "optimizer-fixture-product", "SQL_EDITOR_OPTIMIZATION_CREDENTIAL_KEY_V1": vault_key, "SQL_EDITOR_OPTIMIZATION_CURSOR_KEY": cursor_key, "SQL_EDITOR_OPTIMIZATION_AGENT_BASE_URL": f"http://127.0.0.1:{args.fixture_port}", "SQL_EDITOR_AGENT_INTERNAL_CALLER_KEY_REQUIRED": "true", "SQL_EDITOR_AGENT_INTERNAL_CALLER_KEY": callback_key})
        jdk = args.java_home or os.environ.get("JAVA_HOME") or "/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home"
        with (runtime / "api.log").open("w") as log:
            java = subprocess.Popen([str(Path(jdk) / "bin/java"), "-jar", str(jar)], cwd=SERVICE, env=env, stdout=log, stderr=log)
        for _ in range(90):
            if stop.is_set():
                return
            if java.poll() is not None:
                raise RuntimeError("API startup failed; inspect " + str(runtime / "api.log"))
            try:
                session = http(base, "/api/v1/session", token)
                if "SQL_OPTIMIZE" in session["capabilities"]:
                    break
            except (URLError, OSError):
                pass
            time.sleep(1)
        else:
            raise RuntimeError("API readiness timed out")
        source = http(base, "/api/v1/data-sources", token, "POST", {"name": "Optimizer local fixture", "engine": "MYSQL", "host": "127.0.0.1", "port": int(db_port), "username": "editor", "password": password, "defaultDatabase": "optimizer_fixture", "sslMode": "DISABLED", "connectTimeoutSeconds": 10, "properties": {}})
        tasks = []
        for name, sql in (("Local callback success", "SELECT 1"), ("Local evidence gap", "SELECT 1 /* fixture:limitations */"), ("Local upstream failure", "SELECT 1 /* fixture:fail */")):
            tasks.append(http(base, "/api/v1/sql-optimization/tasks", token, "POST", {"name": name, "datasourceId": source["id"], "database": "optimizer_fixture", "sql": sql}))
        for _ in range(60):
            details = [http(base, "/api/v1/sql-optimization/tasks/" + task["id"], token) for task in tasks]
            if all(detail["status"] in ("SUCCEEDED", "FAILED") for detail in details):
                break
            time.sleep(0.5)
        expected = ["SUCCEEDED", "SUCCEEDED", "FAILED"]
        if [detail["status"] for detail in details] != expected:
            private_json(runtime / "failed-smoke.json", details)
            raise RuntimeError("Local callback smoke failed; inspect " + str(runtime / "failed-smoke.json"))
        private_json(runtime / "tasks.json", details)
        private_json(runtime / "runtime.json", {"pid": os.getpid(), "apiPid": java.pid, "container": container, "baseUrl": base, "datasourceId": source["id"], "database": "optimizer_fixture", "taskIds": [task["id"] for task in tasks]})
        print(json.dumps({"baseUrl": base, "clientFile": str(runtime / "client.json"), "resultsFile": str(runtime / "tasks.json"), "taskIds": [task["id"] for task in tasks], "statuses": expected, "fixturePid": os.getpid()}, ensure_ascii=False), flush=True)
        if not args.smoke_only:
            while not stop.wait(1):
                if java.poll() is not None:
                    raise RuntimeError("Local API exited")
    finally:
        fixture.shutdown()
        fixture.server_close()
        if java is not None and java.poll() is None:
            java.terminate()
            try:
                java.wait(timeout=10)
            except subprocess.TimeoutExpired:
                java.kill()
                java.wait()
        if container_started:
            subprocess.run(["docker", "stop", "-t", "5", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for sig, handler in old_handlers.items():
            signal.signal(sig, handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-port", type=int, default=18080)
    parser.add_argument("--fixture-port", type=int, default=18090)
    parser.add_argument("--java-home")
    parser.add_argument("--smoke-only", action="store_true")
    parser.add_argument("--client", type=Path, help="Use a running fixture's private client.json to issue an API request")
    parser.add_argument("--method", default="GET", choices=["GET", "POST"])
    parser.add_argument("--path", default="/api/v1/sql-optimization/tasks")
    parser.add_argument("--body-file", type=Path)
    args = parser.parse_args()
    if args.client:
        client = json.loads(args.client.read_text())
        body = json.loads(args.body_file.read_text()) if args.body_file else None
        print(json.dumps(http(client["baseUrl"], args.path, client["token"], args.method, body), ensure_ascii=False, indent=2))
    else:
        serve(args)


if __name__ == "__main__":
    main()
