package com.nars.maplibre.modes

/**
 * Canonical GeoJSON property keys shared by the serialization/rendering layers.
 * Centralized so a typo in a key (which would otherwise silently produce a
 * malformed blob) cannot drift between the converter and the renderer.
 */
object GeoJsonProps {
    const val TYPE = "type"
    const val FEATURE = "Feature"
    const val FEATURES = "features"
    const val ID = "id"
    const val GEOMETRY = "geometry"
    const val PROPERTIES = "properties"
    const val COORDINATES = "coordinates"
    const val NAME = "name"
    const val LABEL = "label"

    const val POINT = "Point"
    const val LINE_STRING = "LineString"
    const val POLYGON = "Polygon"
    const val MULTI_POLYGON = "MultiPolygon"
    const val FEATURE_COLLECTION = "FeatureCollection"
}
