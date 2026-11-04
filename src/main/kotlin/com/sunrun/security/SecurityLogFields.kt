package com.sunrun.security

/** Field strings copied from sr-sec-py-sns-logger v2.3.0 security_log_fields.py. */
object Status {
    const val SUCCESS = "status.general.success"
    const val FAILURE = "status.general.failure"
}

object ActorType {
    const val HUMAN_INTERNAL = "actor.human.internal"
    const val HUMAN_PARTNER = "actor.human.partner"
    const val HUMAN_CUSTOMER = "actor.human.customer"
    const val HUMAN_EXTERNAL = "actor.human.external"
    const val SERVICE_INTERNAL = "actor.service.internal"
    const val SERVICE_PARTNER = "actor.service.partner"
    const val SERVICE_CUSTOMER = "actor.service.customer"
    const val SYSTEM_SELF = "actor.system.self"
}

object LogCategory {
    const val AUTHN_SESSION = "authn_n_session"
    const val AUTHZ_ACCESS = "authz_n_access"
    const val API_ENDPOINT_ACCESS = "api_endpoint_access"
    const val CUSTOMER_DATA_ACTIONS = "customer_data_actions"
    const val KEY_CONFIG_CHANGES = "key_config_changes"
}

object EventType {
    const val LOGIN_ATTEMPT = "login_attempt"
    const val USER_LOGOUT = "user_logout"
    const val MFA_CHALLENGE = "mfa_challenge"
    const val PERMISSION_CHANGE = "permission_change"
    const val USER_STATUS_CHANGE = "user_status_change"
    const val IMPERSONATION_EVENT = "impersonation_event"
    const val USER_INVITE_EVENT = "user_invite_event"
    const val API_REQUEST_PROCESSED = "api_request_processed"
    const val RECORD_ACCESS = "record_access"
    const val MFA_STATUS_CHANGE = "mfa_status_change"
    const val PASSWORD_CHANGE_RESET = "password_change_reset"
    const val API_KEY_LIFECYCLE = "api_key_lifecycle"
    const val AUTH_MECHANISM_MODIFICATION = "auth_mechanism_modification"
}

object AuthProtocol {
    const val BASIC_AUTH = "auth.protocol.basic_auth"
    const val FORM_BASED = "auth.protocol.form_based"
    const val API_KEY = "auth.protocol.api_key"
    const val M2M_TOKEN = "auth.protocol.m2m_token"
    const val SESSION_COOKIE = "auth.protocol.session_cookie"
    const val OAUTH2_JWT = "auth.protocol.oauth2.jwt"
    const val OAUTH2_CLIENT_CREDENTIALS = "auth.protocol.oauth2.client_credentials"
    const val OAUTH2_AUTHORIZATION_CODE = "auth.protocol.oauth2.authorization_code"
    const val OAUTH2_IMPLICIT = "auth.protocol.oauth2.implicit"
    const val OAUTH2_PASSWORD_GRANT = "auth.protocol.oauth2.password_grant"
    const val SAML = "auth.protocol.saml"
    const val OIDC = "auth.protocol.oidc"
    const val NONE = "auth.protocol.none"
}

object Detail {
    const val INVALID_CREDENTIALS = "detail.auth.invalid_credentials"
    const val ACCOUNT_LOCKED = "detail.auth.account_locked"
    const val IP_RESTRICTED = "detail.auth.ip_restricted"
    const val MFA_REQUIRED = "detail.auth.mfa_required"
    const val POLICY_VIOLATION = "detail.auth.policy_violation"
    const val CAPTCHA_FAILURE = "detail.auth.captcha_failure"
    const val TOKEN_EXPIRED = "detail.auth.token_expired"
    const val TOKEN_INVALID = "detail.auth.token_invalid"
    const val UNAUTHORIZED_ACCESS = "detail.auth.unauthorized_access"
    const val RATE_LIMIT_EXCEEDED = "detail.auth.rate_limit_exceeded"
    const val USER_INITIATED = "detail.trigger.user_initiated"
    const val ADMIN_INITIATED = "detail.trigger.admin_initiated"
    const val SYSTEM_AUTOMATED = "detail.trigger.system_automated"
    const val SYSTEM_POLICY_VIOLATION = "detail.trigger.system_policy_violation"
    const val SESSION_TIMEOUT = "detail.trigger.session_timeout"
    const val CONCURRENT_SESSION = "detail.trigger.concurrent_session"
    const val FAILED_ATTEMPTS_THRESHOLD = "detail.trigger.failed_attempts_threshold"
    const val INTERNAL_ERROR = "detail.system.internal_error"
    const val SERVICE_UNAVAILABLE = "detail.system.service_unavailable"
    const val MAINTENANCE = "detail.system.maintenance"
    const val INVALID_REQUEST = "detail.client.invalid_request"
    const val NOT_APPLICABLE = "detail.not_applicable"
    const val API_KEY_CREATED = "detail.api_key.created"
    const val API_KEY_REVOKED = "detail.api_key.revoked"
    const val API_KEY_PERMISSIONS_MODIFIED = "detail.api_key.permissions_modified"
    const val SSO_CONFIG_CREATED = "detail.auth.sso_config_created"
    const val SSO_CONFIG_MODIFIED = "detail.auth.sso_config_modified"
    const val SSO_CONFIG_DELETED = "detail.auth.sso_config_deleted"
    const val LOCAL_AUTH_ENABLED = "detail.auth.local_auth_enabled"
    const val LOCAL_AUTH_DISABLED = "detail.auth.local_auth_disabled"
    const val USER_DISABLED = "detail.action.user_disabled"
    const val USER_ENABLED = "detail.action.user_enabled"
    const val USER_DELETED = "detail.action.user_deleted"
    const val USER_LOCKED = "detail.action.user_locked"
    const val USER_UNLOCKED = "detail.action.user_unlocked"
    const val IMPERSONATION_START = "detail.action.impersonation_start"
    const val IMPERSONATION_STOP = "detail.action.impersonation_stop"
    const val VIEW_LIST = "detail.action.view_list"
    const val MODIFY_CUSTOMER_DATA = "detail.action.modify_customer_data"
    const val EXPORT_REPORT = "detail.action.export_report"
    const val VIEW_RECORD = "detail.action.view_record"
    const val EDIT_RECORD = "detail.action.edit_record"
    const val CREATE_RECORD = "detail.action.create_record"
    const val DELETE_RECORD = "detail.action.delete_record"
    const val MFA_DISABLED = "detail.action.mfa_disabled"
    const val MFA_ENABLED = "detail.action.mfa_enabled"
    const val NEW_MFA_DEVICE = "detail.action.new_mfa_device"
    const val MFA_INVALID_CODE = "detail.mfa.invalid_code"
    const val MFA_EXPIRED_CODE = "detail.mfa.expired_code"
    const val MFA_DEVICE_NOT_ENROLLED = "detail.mfa.device_not_enrolled"
    const val MFA_TOO_MANY_ATTEMPTS = "detail.mfa.too_many_attempts"
    const val PASSWORD_CHANGE = "detail.action.password_change"
    const val PASSWORD_RESET = "detail.action.password_reset"
    const val NEW_SSO_PROVIDER = "detail.action.new_sso_provider"
    const val ENABLE_LOCAL_AUTHN = "detail.action.enable_local_authn"
    const val DISABLE_SSO = "detail.action.disable_sso"
}

object MfaType {
    const val SMS = "sms"
    const val TOTP = "totp"
    const val PUSH = "push"
    const val EMAIL = "email"
    const val OKTA_VERIFY = "okta_verify"
    const val AUTHENTICATOR_APP = "authenticator_app"
    const val HARDWARE_TOKEN = "hardware_token"
    const val BIOMETRIC = "biometric"
    const val BACKUP_CODES = "backup_codes"
}

object HttpMethod {
    const val GET = "http.method.GET"
    const val POST = "http.method.POST"
    const val PUT = "http.method.PUT"
    const val PATCH = "http.method.PATCH"
    const val DELETE = "http.method.DELETE"
    const val HEAD = "http.method.HEAD"
    const val OPTIONS = "http.method.OPTIONS"
}

object CloudEnvType {
    const val PROD = "prod"
    const val STAGE = "stage"
    const val TEST = "test"
    const val DEV = "dev"
}

object CloudServiceApiType {
    const val AWS_LAMBDA = "aws_lambda"
    const val GKE_NAMESPACE = "gke_namespace"
    const val ECS_SERVICE = "ecs_service"
}

object DataSensitivityLevel {
    const val PUBLIC = "sensitivity.level.public"
    const val NON_PUBLIC = "sensitivity.level.non_public"
    const val CONFIDENTIAL = "sensitivity.level.confidential"
    const val RESTRICTED = "sensitivity.level.restricted"
}

object EndpointSensitivity {
    const val PUBLIC = "sensitivity.level.public"
    const val NON_PUBLIC = "sensitivity.level.non_public"
    const val CONFIDENTIAL = "sensitivity.level.confidential"
    const val RESTRICTED = "sensitivity.level.restricted"
}

object UserRole {
    const val ADMIN = "role.classification.admin"
    const val SALES_REP = "role.classification.sales_rep"
    const val CUSTOMER_SUPPORT = "role.classification.customer_support"
    const val PARTNER_ADMIN = "role.classification.partner_admin"
    const val CUSTOMER_USER = "role.classification.customer_user"
    const val NON_ADMIN_INTERNAL = "role.classification.non_admin_internal"
    const val NOT_AVAILABLE = "role.classification.not_available"
}

object InviteStatus {
    const val SENT = "sent"
    const val ACCEPTED = "accepted"
    const val REVOKED = "revoked"
    const val EXPIRED = "expired"
}
