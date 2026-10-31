package org.airsonic.player.ajax

import org.airsonic.player.dao.MediaFileDao
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
@MessageMapping("/rate/mediafile")
class StarWSController(private var mediaFileDao: MediaFileDao) {
    @MessageMapping("/star")
    fun star(user: Principal, ids: List<Int>) {
        mediaFileDao.starMediaFiles(ids, user.name)
    }

    @MessageMapping("/unstar")
    fun unstar(user: Principal, ids: List<Int>) {
        mediaFileDao.unstarMediaFiles(ids, user.name)
    }

    fun setMediaFileDao(mediaFileDao: MediaFileDao) {
        this.mediaFileDao = mediaFileDao
    }
}
