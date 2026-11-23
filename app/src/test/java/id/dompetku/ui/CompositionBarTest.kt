package id.dompetku.ui

import id.dompetku.ui.components.compositionShares
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositionBarTest {
    @Test fun sharesSplitProportionally() {
        val (l, i) = compositionShares(6600, 3400)
        assertEquals(0.66f, l, 0.0001f)
        assertEquals(0.34f, i, 0.0001f)
    }

    @Test fun zeroTotalGivesZeroShares() {
        assertEquals(0f to 0f, compositionShares(0, 0))
    }
}
