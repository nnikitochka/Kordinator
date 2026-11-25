package su.nnedition.kord.api.module.admin.status

data class CacheStatisticsResponse(val name: String, val hits: Double?, val misses: Double?, val puts: Double?, val removals: Double?)
