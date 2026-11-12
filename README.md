# sr-sec-kt-sns-logger

Kotlin security logging library for Quarkus and JVM AWS Lambda functions. Sends standardized, structured security events to centralized SoftSec AWS SNS topics for compliance, audit, and incident detection.

Ported from [`sr-sec-py-sns-logger`](https://github.com/SunRun/sr-sec-py-sns-logger) `v2.3.0`.

- **Coordinates**: `com.sunrun.security:sr-sec-kt-sns-logger:1.0.3`
- **Repository**: `https://maven.pkg.github.com/sunrun/sr-sec-kt-sns-logger`
- **Target JDK**: 17+ / Kotlin 1.9+

---

## Key Features

- 🔒 **Security-First**: Canonical flat JSON event schema aligned with Sunrun's central normalizer (`sr-sec-logging`).
- 🚀 **Quarkus & Lambda Ready**: Reuses SNS client across invocations; uses Lambda execution role directly.
- 📡 **Local Stdout Mirroring**: Opt-in CloudWatch stdout mirroring (`logToLocalStdout = true` or `SR_SEC_LOG_TO_STDOUT=true` / `SR_SEC_LOCAL_LOGGING=true`).
- ⚡ **Fail-Safe & Non-Throwing**: Returns `Map<String, String>` with `status="success"` or `status="failure"`; never crashes the calling application or request handler.
- 📦 **Auto-Batching**: Automatically chunks large `id_list` record access payloads across multiple SNS messages when exceeding 256 KB.
- 🔍 **Context Extraction**: Automatically captures caller function and file name on every log event.

---

## Installation

### Gradle (Kotlin DSL)

Add the GitHub Packages repository and dependency in `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/sunrun/sr-sec-kt-sns-logger")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("com.sunrun.security:sr-sec-kt-sns-logger:1.0.3")
    implementation("software.amazon.awssdk:sns:2.25.0") // Or Quarkus AWS SNS extension
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
}
```

### Maven (`pom.xml`)

```xml
<dependency>
    <groupId>com.sunrun.security</groupId>
    <artifactId>sr-sec-kt-sns-logger</artifactId>
    <version>1.0.3</version>
</dependency>
```

---

## Initialization

### 1. API Gateway Lambda Adapter (`initLambdaSecurityLogging`)

For Quarkus/JVM Lambdas handling API Gateway events, initialize once at startup:

```kotlin
import com.sunrun.security.CloudEnvType
import com.sunrun.security.LambdaEnv
import com.sunrun.security.initLambdaSecurityLogging

initLambdaSecurityLogging(
    serviceName = "user-service",
    defaultAccountId = "111111111111",
    region = "us-west-2",
    testMode = false,              // Set to true in local/dev tests to print to stdout without publishing to SNS
    logToLocalStdout = false,      // Set to true or use SR_SEC_LOG_TO_STDOUT=true to mirror to CloudWatch logs
    envs = listOf(
        LambdaEnv(names = listOf("production", "prod"), cloudEnvType = CloudEnvType.PROD, cloudEnvName = "production", accountId = "222222222222"),
        LambdaEnv(names = listOf("stage", "staging"), cloudEnvType = CloudEnvType.STAGE, cloudEnvName = "stage", accountId = "333333333333"),
        LambdaEnv(names = listOf("dev", "development"), cloudEnvType = CloudEnvType.DEV, cloudEnvName = "dev", accountId = "111111111111")
    )
)
```

`awsAccountId` or `AWS_ACCOUNT_ID` environment variables override the mapped account.

### 2. General Initialization (`initSecurityLogging`)

For standalone workers, ECS tasks, or custom setups:

```kotlin
import com.sunrun.security.CloudEnvType
import com.sunrun.security.initSecurityLogging

initSecurityLogging(
    cloudEnvType = CloudEnvType.PROD,
    cloudEnvUniqueId = "123456789012",
    cloudEnvName = "production",
    serviceAccountId = "arn:aws:iam::123456789012:role/my-service-role",
    serviceName = "my-service",
    testMode = false,
    logToLocalStdout = false // Set to true or use SR_SEC_LOG_TO_STDOUT=true
)
```

### Environment Configuration & `EnvFields`

Each logging function accepts an `EnvFields` object representing the environment:

```kotlin
import com.sunrun.security.EnvFields

val env = EnvFields(
    cloudEnvType = "prod",
    cloudEnvUniqueId = "123456789012",
    cloudEnvName = "production",
    serviceAccountId = "arn:aws:iam::123456789012:role/my-service-role",
    serviceName = "my-service"
)
```

### Lambda Context Helpers

- `buildLambdaContext(event, preSession, sessionId, identity, actorIdentifier, actorType, userRole)`: Extracts `actor_identifier`, `session_id`, `source_ip_address`, and `user_agent` from API Gateway events.
- `ignoreLogError { ... }`: Wraps a logging call so any signature mismatch or publishing failure never fails the Lambda handler.
- `extractFailureReason(error)`: Extracts clean exception class names for failure details without leaking customer data.

---

## API Function Reference

All logging functions return `Map<String, String>` with at least `"status"` (`"success"` or `"failure"`).

### 1. `logUserLogin` — User Login Events

Log successful or failed login attempts.

```kotlin
import com.sunrun.security.*

val result = logUserLogin(
    env = env,
    eventType = EventType.LOGIN_ATTEMPT,
    userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
    userRole = UserRole.ADMIN,
    status = Status.SUCCESS, // or Status.FAILURE
    authProtocol = AuthProtocol.OAUTH2_JWT,
    actorIdentifier = "alice@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_abc123",
    sourceIpAddress = "192.168.1.100",
    detail = Detail.USER_INITIATED, // or Detail.INVALID_CREDENTIALS
    deviceId = "device-xyz" // optional
)
```

### 2. `logMfaChallenge` — MFA Challenge Verification

Log MFA challenge verification attempts.

```kotlin
val result = logMfaChallenge(
    env = env,
    eventType = EventType.MFA_CHALLENGE,
    userAgent = "Mozilla/5.0 ...",
    userRole = UserRole.ADMIN,
    status = Status.FAILURE,
    mfaType = MfaType.TOTP,
    actorIdentifier = "alice@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_abc123", // Link to same login session_id
    sourceIpAddress = "192.168.1.100",
    detail = Detail.MFA_INVALID_CODE
)
```

### 3. `logUserLogout` — Logout & Session Termination

Log explicit or automated user logouts.

```kotlin
val result = logUserLogout(
    env = env,
    eventType = EventType.USER_LOGOUT,
    userAgent = "Mozilla/5.0 ...",
    userRole = UserRole.ADMIN,
    status = Status.SUCCESS,
    actorIdentifier = "alice@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_abc123",
    sourceIpAddress = "192.168.1.100",
    detail = Detail.USER_INITIATED // or Detail.SESSION_TIMEOUT, Detail.ADMIN_INITIATED
)
```

### 4. `logRecordAccess` — Customer / Sensitive Data Access

Unified function for viewing, creating, editing, deleting, or exporting records.

> **Note on `status`**: `status` defaults to `Status.SUCCESS`, matching the Python library. Pass `Status.FAILURE` when the underlying query or database operation failed. The canonical normalizer strictly requires `status` on all `customer_data_actions` events.

```kotlin
val result = logRecordAccess(
    env = env,
    eventType = EventType.RECORD_ACCESS,
    endpointPath = "/api/v1/customers",
    dataSensitivityLevel = DataSensitivityLevel.CONFIDENTIAL,
    idList = listOf("cust_123", "cust_456"),
    detail = Detail.VIEW_LIST, // or Detail.VIEW_RECORD, Detail.EXPORT_REPORT, Detail.EDIT_RECORD, Detail.CREATE_RECORD, Detail.DELETE_RECORD
    status = Status.SUCCESS, // defaults to Status.SUCCESS; pass Status.FAILURE on failure
    actorIdentifier = "sales.rep@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_abc123",
    sourceIpAddress = "10.0.1.25",
    fieldsAccessed = listOf("email", "phone"), // optional
    userAgent = "Mozilla/5.0 ..." // optional
)
```

### 5. `logPermissionRoleChange` — Permissions & Roles

Log granting, revoking, or updating roles and permissions.

```kotlin
val result = logPermissionRoleChange(
    env = env,
    eventType = EventType.PERMISSION_CHANGE,
    actorIdentifier = "admin@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_admin123",
    targetUserIdentifier = "bob@sunrun.com",
    objectChanged = "role",
    previousValue = "sales_rep",
    newValue = "admin",
    detail = Detail.ADMIN_INITIATED,
    sourceIpAddress = "10.0.1.25"
)
```

### 6. `logUserStatusChange` — User Activation & Locking

Log enabling, disabling, locking, or unlocking accounts.

```kotlin
val result = logUserStatusChange(
    env = env,
    eventType = EventType.USER_STATUS_CHANGE,
    actorIdentifier = "admin@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_admin123",
    targetUserIdentifier = "departed@sunrun.com",
    detail = Detail.USER_DISABLED, // Detail.USER_ENABLED, Detail.USER_LOCKED, Detail.USER_UNLOCKED, Detail.USER_DELETED
    sourceIpAddress = "10.0.1.25"
)
```

### 7. `logImpersonationEvent` — User Impersonation

Log start or stop of user impersonation sessions.

```kotlin
val result = logImpersonationEvent(
    env = env,
    eventType = EventType.IMPERSONATION_EVENT,
    actorIdentifier = "support@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_imp123",
    targetUserIdentifier = "customer@domain.com",
    detail = Detail.IMPERSONATION_START, // or Detail.IMPERSONATION_STOP
    sourceIpAddress = "10.0.1.25"
)
```

### 8. `logUserInviteEvent` — User Invitations

Log invitations sent, accepted, revoked, or expired.

```kotlin
val result = logUserInviteEvent(
    env = env,
    eventType = EventType.USER_INVITE_EVENT,
    actorIdentifier = "admin@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_admin123",
    targetUserEmail = "contractor@partner.com",
    assignedRole = UserRole.PARTNER_ADMIN,
    inviteStatus = InviteStatus.SENT, // InviteStatus.ACCEPTED, InviteStatus.REVOKED, InviteStatus.EXPIRED
    detail = Detail.ADMIN_INITIATED,
    sourceIpAddress = "10.0.1.25"
)
```

### 9. `logApiRequest` — API Gateway / Service Access

Log incoming external or internal API requests.

```kotlin
val result = logApiRequest(
    env = env,
    eventType = EventType.API_REQUEST_PROCESSED,
    actorIdentifier = "service-billing",
    actorType = ActorType.SERVICE_INTERNAL,
    sessionId = "req-uuid-123",
    authProtocol = AuthProtocol.M2M_TOKEN,
    endpointPath = "/api/v1/invoices",
    httpMethod = HttpMethod.POST,
    authorizationStatus = Status.SUCCESS, // Status.SUCCESS or Status.FAILURE
    endpointSensitivity = EndpointSensitivity.CONFIDENTIAL,
    sourceIpAddress = "10.0.2.50"
)
```

### 10. `logMfaStatusChange` — MFA Enrollment Changes

Log MFA device enrollment or removal.

```kotlin
val result = logMfaStatusChange(
    env = env,
    eventType = EventType.MFA_STATUS_CHANGE,
    actorIdentifier = "alice@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_abc123",
    targetObject = "alice@sunrun.com",
    status = Status.SUCCESS,
    mfaId = "mfa_yubikey_01",
    detail = Detail.NEW_MFA_DEVICE, // Detail.MFA_ENABLED, Detail.MFA_DISABLED
    sourceIpAddress = "192.168.1.100"
)
```

### 11. `logPasswordChangeReset` — Password Changes

Log password change and reset events.

```kotlin
val result = logPasswordChangeReset(
    env = env,
    eventType = EventType.PASSWORD_CHANGE_RESET,
    actorIdentifier = "alice@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_abc123",
    targetObject = "alice@sunrun.com",
    status = Status.SUCCESS,
    detail = Detail.PASSWORD_CHANGE, // or Detail.PASSWORD_RESET
    sourceIpAddress = "192.168.1.100"
)
```

### 12. `logApiKeyLifecycle` — API Key Lifecycle

Log API key creation, permissions change, or revocation.

```kotlin
val result = logApiKeyLifecycle(
    env = env,
    eventType = EventType.API_KEY_LIFECYCLE,
    actorIdentifier = "admin@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_admin123",
    targetObject = "api_key_partner_99",
    status = Status.SUCCESS,
    detail = Detail.API_KEY_CREATED, // Detail.API_KEY_REVOKED, Detail.API_KEY_PERMISSIONS_MODIFIED
    sourceIpAddress = "10.0.1.25"
)
```

### 13. `logAuthMechanismModification` — Auth Configuration

Log changes to authentication providers or SSO configs.

```kotlin
val result = logAuthMechanismModification(
    env = env,
    eventType = EventType.AUTH_MECHANISM_MODIFICATION,
    actorIdentifier = "secadmin@sunrun.com",
    actorType = ActorType.HUMAN_INTERNAL,
    sessionId = "sess_sec123",
    targetObject = "okta_sso_config",
    status = Status.SUCCESS,
    detail = Detail.SSO_CONFIG_MODIFIED, // Detail.SSO_CONFIG_CREATED, Detail.SSO_CONFIG_DELETED, Detail.LOCAL_AUTH_ENABLED, etc.
    sourceIpAddress = "10.0.1.25"
)
```

---

## Enum Reference Tables

All constants are defined as Kotlin `object` properties in `SecurityLogFields.kt`.

### LogCategory
| Constant | Value | Description |
|----------|-------|-------------|
| `LogCategory.AUTHN_SESSION` | `"authn_n_session"` | Authentication and session events |
| `LogCategory.AUTHZ_ACCESS` | `"authz_n_access"` | Authorization and access control events |
| `LogCategory.API_ENDPOINT_ACCESS` | `"api_endpoint_access"` | API endpoint requests and access |
| `LogCategory.CUSTOMER_DATA_ACTIONS` | `"customer_data_actions"` | Customer data access (view, modify, export) |
| `LogCategory.KEY_CONFIG_CHANGES` | `"key_config_changes"` | Security configuration changes |

### EventType
| Constant | Value | Category |
|----------|-------|----------|
| `EventType.LOGIN_ATTEMPT` | `"login_attempt"` | `authn_n_session` |
| `EventType.USER_LOGOUT` | `"user_logout"` | `authn_n_session` |
| `EventType.MFA_CHALLENGE` | `"mfa_challenge"` | `authn_n_session` |
| `EventType.PERMISSION_CHANGE` | `"permission_change"` | `authz_n_access` |
| `EventType.USER_STATUS_CHANGE` | `"user_status_change"` | `authz_n_access` |
| `EventType.IMPERSONATION_EVENT` | `"impersonation_event"` | `authz_n_access` |
| `EventType.USER_INVITE_EVENT` | `"user_invite_event"` | `authz_n_access` |
| `EventType.API_REQUEST_PROCESSED` | `"api_request_processed"` | `api_endpoint_access` |
| `EventType.RECORD_ACCESS` | `"record_access"` | `customer_data_actions` |
| `EventType.MFA_STATUS_CHANGE` | `"mfa_status_change"` | `key_config_changes` |
| `EventType.PASSWORD_CHANGE_RESET` | `"password_change_reset"` | `key_config_changes` |
| `EventType.API_KEY_LIFECYCLE` | `"api_key_lifecycle"` | `key_config_changes` |
| `EventType.AUTH_MECHANISM_MODIFICATION` | `"auth_mechanism_modification"` | `key_config_changes` |

*(Note: The backend normalizer also recognizes `api.request.success` and `api.request.failure` for API events).*

### Status
| Constant | Value |
|----------|-------|
| `Status.SUCCESS` | `"status.general.success"` |
| `Status.FAILURE` | `"status.general.failure"` |

### ActorType
| Constant | Value | Description |
|----------|-------|-------------|
| `ActorType.HUMAN_INTERNAL` | `"actor.human.internal"` | Sunrun employee or internal contractor |
| `ActorType.HUMAN_PARTNER` | `"actor.human.partner"` | Partner organization employee |
| `ActorType.HUMAN_CUSTOMER` | `"actor.human.customer"` | Sunrun customer / homeowner |
| `ActorType.HUMAN_EXTERNAL` | `"actor.human.external"` | External human |
| `ActorType.SERVICE_INTERNAL` | `"actor.service.internal"` | Internal Sunrun microservice / Lambda |
| `ActorType.SERVICE_PARTNER` | `"actor.service.partner"` | Partner service or API integration |
| `ActorType.SERVICE_CUSTOMER` | `"actor.service.customer"` | Customer-owned application / webhook |
| `ActorType.SYSTEM_SELF` | `"actor.system.self"` | Automated system execution (cron, cleanup) |

### UserRole
| Constant | Value | Description |
|----------|-------|-------------|
| `UserRole.ADMIN` | `"role.classification.admin"` | Administrative user |
| `UserRole.SALES_REP` | `"role.classification.sales_rep"` | Sales representative |
| `UserRole.CUSTOMER_SUPPORT` | `"role.classification.customer_support"` | Customer support user |
| `UserRole.PARTNER_ADMIN` | `"role.classification.partner_admin"` | Partner administrative user |
| `UserRole.CUSTOMER_USER` | `"role.classification.customer_user"` | Customer user |
| `UserRole.NON_ADMIN_INTERNAL` | `"role.classification.non_admin_internal"` | Standard internal Sunrun employee |
| `UserRole.NOT_AVAILABLE` | `"role.classification.not_available"` | Role cannot be determined |

### HttpMethod
| Constant | Value |
|----------|-------|
| `HttpMethod.GET` | `"http.method.GET"` |
| `HttpMethod.POST` | `"http.method.POST"` |
| `HttpMethod.PUT` | `"http.method.PUT"` |
| `HttpMethod.PATCH` | `"http.method.PATCH"` |
| `HttpMethod.DELETE` | `"http.method.DELETE"` |
| `HttpMethod.HEAD` | `"http.method.HEAD"` |
| `HttpMethod.OPTIONS` | `"http.method.OPTIONS"` |

### CloudEnvType
| Constant | Value |
|----------|-------|
| `CloudEnvType.PROD` | `"prod"` |
| `CloudEnvType.STAGE` | `"stage"` |
| `CloudEnvType.TEST` | `"test"` |
| `CloudEnvType.DEV` | `"dev"` |

### CloudServiceApiType
| Constant | Value | Description |
|----------|-------|-------------|
| `CloudServiceApiType.AWS_LAMBDA` | `"aws_lambda"` | AWS Lambda function |
| `CloudServiceApiType.GKE_NAMESPACE` | `"gke_namespace"` | Google Kubernetes Engine namespace |
| `CloudServiceApiType.ECS_SERVICE` | `"ecs_service"` | Amazon ECS service |

### DataSensitivityLevel & EndpointSensitivity
| Constant | Value | Description |
|----------|-------|-------------|
| `DataSensitivityLevel.PUBLIC` | `"sensitivity.level.public"` | Publicly shareable data |
| `DataSensitivityLevel.NON_PUBLIC` | `"sensitivity.level.non_public"` | Internal business data, not customer-specific |
| `DataSensitivityLevel.CONFIDENTIAL` | `"sensitivity.level.confidential"` | Customer/business data (records, agreements) |
| `DataSensitivityLevel.RESTRICTED` | `"sensitivity.level.restricted"` | Highly sensitive: PII, SSN, banking, passwords |

### Detail (Categorized)
| Subcategory | Constant | Value |
|-------------|----------|-------|
| **Auth Failures** | `Detail.INVALID_CREDENTIALS` | `"detail.auth.invalid_credentials"` |
| | `Detail.ACCOUNT_LOCKED` | `"detail.auth.account_locked"` |
| | `Detail.IP_RESTRICTED` | `"detail.auth.ip_restricted"` |
| | `Detail.MFA_REQUIRED` | `"detail.auth.mfa_required"` |
| | `Detail.POLICY_VIOLATION` | `"detail.auth.policy_violation"` |
| | `Detail.CAPTCHA_FAILURE` | `"detail.auth.captcha_failure"` |
| | `Detail.TOKEN_EXPIRED` | `"detail.auth.token_expired"` |
| | `Detail.TOKEN_INVALID` | `"detail.auth.token_invalid"` |
| | `Detail.UNAUTHORIZED_ACCESS` | `"detail.auth.unauthorized_access"` |
| | `Detail.RATE_LIMIT_EXCEEDED` | `"detail.auth.rate_limit_exceeded"` |
| **Triggers** | `Detail.USER_INITIATED` | `"detail.trigger.user_initiated"` |
| | `Detail.ADMIN_INITIATED` | `"detail.trigger.admin_initiated"` |
| | `Detail.SYSTEM_AUTOMATED` | `"detail.trigger.system_automated"` |
| | `Detail.SYSTEM_POLICY_VIOLATION` | `"detail.trigger.system_policy_violation"` |
| | `Detail.SESSION_TIMEOUT` | `"detail.trigger.session_timeout"` |
| | `Detail.CONCURRENT_SESSION` | `"detail.trigger.concurrent_session"` |
| | `Detail.FAILED_ATTEMPTS_THRESHOLD` | `"detail.trigger.failed_attempts_threshold"` |
| **Customer Data** | `Detail.VIEW_LIST` | `"detail.action.view_list"` |
| | `Detail.MODIFY_CUSTOMER_DATA` | `"detail.action.modify_customer_data"` |
| | `Detail.EXPORT_REPORT` | `"detail.action.export_report"` |
| | `Detail.VIEW_RECORD` | `"detail.action.view_record"` |
| | `Detail.EDIT_RECORD` | `"detail.action.edit_record"` |
| | `Detail.CREATE_RECORD` | `"detail.action.create_record"` |
| | `Detail.DELETE_RECORD` | `"detail.action.delete_record"` |
| **User Status** | `Detail.USER_DISABLED` | `"detail.action.user_disabled"` |
| | `Detail.USER_ENABLED` | `"detail.action.user_enabled"` |
| | `Detail.USER_DELETED` | `"detail.action.user_deleted"` |
| | `Detail.USER_LOCKED` | `"detail.action.user_locked"` |
| | `Detail.USER_UNLOCKED` | `"detail.action.user_unlocked"` |
| **Impersonation** | `Detail.IMPERSONATION_START` | `"detail.action.impersonation_start"` |
| | `Detail.IMPERSONATION_STOP` | `"detail.action.impersonation_stop"` |
| **MFA Actions** | `Detail.MFA_DISABLED` | `"detail.action.mfa_disabled"` |
| | `Detail.MFA_ENABLED` | `"detail.action.mfa_enabled"` |
| | `Detail.NEW_MFA_DEVICE` | `"detail.action.new_mfa_device"` |
| **MFA Challenge** | `Detail.MFA_INVALID_CODE` | `"detail.mfa.invalid_code"` |
| | `Detail.MFA_EXPIRED_CODE` | `"detail.mfa.expired_code"` |
| | `Detail.MFA_DEVICE_NOT_ENROLLED` | `"detail.mfa.device_not_enrolled"` |
| | `Detail.MFA_TOO_MANY_ATTEMPTS` | `"detail.mfa.too_many_attempts"` |
| **Passwords** | `Detail.PASSWORD_CHANGE` | `"detail.action.password_change"` |
| | `Detail.PASSWORD_RESET` | `"detail.action.password_reset"` |
| **API Keys** | `Detail.API_KEY_CREATED` | `"detail.api_key.created"` |
| | `Detail.API_KEY_REVOKED` | `"detail.api_key.revoked"` |
| | `Detail.API_KEY_PERMISSIONS_MODIFIED` | `"detail.api_key.permissions_modified"` |
| **Auth Mechanisms** | `Detail.SSO_CONFIG_CREATED` | `"detail.auth.sso_config_created"` |
| | `Detail.SSO_CONFIG_MODIFIED` | `"detail.auth.sso_config_modified"` |
| | `Detail.SSO_CONFIG_DELETED` | `"detail.auth.sso_config_deleted"` |
| | `Detail.LOCAL_AUTH_ENABLED` | `"detail.auth.local_auth_enabled"` |
| | `Detail.LOCAL_AUTH_DISABLED` | `"detail.auth.local_auth_disabled"` |
| | `Detail.NEW_SSO_PROVIDER` | `"detail.action.new_sso_provider"` |
| | `Detail.ENABLE_LOCAL_AUTHN` | `"detail.action.enable_local_authn"` |
| | `Detail.DISABLE_SSO` | `"detail.action.disable_sso"` |
| **System** | `Detail.INTERNAL_ERROR` | `"detail.system.internal_error"` |
| | `Detail.SERVICE_UNAVAILABLE` | `"detail.system.service_unavailable"` |
| | `Detail.MAINTENANCE` | `"detail.system.maintenance"` |
| | `Detail.INVALID_REQUEST` | `"detail.client.invalid_request"` |
| | `Detail.NOT_APPLICABLE` | `"detail.not_applicable"` |

### Authorization Status & Session Termination Notes
- **`authorizationStatus`**: Used in `logApiRequest`. Pass `Status.SUCCESS` or `Status.FAILURE`. The normalizer promotes this value to `status` if `status` is not set.
- **Session Termination Reason**: Used in `logUserLogout` via the `detail` parameter. Pass a valid `Detail` constant: `Detail.USER_INITIATED`, `Detail.SESSION_TIMEOUT`, `Detail.ADMIN_INITIATED`, or `Detail.CONCURRENT_SESSION`. There is no separate `SessionTerminationReason` object.

### AuthProtocol
| Constant | Value | Description |
|----------|-------|-------------|
| `AuthProtocol.BASIC_AUTH` | `"auth.protocol.basic_auth"` | HTTP Basic Authentication |
| `AuthProtocol.FORM_BASED` | `"auth.protocol.form_based"` | Form-based username/password login |
| `AuthProtocol.API_KEY` | `"auth.protocol.api_key"` | API key authentication |
| `AuthProtocol.M2M_TOKEN` | `"auth.protocol.m2m_token"` | Machine-to-machine token |
| `AuthProtocol.SESSION_COOKIE` | `"auth.protocol.session_cookie"` | Session cookie authentication |
| `AuthProtocol.OAUTH2_JWT` | `"auth.protocol.oauth2.jwt"` | OAuth2 with JWT tokens |
| `AuthProtocol.OAUTH2_CLIENT_CREDENTIALS` | `"auth.protocol.oauth2.client_credentials"` | OAuth2 client credentials grant |
| `AuthProtocol.OAUTH2_AUTHORIZATION_CODE` | `"auth.protocol.oauth2.authorization_code"` | OAuth2 authorization code flow |
| `AuthProtocol.OAUTH2_IMPLICIT` | `"auth.protocol.oauth2.implicit"` | OAuth2 implicit flow |
| `AuthProtocol.OAUTH2_PASSWORD_GRANT` | `"auth.protocol.oauth2.password_grant"` | OAuth2 resource owner password grant |
| `AuthProtocol.SAML` | `"auth.protocol.saml"` | SAML SSO authentication |
| `AuthProtocol.OIDC` | `"auth.protocol.oidc"` | OpenID Connect authentication |
| `AuthProtocol.NONE` | `"auth.protocol.none"` | No authentication (public endpoints) |

### InviteStatus
| Constant | Value |
|----------|-------|
| `InviteStatus.SENT` | `"sent"` |
| `InviteStatus.ACCEPTED` | `"accepted"` |
| `InviteStatus.REVOKED` | `"revoked"` |
| `InviteStatus.EXPIRED` | `"expired"` |

### MfaType
| Constant | Value |
|----------|-------|
| `MfaType.SMS` | `"sms"` |
| `MfaType.TOTP` | `"totp"` |
| `MfaType.PUSH` | `"push"` |
| `MfaType.EMAIL` | `"email"` |
| `MfaType.OKTA_VERIFY` | `"okta_verify"` |
| `MfaType.AUTHENTICATOR_APP` | `"authenticator_app"` |
| `MfaType.HARDWARE_TOKEN` | `"hardware_token"` |
| `MfaType.BIOMETRIC` | `"biometric"` |
| `MfaType.BACKUP_CODES` | `"backup_codes"` |
