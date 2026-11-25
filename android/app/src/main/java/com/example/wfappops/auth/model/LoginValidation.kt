package com.example.wfappops.auth.model

internal enum class LoginFieldIssue { REQUIRED, INVALID_EMAIL }

internal data class LoginValidation(val email: LoginFieldIssue?, val password: LoginFieldIssue?)

/** Valida únicamente el formulario; las credenciales siempre las verifica el API. */
internal fun validateLoginFields(email: String, password: String): LoginValidation = LoginValidation(
    email = when {
        email.isBlank() -> LoginFieldIssue.REQUIRED
        !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) -> LoginFieldIssue.INVALID_EMAIL
        else -> null
    },
    password = if (password.isEmpty()) LoginFieldIssue.REQUIRED else null
)
