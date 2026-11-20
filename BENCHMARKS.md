# Benchmarks

Measured 2026-11-19 in AWS account `687126124183` (SoftSec Dev), region `us-west-2`.

## What was tested

| Item | Value |
|---|---|
| Function | `secbench-kt-jvm`, `java21`, 1024 MB, timeout 30 s (60 s for the failure run) |
| Package under test | `com.sunrun.security:sr-sec-kt-sns-logger:1.0.4` (built from this repo) |
| Event logged | one `record_access`, `id_list` of 1 |
| Primary topic | `arn:aws:sns:us-west-2:687126124183:sr-sec-logging-log-topic-dev` |
| Failover topic | `arn:aws:sns:us-east-2:687126124183:sr-sec-logging-log-topic-failover-dev` |
| Failover override | explicitly set — see the safety note below |

One event per invocation, so these are per-log-call costs, not per-batch.

## Results — JVM

### Cold — first invocation in a fresh container

| Measurement | ms |
|---|---|
| Lambda `Init Duration` | 492 |
| Library init only (`initLambdaSecurityLogging`) | 578 |
| First `logRecordAccess` (lazy `SnsClient` build + DNS/TLS + publish) | 2,633 |

### Warm — subsequent invocations, same container

`logRecordAccess` per call: 22, 18, 22, 19, 49, 20, 17, 26 ms.

Each call is one synchronous SNS publish returning a status map. It does not throw on failure.

### Why the first call costs 2.6 s

`SnsClient` is constructed **lazily inside `AwsSnsTransport.publish()`** via `clients.getOrPut(region)`, not during `initLambdaSecurityLogging()`. So library init looks cheap on a cold container and the *first log call* absorbs client construction plus a fresh TLS handshake. Subsequent calls reuse the client and return to ~20 ms.

This is invisible on services with provisioned concurrency, but real for anything that scales to zero or logs on a rarely-hit path.

### Failure path — SNS endpoint unreachable

`AWS_ENDPOINT_URL=http://192.0.2.1:1`, cold container, 60 s Lambda timeout:

| Measurement | Result |
|---|---|
| `logRecordAccess` wall time | 19,303 ms |
| Whole invocation (`REPORT` Duration) | 20,017 ms |
| Returned value | `{"status": "failure", "message": "Unable to execute HTTP request: Connect to 192.0.2.1:1 [/192.0.2.1] failed: Connect timed out (SDK Attempt Count: 4)"}` |
| Raised exception | none |

**It reports failure correctly.** The library's `publishMessage` returns `{"status":"failure", ...}` when both the primary and failover publishes fail (`SnsPublisher.kt:139`), and that is what this run captured. The returned message names the underlying cause, so a caller can distinguish a dropped event from a delivered one.

**Why it is ~19 s.** No `overrideConfiguration` / `apiCallTimeout` is set on the SDK client, so the failure is bounded only by SDK defaults. The measured message shows `SDK Attempt Count: 4`, and the failover attempt adds a second such budget. So the cost is two SDK retry budgets rather than a deliberate ceiling.

A configurable `apiCallTimeout` (with the tradeoff that a slow-but-alive network then *drops* the event instead of waiting) would let a service bound this. That is a design decision, not an obvious fix, and is not implemented here.

**Single-region behavior is different and fine.** A failure in one region triggers the failover attempt, which normally succeeds. The ~19 s case requires both regions unreachable at once.

## Results — GraalVM native

Not measured from this repository. It is a plain Kotlin JVM library (`kotlin("jvm")` and `maven-publish` only) with no native-image target, no Quarkus plugin, and no Lambda handler class, so a synthetic native build would not reflect how the library is actually deployed.

The reference native implementation is the `a-la-carte-pricing` service, a real GraalVM native image (`container-build`, mandrel JDK 21) with provisioned concurrency. PR `SunRun/a-la-carte-pricing#357` adds temporary `[sec-logging-timing]` instrumentation to that service. Once that build is deployed its CloudWatch logs carry:

```
[sec-logging-timing] phase=init init_ms=<float>
[sec-logging-timing] phase=log status=success|failure invoke=<n> cold=<bool> log_ms=<float>
```

The `invoke` counter separates the first log call (which pays the lazy client build) from warm calls. Those numbers should be copied back into this file once available.

## Method

1. Built this library with `./gradlew jar` and consumed the jar as a file dependency from a throwaway Gradle project that produced a Lambda deploy package (classes at the zip root, runtime jars under `lib/`).
2. Deployed `secbench-kt-jvm` with the dev topic and dev failover topic in the environment.
3. Cold was the first invoke after creating the function; warm were later invokes in the same container. `Init Duration` came from the Lambda `REPORT` line; in-function timers covered init and the log call.
4. For the failure path, the handler deliberately did **not** wrap the call in `ignoreLogError`, so the library's real return value was captured rather than a handler-assumed one. An earlier run of this bench hardcoded a success result in the handler and produced a misleading "returns success on failure" claim; that claim was wrong and has been removed.
5. Deleted the function, IAM role, and log group; verified with `list-functions` and `describe-log-groups` that nothing remained.

## Safety note — the failover default is production

`initSecurityLogging` falls back to the **prod** failover topic in account `000576341507` whenever failover is enabled and no override is set (`SecurityLoggingSns.kt:121`). Any benchmark run outside production must set `SECURITY_LOGS_FAILOVER_TOPIC_ARN` explicitly, or a dev test can publish into production.
