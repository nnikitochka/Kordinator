package su.nnedition.kord.domain

import junit.framework.TestCase

class TranscodeSchemeTestCase : TestCase() {
    fun testStrictest() {
        assertSame(TranscodeScheme.OFF, TranscodeScheme.OFF.strictest(null))
        assertSame(TranscodeScheme.OFF, TranscodeScheme.OFF.strictest(TranscodeScheme.OFF))
        assertSame(TranscodeScheme.MAX_32, TranscodeScheme.OFF.strictest(TranscodeScheme.MAX_32))
        assertSame(TranscodeScheme.MAX_32, TranscodeScheme.MAX_32.strictest(null))
        assertSame(TranscodeScheme.MAX_32, TranscodeScheme.MAX_32.strictest(TranscodeScheme.OFF))
        assertSame(TranscodeScheme.MAX_32, TranscodeScheme.MAX_32.strictest(TranscodeScheme.MAX_64))
        assertSame(TranscodeScheme.MAX_32, TranscodeScheme.MAX_64.strictest(TranscodeScheme.MAX_32))
    }
}
