package tw.toilet.nearby

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenClusteringTest {
    @Test fun joinsNearbyPointsAcrossGridBoundary() {
        val groups = clusterNearby(listOf(
            ScreenPoint(23, 40, "A"), ScreenPoint(25, 40, "B"),
            ScreenPoint(250, 40, "C"),
        ), 24)
        assertEquals(listOf(listOf("A", "B"), listOf("C")), groups)
    }

    @Test fun retainsAllEntriesAtOneCoordinate() {
        val entries = (1..85).map { ScreenPoint(20, 30, it) }
        val groups = clusterNearby(entries, 24)
        assertEquals(1, groups.size)
        assertEquals((1..85).toList(), groups.single())
    }
}
