package tw.toilet.nearby

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

data class MarkerCluster(val toilets: List<Toilet>) {
    val position: GeoPoint
        get() = GeoPoint(toilets.map { it.latitude }.average(), toilets.map { it.longitude }.average())

    val sameCoordinates: Boolean
        get() = toilets.all { it.latitude == toilets[0].latitude && it.longitude == toilets[0].longitude }
}

// 以畫面像素聚合：移動或縮放地圖後重新計算，而非以固定的經緯度半徑聚合。
fun clustersInView(map: MapView, coordinateGroups: List<List<Toilet>>): List<MarkerCluster> {
    if (map.width <= 0 || map.height <= 0) return emptyList()
    val radius = (24 * map.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val visible = mutableListOf<ScreenPoint<List<Toilet>>>()
    for (group in coordinateGroups) {
        val toilet = group[0]
        val pixel = map.projection.toPixels(GeoPoint(toilet.latitude, toilet.longitude), null)
        if (pixel.x !in -radius..(map.width + radius) || pixel.y !in -radius..(map.height + radius)) continue
        visible.add(ScreenPoint(pixel.x, pixel.y, group))
    }
    return clusterNearby(visible, radius).map { clusters -> MarkerCluster(clusters.flatten()) }
}

fun clusterIcon(map: MapView, count: Int): Drawable {
    val density = map.resources.displayMetrics.density
    val size = (40 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    paint.color = Color.WHITE
    canvas.drawCircle(center, center, center - density, paint)
    paint.color = Color.rgb(95, 42, 145)
    canvas.drawCircle(center, center, center - 4 * density, paint)
    paint.color = Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = (when {
        count >= 1000 -> 11
        count >= 100 -> 13
        else -> 16
    }) * density
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText(count.toString(), center, center - (paint.ascent() + paint.descent()) / 2, paint)
    return BitmapDrawable(map.resources, bitmap)
}
