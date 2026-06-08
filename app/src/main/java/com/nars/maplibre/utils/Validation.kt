package com.nars.maplibre.utils

import androidx.annotation.StringRes
import com.nars.maplibre.R
import com.nars.maplibre.data.model.FeatureProperties

data class ValidationResult(
    val valid: Boolean,
    val errors: Map<String, Int> = emptyMap(),
    val warnings: Map<String, Int> = emptyMap()
) {
    companion object {
        fun success() = ValidationResult(valid = true)
        fun successWithWarning(field: String, @StringRes message: Int) = ValidationResult(
            valid = true,
            warnings = mapOf(field to message)
        )
        fun failure(field: String, @StringRes message: Int) = ValidationResult(
            valid = false,
            errors = mapOf(field to message)
        )
        fun failure(errors: Map<String, Int>) = ValidationResult(
            valid = false,
            errors = errors
        )
    }
}

data class HouseEntranceValidation(
    val hasEntrance: Boolean,
    val hasNumberingPanel: Boolean? = null,
    val numberingPanelCorrect: Boolean? = null,
    val numberingPanelPositionCorrect: Boolean? = null,
    val needsNotification: Boolean = false,
    val issues: List<Int> = emptyList()
)

data class NamingPanelValidation(
    val hasLocation: Boolean,
    val hasNamingPanel: Boolean? = null,
    val namingCorrect: Boolean? = null,
    val namingPanelPositionCorrect: Boolean? = null,
    val needsNotification: Boolean = false,
    val issues: List<Int> = emptyList()
)

fun validateFeatureProperties(
    properties: FeatureProperties
): ValidationResult {
    val errors = mutableMapOf<String, Int>()

    when (properties) {
        is FeatureProperties.RoadProperties -> validateRoadFieldProperties(properties, errors)
        is FeatureProperties.HouseEntranceProperties -> validateHouseEntranceFieldProperties(properties, errors)
        is FeatureProperties.NamingPanelProperties -> validateNamingPanelFieldProperties(properties, errors)
    }

    return if (errors.isEmpty()) {
        ValidationResult.success()
    } else {
        ValidationResult.failure(errors)
    }
}

private fun validateRoadFieldProperties(
    properties: FeatureProperties.RoadProperties,
    errors: MutableMap<String, Int>
) {
    if (properties.name.isNullOrBlank()) {
        errors["label"] = R.string.validation_road_name_required
    }

    if (properties.roadTypeKey.isNullOrBlank()) {
        errors["roadType"] = R.string.validation_road_type_required
    }

    if (properties.roadTraffic.isNullOrBlank()) {
        errors["roadTraffic"] = R.string.validation_road_traffic_required
    }

    if (properties.tradActivity.isNullOrBlank()) {
        errors["tradActivity"] = R.string.validation_trad_activity_required
    }

    if (properties.numLanes == null) {
        errors["numLanes"] = R.string.validation_num_lanes_required
    }

    if (properties.hasMedian == null) {
        errors["hasMedian"] = R.string.validation_has_median_required
    }

    if (properties.hasVegetation == null) {
        errors["hasVegetation"] = R.string.validation_has_vegetation_required
    }

    if (properties.isDeadEnd == null) {
        errors["isDeadEnd"] = R.string.validation_is_dead_end_required
    }

    if (properties.hasSidewalk == null) {
        errors["hasSidewalk"] = R.string.validation_has_sidewalk_required
    }
}

private fun validateHouseEntranceFieldProperties(
    properties: FeatureProperties.HouseEntranceProperties,
    errors: MutableMap<String, Int>
) {
    if (properties.entranceTypeKey == "main_entrance") {
        if (properties.roadDbId == null) {
            errors["road"] = R.string.validation_main_entrance_road_required
        }

        if (properties.side.isNullOrBlank()) {
            errors["side"] = R.string.validation_side_required
        }
    }
}

private fun validateNamingPanelFieldProperties(
    properties: FeatureProperties.NamingPanelProperties,
    errors: MutableMap<String, Int>
) {
    if (properties.name.isNullOrBlank()) {
        errors["label"] = R.string.validation_street_name_required
    }
}

fun validateHouseEntranceFieldWorkflow(properties: FeatureProperties.HouseEntranceProperties): HouseEntranceValidation {
    val issues = mutableListOf<Int>()

    if (properties.hasEntrance != true) {
        issues.add(R.string.validation_entrance_not_found)
        return HouseEntranceValidation(
            hasEntrance = false,
            needsNotification = true,
            issues = issues
        )
    }

    if (properties.hasNumberingPanel != true) {
        issues.add(R.string.validation_numbering_panel_not_found)
        return HouseEntranceValidation(
            hasEntrance = true,
            hasNumberingPanel = false,
            needsNotification = true,
            issues = issues
        )
    }

    if (properties.numberingPanelCorrect != true) {
        issues.add(R.string.validation_number_incorrect)
        return HouseEntranceValidation(
            hasEntrance = true,
            hasNumberingPanel = true,
            numberingPanelCorrect = false,
            needsNotification = true,
            issues = issues
        )
    }

    if (properties.numberingPanelPositionCorrect != true) {
        issues.add(R.string.validation_numbering_panel_position_incorrect)
        return HouseEntranceValidation(
            hasEntrance = true,
            hasNumberingPanel = true,
            numberingPanelCorrect = true,
            numberingPanelPositionCorrect = false,
            needsNotification = true,
            issues = issues
        )
    }

    return HouseEntranceValidation(
        hasEntrance = true,
        hasNumberingPanel = true,
        numberingPanelCorrect = true,
        numberingPanelPositionCorrect = true,
        needsNotification = false,
        issues = emptyList()
    )
}

fun validateNamingPanelFieldWorkflow(properties: FeatureProperties.NamingPanelProperties): NamingPanelValidation {
    val issues = mutableListOf<Int>()

    if (properties.hasNamingPanelLocation != true) {
        issues.add(R.string.validation_naming_panel_location_not_found)
        return NamingPanelValidation(
            hasLocation = false,
            needsNotification = true,
            issues = issues
        )
    }

    if (properties.hasNamingPanel != true) {
        issues.add(R.string.validation_naming_panel_not_found)
        return NamingPanelValidation(
            hasLocation = true,
            hasNamingPanel = false,
            needsNotification = true,
            issues = issues
        )
    }

    if (properties.namingCorrect != true) {
        issues.add(R.string.validation_street_name_incorrect)
        return NamingPanelValidation(
            hasLocation = true,
            hasNamingPanel = true,
            namingCorrect = false,
            needsNotification = true,
            issues = issues
        )
    }

    if (properties.namingPanelPositionCorrect != true) {
        issues.add(R.string.validation_naming_panel_position_incorrect)
        return NamingPanelValidation(
            hasLocation = true,
            hasNamingPanel = true,
            namingCorrect = true,
            namingPanelPositionCorrect = false,
            needsNotification = true,
            issues = issues
        )
    }

    return NamingPanelValidation(
        hasLocation = true,
        hasNamingPanel = true,
        namingCorrect = true,
        namingPanelPositionCorrect = true,
        needsNotification = false,
        issues = emptyList()
    )
}

fun validateRoadLength(lengthMeters: Double): ValidationResult {
    if (lengthMeters < Config.MIN_ROAD_LENGTH_METERS) {
        return ValidationResult.failure(
            "length",
            R.string.validation_road_min_length
        )
    }
    return ValidationResult.success()
}
