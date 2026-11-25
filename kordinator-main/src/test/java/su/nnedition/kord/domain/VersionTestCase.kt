package su.nnedition.kord.domain

import junit.framework.TestCase

/** Unit tests for version comparison and preview detection. */
class VersionTestCase : TestCase() {
    fun testVersion() {
        doTestVersion("0.0", "0.1")
        doTestVersion("1.5", "2.3")
        doTestVersion("2.3", "2.34")

        doTestVersion("1.5", "1.5.1")
        doTestVersion("1.5.1", "1.5.2")
        doTestVersion("1.5.2", "1.5.11")

        doTestVersion("1.4", "1.5.beta1")
        doTestVersion("1.4.1", "1.5.beta1")
        doTestVersion("1.5.beta1", "1.5")
        doTestVersion("1.5.beta1", "1.5.1")
        doTestVersion("1.5.beta1", "1.6")
        doTestVersion("1.5.beta1", "1.5.beta2")
        doTestVersion("1.5.beta2", "1.5.beta11")

        doTestVersion("6.2-SNAPSHOT", "6.11-SNAPSHOT")

        // Internal stable comparisons.
        doTestVersion("10.6.0.20200424173747", "10.6.0.20200424173748")
        doTestVersion("10.6.0.20200424173747", "11.0.0.20200424173747")
        // Internal stable vs external stable.
        doTestVersion("10.6.0", "10.6.0.20200424173747")
        doTestVersion("10.6.0.20200424173747", "11.0.0")
        // Internal stable vs external snapshots.
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "10.6.0.20200424173747")
        doTestVersion("10.6.0.20200424173747", "11.0.0-SNAPSHOT.20200424173747")
        // Internal snapshot vs external stable.
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "10.6.0")
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "11.0.0")
        // Internal snapshot vs external snapshot.
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "10.6.0-SNAPSHOT.20200424173748")
        doTestVersion("10.6.0-SNAPSHOT.20200424173747", "11.0.0-SNAPSHOT.20200424173747")
    }

    fun testIsPreview() {
        assertTrue("Version should be snapshot", Version("1.6.0-SNAPSHOT").isPreview())
        assertTrue("Version should be snapshot", Version("1.6.0-SNAPSHOT.20200424173747").isPreview())
        assertTrue("Version should be snapshot", Version("1.6.0-beta2").isPreview())
        assertFalse("Version should not be snapshot", Version("1.6.0").isPreview())
        assertFalse("Version should not be snapshot", Version("1.6.0-RELEASE").isPreview())
    }

    private fun doTestVersion(v1: String, v2: String) {
        val ver1 = Version(v1)
        val ver2 = Version(v2)

        assertEquals("Error in toString().", v1, ver1.toString())
        assertEquals("Error in toString().", v2, ver2.toString())
        assertEquals("Error in equals().", ver1, ver1)
        assertEquals("Error in compareTo().", 0, ver1.compareTo(ver1))
        assertEquals("Error in compareTo().", 0, ver2.compareTo(ver2))
        assertTrue("Error in compareTo().", ver1.compareTo(ver2) < 0)
        assertTrue("Error in compareTo().", ver2.compareTo(ver1) > 0)
    }
}
