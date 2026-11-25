package su.nnedition.kord.controller.captions

class CaptionInfo(
    val identifier: String,
    val location: Location,
    val format: String,
    val language: String
) {
    enum class Location {
        INTERNAL, EXTERNAL
    }
}
