package software.rdd.apexperformance.core

import software.rdd.apexperformance.BuildConfig

object AppEnvironment {
    // Test API for debug builds, production API for release, same as the iOS xcconfigs.
    const val API_URL: String = BuildConfig.API_URL

    fun apiUrl(path: String): String = "$API_URL/${path.trimStart('/')}"

    // The web app is served on the same host, without the /api suffix.
    val webUrl: String = API_URL.removeSuffix("/").removeSuffix("/api")
}
