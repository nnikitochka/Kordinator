package su.nnedition.kord.api.module.admin.library

data class LibraryResponse(val id: Long, val name: String, val path: String, val enabled: Boolean, val createdAt: String, val scanId: String? = null)
