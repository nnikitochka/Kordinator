package su.nnedition.kord.security

/** Framework-neutral signal that the current caller is not allowed to perform an operation. */
class AuthorizationDeniedException(message: String) : RuntimeException(message)
