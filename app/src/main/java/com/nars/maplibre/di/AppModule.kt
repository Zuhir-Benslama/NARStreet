package com.nars.maplibre.di

import com.nars.maplibre.AppPreferences
import com.nars.maplibre.BuildConfig
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.SettingsViewModel
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.data.store.FeatureStore
import com.nars.maplibre.data.store.FeatureStoreInterface
import com.nars.maplibre.utils.Config
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        single {
            HttpClient(OkHttp) {
                engine {
                    config {
                        retryOnConnectionFailure(true)
                        val hashes = BuildConfig.SSL_CERT_HASHES
                        if (hashes.isNotBlank()) {
                            val pinnerBuilder = CertificatePinner.Builder()
                            hashes.split(",").forEach { entry ->
                                val parts = entry.trim().split("=", limit = 2)
                                if (parts.size == 2) {
                                    pinnerBuilder.add(parts[0].trim(), parts[1].trim())
                                }
                            }
                            certificatePinner(pinnerBuilder.build())
                        }
                    }
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
                install(Logging) {
                    level = LogLevel.NONE
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = Config.API_DEFAULT_TIMEOUT_MS.toLong()
                    connectTimeoutMillis = Config.API_CONNECT_TIMEOUT_MS.toLong()
                    socketTimeoutMillis = Config.API_DEFAULT_TIMEOUT_MS.toLong()
                }
                defaultRequest {
                    val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
                    url(baseUrl)
                    contentType(ContentType.Application.Json)
                }
            }
        }

        single { AppPreferences(androidContext()) }

        single<FeatureStoreInterface> { FeatureStore() }

        single { ApiService(get(), get()) }

        single { SessionManager(get(), get()) }

        viewModel { MapViewModel(androidContext().applicationContext as android.app.Application, get(), get(), get()) }
        viewModel { SettingsViewModel(get()) }
    }
