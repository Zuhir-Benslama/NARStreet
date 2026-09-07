package com.nars.maplibre.utils

import java.io.IOException

/**
 * A transient, retryable HTTP failure — server overload or a temporary outage
 * (HTTP 408, 429, 500, 502, 503, 504). Raised by [ApiService] for these
 * statuses so [retryOnTransientFailure] retries them like an [IOException];
 * ordinary HTTP errors (4xx, other 5xx) stay non-retryable.
 */
internal class TransientHttpException(val statusCode: Int) : IOException("Transient HTTP error: HTTP $statusCode")
