package su.nnedition.kord.data.local.artist

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = ArtistEntity.TABLE_NAME)
class ArtistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "artist_id")
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "genres_raw")
    val genresRaw: String,
) {
    companion object {
        const val TABLE_NAME = "artists"
    }
}