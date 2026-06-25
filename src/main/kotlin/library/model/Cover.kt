package su.nnedition.kord.library.model

import de.androidpit.colorthief.ColorThief
import java.awt.Color
import java.io.File
import javax.imageio.ImageIO

open class Cover {
    class None : Cover()
    class FileCover(
        val file: File
    ) : Cover() {
        val accentColor: Color = ColorThief.getColor(ImageIO.read(file))?.let {
            Color(it[0], it[1], it[2])
        }!!
    }
}