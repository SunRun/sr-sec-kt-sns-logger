# sr-sec-kt-sns-logger

Kotlin port of [`sr-sec-py-sns-logger`](https://github.com/SunRun/sr-sec-py-sns-logger) `v2.3.0` for Quarkus Lambdas.

Coordinates: `com.sunrun.security:sr-sec-kt-sns-logger:1.0.0`

Repository: `https://maven.pkg.github.com/sunrun/sr-sec-kt-sns-logger`

The jar publishes security events to the SoftSec SNS topics in account `000576341507`. It uses the Lambda execution role. Publish errors are returned as `status=failure` and do not throw.

`logRecordAccess` defaults `status` to `status.general.success`, matching the Python package. Pass `Status.FAILURE` when the handler failed.
