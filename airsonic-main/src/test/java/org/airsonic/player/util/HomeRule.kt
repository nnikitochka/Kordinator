package org.airsonic.player.util

import org.airsonic.player.TestCaseUtils
import org.airsonic.player.service.ConfigurationPropertiesService
import org.junit.rules.ExternalResource
import java.nio.file.Paths

/** Sets and resets the test home before the tests that depend on it run. */
class HomeRule : ExternalResource() {
    override fun before() {
        super.before()
        System.setProperty("airsonic.home", TestCaseUtils.airsonicHomePathForTest())
        TestCaseUtils.cleanAirsonicHomeForTest()
        ConfigurationPropertiesService.reset()
    }

    override fun after() {
        FileUtil.delete(Paths.get(TestCaseUtils.airsonicHomePathForTest()))
    }
}
