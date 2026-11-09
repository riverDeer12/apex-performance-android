package software.rdd.apexperformance.core.network

import okhttp3.OkHttpClient

// Release builds talk to the API only; the demo data is in debug builds.
internal object BuildTypeNetwork {
    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
}
