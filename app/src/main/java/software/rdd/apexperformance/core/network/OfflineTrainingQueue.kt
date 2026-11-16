package software.rdd.apexperformance.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import software.rdd.apexperformance.BuildConfig
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.auth.AuthManager
import software.rdd.apexperformance.core.util.ToastManager
import software.rdd.apexperformance.core.util.ToastType
import software.rdd.apexperformance.model.Training
import java.io.File

// Training form saves made without a connection. They are kept in a file
// (survive the app being closed) and sent when the connection returns
// (ConnectivityManager callback) and when the app comes to the foreground.
// Every save of the training form goes through here, so a training started
// offline is created once and then only updated.
object OfflineTrainingQueue {

    // Waiting saves, shown in the banner above the tabs.
    var entries by mutableStateOf<List<PendingTrainingSave>>(emptyList())
        private set

    // Saves of the logged-in coach.
    val visibleEntries: List<PendingTrainingSave>
        get() {
            val userId = AuthManager.userId ?: return emptyList()
            return entries.filter { it.userId == null || it.userId == userId }
        }

    private var file: File? = null
    private val scope = MainScope()
    private val mutex = Mutex()

    // Trainings created for a form's key, also after its save left the queue,
    // so a save the form built before it heard about it doesn't create it again.
    private val knownIds = mutableMapOf<String, String>()

    // Open forms told when their waiting save went through.
    private val observers = mutableMapOf<String, (Training) -> Unit>()

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
        entries = read()
        try {
            context.getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        syncLater()
                    }
                })
        } catch (e: Exception) {
            // Without the callback the saves still go when the app comes to the foreground.
            if (BuildConfig.DEBUG) Log.w(TAG, "No network callback", e)
        }
    }

    fun observe(key: String, onSaved: (Training) -> Unit) {
        observers[key] = onSaved
    }

    fun stopObserving(key: String) {
        observers.remove(key)
    }

    fun knownTrainingId(key: String): String? = knownIds[key]

    // Sends a save of the training form right away. Without a connection it
    // is kept and sent later; the result tells the form what happened.
    suspend fun submit(entry: PendingTrainingSave): SendResult = mutex.withLock {
        val existing = entries.firstOrNull { it.key == entry.key }
        val merged = OfflineTrainingSync.merge(existing, entry, knownIds[entry.key])
        val result = OfflineTrainingSync.send(merged, ApiTrainingRemote)
        result.entry.trainingId?.let { knownIds[entry.key] = it }
        when (result) {
            is SendResult.Synced -> remove(entry.key)
            is SendResult.Failed -> {
                if (OfflineTrainingSync.shouldKeep(result.failure, wasQueued = existing != null)) {
                    upsert(result.entry.copy(error = rejectionMessage(result)))
                } else {
                    remove(entry.key)
                }
            }
        }
        result
    }

    fun syncLater() {
        scope.launch { syncAll() }
    }

    // Sends the waiting saves of the logged-in coach; stops at the first one
    // without a connection. Rejected ones wait for the coach (Retry / Discard).
    suspend fun syncAll() {
        if (!AuthManager.isAuthenticated) return
        mutex.withLock {
            for (entry in visibleEntries.filter { it.error == null }) {
                if (!sendQueued(entry)) break
            }
        }
    }

    // Retry from the banner, also for a rejected save.
    fun retry(key: String) {
        scope.launch {
            mutex.withLock {
                val entry = entries.firstOrNull { it.key == key } ?: return@withLock
                sendQueued(entry.copy(error = null))
            }
        }
    }

    fun discard(key: String) {
        scope.launch {
            mutex.withLock { remove(key) }
        }
    }

    // False when the connection is still missing.
    private suspend fun sendQueued(entry: PendingTrainingSave): Boolean {
        val result = OfflineTrainingSync.send(entry, ApiTrainingRemote)
        result.entry.trainingId?.let { knownIds[entry.key] = it }
        return when (result) {
            is SendResult.Synced -> {
                remove(entry.key)
                observers[entry.key]?.invoke(result.training)
                ToastManager.show(R.string.offline_synced, ToastType.SUCCESS)
                true
            }
            is SendResult.Failed -> {
                upsert(result.entry.copy(error = rejectionMessage(result)))
                if (result.failure == SaveFailure.REJECTED) showError(result.error)
                result.failure != SaveFailure.OFFLINE
            }
        }
    }

    private fun showError(error: Throwable) {
        ToastManager.show(mapError(error), ToastType.ERROR)
    }

    private fun rejectionMessage(result: SendResult.Failed): String? =
        if (result.failure == SaveFailure.REJECTED) mapError(result.error) else null

    private suspend fun upsert(entry: PendingTrainingSave) {
        entries = entries.filter { it.key != entry.key } + entry
        write()
    }

    private suspend fun remove(key: String) {
        if (entries.none { it.key == key }) return
        entries = entries.filter { it.key != key }
        write()
    }

    private fun read(): List<PendingTrainingSave> = try {
        val text = file?.takeIf { it.exists() }?.readText()
        if (text.isNullOrBlank()) emptyList()
        else ApiClient.json.decodeFromString(ListSerializer(PendingTrainingSave.serializer()), text)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Pending saves not read", e)
        emptyList()
    }

    private suspend fun write() {
        val target = file ?: return
        val text = ApiClient.json.encodeToString(ListSerializer(PendingTrainingSave.serializer()), entries)
        withContext(Dispatchers.IO) {
            try {
                target.writeText(text)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Pending saves not written", e)
            }
        }
    }

    private const val FILE_NAME = "pending_training_saves.json"
    private const val TAG = "OfflineTrainingQueue"
}
