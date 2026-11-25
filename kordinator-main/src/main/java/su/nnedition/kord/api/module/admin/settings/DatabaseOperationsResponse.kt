package su.nnedition.kord.api.module.admin.settings

import su.nnedition.kord.service.DatabaseService

data class DatabaseOperationsResponse(
    val backup: DatabaseService.OperationStatus?,
    val export: DatabaseService.OperationStatus?,
    val import: DatabaseService.OperationStatus?,
)
