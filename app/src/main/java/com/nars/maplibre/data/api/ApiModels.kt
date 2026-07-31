package com.nars.maplibre.data.api

import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.Geometry
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PointGeometry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.GregorianCalendar
import java.util.TimeZone

val apiJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

@Serializable
data class LoginRequest(val username: String, val password: String)

// ─── Feature API contract (mirrors NARS backend /api/features) ────────────────

/**
 * Request body for creating a new feature.
 * `data` is an opaque JSON blob (GeoJSON-like) matching the web frontend format:
 * `{ type, label, coordinates: [{lat,lng}], lat, lng, radius, roadTypeKey, ... }`.
 */
@Serializable
data class ApiSaveFeatureRequest(val type: String, val layer: String, val label: String, val data: JsonElement)

/** Request body for updating an existing feature. Both fields are optional. */
@Serializable
data class ApiUpdateFeatureRequest(val label: String? = null, val data: JsonElement? = null)

/** A single feature row returned by the backend. */
@Serializable
data class ApiFeatureResult(
    val id: String,
    val type: String,
    val layer: String? = null,
    val label: String? = null,
    val data: JsonElement = JsonObject(emptyMap()),
    val createdAt: String? = null,
) {
    fun toNarsFeature(): NarsFeature? {
        val featureType = when (type) {
            "road" -> NarsFeatureType.ROAD
            "house_entrance" -> NarsFeatureType.HOUSE_ENTRANCE
            "naming_panel" -> NarsFeatureType.NAMING_PANEL
            else -> return null
        }
        val dataObject = data as? JsonObject ?: JsonObject(emptyMap())
        val geometry = parseGeometry(dataObject) ?: return null
        return NarsFeature(
            id = id,
            dbId = id,
            type = featureType,
            geometry = geometry,
            properties = parseProperties(dataObject, featureType, label, layer),
            createdAt = createdAt.toEpochMillis(),
        )
    }

    private companion object {
        fun parseGeometry(data: JsonObject): Geometry? {
            val lat = data.str("lat")?.toDoubleOrNull()
            val lng = data.str("lng")?.toDoubleOrNull()
            val radius = data.str("radius")?.toDoubleOrNull()
            val coords =
                (data["coordinates"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val clat = obj.str("lat")?.toDoubleOrNull() ?: return@mapNotNull null
                    val clng = obj.str("lng")?.toDoubleOrNull() ?: return@mapNotNull null
                    listOf(clng, clat)
                }?.flatten()

            return when {
                radius != null && lat != null && lng != null ->
                    CircleGeometry(coordinates = listOf(lng, lat, radius))

                lat != null && lng != null ->
                    PointGeometry(coordinates = listOf(lng, lat))

                coords != null && coords.size >= 4 ->
                    LineStringGeometry(coordinates = coords)

                coords != null && coords.size == 2 ->
                    PointGeometry(coordinates = coords)

                else -> null
            }
        }

        fun parseProperties(
            data: JsonObject,
            featureType: NarsFeatureType,
            topLabel: String?,
            topLayer: String?,
        ): FeatureProperties {
            val name = data.str("label") ?: topLabel
            return when (featureType) {
                NarsFeatureType.ROAD -> {
                    FeatureProperties.RoadProperties(
                        name = name,
                        phase = Phases.ROADS_KEY,
                        color = "#3498db",
                        roadTypeKey = data.str("roadTypeKey") ?: topLayer,
                    )
                }

                NarsFeatureType.HOUSE_ENTRANCE -> {
                    FeatureProperties.HouseEntranceProperties(
                        name = name,
                        phase = Phases.HOUSE_ENTRANCES_KEY,
                        color = "#27ae60",
                        entranceTypeKey = data.str("entranceTypeKey") ?: topLayer,
                        roadDbId = data.str("roadDbId"),
                        side = data.str("side"),
                    )
                }

                NarsFeatureType.NAMING_PANEL -> {
                    FeatureProperties.NamingPanelProperties(
                        name = name,
                        phase = Phases.NAMING_PANELS_KEY,
                        color = "#9b59b6",
                    )
                }
            }
        }

        private fun JsonObject.str(key: String): String? = when (val value = this[key]) {
            is kotlinx.serialization.json.JsonNull -> null
            is JsonPrimitive -> value.content
            else -> null
        }
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val MILLIS_PER_MINUTE = 60_000L

        private val ISO_8601_REGEX =
            Regex(
                """(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(?:([+-])(\d{2}):(\d{2})|Z)?""",
            )

        /**
         * Parses the backend's ISO-8601 round-trip timestamps into epoch millis.
         * Implemented with [GregorianCalendar] instead of `java.time` because the
         * app targets API 24 (min) without core library desugaring.
         */
        @Suppress("MagicNumber")
        private fun String?.toEpochMillis(): Long {
            val raw = this ?: return System.currentTimeMillis()
            val match = ISO_8601_REGEX.matchEntire(raw) ?: return System.currentTimeMillis()
            val parts = match.groupValues
            val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            calendar.isLenient = false
            calendar.clear()
            calendar.set(
                parts[1].toInt(),
                parts[2].toInt() - 1,
                parts[3].toInt(),
                parts[4].toInt(),
                parts[5].toInt(),
                parts[6].toInt(),
            )
            var millis = calendar.timeInMillis
            val sign = parts[7]
            if (sign == "+" || sign == "-") {
                val delta = parts[8].toInt() * MILLIS_PER_HOUR + parts[9].toInt() * MILLIS_PER_MINUTE
                millis -= if (sign == "+") delta else -delta
            }
            return millis
        }
    }
}

/** Wrapper returned by GET /api/features (pagination envelope). */
@Serializable
data class ApiLoadFeaturesResponse(
    val features: List<ApiFeatureResult> = emptyList(),
    val count: Int = 0,
    val skip: Int = 0,
    val take: Int = 0,
)

@Serializable
data class ApiSaveFeatureResponse(val success: Boolean = true, val id: String? = null, val message: String? = null)

@Serializable
data class ApiUpdateFeatureResponse(val success: Boolean = true, val id: String? = null, val updatedAt: String? = null)

// ─── Feature ↔ API DTO conversion ─────────────────────────────────────────────

/**
 * Builds the `data` JSON blob persisted by the backend. The format matches the
 * web frontend so features authored in either app remain interchangeable.
 */
fun NarsFeature.toApiData(): JsonObject = buildJsonObject {
    put("type", properties.phase)
    properties.name?.let { put("label", it) }
    addGeometryPayload(geometry)
    addPropertyKeys(properties)
}

private fun JsonObjectBuilder.addGeometryPayload(geometry: Geometry) {
    putJsonArray("coordinates") {
        geometry.coordinates.chunked(2).forEach { pair ->
            if (pair.size == 2) {
                add(
                    buildJsonObject {
                        put("lat", pair[1])
                        put("lng", pair[0])
                    },
                )
            }
        }
    }
    if (geometry is PointGeometry || geometry is CircleGeometry) {
        geometry.coordinates.getOrNull(1)?.let { put("lat", it) }
        geometry.coordinates.getOrNull(0)?.let { put("lng", it) }
    }
    if (geometry is CircleGeometry) {
        geometry.coordinates.getOrNull(2)?.let { put("radius", it) }
    }
}

private fun JsonObjectBuilder.addPropertyKeys(properties: FeatureProperties) {
    when (properties) {
        is FeatureProperties.RoadProperties -> properties.roadTypeKey?.let { put("roadTypeKey", it) }

        is FeatureProperties.HouseEntranceProperties -> {
            properties.entranceTypeKey?.let { put("entranceTypeKey", it) }
            properties.roadDbId?.let { put("roadDbId", it) }
            properties.side?.let { put("side", it) }
        }

        is FeatureProperties.NamingPanelProperties -> Unit
    }
}

fun NarsFeature.toApiSaveRequest(): ApiSaveFeatureRequest {
    val (apiType, apiLayer) =
        when (val props = properties) {
            is FeatureProperties.RoadProperties -> "road" to (props.roadTypeKey ?: "street")

            is FeatureProperties.HouseEntranceProperties ->
                "house_entrance" to (props.entranceTypeKey ?: "main_entrance")

            is FeatureProperties.NamingPanelProperties -> "naming_panel" to "naming_panel"
        }
    return ApiSaveFeatureRequest(
        type = apiType,
        layer = apiLayer,
        label = properties.name ?: "",
        data = toApiData(),
    )
}

fun NarsFeature.toApiUpdateRequest(): ApiUpdateFeatureRequest = ApiUpdateFeatureRequest(
    label = properties.name,
    data = toApiData(),
)
