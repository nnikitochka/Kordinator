package su.nnedition.kord.api.module.admin.settings

data class TranscodingProfileResponse(
    val id: Int, val name: String, val sourceFormats: String, val targetFormat: String,
    val step1: String, val step2: String?, val step3: String?, val defaultActive: Boolean,
)
