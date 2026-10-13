## Why
SQL Lite synchronous calls need durable asynchronous tasks so users can leave the page and retrieve a report later without executing their SQL.

## What Changes
- Add four authenticated task APIs following shared contract v1.
- Add a database queue, attempts, bounded independent workers and recovery.
- Encrypt short-lived verified Bearer credentials with independent keys; validate reports and maintain retention.
- Deliver local MySQL/WireMock evidence while keeping production integration gated.

## Capabilities
### New Capabilities
- `backend-sql-optimization`: durable optimization tasks, authorization, queue and report lifecycle.
### Modified Capabilities
- `backend-auth-session`: conditionally grant SQL_OPTIMIZE and permit isolated encrypted short-lived task credentials.

## Impact
Only service/**, backend plan and this change. No shared DTO changes, frontend edits, new Agent API or automatic execution. Java 8 compatibility retained on JDK 17.
