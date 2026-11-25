package su.nnedition.kord.domain

import com.google.common.base.Objects
import java.io.Serializable
import java.nio.file.Path
import java.time.Instant
import java.util.function.Function
import kotlin.jvm.JvmName

open class MusicFolder(
    open var id: Int?,
    open var path: Path,
    open var name: String,
    open var type: Type,
    enabled: Boolean,
    open var changed: Instant?,
) : Serializable {
    @get:JvmName("getEnabledValue")
    @set:JvmName("setEnabledValue")
    private var enabled: Boolean = enabled

    companion object {
        private const val serialVersionUID = -6427012828888540476L

        @JvmStatic
        fun toIdList(from: List<MusicFolder>): List<Int?> = from.map { it.id }

        @JvmStatic
        fun toPathList(from: List<MusicFolder>): List<String> = from.map { it.path.toString() }

        @JvmStatic
        fun toId(): Function<MusicFolder, Int?> = Function { it.id }

        @JvmStatic
        fun toPath(): Function<MusicFolder, String> = Function { it.path.toString() }
    }

    constructor(path: Path, name: String, type: Type, enabled: Boolean, changed: Instant?) :
        this(null, path, name, type, enabled, changed)

    open fun isEnabled(): Boolean = enabled

    open fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other != null && javaClass == other.javaClass && Objects.equal(id, (other as MusicFolder).id))

    override fun hashCode(): Int = Objects.hashCode(id)

    enum class Type {
        MEDIA,
    }
}
