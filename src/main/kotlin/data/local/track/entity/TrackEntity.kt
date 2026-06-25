package su.nnedition.kord.data.local.track.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = TrackEntity.TABLE_NAME)
class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "track_id")
    val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "release_date")
    val releaseDate: String,
) {
    companion object {
        const val TABLE_NAME = "tracks"
    }
}