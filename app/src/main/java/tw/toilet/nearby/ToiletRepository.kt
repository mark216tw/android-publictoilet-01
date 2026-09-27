package tw.toilet.nearby

import android.content.Context
import android.location.Location
import org.json.JSONArray
import kotlin.math.roundToInt

data class Toilet(
    val id: String,
    val name: String,
    val region: String,
    val categories: List<String>,
    val latitude: Double,
    val longitude: Double,
)

data class NearbyToilet(val toilet: Toilet, val distanceMeters: Float) {
    val distanceText: String
        get() = if (distanceMeters < 1000) "${distanceMeters.roundToInt()} 公尺"
        else "%.1f 公里".format(java.util.Locale.TAIWAN, distanceMeters / 1000)
}

object ToiletRepository {
    fun load(context: Context): List<Toilet> {
        val data = context.assets.open("toilets.json").bufferedReader(Charsets.UTF_8).use {
            JSONArray(it.readText())
        }
        require(data.length() > 0) { "廁所資料是空的" }
        return List(data.length()) { index ->
            val entry = data.getJSONObject(index)
            val labels = entry.getJSONArray("categories")
            require(labels.length() > 0) { "廁所資料缺少類型標籤" }
            Toilet(
                id = entry.getString("id"), name = entry.getString("name"),
                region = entry.getString("region"),
                categories = List(labels.length()) { labels.getString(it) },
                latitude = entry.getDouble("latitude"), longitude = entry.getDouble("longitude"),
            )
        }
    }

    fun nearby(toilets: List<Toilet>, location: Location, limit: Int = 50): List<NearbyToilet> =
        toilets.map { toilet ->
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, toilet.latitude, toilet.longitude, result)
            NearbyToilet(toilet, result[0])
        }.sortedBy { it.distanceMeters }.take(limit)
}
