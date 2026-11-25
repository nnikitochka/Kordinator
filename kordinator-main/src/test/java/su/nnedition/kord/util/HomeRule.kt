package su.nnedition.kord.util

import su.nnedition.kord.TestCaseUtils
import su.nnedition.kord.service.ConfigurationPropertiesService
import org.junit.rules.ExternalResource
import java.nio.file.Paths

/** Sets and resets the test home before the tests that depend on it run. */
class HomeRule : ExternalResource() {
    override fun before() {
        super.before()
        System.setProperty("kordinator.home", TestCaseUtils.kordinatorHomePathForTest())
        TestCaseUtils.cleanKordinatorHomeForTest()
        ConfigurationPropertiesService.reset()
    }

    override fun after() {
        FileUtil.delete(Paths.get(TestCaseUtils.kordinatorHomePathForTest()))
    }
}
