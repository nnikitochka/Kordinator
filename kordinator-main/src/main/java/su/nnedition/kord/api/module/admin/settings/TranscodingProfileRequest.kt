package su.nnedition.kord.api.module.admin.settings

data class TranscodingProfileRequest(
    val id: Int? = null, val name: String, val sourceFormats: String, val targetFormat: String,
    val step1: String, val step2: String? = null, val step3: String? = null, val defaultActive: Boolean = false,
)
