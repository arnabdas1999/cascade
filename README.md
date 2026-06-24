# Cascade — Durable Workflow Engine

A production-grade, durable workflow execution engine built in Java 21 + React — inspired by Temporal/Conductor. Define workflows as JSON trees, trigger runs via REST API, and watch them execute live on a visual canvas. Supports saga compensation, parallel execution, conditional branching, retries, and crash recovery.

---

## Features

| Feature | Details |
|---------|---------|
| **Durable execution** | State is persisted after every step; crashed runs resume from the last completed step |
| **Saga compensation** | On failure, completed steps are rolled back in reverse order automatically |
| **Parallel execution** | Steps in a `parallel` block run concurrently on Java virtual threads |
| **Conditional branching** | `conditional` steps evaluate a SpEL expression and route to `trueBranch` or `falseBranch` |
| **Retry policies** | Per-step `NoRetry`, `FixedDelay`, or `ExponentialBackoff` retry strategies |
| **Sub-workflows** | Inline nested workflow trees within a step |
| **Live run viewer** | React frontend polls run status in real time, highlights the active step on the canvas |
| **REST API** | Full CRUD for workflows + trigger/poll runs |
| **Swagger UI** | Auto-generated API docs at `/swagger-ui.html` |
| **Crash recovery** | On startup, `RecoveryService` resumes any interrupted runs from persisted state |

---

## Tech Stack

### Backend
- **Java 21** — virtual threads, sealed interfaces, pattern matching switch
- **Spring Boot 3.3** — web, data-jpa, validation, actuator
- **PostgreSQL** — persistent state store for runs, snapshots, and audit events
- **Hibernate 6** — JPA entities with `ddl-auto: update`
- **Spring Expression Language (SpEL)** — expression evaluation in transform and conditional steps
- **JUnit 5 + AssertJ** — 24 unit tests; Testcontainers for integration tests

### Frontend
- **React 18 + TypeScript** — Vite scaffold
- **Tailwind CSS v4** — dark-themed UI
- **@xyflow/react** — interactive workflow canvas (React Flow)
- **Lucide React** — icons

### Infrastructure
- **Docker Compose** — local Postgres + backend + frontend
- **Multi-stage Dockerfiles** — `eclipse-temurin:21` for backend, `nginx:alpine` for frontend

---

## Project Structure

```
cascade/
├── backend/                        # Spring Boot application
│   ├── src/main/java/com/cascade/
│   │   ├── api/                    # REST controllers + DTOs
│   │   ├── domain/
│   │   │   ├── node/               # Sealed WorkflowNode tree (Step, Sequential, Parallel)
│   │   │   ├── step/               # Step implementations (Delay, Transform, Http, Conditional…)
│   │   │   ├── retry/              # Retry policies
│   │   │   ├── model/              # Run state machine, ExecutionContext
│   │   │   └── event/              # RunEvent observer
│   │   ├── engine/                 # ExecutionEngine, WorkflowParser, StepFactory, Scheduler
│   │   ├── persistence/            # JPA entities, StateStore, JpaAuditLogger
│   │   └── config/                 # CORS, Executor, OpenAPI config
│   └── src/test/                   # 24 unit tests + integration tests
├── frontend/                       # Vite + React + TypeScript
│   ├── src/
│   │   ├── api.ts                  # Typed API client
│   │   ├── hooks/useRunPoller.ts   # Polls run status until terminal
│   │   ├── components/             # WorkflowCanvas, RunViewer, RunStatusBadge, Modal
│   │   └── pages/                  # WorkflowListPage, WorkflowDetailPage
│   ├── Dockerfile
│   └── nginx.conf                  # Reverse-proxies /api to backend
├── docker-compose.yml
└── README.md
```

---

## Design Patterns Used

| Pattern | Where |
|---------|-------|
| **Sealed interface + Visitor** | `WorkflowNode` (sealed) + `ExecutionEngine implements NodeVisitor` |
| **Template Method** | `AbstractStep.execute()` — validate → doExecute → record |
| **Composite** | `SequentialBlock` and `ParallelBlock` hold `List<WorkflowNode>` |
| **Strategy** | `RetryPolicy` — `NoRetry`, `FixedDelayRetry`, `ExponentialBackoffRetry` |
| **Interpreter** | `WorkflowParser` + `StepFactory` — JSON → `WorkflowNode` tree |
| **Command + Undo** | `Step.execute()` / `Step.compensate()` |
| **Observer** | `RunEventListener` → `JpaAuditLogger` writes immutable audit rows |
| **Decorator** | `LoggingStepDecorator` wraps any `Step` with timing/logging |
| **State machine** | `Run` guards all status transitions (PENDING → RUNNING → COMPLETED/FAILED/COMPENSATED) |

---

## Workflow Definition Format

Workflows are defined as JSON trees. The root node can be any node type.

### Node types

#### `sequential` — runs children one by one
```json
{
  "type": "sequential",
  "id": "main",
  "children": [ ... ]
}
```

#### `parallel` — runs children concurrently (virtual threads)
```json
{
  "type": "parallel",
  "id": "fan-out",
  "children": [ ... ]
}
```

#### `delay` — waits for `ms` milliseconds
```json
{ "type": "delay", "id": "wait", "ms": 1000 }
```

#### `transform` — evaluates a SpEL expression; can reference prior step outputs via `#stepId`
```json
{ "type": "transform", "id": "total", "expr": "#price * 1.18" }
```

#### `http` — makes an outbound HTTP call
```json
{
  "type": "http",
  "id": "fetch",
  "url": "https://api.example.com/data",
  "method": "GET"
}
```

#### `conditional` — branches based on a SpEL condition
```json
{
  "type": "conditional",
  "id": "check",
  "condition": "#total > 100",
  "trueBranch": { "type": "transform", "id": "discount", "expr": "#total * 0.9" },
  "falseBranch": { "type": "transform", "id": "full",     "expr": "#total" }
}
```

#### `subworkflow` — embeds a nested workflow inline
```json
{
  "type": "subworkflow",
  "id": "sub",
  "definition": {
    "root": { "type": "delay", "id": "inner", "ms": 200 }
  }
}
```

### Retry policy (optional, on any step)
```json
{
  "type": "delay", "id": "flaky", "ms": 100,
  "retry": {
    "strategy": "exponential",
    "maxAttempts": 5,
    "initialDelayMs": 200,
    "multiplier": 2.0,
    "maxDelayMs": 10000
  }
}
```
Strategies: `"fixed"` (with `delayMs`), `"exponential"` (with `initialDelayMs`, `multiplier`, `maxDelayMs`), or omit for no retry.

---

## Example Workflow

```json
{
  "root": {
    "type": "sequential",
    "id": "order-flow",
    "children": [
      { "type": "delay",     "id": "init",    "ms": 300 },
      { "type": "transform", "id": "price",   "expr": "49.99" },
      { "type": "transform", "id": "tax",     "expr": "#price * 0.18" },
      { "type": "transform", "id": "total",   "expr": "#price + #tax" },
      {
        "type": "conditional",
        "id": "discount-check",
        "condition": "#total > 60",
        "trueBranch": { "type": "transform", "id": "final", "expr": "#total * 0.9" },
        "falseBranch": { "type": "transform", "id": "final", "expr": "#total" }
      }
    ]
  }
}
```

---

## Running Locally

### Prerequisites
- Java 21+ (JDK 25 works)
- Node.js 18+
- PostgreSQL running locally

### 1. Create the database
```sql
CREATE DATABASE cascade;
```

### 2. Start the backend
```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$wrapperJar = "backend\.mvn\wrapper\maven-wrapper.jar"
$projectBase = "$(pwd)\backend"
Set-Location $projectBase
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/cascade?user=postgres&password=YOUR_PASSWORD"
& "$env:JAVA_HOME\bin\java.exe" -classpath $wrapperJar "-Dmaven.multiModuleProjectDirectory=$projectBase" org.apache.maven.wrapper.MavenWrapperMain spring-boot:run
```

Backend starts on **http://localhost:8080** (or use `-Dserver.port=8081` if 8080 is taken).

### 3. Start the frontend
```bash
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:5173** and proxies `/api` to the backend.

### Running with Docker (full stack)
```bash
docker compose up --build
```
- Frontend: **http://localhost:3000**
- Backend: **http://localhost:8080**
- Swagger UI: **http://localhost:8080/swagger-ui.html**

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/workflows` | List all workflows |
| `POST` | `/api/workflows` | Create a workflow |
| `GET` | `/api/workflows/{id}` | Get a workflow |
| `POST` | `/api/workflows/{id}/runs` | Trigger a run (returns `runId`) |
| `GET` | `/api/workflows/{id}/runs` | List runs for a workflow |
| `GET` | `/api/runs/{runId}` | Get run status + outputs |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/swagger-ui.html` | Interactive API docs |

### Create workflow
```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Workflow",
    "definition": {
      "root": {
        "type": "sequential", "id": "main",
        "children": [
          { "type": "delay", "id": "wait", "ms": 500 },
          { "type": "transform", "id": "result", "expr": "42 * 2" }
        ]
      }
    }
  }'
```

### Trigger a run
```bash
curl -X POST http://localhost:8080/api/workflows/{id}/runs \
  -H "Content-Type: application/json" \
  -d '{"inputs": {}}'
```

### Poll run status
```bash
curl http://localhost:8080/api/runs/{runId}
```

---

## Run Lifecycle

```
PENDING → RUNNING → COMPLETED
                 ↘ FAILED → COMPENSATING → COMPENSATED
```

- **PENDING** — queued, not yet executing
- **RUNNING** — actively executing steps
- **COMPLETED** — all steps finished successfully
- **FAILED** — a step threw a non-retryable error
- **COMPENSATING** — saga rollback in progress
- **COMPENSATED** — all completed steps have been rolled back

---

## Running Tests

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$wrapperJar = "backend\.mvn\wrapper\maven-wrapper.jar"
$projectBase = "$(pwd)\backend"
Set-Location $projectBase
& "$env:JAVA_HOME\bin\java.exe" -classpath $wrapperJar "-Dmaven.multiModuleProjectDirectory=$projectBase" org.apache.maven.wrapper.MavenWrapperMain test "-Dtest=WorkflowParserTest,ExecutionEngineTest,Phase3FeaturesTest,CompensationOrchestratorTest"
```

**24 tests, 0 failures.**

| Test class | Tests | What it covers |
|------------|-------|----------------|
| `WorkflowParserTest` | 4 | JSON → WorkflowNode tree parsing |
| `ExecutionEngineTest` | 5 | Sequential, cross-step references, short-circuit on failure |
| `Phase3FeaturesTest` | 11 | Conditional, parallel, retry, sub-workflow, decorator, parser round-trips |
| `CompensationOrchestratorTest` | 4 | Reverse-order compensation, only-ran-steps, state transitions, resilience |

Integration tests (`WorkflowDurabilityIntegrationTest`) require Docker Desktop for Testcontainers.
