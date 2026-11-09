package software.rdd.apexperformance.core.network

import okhttp3.OkHttpClient

// Debug builds: the API can be answered with invented demo data (DemoAPI),
// switched on by the store screenshot test. Off unless the test turns it on.
internal object BuildTypeNetwork {
    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        builder.addInterceptor(DemoAPI.DemoInterceptor())
}
