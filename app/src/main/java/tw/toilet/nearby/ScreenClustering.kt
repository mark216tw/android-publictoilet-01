package tw.toilet.nearby

data class ScreenPoint<T>(val x: Int, val y: Int, val value: T)

// 檢查相鄰格子，而不是只依格子分組：跨越格線但距離很近的標記也能合併。
fun <T> clusterNearby(points: List<ScreenPoint<T>>, radius: Int): List<List<T>> {
    require(radius > 0)
    class Group(val x: Int, val y: Int, val members: MutableList<T>) {
        var sumX = x.toLong()
        var sumY = y.toLong()
        fun add(point: ScreenPoint<T>) {
            members.add(point.value)
            sumX += point.x
            sumY += point.y
        }
        fun squaredDistance(point: ScreenPoint<T>): Double {
            val dx = point.x - sumX.toDouble() / members.size
            val dy = point.y - sumY.toDouble() / members.size
            return dx * dx + dy * dy
        }
    }
    val groups = mutableListOf<Group>()
    val cells = mutableMapOf<Pair<Int, Int>, MutableList<Group>>()
    for (point in points) {
        val cellX = Math.floorDiv(point.x, radius)
        val cellY = Math.floorDiv(point.y, radius)
        val closest = (-1..1).flatMap { dx ->
            (-1..1).flatMap { dy -> cells[(cellX + dx) to (cellY + dy)].orEmpty() }
        }.minByOrNull { it.squaredDistance(point) }
        if (closest != null && closest.squaredDistance(point) <= radius.toDouble() * radius) {
            closest.add(point)
        } else {
            val group = Group(point.x, point.y, mutableListOf(point.value))
            groups.add(group)
            cells.getOrPut(cellX to cellY) { mutableListOf() }.add(group)
        }
    }
    return groups.map { it.members.toList() }
}
