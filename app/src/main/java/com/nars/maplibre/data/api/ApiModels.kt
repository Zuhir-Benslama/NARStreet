package com.nars.maplibre.data.api

import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val apiJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

@Serializable
data class ApiFeatureListResponse(
    val features: List<ApiFeatureResponse>? = null,
    val success: Boolean = true,
    val message: String? = null,
)

@Serializable
data class ApiFeatureResponse(
    val id: String,
    @SerialName("db_id") val dbId: String? = null,
    val type: String,
    val geometry: ApiGeometryResponse,
    val properties: ApiPropertiesResponse,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toNarsFeature(): NarsFeature? {
        val featureType = NarsFeatureType.fromValue(type)
        val modelGeometry = geometry.toModelGeometry() ?: return null
        return NarsFeature(
            id = id,
            dbId = dbId,
            type = featureType,
            geometry = modelGeometry,
            properties = properties.toFeatureProperties(featureType),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
data class ApiGeometryResponse(val type: String, val coordinates: List<Double>) {
    fun toModelGeometry() = when (type) {
        "Point" -> {
            com.nars.maplibre.data.model
                .PointGeometry(type, coordinates)
        }

        "LineString" -> {
            com.nars.maplibre.data.model
                .LineStringGeometry(type, coordinates)
        }

        "Polygon" -> {
            com.nars.maplibre.data.model
                .PolygonGeometry(type, coordinates)
        }

        "Circle" -> {
            com.nars.maplibre.data.model
                .CircleGeometry(type, coordinates)
        }

        else -> null
    }
}

@Serializable
data class ApiPropertiesResponse(
    val name: String? = null,
    val phase: String? = null,
    val color: String? = null,
    @SerialName("road_type_key") val roadTypeKey: String? = null,
    @SerialName("road_traffic") val roadTraffic: String? = null,
    @SerialName("trad_activity") val tradActivity: String? = null,
    @SerialName("num_lanes") val numLanes: Int? = null,
    @SerialName("has_median") val hasMedian: Boolean? = null,
    @SerialName("has_vegetation") val hasVegetation: Boolean? = null,
    @SerialName("is_dead_end") val isDeadEnd: Boolean? = null,
    @SerialName("has_sidewalk") val hasSidewalk: Boolean? = null,
    @SerialName("entrance_type_key") val entranceTypeKey: String? = null,
    @SerialName("road_db_id") val roadDbId: String? = null,
    val side: String? = null,
    @SerialName("has_entrance") val hasEntrance: Boolean? = null,
    @SerialName("has_numbering_panel") val hasNumberingPanel: Boolean? = null,
    @SerialName("numbering_panel_correct") val numberingPanelCorrect: Boolean? = null,
    @SerialName("numbering_panel_position_correct") val numberingPanelPositionCorrect: Boolean? = null,
    @SerialName("has_naming_panel_location") val hasNamingPanelLocation: Boolean? = null,
    @SerialName("has_naming_panel") val hasNamingPanel: Boolean? = null,
    @SerialName("naming_correct") val namingCorrect: Boolean? = null,
    @SerialName("naming_panel_position_correct") val namingPanelPositionCorrect: Boolean? = null,
) {
    fun toFeatureProperties(featureType: NarsFeatureType): FeatureProperties = when (featureType) {
        NarsFeatureType.ROAD -> {
            FeatureProperties.RoadProperties(
                name = name,
                phase = phase ?: "roads",
                color = color ?: "#3498db",
                roadTypeKey = roadTypeKey,
                roadTraffic = roadTraffic,
                tradActivity = tradActivity,
                numLanes = numLanes,
                hasMedian = hasMedian,
                hasVegetation = hasVegetation,
                isDeadEnd = isDeadEnd,
                hasSidewalk = hasSidewalk,
            )
        }

        NarsFeatureType.HOUSE_ENTRANCE -> {
            FeatureProperties.HouseEntranceProperties(
                name = name,
                phase = phase ?: "houseEntrances",
                color = color ?: "#27ae60",
                entranceTypeKey = entranceTypeKey,
                roadDbId = roadDbId,
                side = side,
                hasEntrance = hasEntrance,
                hasNumberingPanel = hasNumberingPanel,
                numberingPanelCorrect = numberingPanelCorrect,
                numberingPanelPositionCorrect = numberingPanelPositionCorrect,
            )
        }

        NarsFeatureType.NAMING_PANEL -> {
            FeatureProperties.NamingPanelProperties(
                name = name,
                phase = phase ?: "namingPanels",
                color = color ?: "#9b59b6",
                hasNamingPanelLocation = hasNamingPanelLocation,
                hasNamingPanel = hasNamingPanel,
                namingCorrect = namingCorrect,
                namingPanelPositionCorrect = namingPanelPositionCorrect,
            )
        }
    }
}

@Serializable
data class SaveFeatureRequest(
    val id: String? = null,
    val type: String,
    val geometry: ApiGeometryRequest,
    val properties: ApiPropertiesRequest,
)

@Serializable
data class ApiGeometryRequest(val type: String, val coordinates: List<Double>)

@Serializable
data class ApiPropertiesRequest(
    val name: String? = null,
    val phase: String? = null,
    val color: String? = null,
    @SerialName("road_type_key") val roadTypeKey: String? = null,
    @SerialName("road_traffic") val roadTraffic: String? = null,
    @SerialName("trad_activity") val tradActivity: String? = null,
    @SerialName("num_lanes") val numLanes: Int? = null,
    @SerialName("has_median") val hasMedian: Boolean? = null,
    @SerialName("has_vegetation") val hasVegetation: Boolean? = null,
    @SerialName("is_dead_end") val isDeadEnd: Boolean? = null,
    @SerialName("has_sidewalk") val hasSidewalk: Boolean? = null,
    @SerialName("entrance_type_key") val entranceTypeKey: String? = null,
    @SerialName("road_db_id") val roadDbId: String? = null,
    val side: String? = null,
    @SerialName("has_entrance") val hasEntrance: Boolean? = null,
    @SerialName("has_numbering_panel") val hasNumberingPanel: Boolean? = null,
    @SerialName("numbering_panel_correct") val numberingPanelCorrect: Boolean? = null,
    @SerialName("numbering_panel_position_correct") val numberingPanelPositionCorrect: Boolean? = null,
    @SerialName("has_naming_panel_location") val hasNamingPanelLocation: Boolean? = null,
    @SerialName("has_naming_panel") val hasNamingPanel: Boolean? = null,
    @SerialName("naming_correct") val namingCorrect: Boolean? = null,
    @SerialName("naming_panel_position_correct") val namingPanelPositionCorrect: Boolean? = null,
)

@Serializable
data class SaveFeatureResponse(val id: String? = null, val success: Boolean = true)

@Serializable
data class CreateEntranceResponse(val id: String? = null, val success: Boolean = true)

@Suppress("LongMethod")
fun NarsFeature.toSaveFeatureRequest(): SaveFeatureRequest {
    val props =
        when (val p = properties) {
            is FeatureProperties.RoadProperties -> {
                ApiPropertiesRequest(
                    name = p.name,
                    phase = p.phase,
                    color = p.color,
                    roadTypeKey = p.roadTypeKey,
                    roadTraffic = p.roadTraffic,
                    tradActivity = p.tradActivity,
                    numLanes = p.numLanes,
                    hasMedian = p.hasMedian,
                    hasVegetation = p.hasVegetation,
                    isDeadEnd = p.isDeadEnd,
                    hasSidewalk = p.hasSidewalk,
                )
            }

            is FeatureProperties.HouseEntranceProperties -> {
                ApiPropertiesRequest(
                    name = p.name,
                    phase = p.phase,
                    color = p.color,
                    entranceTypeKey = p.entranceTypeKey,
                    roadDbId = p.roadDbId,
                    side = p.side,
                    hasEntrance = p.hasEntrance,
                    hasNumberingPanel = p.hasNumberingPanel,
                    numberingPanelCorrect = p.numberingPanelCorrect,
                    numberingPanelPositionCorrect = p.numberingPanelPositionCorrect,
                )
            }

            is FeatureProperties.NamingPanelProperties -> {
                ApiPropertiesRequest(
                    name = p.name,
                    phase = p.phase,
                    color = p.color,
                    hasNamingPanelLocation = p.hasNamingPanelLocation,
                    hasNamingPanel = p.hasNamingPanel,
                    namingCorrect = p.namingCorrect,
                    namingPanelPositionCorrect = p.namingPanelPositionCorrect,
                )
            }
        }
    return SaveFeatureRequest(
        id = dbId,
        type = type.value,
        geometry = ApiGeometryRequest(type = geometry.type, coordinates = geometry.coordinates),
        properties = props,
    )
}
