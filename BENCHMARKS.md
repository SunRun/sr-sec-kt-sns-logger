# Benchmarks

Measured 2026-11-19 in AWS account `687126124183` (SoftSec Dev), region `us-west-2`.

| Item | Value |
|---|---|
| Function | `secbench-kt-jvm`, `java21`, 1024 MB, timeout 30 s |
| Package under test | `com.sunrun.security:sr-sec-kt-sns-logger:1.0.4` (built from this repo) |
| Topic | `arn:aws:sns:us-west-2:687126124183:sr-sec-logging-log-topic-dev` |
| Failover | dev failover topic, explicitly set (never the prod default) |
| Event | one `record_access`, `id_list` of 1 |

## Results — JVM

### Cold (first invocation in a fresh container)

| Measurement | ms |
|---|---|
| `Init Duration` from Lambda `REPORT` | 492 |
| Library init only (`initLambdaSecurityLogging`) | 578 |
| First `logRecordAccess` (lazy `SnsClient` build + DNS/TLS + publish) | 2,633 |

### Warm (subsequent invocations, same container)

`logRecordAccess` per call: 22, 18, 22, 19, 49, 20, 17, 26 ms.

### Failure path (SNS endpoint blackholed)

`AWS_ENDPOINT_URL=http://192.0.2.1:1`, cold container:

| Measurement | Result |
|---|---|
| `logRecordAccess` wall time | 18,866 ms |
| Returned value | `{"status": "success"}` — **nothing was published** |
| Raised exception | none |

The client builds `SnsClient` with no `overrideConfiguration` and no `apiCallTimeout`, so a blackholed endpoint is bounded only by SDK defaults plus the failover attempt. Worse, the failure is reported as success, which means a caller cannot distinguish a delivered event from a dropped one.

## Results — GraalVM native

Not measured from this repository: it is a plain Kotlin JVM library (`kotlin("jvm")` and `maven-publish` only) with no native-image target, no Quarkus plugin, and no Lambda handler class. A synthetic native build would not reflect how the library is actually deployed.

The reference native measurement is the `a-la-carte-pricing` service, which is a real GraalVM native image (`container-build`, mandrel JDK 21) with provisioned concurrency. PR `SunRun/a-la-carte-pricing#357` adds temporary `[sec-logging-timing]` instrumentation to that service; once deployed, its CloudWatch logs carry:

```
[sec-logging-timing] phase=init init_ms=<float>
[sec-logging-timing] phase=log status=success|failure invoke=<n> cold=<bool> log_ms=<float>
```

The `invoke` counter separates the first log call (which pays the lazy client build) from warm calls. Those numbers should be copied back here once available.

## Method

1. Built this library with `./gradlew jar` and consumed the jar as a file dependency from a throwaway Gradle project that produced a Lambda deploy package (classes at the zip root, runtime jars under `lib/`).
2. Deployed `secbench-kt-jvm` with the dev topic and dev failover topic in the environment. This matters: `initSecurityLogging` defaults failover to the **prod** failover topic when no override is supplied, so a dev bench that omits the env var will publish into production.
3. Cold was the first invoke after creating the function; warm were later invokes in the same container. `Init Duration` came from the Lambda `REPORT` line; in-function timers covered init and the log call.
4. Failure path re-ran with `AWS_ENDPOINT_URL` pointing at a non-routable address.
5. Deleted the function, IAM role, and log group; verified with `list-functions` that nothing remained.
