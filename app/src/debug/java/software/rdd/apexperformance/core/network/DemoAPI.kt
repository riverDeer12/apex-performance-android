package software.rdd.apexperformance.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.AppEnvironment
import software.rdd.apexperformance.core.auth.JwtDecoder
import software.rdd.apexperformance.core.util.Permissions
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import java.util.UUID

// Store screenshots without real people's data, in debug builds only (this
// file is in the debug source set, release builds don't contain it).
// Switched on by the screenshot test (DemoAPI.isEnabled = true): every request
// to the API host is then answered on the device with invented data: coach
// Luka Marić and his clients, Ana Kovač being the demo client. The same people,
// trainings and messages as DemoAPI on iOS, with the texts people write
// (chat, notes, goal, reviews...) in the app's language.
object DemoAPI {

    @Volatile
    var isEnabled: Boolean = false

    // Language of the demo texts ("hr", "en", "it", "de"); null follows the app's language.
    @Volatile
    var language: String? = null

    internal val currentLanguage: String
        get() = language
            ?: runCatching { ApexApp.appContext.getString(R.string.app_language_code) }.getOrNull()
            ?: "en"

    enum class Role { COACH, CLIENT }

    class Reply(val status: Int = 200, val contentType: String = "application/json", val body: ByteArray = ByteArray(0)) {
        companion object {
            val notFound = Reply(status = 404)
            fun json(element: JsonElement) = Reply(body = element.toString().toByteArray())
        }
    }

    // MARK: - Ids

    // What an id belongs to, so everything keeps its id in all answers.
    enum class Kind {
        USER, COACH, CLIENT, WORKOUT, WORKOUT_TYPE, TRAINING, EXERCISE, EXERCISE_SET,
        MEASUREMENT, APPOINTMENT, TIME_SLOT, STATUS, REQUEST, CONVERSATION, MESSAGE,
        ATTACHMENT, NOTIFICATION, RECORD, REVIEW, FMS, TEMPLATE, CATALOG
    }

    fun id(kind: Kind, number: Int = 0): String =
        String.format(Locale.ROOT, "DE300000-%04X-4000-8000-%012X", kind.ordinal + 1, number.toLong())

    fun clientId(index: Int) = id(Kind.CLIENT, index + 1)
    fun userIdOfClient(index: Int) = id(Kind.USER, 100 + index)
    fun conversationId(index: Int) = id(Kind.CONVERSATION, index + 1)

    // The tokens' "sub": the chat tells own messages by it.
    val coachUserId = id(Kind.USER, 1)
    val coachId = id(Kind.COACH, 1)
    val anaUserId = userIdOfClient(ClientIndex.ANA)
    val anaClientId = clientId(ClientIndex.ANA)
    val anaConversationId = conversationId(ClientIndex.ANA)

    // MARK: - People

    class Person(val firstName: String, val lastName: String, val phone: String, val plan: String, val credits: Int) {
        val fullName: String get() = "$firstName $lastName"

        // firstname.lastname@example.com without diacritics.
        val email: String
            get() = java.text.Normalizer.normalize("$firstName.$lastName@example.com", java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .lowercase(Locale.ROOT)
    }

    object ClientIndex {
        const val ANA = 0
        const val MARKO = 1
        const val IVANA = 2
        const val PETRA = 3
        const val TOMISLAV = 4
        const val LUCIJA = 5
        const val FILIP = 6
        const val MAJA = 7
    }

    val coach = Person("Luka", "Marić", "+385 91 555 0100", "", 0)

    // Ana first: the screenshots open the first client of the list.
    val clients = listOf(
        Person("Ana", "Kovač", "+385 91 555 0123", "PrivateCoaching", 5),
        Person("Marko", "Horvat", "+385 98 555 0147", "PrivateCoaching", 8),
        Person("Ivana", "Babić", "+385 95 555 0162", "OnlineCoaching", 4),
        Person("Petra", "Novak", "+385 91 555 0178", "PrivateCoaching", 10),
        Person("Tomislav", "Jurić", "+385 92 555 0185", "Membership", 2),
        Person("Lucija", "Perić", "+385 99 555 0191", "OnlineCoaching", 6),
        Person("Filip", "Knežević", "+385 97 555 0134", "PrivateCoaching", 0),
        Person("Maja", "Šimić", "+385 91 555 0156", "Membership", 3)
    )

    val coachPermissions = listOf(
        Permissions.CAN_GET_CLIENTS,
        Permissions.CAN_GET_APPOINTMENTS,
        Permissions.CAN_CREATE_APPOINTMENT,
        Permissions.CAN_GET_APPOINTMENT_REQUESTS
    )

    val clientPermissions = listOf(
        Permissions.CAN_GET_APPOINTMENTS,
        Permissions.CAN_CREATE_APPOINTMENT
    )

    // MARK: - Workouts

    // Text in English, Croatian, Italian and German.
    class Text4(val en: String, val hr: String, val it: String, val de: String)

    class WorkoutInfo(
        val number: Int,
        val name: Text4,
        val description: Pair<String, String>,
        val types: List<Int>,
        val muscles: List<String>,
        val secondaryMuscles: List<String>
    )

    object W {
        const val BENCH = 1
        const val INCLINE_PRESS = 2
        const val SQUAT = 3
        const val ROMANIAN_DEADLIFT = 4
        const val DEADLIFT = 5
        const val PULL_UP = 6
        const val ROW = 7
        const val OVERHEAD_PRESS = 8
        const val LATERAL_RAISE = 9
        const val HIP_THRUST = 10
        const val LUNGE = 11
        const val CURL = 12
        const val PUSHDOWN = 13
        const val PLANK = 14
        const val HIP_FLEXOR_STRETCH = 15
        const val THORACIC_ROTATION = 16
        const val ROW_ERG = 17
        const val CAT_COW = 18
        const val HIP_SWITCH = 19
        const val WORLDS_GREATEST_STRETCH = 20
        const val HAMSTRING_STRETCH = 21
        const val ANKLE_MOBILITY = 22
    }

    // Done over a distance: sets record the distance and show the pace (min/500 m).
    val paceTypes = mapOf(W.ROW_ERG to "Per500m")

    // Strength, upper body, lower body, core, mobility (the plan's library).
    val workoutTypes = listOf(
        1 to Text4("Strength", "Snaga", "Forza", "Kraft"),
        2 to Text4("Upper body", "Gornji dio tijela", "Parte superiore", "Oberkörper"),
        3 to Text4("Lower body", "Donji dio tijela", "Parte inferiore", "Unterkörper"),
        4 to Text4("Core", "Trup", "Core", "Rumpf"),
        5 to Text4("Mobility & stretching", "Mobilnost i istezanje", "Mobilità e allungamento", "Mobilität & Dehnung")
    )

    // The mobility type: its workouts are in the mobility and flexibility category.
    const val MOBILITY_TYPE_NUMBER = 5

    // Muscle codes as in BodyMapPaths.
    val workouts = listOf(
        WorkoutInfo(
            W.BENCH, Text4("Bench Press", "Potisak s ravne klupe", "Panca piana", "Bankdrücken"),
            "Lie on a flat bench with your feet planted. Lower the bar to mid-chest under control and press it back up, keeping your shoulder blades pulled together." to
                "Lezi na ravnu klupu, stopala čvrsto na podu. Kontrolirano spusti šipku do sredine prsa i potisni je natrag, lopatice drži skupljene.",
            listOf(1, 2), listOf("chest", "triceps"), listOf("shoulders")
        ),
        WorkoutInfo(
            W.INCLINE_PRESS, Text4("Incline Dumbbell Press", "Kosi potisak bučicama", "Distensioni su panca inclinata", "Schrägbankdrücken mit Kurzhanteln"),
            "On a bench set to about 30°, press the dumbbells up over your upper chest and lower them slowly to chest level." to
                "Na klupi nagnutoj oko 30° potisni bučice iznad gornjeg dijela prsa i polako ih spusti do razine prsa.",
            listOf(1, 2), listOf("chest", "shoulders"), listOf("triceps")
        ),
        WorkoutInfo(
            W.SQUAT, Text4("Barbell Back Squat", "Čučanj sa šipkom na leđima", "Squat con bilanciere", "Kniebeuge mit Langhantel"),
            "With the bar on your upper back, brace your core and sit down between your hips until your thighs are at least parallel, then drive back up." to
                "Sa šipkom na gornjem dijelu leđa zategni trup i spusti se između kukova barem do paralele, zatim se snažno podigni.",
            listOf(1, 3), listOf("quads", "glutes"), listOf("adductors", "hamstrings", "lower_back")
        ),
        WorkoutInfo(
            W.ROMANIAN_DEADLIFT, Text4("Romanian Deadlift", "Rumunjsko mrtvo dizanje", "Stacco rumeno", "Rumänisches Kreuzheben"),
            "Hold the bar at hip height, push your hips back with soft knees and lower the bar along your legs until you feel a stretch in the hamstrings." to
                "Drži šipku u visini kukova, gurni kukove unatrag s blago savijenim koljenima i spuštaj šipku uz noge dok ne osjetiš istezanje stražnje lože.",
            listOf(1, 3), listOf("hamstrings", "glutes"), listOf("lower_back", "forearms")
        ),
        WorkoutInfo(
            W.DEADLIFT, Text4("Deadlift", "Mrtvo dizanje", "Stacco da terra", "Kreuzheben"),
            "Start with the bar over mid-foot, grip it just outside your legs, brace and stand up by pushing the floor away. Keep your back neutral throughout." to
                "Šipka je iznad sredine stopala, uhvati je odmah izvan nogu, zategni trup i ustani odgurujući pod. Leđa su cijelo vrijeme neutralna.",
            listOf(1, 3), listOf("glutes", "hamstrings", "lower_back"), listOf("quads", "traps", "forearms")
        ),
        WorkoutInfo(
            W.PULL_UP, Text4("Pull-Up", "Zgib", "Trazioni alla sbarra", "Klimmzug"),
            "Hang from the bar with straight arms and pull your chest towards it by driving your elbows down. Lower yourself all the way under control." to
                "Visi na šipki ispruženih ruku i povuci prsa prema njoj vodeći laktove prema dolje. Kontrolirano se spusti do kraja.",
            listOf(1, 2), listOf("lats"), listOf("biceps", "upper_back", "forearms")
        ),
        WorkoutInfo(
            W.ROW, Text4("Barbell Row", "Veslanje šipkom", "Rematore con bilanciere", "Langhantelrudern"),
            "Hinge forward with a flat back and row the bar to your lower ribs, squeezing your shoulder blades together at the top." to
                "Nagni se naprijed ravnih leđa i povuci šipku prema donjim rebrima, na vrhu stisni lopatice.",
            listOf(1, 2), listOf("lats", "upper_back"), listOf("biceps", "lower_back")
        ),
        WorkoutInfo(
            W.OVERHEAD_PRESS, Text4("Overhead Press", "Potisak iznad glave", "Military press", "Schulterdrücken"),
            "Stand tall with your glutes and core tight and press the bar from your shoulders to straight arms overhead." to
                "Stojeći uspravno sa zategnutim trupom i stražnjicom potisni šipku s ramena iznad glave do ispruženih ruku.",
            listOf(1, 2), listOf("shoulders"), listOf("triceps", "traps")
        ),
        WorkoutInfo(
            W.LATERAL_RAISE, Text4("Lateral Raise", "Odručenje bučicama", "Alzate laterali", "Seitheben"),
            "With a slight bend in the elbows, raise the dumbbells out to the sides up to shoulder height and lower them slowly." to
                "Blago savijenih laktova podigni bučice u stranu do visine ramena i polako ih spusti.",
            listOf(2), listOf("shoulders"), listOf("traps")
        ),
        WorkoutInfo(
            W.HIP_THRUST, Text4("Hip Thrust", "Potisak kukovima", "Hip thrust", "Hip Thrust"),
            "With your upper back on a bench and the bar over your hips, drive through your heels until your body is in a straight line. Pause and squeeze your glutes." to
                "Gornjim dijelom leđa osloni se na klupu, šipka je na kukovima. Odgurni se petama do ravne linije tijela, zadrži i stisni gluteuse.",
            listOf(1, 3), listOf("glutes"), listOf("hamstrings")
        ),
        WorkoutInfo(
            W.LUNGE, Text4("Walking Lunge", "Hodajući iskorak", "Affondi camminati", "Ausfallschritte im Gehen"),
            "Step forward into a long lunge until the back knee nearly touches the floor, then step through into the next one. Keep your torso upright." to
                "Iskorači naprijed dok stražnje koljeno gotovo ne dotakne pod, zatim nastavi u sljedeći iskorak. Trup drži uspravno.",
            listOf(3), listOf("quads", "glutes"), listOf("hamstrings", "adductors", "calves")
        ),
        WorkoutInfo(
            W.CURL, Text4("Biceps Curl", "Pregib za biceps", "Curl per bicipiti", "Bizepscurl"),
            "With your elbows by your sides, curl the dumbbells up without swinging and lower them slowly." to
                "Laktovi uz tijelo, savij bučice prema ramenima bez njihanja i polako ih spusti.",
            listOf(2), listOf("biceps"), listOf("forearms")
        ),
        WorkoutInfo(
            W.PUSHDOWN, Text4("Triceps Pushdown", "Potisak na sajli za triceps", "Push down per tricipiti", "Trizepsdrücken am Kabel"),
            "Keep your elbows pinned to your sides and push the cable attachment down until your arms are straight." to
                "Laktove drži uz tijelo i potiskuj ručku sajle prema dolje do potpuno ispruženih ruku.",
            listOf(2), listOf("triceps"), emptyList()
        ),
        WorkoutInfo(
            W.PLANK, Text4("Plank", "Plank", "Plank", "Plank"),
            "Support yourself on your forearms and toes with your body in a straight line. Brace your core and breathe steadily." to
                "Osloni se na podlaktice i prste, tijelo je u ravnoj liniji. Zategni trup i diši ravnomjerno.",
            listOf(4), listOf("abs"), listOf("obliques", "shoulders")
        ),
        WorkoutInfo(
            W.HIP_FLEXOR_STRETCH, Text4("Hip Flexor Stretch", "Istezanje pregibača kuka", "Allungamento dei flessori dell'anca", "Hüftbeuger-Dehnung"),
            "From a half-kneeling position, tuck your pelvis and shift forward until you feel a stretch at the front of the back hip. Hold 30–45 s per side." to
                "Iz polukleka podvuci zdjelicu i pomakni se naprijed dok ne osjetiš istezanje s prednje strane stražnjeg kuka. Zadrži 30–45 s po strani.",
            listOf(5), listOf("quads"), listOf("abs")
        ),
        WorkoutInfo(
            W.THORACIC_ROTATION, Text4("Thoracic Rotation", "Rotacija prsne kralježnice", "Rotazione toracica", "Brustwirbelsäulen-Rotation"),
            "Lying on your side with your knees bent, open your top arm and chest towards the floor behind you and return slowly. 8–10 reps per side." to
                "Ležeći na boku savijenih koljena otvori gornju ruku i prsa prema podu iza sebe pa se polako vrati. 8–10 ponavljanja po strani.",
            listOf(5), listOf("upper_back"), listOf("chest", "obliques")
        ),
        WorkoutInfo(
            W.ROW_ERG, Text4("Row Erg", "Veslački ergometar", "Vogatore", "Rudergerät"),
            "Drive with your legs first, then lean back slightly and pull the handle to your lower ribs. Return in the reverse order." to
                "Prvo se odgurni nogama, zatim se lagano zabaci i povuci ručku prema donjim rebrima. Vrati se obrnutim redom.",
            listOf(2, 3), listOf("quads", "upper_back"), listOf("glutes", "hamstrings", "lats", "biceps")
        ),
        WorkoutInfo(
            W.CAT_COW, Text4("Cat-Cow", "Mačka-krava", "Gatto-mucca", "Katze-Kuh"),
            "On all fours, slowly round your back towards the ceiling, then let your belly sink and lift your chest. 10 slow reps." to
                "U četveronožnom položaju polako zaobli leđa prema stropu, zatim spusti trbuh i podigni prsa. 10 sporih ponavljanja.",
            listOf(5), listOf("lower_back"), listOf("upper_back", "abs")
        ),
        WorkoutInfo(
            W.HIP_SWITCH, Text4("90/90 Hip Switch", "90/90 rotacija kukova", "90/90 rotazione delle anche", "90/90 Hüftwechsel"),
            "Sitting with both knees bent at 90°, rotate your knees from one side to the other while keeping your chest tall. 8 reps per side." to
                "U sjedu s oba koljena savijena pod 90° prebacuj koljena s jedne strane na drugu uz uspravna prsa. 8 ponavljanja po strani.",
            listOf(5), listOf("glutes"), listOf("adductors")
        ),
        WorkoutInfo(
            W.WORLDS_GREATEST_STRETCH, Text4("World's Greatest Stretch", "Najbolje istezanje na svijetu", "World's greatest stretch", "World's Greatest Stretch"),
            "From a lunge, place the hand on the floor beside the front foot, rotate the other arm to the ceiling, then straighten the front leg. 5 reps per side." to
                "Iz iskoraka spusti ruku na pod uz prednje stopalo, drugu ruku zarotiraj prema stropu pa ispruži prednju nogu. 5 ponavljanja po strani.",
            listOf(5), listOf("hamstrings", "glutes"), listOf("upper_back", "quads")
        ),
        WorkoutInfo(
            W.HAMSTRING_STRETCH, Text4("Hamstring Stretch", "Istezanje stražnje lože", "Allungamento dei femorali", "Dehnung der Oberschenkelrückseite"),
            "Lying on your back, lift one straight leg with a band around the foot until you feel a stretch behind the knee. Hold 30–45 s per side." to
                "Ležeći na leđima podigni ispruženu nogu s gumom oko stopala dok ne osjetiš istezanje iza koljena. Zadrži 30–45 s po strani.",
            listOf(5), listOf("hamstrings"), listOf("calves")
        ),
        WorkoutInfo(
            W.ANKLE_MOBILITY, Text4("Ankle Mobility", "Mobilnost gležnja", "Mobilità della caviglia", "Sprunggelenk-Mobilität"),
            "In a half-kneeling position near a wall, push the front knee towards the wall over the toes, heel down. 10 reps per side." to
                "U polukleku uz zid guraj prednje koljeno prema zidu preko prstiju, peta ostaje na podu. 10 ponavljanja po strani.",
            listOf(5), listOf("calves"), emptyList()
        )
    )

    // MARK: - Body

    // Ana, oldest first: days ago, weight, shoulders, chest, upper arm, waist, thigh, calves, glutes.
    val anaMeasurements = listOf(
        listOf(154.0, 84.0, 108.0, 102.0, 32.0, 92.0, 62.0, 39.0, 108.0),
        listOf(124.0, 83.0, 107.5, 101.0, 31.5, 91.0, 61.5, 39.0, 107.0),
        listOf(94.0, 82.0, 107.5, 100.5, 31.5, 89.5, 61.0, 38.5, 106.0),
        listOf(63.0, 81.0, 107.0, 99.5, 31.0, 88.0, 60.0, 38.5, 105.0),
        listOf(33.0, 80.5, 106.5, 99.0, 31.0, 87.0, 59.5, 38.0, 104.5),
        listOf(3.0, 79.5, 106.5, 98.5, 30.5, 86.0, 59.0, 38.0, 104.0)
    )

    // Other clients: height and weights, oldest first, a month apart.
    val otherBodies: Map<Int, Pair<Double, List<Double>>> = mapOf(
        ClientIndex.MARKO to (182.0 to listOf(92.0, 90.5, 89.0)),
        ClientIndex.IVANA to (165.0 to listOf(68.0, 67.0)),
        ClientIndex.PETRA to (170.0 to listOf(63.5, 63.0, 62.5)),
        ClientIndex.TOMISLAV to (178.0 to listOf(85.0)),
        ClientIndex.LUCIJA to (160.0 to listOf(58.0, 57.5)),
        ClientIndex.FILIP to (185.0 to listOf(78.0, 79.0, 80.5))
    )

    // MARK: - Appointments

    // Other clients' sessions: number, day offset, hour, minute, client.
    class Session(val number: Int, val day: Int, val hour: Int, val minute: Int, val client: Int)

    val otherSessions = listOf(
        Session(10, -3, 7, 30, ClientIndex.LUCIJA),
        Session(11, -2, 8, 0, ClientIndex.PETRA),
        Session(12, -2, 18, 0, ClientIndex.FILIP),
        Session(13, -1, 7, 0, ClientIndex.MARKO),
        Session(14, -1, 17, 0, ClientIndex.IVANA),
        Session(15, 0, 7, 30, ClientIndex.MARKO),
        Session(16, 0, 9, 0, ClientIndex.PETRA),
        Session(17, 0, 17, 0, ClientIndex.IVANA),
        Session(18, 0, 18, 30, ClientIndex.FILIP),
        Session(19, 1, 18, 0, ClientIndex.MARKO),
        // Lucija asks to cancel this one.
        Session(20, 1, 10, 0, ClientIndex.LUCIJA),
        // Maja asks to join this one.
        Session(21, 2, 8, 0, ClientIndex.PETRA),
        Session(22, 2, 18, 30, ClientIndex.IVANA),
        Session(23, 3, 7, 30, ClientIndex.MARKO),
        Session(24, 4, 9, 0, ClientIndex.PETRA),
        Session(25, 4, 17, 30, ClientIndex.LUCIJA),
        Session(26, 5, 10, 0, ClientIndex.FILIP),
        Session(27, 5, 17, 0, ClientIndex.TOMISLAV)
    )

    // Picture of the chat's image message: meal prep boxes (drawn illustration, 600 × 733 JPEG).
    const val ATTACHMENT_WIDTH = 600
    const val ATTACHMENT_HEIGHT = 733

    private val attachmentImage: ByteArray by lazy {
        runCatching {
            ApexApp.appContext.resources.openRawResource(R.raw.demo_meal_prep).use { it.readBytes() }
        }.getOrDefault(ByteArray(0))
    }

    // MARK: - Tokens

    // Unsigned JWT; the app only reads the payload.
    fun token(role: Role): String {
        val now = System.currentTimeMillis() / 1000
        val isCoach = role == Role.COACH
        val payload = json(
            "name" to if (isCoach) "luka.maric" else "ana.kovac",
            "sub" to if (isCoach) coachUserId else anaUserId,
            "permissions" to if (isCoach) coachPermissions else clientPermissions,
            "role" to if (isCoach) "Coach" else "Client",
            "exp" to now + 10L * 365 * 24 * 3600,
            "iat" to now,
            "nbf" to now
        )
        fun base64Url(element: JsonElement): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(element.toString().toByteArray())
        return "${base64Url(json("alg" to "none", "typ" to "JWT"))}.${base64Url(payload)}.demo"
    }

    // The coach when the request carries the coach's token, otherwise Ana.
    fun role(authorization: String?): Role {
        val token = authorization?.removePrefix("Bearer ")?.takeIf { it != authorization } ?: return Role.CLIENT
        return if (JwtDecoder.getUserId(token).equals(coachUserId, ignoreCase = true)) Role.COACH else Role.CLIENT
    }

    // MARK: - Answers

    private val apiHost: String by lazy { AppEnvironment.API_URL.toHttpUrl().host }
    private val apiPathSegments: List<String> by lazy {
        AppEnvironment.API_URL.toHttpUrl().pathSegments.filter { it.isNotEmpty() }
    }

    fun handles(request: Request): Boolean = isEnabled && request.url.host == apiHost

    fun reply(request: Request): Reply {
        // Path relative to the API, e.g. "chat/conversations".
        var segments = request.url.pathSegments.filter { it.isNotEmpty() }
        if (segments.take(apiPathSegments.size) == apiPathSegments) segments = segments.drop(apiPathSegments.size)
        val query = request.url.queryParameterNames.associateWith { request.url.queryParameter(it).orEmpty() }

        val body = request.body?.let { requestBody ->
            runCatching {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                buffer.readUtf8()
            }.getOrNull()
        }

        return Fixtures(role(request.header("Authorization")), currentLanguage)
            .reply(request.method, segments.joinToString("/"), query, body)
    }

    // Answer as the coach or the client, e.g. "monthly-reviews?clientId=...", for tests.
    fun fixture(method: String, path: String, role: Role, body: String? = null, language: String = "en"): Reply {
        val parts = path.split("?", limit = 2)
        val url = "${AppEnvironment.API_URL}/${parts[0]}".toHttpUrl().newBuilder()
            .apply { if (parts.size > 1) encodedQuery(parts[1]) }
            .build()
        val query = url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() }
        return Fixtures(role, language).reply(method, parts[0].trim('/'), query, body)
    }

    // Answers requests to the API host while demo mode is on; other requests go out.
    class DemoInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!handles(request)) return chain.proceed(request)
            val reply = reply(request)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(reply.status)
                .message(if (reply.status in 200..299) "OK" else "Demo")
                .body(reply.body.toResponseBody(reply.contentType.toMediaType()))
                .build()
        }
    }

    internal fun imageBytes(): ByteArray = attachmentImage

    // MARK: - JSON

    fun json(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { it.first to element(it.second) })

    fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { it.key as String to element(it.value) })
        is Iterable<*> -> JsonArray(value.map { element(it) })
        else -> throw IllegalArgumentException("Not JSON: $value")
    }
}

// Demo data as the API returns it, with dates around now.
private class Fixtures(private val role: DemoAPI.Role, private val language: String) {

    private val now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    private val today: LocalDate = now.toLocalDate()
    private val isCoach = role == DemoAPI.Role.COACH

    class SetSpec(val reps: String, val weight: Double?, val isDropSet: Boolean)
    class ExerciseSpec(val workout: Int, val sets: List<SetSpec>, val note: String?, val isSuperset: Boolean)
    class Measurement(
        val number: Int, val daysAgo: Int,
        val height: Double, val weight: Double, val shoulders: Double, val chest: Double, val upperArm: Double,
        val waist: Double, val thigh: Double, val calves: Double, val glutes: Double
    )

    class MessageSpec(
        val number: Int,
        val fromCoach: Boolean,
        val date: ZonedDateTime,
        // Minutes until the other side read it, null while unread.
        val readAfter: Int?,
        val content: String,
        val type: String = "text",
        val workout: Int? = null,
        val hasAttachment: Boolean = false
    )

    // MARK: Helpers

    // Day relative to today at a time, e.g. day(1, 7, 30) is tomorrow 07:30.
    fun day(offset: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        today.plusDays(offset.toLong()).atTime(hour, minute).atZone(now.zone)

    fun minutesAgo(minutes: Int): ZonedDateTime = now.minusMinutes(minutes.toLong())

    fun iso(date: ZonedDateTime): String =
        date.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    // The text in the app's language.
    fun tr(en: String, hr: String, it: String, de: String): String = when (language) {
        "hr" -> hr
        "it" -> it
        "de" -> de
        else -> en
    }

    fun tr(text: DemoAPI.Text4): String = tr(text.en, text.hr, text.it, text.de)

    // LocalizedText as the API sends it.
    fun text(value: DemoAPI.Text4): JsonObject =
        DemoAPI.json("translations" to mapOf("EN" to value.en, "HR" to value.hr, "IT" to value.it, "DE" to value.de))

    fun text(en: String, hr: String): JsonObject = DemoAPI.json("translations" to mapOf("EN" to en, "HR" to hr))

    private fun pad(value: Int) = if (value < 10) "0$value" else "$value"

    // For example "07:30".
    fun timeText(date: ZonedDateTime): String = "${pad(date.hour)}:${pad(date.minute)}"

    fun clientIndex(id: String?): Int? =
        DemoAPI.clients.indices.firstOrNull { DemoAPI.clientId(it).equals(id, ignoreCase = true) }

    fun conversationIndex(id: String?): Int? =
        DemoAPI.clients.indices.firstOrNull { DemoAPI.conversationId(it).equals(id, ignoreCase = true) }

    fun workoutNumber(id: String?): Int? =
        DemoAPI.workouts.firstOrNull { DemoAPI.id(DemoAPI.Kind.WORKOUT, it.number).equals(id, ignoreCase = true) }?.number

    private fun json(vararg pairs: Pair<String, Any?>) = DemoAPI.json(*pairs)

    // MARK: People

    val rules: JsonObject
        get() = json(
            "document" to "CooperationRules",
            "version" to "1.0",
            "documentUrl" to "https://example.com/cooperation-rules.pdf",
            // Ana accepted them a while ago, so no popup covers the screenshots.
            "isRequired" to !isCoach,
            "isAccepted" to true,
            "acceptedVersion" to "1.0",
            "acceptedAt" to iso(day(-60, 18))
        )

    val profile: JsonObject
        get() {
            val person = if (isCoach) DemoAPI.coach else DemoAPI.clients[DemoAPI.ClientIndex.ANA]
            return json(
                "userId" to if (isCoach) DemoAPI.coachUserId else DemoAPI.anaUserId,
                "username" to if (isCoach) "luka.maric" else "ana.kovac",
                "email" to person.email,
                "roles" to listOf(if (isCoach) "Coach" else "Client"),
                "profileType" to if (isCoach) "Coach" else "Client",
                "firstName" to person.firstName,
                "lastName" to person.lastName,
                "phone" to person.phone,
                // The app shows initials, no picture to load.
                "hasProfilePicture" to false,
                "profilePictureUpdatedAt" to null
            )
        }

    val coach: JsonObject
        get() = json(
            "id" to DemoAPI.coachId,
            "firstName" to DemoAPI.coach.firstName,
            "lastName" to DemoAPI.coach.lastName,
            "email" to DemoAPI.coach.email,
            "phone" to DemoAPI.coach.phone
        )

    // Client as in lists and clients/current-client (also read as UserProfile).
    fun client(index: Int, withMeasurements: Boolean = false): JsonObject {
        val person = DemoAPI.clients[index]
        val fields = mutableListOf<Pair<String, Any?>>(
            "id" to DemoAPI.clientId(index),
            "firstName" to person.firstName,
            "lastName" to person.lastName,
            "email" to person.email,
            "phone" to person.phone,
            "credits" to person.credits,
            "plan" to person.plan,
            "lastCreditsIncrease" to iso(day(-12 - index * 4, 10))
        )
        if (withMeasurements) {
            fields += "bodyMeasurements" to measurements(index).map { measurementJson(it, "measuredAt") }
        }
        return json(*fields.toTypedArray())
    }

    // Short form in trainings and FMS.
    fun person(index: Int): JsonObject {
        val person = DemoAPI.clients[index]
        return json("id" to DemoAPI.clientId(index), "firstName" to person.firstName, "lastName" to person.lastName)
    }

    // MARK: Body measurements

    fun measurements(index: Int): List<Measurement> {
        if (index == DemoAPI.ClientIndex.ANA) {
            return DemoAPI.anaMeasurements.mapIndexed { number, row ->
                Measurement(number, row[0].toInt(), 172.0, row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8])
            }
        }
        val (height, weights) = DemoAPI.otherBodies[index] ?: return emptyList()
        // Circumferences that go with the weight, to the half centimetre.
        fun half(value: Double) = Math.round(value * 2) / 2.0
        return weights.mapIndexed { number, weight ->
            Measurement(
                index * 10 + number, (weights.size - 1 - number) * 30 + 6, height, weight,
                half(70 + weight * 0.45), half(60 + weight * 0.45), half(14 + weight * 0.22),
                half(30 + weight * 0.62), half(25 + weight * 0.38), half(22 + weight * 0.18), half(55 + weight * 0.55)
            )
        }
    }

    // dateKey is measuredAt in client lists, createdAt in GET body-measurements.
    fun measurementJson(m: Measurement, dateKey: String): JsonObject = json(
        "id" to DemoAPI.id(DemoAPI.Kind.MEASUREMENT, m.number),
        "height" to m.height,
        "weight" to m.weight,
        "shoulders" to m.shoulders,
        "chest" to m.chest,
        "upperArm" to m.upperArm,
        "waist" to m.waist,
        "thigh" to m.thigh,
        "calves" to m.calves,
        "glutes" to m.glutes,
        dateKey to iso(day(-m.daysAgo, 8))
    )

    // MARK: Goal, reviews and records

    val anaGoal: JsonObject
        get() = json(
            "goal" to tr(
                "Lose 5 kg of body fat and bench press 80 kg",
                "Izgubiti 5 kg masnog tkiva i potisnuti 80 kg s klupe",
                "Perdere 5 kg di grasso e sollevare 80 kg in panca piana",
                "5 kg Körperfett verlieren und 80 kg bankdrücken"
            ),
            "currentBlock" to tr(
                "Strength block — 4 trainings per week",
                "Blok snage — 4 treninga tjedno",
                "Blocco di forza — 4 allenamenti a settimana",
                "Kraftblock – 4 Trainings pro Woche"
            ),
            "focus" to tr(
                "Progressive overload and a calorie deficit",
                "Progresivno opterećenje i kalorijski deficit",
                "Sovraccarico progressivo e deficit calorico",
                "Progressive Steigerung und ein Kaloriendefizit"
            ),
            "nextAssessment" to tr(
                "Body measurements and FMS re-test",
                "Mjerenje tijela i ponovni FMS test",
                "Misurazioni corporee e nuovo test FMS",
                "Körpermessung und FMS-Retest"
            ),
            "nextAssessmentDate" to iso(day(21, 9)),
            // No date, so the "goal and plan updated" sheet isn't shown over home.
            "updatedAt" to null
        )

    // Last month's review and the one before, newest first.
    val anaReviews: List<JsonObject>
        get() {
            val contents = listOf(
                tr(
                    "Great month, Ana! You trained consistently, set a new bench press record of 70 kg and lost another kilo while getting stronger. Keep the protein high and aim for seven hours of sleep — next month we go after 75 kg on the bench.",
                    "Odličan mjesec, Ana! Trenirala si redovito, postavila novi rekord od 70 kg u potisku s klupe i izgubila još jedan kilogram, a pritom postala jača. Drži unos proteina visokim i ciljaj na sedam sati sna — idući mjesec idemo na 75 kg na klupi.",
                    "Mese fantastico, Ana! Ti sei allenata con costanza, hai stabilito un nuovo record di 70 kg in panca piana e hai perso un altro chilo diventando più forte. Mantieni alte le proteine e punta a sette ore di sonno: il mese prossimo puntiamo ai 75 kg in panca.",
                    "Großartiger Monat, Ana! Du hast konsequent trainiert, einen neuen Rekord von 70 kg im Bankdrücken aufgestellt und ein weiteres Kilo verloren, während du stärker geworden bist. Halte die Proteinzufuhr hoch und plane sieben Stunden Schlaf ein – nächsten Monat nehmen wir 75 kg auf der Bank ins Visier."
                ),
                tr(
                    "A solid start to the strength block. Your squat depth and bracing improved a lot and the scale is moving in the right direction. Next step: keep the weekends on plan so the progress stays steady.",
                    "Dobar početak bloka snage. Dubina čučnja i stabilizacija trupa znatno su se poboljšale, a vaga ide u pravom smjeru. Sljedeći korak: drži se plana i vikendom kako bi napredak ostao stabilan.",
                    "Un buon inizio del blocco di forza. La profondità dello squat e la stabilità del core sono migliorate molto e la bilancia va nella direzione giusta. Prossimo passo: rispetta il piano anche nel weekend, così i progressi restano costanti.",
                    "Ein solider Start in den Kraftblock. Kniebeugentiefe und Rumpfspannung haben sich deutlich verbessert, und die Waage bewegt sich in die richtige Richtung. Nächster Schritt: auch am Wochenende beim Plan bleiben, damit der Fortschritt stetig bleibt."
                )
            )
            val monthStart = today.withDayOfMonth(1)
            return contents.mapIndexed { index, content ->
                val month = monthStart.minusMonths((index + 1).toLong())
                // Written on the 1st of the next month.
                val written = month.plusMonths(1).atTime(10, 0).atZone(now.zone)
                json(
                    "id" to DemoAPI.id(DemoAPI.Kind.REVIEW, index + 1),
                    "clientId" to DemoAPI.anaClientId,
                    "year" to month.year,
                    "month" to month.monthValue,
                    "content" to content,
                    "updatedAt" to iso(written)
                )
            }
        }

    // Records from Ana's trainings, Epley for the estimated 1RM.
    val anaRecords: List<JsonObject>
        get() {
            class Row(val workout: Int, val training: Int, val type: String, val value: Double, val weight: Double, val reps: Double)
            val rows = listOf(
                Row(DemoAPI.W.BENCH, 2, "MaxWeight", 62.5, 62.5, 6.0), Row(DemoAPI.W.BENCH, 2, "EstimatedOneRepMax", 75.0, 62.5, 6.0),
                Row(DemoAPI.W.BENCH, 4, "MaxWeight", 67.5, 67.5, 6.0), Row(DemoAPI.W.BENCH, 4, "EstimatedOneRepMax", 81.0, 67.5, 6.0),
                Row(DemoAPI.W.BENCH, 6, "MaxWeight", 70.0, 70.0, 6.0), Row(DemoAPI.W.BENCH, 6, "EstimatedOneRepMax", 84.0, 70.0, 6.0),
                Row(DemoAPI.W.SQUAT, 1, "MaxWeight", 90.0, 90.0, 3.0), Row(DemoAPI.W.SQUAT, 1, "EstimatedOneRepMax", 99.0, 90.0, 3.0),
                Row(DemoAPI.W.SQUAT, 3, "MaxWeight", 95.0, 95.0, 3.0), Row(DemoAPI.W.SQUAT, 3, "EstimatedOneRepMax", 104.5, 95.0, 3.0),
                Row(DemoAPI.W.SQUAT, 5, "MaxWeight", 100.0, 100.0, 3.0), Row(DemoAPI.W.SQUAT, 5, "EstimatedOneRepMax", 110.0, 100.0, 3.0),
                Row(DemoAPI.W.DEADLIFT, 1, "MaxWeight", 110.0, 110.0, 3.0), Row(DemoAPI.W.DEADLIFT, 1, "EstimatedOneRepMax", 121.0, 110.0, 3.0),
                Row(DemoAPI.W.DEADLIFT, 3, "MaxWeight", 115.0, 115.0, 3.0), Row(DemoAPI.W.DEADLIFT, 3, "EstimatedOneRepMax", 126.5, 115.0, 3.0),
                Row(DemoAPI.W.DEADLIFT, 5, "MaxWeight", 120.0, 120.0, 3.0), Row(DemoAPI.W.DEADLIFT, 5, "EstimatedOneRepMax", 132.0, 120.0, 3.0)
            )
            val records = mutableListOf<JsonObject>()
            rows.forEachIndexed { index, row ->
                records += json(
                    "id" to DemoAPI.id(DemoAPI.Kind.RECORD, index + 1),
                    "clientId" to DemoAPI.anaClientId,
                    "workoutId" to DemoAPI.id(DemoAPI.Kind.WORKOUT, row.workout),
                    "workoutName" to workoutName(row.workout),
                    "trainingId" to DemoAPI.id(DemoAPI.Kind.TRAINING, row.training),
                    "type" to row.type,
                    "value" to row.value,
                    "weight" to row.weight,
                    "reps" to row.reps,
                    "achievedAt" to iso(anaTrainingDate(row.training).plusMinutes(75))
                )
            }
            // Longest plank, in seconds.
            listOf(3 to 50.0, 5 to 60.0).forEachIndexed { offset, (training, seconds) ->
                records += json(
                    "id" to DemoAPI.id(DemoAPI.Kind.RECORD, rows.size + offset + 1),
                    "clientId" to DemoAPI.anaClientId,
                    "workoutId" to DemoAPI.id(DemoAPI.Kind.WORKOUT, DemoAPI.W.PLANK),
                    "workoutName" to workoutName(DemoAPI.W.PLANK),
                    "trainingId" to DemoAPI.id(DemoAPI.Kind.TRAINING, training),
                    "type" to "LongestDuration",
                    "value" to seconds,
                    "weight" to 0,
                    "reps" to null,
                    "achievedAt" to iso(anaTrainingDate(training).plusMinutes(75))
                )
            }
            // Row erg 2000 m: the distance, and the fastest pace as metres per minute
            // (2000 m in 7:45, then in 7:30 = 1:52 /500 m).
            listOf(Triple(3, "LongestDistance", 2000.0), Triple(3, "FastestPace", 258.06), Triple(5, "FastestPace", 266.67))
                .forEachIndexed { offset, (training, type, value) ->
                    records += json(
                        "id" to DemoAPI.id(DemoAPI.Kind.RECORD, rows.size + 2 + offset + 1),
                        "clientId" to DemoAPI.anaClientId,
                        "workoutId" to DemoAPI.id(DemoAPI.Kind.WORKOUT, DemoAPI.W.ROW_ERG),
                        "workoutName" to workoutName(DemoAPI.W.ROW_ERG),
                        "trainingId" to DemoAPI.id(DemoAPI.Kind.TRAINING, training),
                        "type" to type,
                        "value" to value,
                        "weight" to 0,
                        "reps" to null,
                        "achievedAt" to iso(anaTrainingDate(training).plusMinutes(75)),
                        "paceType" to DemoAPI.paceTypes[DemoAPI.W.ROW_ERG]
                    )
                }
            return records
        }

    // MARK: Workouts

    fun workoutType(number: Int): JsonObject {
        val type = DemoAPI.workoutTypes.firstOrNull { it.first == number } ?: DemoAPI.workoutTypes[0]
        return json("id" to DemoAPI.id(DemoAPI.Kind.WORKOUT_TYPE, type.first), "name" to text(type.second))
    }

    fun workoutName(number: Int): JsonObject =
        DemoAPI.workouts.firstOrNull { it.number == number }?.let { text(it.name) } ?: text("", "")

    fun workout(number: Int): JsonObject? {
        val info = DemoAPI.workouts.firstOrNull { it.number == number } ?: return null
        return json(
            "id" to DemoAPI.id(DemoAPI.Kind.WORKOUT, info.number),
            "name" to text(info.name),
            "description" to text(info.description.first, info.description.second),
            // Empty, the app shows its placeholder.
            "thumbnailUrl" to "",
            "videoUrl" to "",
            "workoutTypes" to info.types.map { workoutType(it) },
            "muscles" to info.muscles,
            "secondaryMuscles" to info.secondaryMuscles,
            // Plank sets record a duration, row erg sets a distance and a duration.
            "isTimed" to (info.number == DemoAPI.W.PLANK || DemoAPI.paceTypes.containsKey(info.number)),
            "category" to if (DemoAPI.MOBILITY_TYPE_NUMBER in info.types) "MobilityFlexibility" else "StrengthConditioning",
            "paceType" to DemoAPI.paceTypes[info.number]
        )
    }

    val allWorkouts: List<JsonObject> get() = DemoAPI.workouts.mapNotNull { workout(it.number) }

    // MARK: Trainings

    private fun s(reps: String, weight: Double?) = SetSpec(reps, weight, false)

    // Drop set: right after the previous set with less weight.
    private fun drop(reps: String, weight: Double) = SetSpec(reps, weight, true)

    private fun sets(count: Int, reps: String, weight: Double?) = List(count) { SetSpec(reps, weight, false) }

    private fun ex(workout: Int, sets: List<SetSpec>, note: String? = null, superset: Boolean = false) =
        ExerciseSpec(workout, sets, note, superset)

    // Exercises of training (or template) number, orders from 1.
    private fun exercisesJson(number: Int, exercises: List<ExerciseSpec>): List<JsonObject> =
        exercises.mapIndexed { index, exercise ->
            val sets = exercise.sets.mapIndexed { setIndex, set ->
                // "45 s" is a duration (plank) and "2000 m 450 s" a distance
                // with a duration (row erg), not repetitions.
                val parts = set.reps.split(" ")
                val isDistance = parts.size == 4 && parts[1] == "m" && parts[3] == "s"
                val distance = if (isDistance) parts[0].toInt() else null
                val duration = if (isDistance) parts[2].toInt()
                else if (set.reps.endsWith(" s")) set.reps.dropLast(2).toInt() else null
                json(
                    "id" to DemoAPI.id(DemoAPI.Kind.EXERCISE_SET, number * 10_000 + index * 100 + setIndex),
                    "order" to setIndex + 1,
                    "reps" to if (duration == null && distance == null) set.reps else null,
                    "weight" to set.weight,
                    "isDropSet" to set.isDropSet,
                    "durationSeconds" to duration,
                    "distanceMeters" to distance
                )
            }
            json(
                "id" to DemoAPI.id(DemoAPI.Kind.EXERCISE, number * 100 + index),
                "workoutId" to DemoAPI.id(DemoAPI.Kind.WORKOUT, exercise.workout),
                "workoutName" to workoutName(exercise.workout),
                "order" to index + 1,
                "note" to exercise.note,
                "sets" to sets,
                "isSupersetWithPrevious" to exercise.isSuperset,
                "paceType" to DemoAPI.paceTypes[exercise.workout]
            )
        }

    private fun training(
        number: Int,
        name: String,
        client: Int,
        date: ZonedDateTime,
        completed: Boolean,
        note: String? = null,
        exercises: List<ExerciseSpec>,
        // Minutes the coach marked as the training's length, with the heart rate.
        minutes: Int? = null,
        heartRate: Triple<Int, Int, Int>? = null
    ): JsonObject = json(
        "id" to DemoAPI.id(DemoAPI.Kind.TRAINING, number),
        "name" to name,
        "date" to iso(date),
        "note" to note,
        "isCompleted" to completed,
        "completedAt" to if (completed) iso(date.plusMinutes(75)) else null,
        "client" to person(client),
        "exercises" to exercisesJson(number, exercises),
        "startedAt" to minutes?.let { iso(date) },
        "endedAt" to minutes?.let { iso(date.plusMinutes(it.toLong())) },
        "heartRate" to heartRate?.let { json("min" to it.first, "max" to it.second, "average" to it.third) }
    )

    val lowerBodyStrength get() = tr("Lower Body Strength", "Snaga – donji dio tijela", "Forza – parte inferiore", "Kraft – Unterkörper")
    val upperBodyStrength get() = tr("Upper Body Strength", "Snaga – gornji dio tijela", "Forza – parte superiore", "Kraft – Oberkörper")
    val fullBodyConditioning get() = tr("Full Body Conditioning", "Kondicija za cijelo tijelo", "Condizionamento total body", "Ganzkörper-Kondition")

    // Ana's completed trainings 1...6, about a week apart, 18:00.
    fun anaTrainingDate(number: Int): ZonedDateTime {
        val daysAgo = listOf(36, 29, 22, 15, 8, 1)
        return day(-daysAgo[(number - 1).coerceIn(0, daysAgo.lastIndex)], 18)
    }

    // Six completed trainings with growing weights and the next planned one.
    val anaTrainings: List<JsonObject>
        get() {
            val ana = DemoAPI.ClientIndex.ANA
            val w = DemoAPI.W
            return listOf(
                training(1, lowerBodyStrength, ana, anaTrainingDate(1), true, exercises = listOf(
                    ex(w.SQUAT, listOf(s("8", 70.0), s("5", 80.0), s("3", 90.0), s("3", 90.0))),
                    ex(w.DEADLIFT, listOf(s("5", 90.0), s("3", 100.0), s("3", 110.0))),
                    ex(w.HIP_THRUST, sets(3, "10", 80.0)),
                    ex(w.LUNGE, sets(3, "12", 10.0)),
                    ex(w.PLANK, sets(3, "45 s", null))
                )),
                training(2, upperBodyStrength, ana, anaTrainingDate(2), true, exercises = listOf(
                    ex(w.BENCH, listOf(s("10", 55.0), s("8", 60.0), s("6", 62.5), s("6", 62.5))),
                    ex(w.ROW, sets(4, "8", 40.0)),
                    ex(w.OVERHEAD_PRESS, sets(3, "10", 25.0)),
                    ex(w.LATERAL_RAISE, sets(3, "15", 6.0))
                )),
                training(3, lowerBodyStrength, ana, anaTrainingDate(3), true, exercises = listOf(
                    ex(w.SQUAT, listOf(s("8", 75.0), s("5", 85.0), s("3", 95.0), s("3", 95.0))),
                    ex(w.DEADLIFT, listOf(s("5", 95.0), s("3", 105.0), s("3", 115.0))),
                    ex(w.HIP_THRUST, sets(3, "10", 90.0)),
                    ex(w.LUNGE, sets(3, "12", 10.0)),
                    ex(w.PLANK, sets(3, "50 s", null)),
                    ex(w.ROW_ERG, listOf(s("2000 m 465 s", null)), note = tr(
                        "2000 m at a steady pace.", "2000 m ujednačenim tempom.",
                        "2000 m a ritmo costante.", "2000 m in gleichmäßigem Tempo."
                    ))
                )),
                training(4, upperBodyStrength, ana, anaTrainingDate(4), true, exercises = listOf(
                    ex(w.BENCH, listOf(s("10", 57.5), s("8", 62.5), s("6", 67.5), s("6", 67.5))),
                    ex(w.ROW, sets(4, "8", 42.5)),
                    ex(w.OVERHEAD_PRESS, sets(3, "10", 27.5)),
                    ex(w.LATERAL_RAISE, sets(3, "15", 7.0))
                )),
                training(5, lowerBodyStrength, ana, anaTrainingDate(5), true,
                    note = tr(
                        "Squat 100 kg for 3 — great depth and bracing.",
                        "Čučanj 100 kg × 3 — odlična dubina i stabilan trup.",
                        "Squat 100 kg per 3 — ottima profondità e core stabile.",
                        "Kniebeuge 100 kg × 3 – super Tiefe und Rumpfspannung."
                    ),
                    exercises = listOf(
                        ex(w.SQUAT, listOf(s("8", 80.0), s("5", 90.0), s("3", 100.0), s("3", 100.0))),
                        ex(w.DEADLIFT, listOf(s("5", 100.0), s("3", 110.0), s("3", 120.0))),
                        ex(w.HIP_THRUST, sets(3, "10", 100.0)),
                        ex(w.LUNGE, sets(3, "12", 12.5)),
                        ex(w.PLANK, sets(3, "60 s", null)),
                        ex(w.ROW_ERG, listOf(s("2000 m 450 s", null)), note = tr(
                            "New 2000 m best: 1:52 /500 m.", "Novi najbolji rezultat na 2000 m: 1:52 /500 m.",
                            "Nuovo record sui 2000 m: 1:52 /500 m.", "Neue Bestzeit über 2000 m: 1:52 /500 m."
                        ))
                    ),
                    minutes = 72, heartRate = Triple(88, 171, 132)
                ),
                training(6, upperBodyStrength, ana, anaTrainingDate(6), true,
                    note = tr(
                        "Strong session — new bench press record: 70 kg for 6 reps!",
                        "Odličan trening — novi rekord u potisku s klupe: 70 kg × 6!",
                        "Ottima sessione — nuovo record in panca piana: 70 kg per 6 ripetizioni!",
                        "Starke Einheit – neuer Rekord im Bankdrücken: 70 kg für 6 Wiederholungen!"
                    ),
                    exercises = listOf(
                        ex(
                            w.BENCH,
                            listOf(s("10", 60.0), s("8", 65.0), s("6", 70.0), s("6", 70.0), drop("8", 55.0), drop("10", 45.0)),
                            note = tr(
                                "Pause on the chest on every rep. Two drop sets after the last set.",
                                "Pauza na prsima u svakom ponavljanju. Dva drop seta nakon zadnje serije.",
                                "Pausa sul petto a ogni ripetizione. Due drop set dopo l'ultima serie.",
                                "Bei jeder Wiederholung kurz auf der Brust pausieren. Zwei Drop-Sets nach dem letzten Satz."
                            )
                        ),
                        ex(w.ROW, sets(4, "8", 45.0)),
                        ex(w.OVERHEAD_PRESS, sets(3, "10", 30.0)),
                        ex(w.CURL, sets(3, "12", 10.0)),
                        ex(
                            w.PUSHDOWN, listOf(s("12", 25.0), s("12", 25.0), s("12", 25.0), drop("12", 17.5)),
                            note = tr(
                                "Elbows pinned to your sides, full lockout.",
                                "Laktovi uz tijelo, ruke potpuno ispruži.",
                                "Gomiti aderenti ai fianchi, estensione completa.",
                                "Ellbogen eng am Körper, Arme ganz durchstrecken."
                            ),
                            superset = true
                        )
                    ),
                    minutes = 68, heartRate = Triple(84, 164, 127)
                ),
                // Planned with tomorrow's appointment; only the coach sees it.
                training(7, lowerBodyStrength, ana, day(1, 7, 30), false,
                    note = tr(
                        "Aim for 102.5 kg on the top squat sets.",
                        "Cilj: 102,5 kg u najtežim serijama čučnja.",
                        "Obiettivo: 102,5 kg nelle serie più pesanti di squat.",
                        "Ziel: 102,5 kg in den schwersten Kniebeugesätzen."
                    ),
                    exercises = listOf(
                        ex(w.SQUAT, listOf(s("8", 80.0), s("5", 90.0), s("3", 102.5), s("3", 102.5))),
                        ex(w.ROMANIAN_DEADLIFT, sets(3, "10", 70.0)),
                        ex(w.HIP_THRUST, sets(3, "10", 105.0)),
                        ex(w.LUNGE, sets(3, "12", 12.5)),
                        ex(w.PLANK, sets(3, "60 s", null))
                    )
                )
            )
        }

    val otherTrainings: List<JsonObject>
        get() {
            val w = DemoAPI.W
            return listOf(
                training(20, fullBodyConditioning, DemoAPI.ClientIndex.MARKO, day(-2, 7, 30), true, exercises = listOf(
                    ex(w.SQUAT, sets(4, "8", 100.0)),
                    ex(w.PULL_UP, sets(4, "8", null)),
                    ex(w.OVERHEAD_PRESS, sets(3, "10", 45.0)),
                    ex(w.PLANK, sets(3, "60 s", null))
                )),
                training(21, tr("Upper Body Hypertrophy", "Hipertrofija – gornji dio tijela", "Ipertrofia – parte superiore", "Hypertrophie – Oberkörper"),
                    DemoAPI.ClientIndex.PETRA, day(-3, 9), true, exercises = listOf(
                        ex(w.INCLINE_PRESS, sets(4, "10", 16.0), note = tr("16 kg dumbbells.", "Bučice od 16 kg.", "Manubri da 16 kg.", "16-kg-Kurzhanteln.")),
                        ex(w.ROW, sets(3, "12", 35.0)),
                        ex(w.LATERAL_RAISE, sets(3, "15", 5.0)),
                        ex(w.CURL, sets(3, "12", 8.0)),
                        ex(w.PUSHDOWN, sets(3, "12", 20.0), superset = true)
                    )),
                training(22, tr("Glutes & Hamstrings", "Gluteusi i stražnja loža", "Glutei e femorali", "Gesäß & Beinbeuger"),
                    DemoAPI.ClientIndex.IVANA, day(2, 18, 30), false, exercises = listOf(
                        ex(w.HIP_THRUST, sets(4, "10", 70.0)),
                        ex(w.ROMANIAN_DEADLIFT, sets(3, "10", 50.0)),
                        ex(w.LUNGE, sets(3, "12", 8.0))
                    ))
            )
        }

    val templates: List<JsonObject>
        get() {
            val w = DemoAPI.W
            val specs = listOf(
                Triple(90, upperBodyStrength, listOf(
                    ex(w.BENCH, sets(4, "6–8", null)),
                    ex(w.ROW, sets(4, "8", null)),
                    ex(w.OVERHEAD_PRESS, sets(3, "10", null)),
                    ex(w.CURL, sets(3, "12", null)),
                    ex(w.PUSHDOWN, sets(3, "12", null), superset = true)
                )),
                Triple(91, lowerBodyStrength, listOf(
                    ex(w.SQUAT, sets(4, "5", null)),
                    ex(w.DEADLIFT, sets(3, "3", null)),
                    ex(w.HIP_THRUST, sets(3, "10", null)),
                    ex(w.LUNGE, sets(3, "12", null)),
                    ex(w.PLANK, sets(3, "45 s", null))
                )),
                Triple(92, fullBodyConditioning, listOf(
                    ex(w.SQUAT, sets(3, "10", null)),
                    ex(w.PULL_UP, sets(3, "8", null)),
                    ex(w.OVERHEAD_PRESS, sets(3, "10", null)),
                    ex(w.ROMANIAN_DEADLIFT, sets(3, "10", null)),
                    ex(w.PLANK, sets(3, "60 s", null))
                ))
            )
            return specs.mapIndexed { index, (number, name, exercises) ->
                json(
                    "id" to DemoAPI.id(DemoAPI.Kind.TEMPLATE, number),
                    "name" to name,
                    "note" to null,
                    "exercises" to exercisesJson(number, exercises),
                    "authorName" to DemoAPI.coach.fullName,
                    "canEdit" to true,
                    "createdAt" to iso(day(-90 + index * 10, 12)),
                    "updatedAt" to iso(day(-30 + index * 5, 12))
                )
            }
        }

    // MARK: FMS

    // scores: deep squat, hurdle step, in-line lunge, active straight leg raise,
    // trunk stability push-up, rotary stability, shoulder mobility.
    private fun screen(number: Int, client: Int, date: ZonedDateTime, scores: List<String>, description: String) = json(
        "id" to DemoAPI.id(DemoAPI.Kind.FMS, number),
        "deepSquat" to scores[0],
        "hurdleStep" to scores[1],
        "inLineLunge" to scores[2],
        "activeStraightLegRaise" to scores[3],
        "trunkStabilityPushUp" to scores[4],
        "rotaryStability" to scores[5],
        "shoulderMobility" to scores[6],
        "xTest" to "Negative",
        "description" to description,
        "createdAt" to iso(date),
        "client" to person(client)
    )

    val anaScreens: List<JsonObject>
        get() = listOf(
            screen(2, DemoAPI.ClientIndex.ANA, day(-7, 10), listOf("2", "2", "2", "3", "2", "2", "3"), tr(
                "Clear progress: the deep squat now reaches parallel with the heels down and the push-up stays solid. Total score 16 (+4).",
                "Jasan napredak: duboki čučanj sada doseže paralelu s petama na podu, a sklek ostaje stabilan. Ukupno 16 bodova (+4).",
                "Progressi evidenti: lo squat profondo ora arriva al parallelo con i talloni a terra e il push-up resta solido. Punteggio totale 16 (+4).",
                "Deutlicher Fortschritt: Die tiefe Kniebeuge erreicht jetzt die Parallele mit den Fersen am Boden, der Liegestütz bleibt stabil. Gesamtpunktzahl 16 (+4)."
            )),
            screen(1, DemoAPI.ClientIndex.ANA, day(-70, 10), listOf("1", "2", "2", "2", "1", "2", "2"), tr(
                "Deep squat limited by ankle mobility, lower back sags in the push-up. Focus: ankle mobility and core bracing. Total score 12.",
                "Duboki čučanj ograničen je mobilnošću gležnja, donji dio leđa propada u skleku. Fokus: mobilnost gležnja i stabilizacija trupa. Ukupno 12 bodova.",
                "Squat profondo limitato dalla mobilità della caviglia, la zona lombare cede nel push-up. Focus: mobilità della caviglia e stabilità del core. Punteggio totale 12.",
                "Tiefe Kniebeuge durch die Sprunggelenkmobilität begrenzt, der untere Rücken hängt im Liegestütz durch. Fokus: Sprunggelenkmobilität und Rumpfspannung. Gesamtpunktzahl 12."
            ))
        )

    val allScreens: List<JsonObject>
        get() = anaScreens + screen(3, DemoAPI.ClientIndex.MARKO, day(-40, 9), listOf("2", "2", "3", "2", "3", "2", "1"), tr(
            "Shoulder mobility is the limiting factor. Thoracic and shoulder mobility work before every upper body session.",
            "Mobilnost ramena je ograničavajući faktor. Rad na mobilnosti prsne kralježnice i ramena prije svakog treninga gornjeg dijela tijela.",
            "La mobilità delle spalle è il fattore limitante. Lavoro di mobilità toracica e delle spalle prima di ogni sessione per la parte superiore.",
            "Die Schultermobilität ist der begrenzende Faktor. Mobilitätsarbeit für Brustwirbelsäule und Schultern vor jeder Oberkörper-Einheit."
        ))

    // MARK: Appointments

    fun status(name: String): JsonObject {
        val number = mapOf("Approved" to 1, "Pending" to 2, "Cancelled" to 3)[name] ?: 1
        return json("id" to DemoAPI.id(DemoAPI.Kind.STATUS, number), "name" to name, "description" to name)
    }

    fun appointment(number: Int, start: ZonedDateTime, clients: List<Int>, statusName: String = "Approved"): JsonObject {
        val end = start.plusHours(1)
        val times = "${timeText(start)} - ${timeText(end)}"
        val slot = json(
            "id" to DemoAPI.id(DemoAPI.Kind.TIME_SLOT, start.hour * 100 + start.minute),
            "name" to times,
            "description" to times,
            "startTime" to "${timeText(start)}:00",
            "endTime" to "${timeText(end)}:00"
        )
        return json(
            "id" to DemoAPI.id(DemoAPI.Kind.APPOINTMENT, number),
            "startTime" to iso(start),
            "endTime" to iso(end),
            "timeSlot" to slot,
            "status" to status(statusName),
            "clients" to clients.map { client(it) }
        )
    }

    // Ana's sessions this month that are over. With two booked and five
    // credits left the package adds up to 12 (fewer early in the month).
    val anaDoneDates: List<ZonedDateTime>
        get() {
            val monthStart = today.withDayOfMonth(1).atStartOfDay(now.zone)
            val candidates = listOf(
                Triple(1, 18, 0), Triple(4, 7, 30), Triple(8, 18, 0), Triple(11, 7, 30), Triple(15, 18, 0),
                Triple(18, 7, 30), Triple(22, 18, 0), Triple(25, 7, 30), Triple(29, 18, 0)
            )
            val dates = candidates.map { day(-it.first, it.second, it.third) }.filter { !it.isBefore(monthStart) }.toMutableList()
            // Early in the month: sessions earlier today.
            var hour = now.hour - 2
            while (dates.size < 5 && hour >= 6) {
                dates += day(0, hour)
                hour -= 2
            }
            return dates.take(5)
        }

    val anaDone: List<JsonObject>
        get() = anaDoneDates.mapIndexed { index, date -> appointment(50 + index, date, listOf(DemoAPI.ClientIndex.ANA)) }

    // Booked: tomorrow 07:30 (the next appointment on home) and in three days.
    val anaBooked: List<JsonObject>
        get() = listOf(
            appointment(1, day(1, 7, 30), listOf(DemoAPI.ClientIndex.ANA)),
            appointment(2, day(3, 18), listOf(DemoAPI.ClientIndex.ANA))
        )

    // Requested by Ana, waiting for the coach.
    val anaPending: List<JsonObject>
        get() = listOf(appointment(3, day(6, 18), listOf(DemoAPI.ClientIndex.ANA), "Pending"))

    fun otherAppointment(number: Int): JsonObject {
        val session = DemoAPI.otherSessions.first { it.number == number }
        return appointment(session.number, day(session.day, session.hour, session.minute), listOf(session.client))
    }

    val appointmentsStatus: JsonObject
        get() {
            var approved = anaDone + anaBooked
            if (isCoach) approved = approved + DemoAPI.otherSessions.map { otherAppointment(it.number) }
            return json(
                "approvedAppointments" to approved,
                "pendingAppointments" to anaPending,
                "inProgressAppointments" to emptyList<JsonObject>()
            )
        }

    fun requestType(name: String): JsonObject {
        val isCancelation = name == "CancelationRequest"
        return json(
            "id" to DemoAPI.id(DemoAPI.Kind.CATALOG, if (isCancelation) 1 else 2),
            "name" to name,
            // Localization keys, the app lowercases them.
            "description" to if (isCancelation) "cancelation-request" else "join-request"
        )
    }

    // Requests Ana sent (GET appointment-requests).
    val anaSentRequests: List<JsonObject>
        get() = listOf(
            json(
                "id" to DemoAPI.id(DemoAPI.Kind.REQUEST, 1),
                "comment" to tr(
                    "I'm on a business trip that week, sorry. I'll make it up next week!",
                    "Taj tjedan sam na službenom putu, oprosti. Nadoknadit ću idući tjedan!",
                    "Quella settimana sono in viaggio di lavoro, scusa. Recupero la settimana prossima!",
                    "In der Woche bin ich auf Geschäftsreise, sorry. Ich hole es nächste Woche nach!"
                ),
                "type" to requestType("CancelationRequest"),
                "status" to json("name" to "Approved"),
                "appointment" to appointment(4, day(-6, 18), listOf(DemoAPI.ClientIndex.ANA), "Cancelled"),
                "createdAt" to iso(day(-9, 12, 10)),
                "updatedAt" to iso(day(-9, 14, 30))
            ),
            json(
                "id" to DemoAPI.id(DemoAPI.Kind.REQUEST, 2),
                "comment" to tr(
                    "Could I join Petra's morning session?",
                    "Mogu li se pridružiti Petrinom jutarnjem treningu?",
                    "Posso unirmi alla sessione mattutina di Petra?",
                    "Kann ich bei Petras Morgeneinheit mitmachen?"
                ),
                "type" to requestType("JoinRequest"),
                "status" to json("name" to "Declined"),
                "appointment" to otherAppointment(21),
                "createdAt" to iso(day(-2, 20, 15)),
                "updatedAt" to iso(day(-1, 8, 5))
            )
        )

    // Appointments Ana requested herself (GET appointments/my-requests).
    val anaMyRequests: List<JsonObject>
        get() = listOf(
            json("appointment" to anaBooked[0], "createdAt" to iso(day(-4, 19)), "updatedAt" to iso(day(-4, 20, 30))),
            json("appointment" to anaPending[0], "createdAt" to iso(minutesAgo(180)), "updatedAt" to iso(minutesAgo(180)))
        )

    // Requests waiting for the coach (GET appointment-requests/pending).
    val pendingRequests: List<JsonObject>
        get() = listOf(
            json(
                "id" to DemoAPI.id(DemoAPI.Kind.REQUEST, 10),
                "comment" to tr(
                    "I have a doctor's appointment tomorrow morning. Sorry for the short notice!",
                    "Sutra ujutro imam pregled kod liječnika. Oprosti što javljam ovako kasno!",
                    "Domani mattina ho una visita medica. Scusa per il poco preavviso!",
                    "Ich habe morgen früh einen Arzttermin. Sorry, dass ich so kurzfristig absage!"
                ),
                "sender" to client(DemoAPI.ClientIndex.LUCIJA),
                "type" to requestType("CancelationRequest"),
                "appointment" to otherAppointment(20),
                "createdAt" to iso(minutesAgo(120))
            ),
            json(
                "id" to DemoAPI.id(DemoAPI.Kind.REQUEST, 11),
                "comment" to tr(
                    "Petra told me about your morning sessions. Could I join this one?",
                    "Petra mi je pričala o tvojim jutarnjim treninzima. Mogu li se pridružiti ovome?",
                    "Petra mi ha parlato delle tue sessioni mattutine. Posso unirmi a questa?",
                    "Petra hat mir von deinen Morgeneinheiten erzählt. Kann ich bei dieser mitmachen?"
                ),
                "sender" to client(DemoAPI.ClientIndex.MAJA),
                "type" to requestType("JoinRequest"),
                "appointment" to otherAppointment(21),
                "createdAt" to iso(minutesAgo(300))
            )
        )

    val appointmentTypes: List<JsonObject>
        get() = listOf(
            json("id" to DemoAPI.id(DemoAPI.Kind.CATALOG, 10), "name" to "PersonalTraining", "description" to "Personal training"),
            json("id" to DemoAPI.id(DemoAPI.Kind.CATALOG, 11), "name" to "Consultation", "description" to "Consultation"),
            json("id" to DemoAPI.id(DemoAPI.Kind.CATALOG, 12), "name" to "GroupTraining", "description" to "Small group training")
        )

    // Free slots of a day, one taken by Petra that can be joined.
    fun timeSlots(dayText: String?): List<JsonObject> =
        listOf(7 to 30, 9 to 0, 10 to 30, 16 to 0, 17 to 30, 19 to 0).map { (hour, minute) ->
            val start = day(1, hour, minute)
            val end = start.plusHours(1)
            val isTaken = hour == 17
            var name = "${timeText(start)} - ${timeText(end)}"
            if (isTaken) name += " (${DemoAPI.clients[DemoAPI.ClientIndex.PETRA].fullName})"
            val fields = mutableListOf<Pair<String, Any?>>(
                "id" to DemoAPI.id(DemoAPI.Kind.TIME_SLOT, 10_000 + hour * 100 + minute),
                "name" to name,
                "startTime" to "${timeText(start)}:00",
                "endTime" to "${timeText(end)}:00",
                "isTaken" to isTaken
            )
            if (dayText != null) fields += "day" to dayText
            if (isTaken) fields += "appointmentId" to DemoAPI.id(DemoAPI.Kind.APPOINTMENT, 90)
            json(*fields.toTypedArray())
        }

    // MARK: Notifications

    private fun notification(
        number: Int, type: String, title: String, body: String, date: ZonedDateTime,
        read: Boolean, data: Map<String, String> = emptyMap()
    ) = json(
        "id" to DemoAPI.id(DemoAPI.Kind.NOTIFICATION, number),
        "type" to type,
        "title" to title,
        "body" to body,
        "data" to data,
        "createdAt" to iso(date),
        "readAt" to if (read) iso(date.plusMinutes(30)) else null
    )

    val lastMonthName: String
        get() {
            val locale = Locale.forLanguageTag(language)
            return now.minusMonths(1).month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, locale)
        }

    // Newest first, two unread.
    val notifications: List<JsonObject>
        get() {
            if (isCoach) {
                return listOf(
                    notification(10, "appointment_request",
                        tr("Cancelation request", "Zahtjev za otkazivanje", "Richiesta di annullamento", "Absageanfrage"),
                        tr(
                            "Lucija Perić asked to cancel tomorrow's session at 10:00.",
                            "Lucija Perić traži otkazivanje sutrašnjeg treninga u 10:00.",
                            "Lucija Perić ha chiesto di annullare la sessione di domani alle 10:00.",
                            "Lucija Perić möchte die morgige Einheit um 10:00 absagen."
                        ),
                        minutesAgo(118), read = false),
                    notification(11, "appointment_request",
                        tr("New appointment request", "Novi zahtjev za termin", "Nuova richiesta di appuntamento", "Neue Terminanfrage"),
                        tr(
                            "Ana Kovač asked for an appointment next week.",
                            "Ana Kovač traži termin idući tjedan.",
                            "Ana Kovač ha chiesto un appuntamento per la prossima settimana.",
                            "Ana Kovač hat einen Termin für nächste Woche angefragt."
                        ),
                        minutesAgo(178), read = false),
                    notification(12, "monthly_review_reminder",
                        tr("Time for monthly reviews", "Vrijeme je za mjesečne osvrte", "È ora delle revisioni mensili", "Zeit für die Monatsrückblicke"),
                        tr(
                            "Write last month's reviews for your clients.",
                            "Napiši osvrte za prošli mjesec svojim klijentima.",
                            "Scrivi le revisioni del mese scorso per i tuoi clienti.",
                            "Schreibe die Rückblicke des letzten Monats für deine Kunden."
                        ),
                        day(-2, 9), read = true)
                )
            }
            return listOf(
                notification(1, "training",
                    tr("New training planned", "Novi trening u planu", "Nuovo allenamento pianificato", "Neues Training geplant"),
                    tr(
                        "Luka planned your next training: $lowerBodyStrength.",
                        "Luka je isplanirao tvoj sljedeći trening: $lowerBodyStrength.",
                        "Luka ha pianificato il tuo prossimo allenamento: $lowerBodyStrength.",
                        "Luka hat dein nächstes Training geplant: $lowerBodyStrength."
                    ),
                    minutesAgo(130), read = false, data = mapOf("trainingId" to DemoAPI.id(DemoAPI.Kind.TRAINING, 7))),
                notification(2, "appointment_updated",
                    tr("Appointment approved", "Termin odobren", "Appuntamento confermato", "Termin bestätigt"),
                    tr(
                        "Your appointment tomorrow at 07:30 is confirmed. See you there!",
                        "Tvoj termin sutra u 07:30 je potvrđen. Vidimo se!",
                        "Il tuo appuntamento di domani alle 07:30 è confermato. A domani!",
                        "Dein Termin morgen um 07:30 ist bestätigt. Bis dann!"
                    ),
                    minutesAgo(310), read = false),
                notification(3, "monthly_review",
                    tr("Your monthly review is ready", "Tvoj mjesečni osvrt je spreman", "La tua revisione mensile è pronta", "Dein Monatsrückblick ist da"),
                    tr(
                        "Luka wrote your review for $lastMonthName. Great progress!",
                        "Luka je napisao tvoj osvrt za $lastMonthName. Odličan napredak!",
                        "Luka ha scritto la tua revisione di $lastMonthName. Ottimi progressi!",
                        "Luka hat deinen Rückblick für $lastMonthName geschrieben. Toller Fortschritt!"
                    ),
                    day(-2, 9, 15), read = true),
                notification(4, "client_goal",
                    tr("Goal and plan updated", "Cilj i plan ažurirani", "Obiettivo e piano aggiornati", "Ziel und Plan aktualisiert"),
                    tr(
                        "Your coach updated your goal and plan for the new strength block.",
                        "Tvoj trener ažurirao je cilj i plan za novi blok snage.",
                        "Il tuo coach ha aggiornato obiettivo e piano per il nuovo blocco di forza.",
                        "Dein Coach hat Ziel und Plan für den neuen Kraftblock aktualisiert."
                    ),
                    day(-3, 10, 20), read = true),
                notification(5, "body_measurement",
                    tr("New body measurement", "Novo mjerenje tijela", "Nuova misurazione corporea", "Neue Körpermessung"),
                    tr(
                        "Weight 79.5 kg, waist 86 cm. That's 4.5 kg down since you started!",
                        "Težina 79,5 kg, struk 86 cm. To je 4,5 kg manje otkad si počela!",
                        "Peso 79,5 kg, vita 86 cm. Sono 4,5 kg in meno da quando hai iniziato!",
                        "Gewicht 79,5 kg, Taille 86 cm. Das sind 4,5 kg weniger seit deinem Start!"
                    ),
                    day(-3, 8, 40), read = true)
            )
        }

    val unreadNotificationCount: Int
        get() = notifications.count { it["readAt"] is JsonNull }

    // MARK: Chat

    fun participant(clientIndex: Int?): JsonObject =
        if (clientIndex == null) {
            json("id" to DemoAPI.coachId, "userId" to DemoAPI.coachUserId, "fullName" to DemoAPI.coach.fullName)
        } else {
            json(
                "id" to DemoAPI.clientId(clientIndex),
                "userId" to DemoAPI.userIdOfClient(clientIndex),
                "fullName" to DemoAPI.clients[clientIndex].fullName
            )
        }

    fun attachmentJson(id: String) = json(
        "id" to id,
        "contentType" to "image/jpeg",
        "width" to DemoAPI.ATTACHMENT_WIDTH,
        "height" to DemoAPI.ATTACHMENT_HEIGHT
    )

    // Yesterday's training talk and this morning's messages, oldest first.
    private val anaMessages: List<MessageSpec>
        get() = listOf(
            MessageSpec(1, true, day(-1, 19, 40), 6, tr(
                "Great work tonight, Ana! 70 kg for 6 on the bench is a new personal record 🎉",
                "Odličan trening večeras, Ana! 70 kg × 6 na klupi je novi osobni rekord 🎉",
                "Ottimo lavoro stasera, Ana! 70 kg per 6 in panca è il tuo nuovo record personale 🎉",
                "Starkes Training heute Abend, Ana! 70 kg für 6 auf der Bank sind ein neuer persönlicher Rekord 🎉"
            )),
            MessageSpec(2, false, day(-1, 19, 52), 3, tr(
                "Thank you! The last set was heavy, but the bar was still moving well.",
                "Hvala! Zadnja serija bila je teška, ali šipka je i dalje išla dobro.",
                "Grazie! L'ultima serie era pesante, ma il bilanciere saliva ancora bene.",
                "Danke! Der letzte Satz war schwer, aber die Stange ging noch gut hoch."
            )),
            MessageSpec(3, true, day(-1, 19, 58), 4, tr(
                "It looked solid. Keep your shoulder blades pinned and drive your feet into the floor — that's where the next 5 kg will come from.",
                "Izgledalo je stabilno. Drži lopatice skupljene i guraj stopala u pod — odatle će doći sljedećih 5 kg.",
                "Sembrava solido. Tieni le scapole addotte e spingi i piedi nel pavimento: è da lì che arriveranno i prossimi 5 kg.",
                "Sah solide aus. Halte die Schulterblätter zusammen und drück die Füße in den Boden – da kommen die nächsten 5 kg her."
            )),
            MessageSpec(4, false, day(-1, 20, 5), 5, tr(
                "Got it! My lower back felt a bit tight after the rows, though.",
                "Jasno! Ali donji dio leđa bio mi je malo napet nakon veslanja.",
                "Capito! Però dopo il rematore la zona lombare era un po' rigida.",
                "Alles klar! Nach dem Rudern war mein unterer Rücken aber etwas verspannt."
            )),
            MessageSpec(5, true, day(-1, 20, 12), 8, tr(
                "That usually means the hips aren't hinging enough. Add this to your warm-up before the next lower body session:",
                "To obično znači da se premalo pregibaš u kukovima. Dodaj ovo u zagrijavanje prije idućeg treninga donjeg dijela tijela:",
                "Di solito significa che le anche non si piegano abbastanza. Aggiungi questo al riscaldamento prima della prossima sessione per la parte inferiore:",
                "Das heißt meist, dass die Hüfte zu wenig beugt. Nimm das vor der nächsten Unterkörper-Einheit ins Aufwärmen auf:"
            )),
            MessageSpec(6, true, day(-1, 20, 13), 7, tr(
                "2 × 10 with an empty bar, slow on the way down.",
                "2 × 10 s praznom šipkom, polako prema dolje.",
                "2 × 10 con il bilanciere scarico, lentamente in discesa.",
                "2 × 10 mit leerer Stange, langsam nach unten."
            ), type = "workout", workout = DemoAPI.W.ROMANIAN_DEADLIFT),
            MessageSpec(7, false, minutesAgo(95), 13, tr(
                "Meal prep for the week 💪",
                "Pripremljeni obroci za cijeli tjedan 💪",
                "Pasti pronti per tutta la settimana 💪",
                "Meal Prep für die ganze Woche 💪"
            ), type = "image", hasAttachment = true),
            MessageSpec(8, true, minutesAgo(80), 20, tr(
                "Looks great — plenty of protein and veggies. Exactly what we need for the deficit.",
                "Izgleda odlično — puno proteina i povrća. Upravo ono što nam treba za deficit.",
                "Ha un ottimo aspetto: tante proteine e verdure. Proprio quello che serve per il deficit.",
                "Sieht super aus – viel Eiweiß und Gemüse. Genau das, was wir für das Defizit brauchen."
            )),
            MessageSpec(9, false, minutesAgo(40), 10, tr(
                "Thanks! See you tomorrow at 7:30?",
                "Hvala! Vidimo se sutra u 7:30?",
                "Grazie! Ci vediamo domani alle 7:30?",
                "Danke! Bis morgen um 7:30?"
            )),
            MessageSpec(10, true, minutesAgo(25), 5, tr(
                "Yes, see you then. We start with squats 💪",
                "Da, vidimo se. Počinjemo s čučnjevima 💪",
                "Sì, a domani. Iniziamo con lo squat 💪",
                "Ja, bis dann. Wir starten mit Kniebeugen 💪"
            ))
        )

    // Messages of a conversation; the coach's other chats have one each.
    private fun messageSpecs(index: Int): List<MessageSpec> = when (index) {
        DemoAPI.ClientIndex.ANA -> anaMessages
        DemoAPI.ClientIndex.MARKO -> listOf(MessageSpec(101, false, minutesAgo(185), null, tr(
            "Thanks, 18:00 tomorrow works for me 👍",
            "Hvala, sutra u 18:00 mi odgovara 👍",
            "Grazie, domani alle 18:00 per me va bene 👍",
            "Danke, morgen um 18:00 passt mir 👍"
        )))
        DemoAPI.ClientIndex.PETRA -> listOf(MessageSpec(102, false, day(-1, 21, 10), 12, tr(
            "My shoulders feel much better since the new warm-up. Thank you!",
            "Ramena su mi puno bolje otkad radim novo zagrijavanje. Hvala!",
            "Le spalle vanno molto meglio da quando faccio il nuovo riscaldamento. Grazie!",
            "Meinen Schultern geht es seit dem neuen Aufwärmen viel besser. Danke!"
        )))
        DemoAPI.ClientIndex.IVANA -> listOf(MessageSpec(103, true, day(-3, 12), 45, tr(
            "Send me a video of your hip thrusts when you get a chance.",
            "Pošalji mi video svojih potisaka kukovima kad stigneš.",
            "Mandami un video dei tuoi hip thrust quando puoi.",
            "Schick mir ein Video deiner Hip Thrusts, wenn du Zeit hast."
        )))
        else -> emptyList()
    }

    private fun message(spec: MessageSpec, index: Int): JsonObject {
        val sender = if (spec.fromCoach) DemoAPI.coachUserId else DemoAPI.userIdOfClient(index)
        val fields = mutableListOf<Pair<String, Any?>>(
            "id" to DemoAPI.id(DemoAPI.Kind.MESSAGE, spec.number),
            "conversationId" to DemoAPI.conversationId(index),
            "senderUserId" to sender,
            "content" to spec.content,
            "createdAt" to iso(spec.date),
            "readAt" to spec.readAfter?.let { iso(spec.date.plusMinutes(it.toLong())) },
            "type" to spec.type
        )
        spec.workout?.let { number -> workout(number)?.let { fields += "workout" to it } }
        if (spec.hasAttachment) fields += "attachment" to attachmentJson(DemoAPI.id(DemoAPI.Kind.ATTACHMENT, spec.number))
        return json(*fields.toTypedArray())
    }

    fun messages(index: Int): List<JsonObject> = messageSpecs(index).map { message(it, index) }

    fun conversation(index: Int): JsonObject {
        val specs = messageSpecs(index)
        // Unread: the other side's messages the logged user hasn't read.
        val unread = specs.count { it.fromCoach != isCoach && it.readAfter == null }
        val fields = mutableListOf<Pair<String, Any?>>(
            "id" to DemoAPI.conversationId(index),
            "coach" to participant(null),
            "client" to participant(index),
            "isEnabled" to true,
            "isBlockedByClient" to false,
            "unreadCount" to unread
        )
        specs.lastOrNull()?.let { last ->
            fields += "lastMessageAt" to iso(last.date)
            fields += "lastMessage" to message(last, index)
        }
        return json(*fields.toTypedArray())
    }

    // Clients the coach chats with; Ana's conversation is the newest.
    val chatClients: List<Int>
        get() {
            if (!isCoach) return listOf(DemoAPI.ClientIndex.ANA)
            return listOf(DemoAPI.ClientIndex.ANA, DemoAPI.ClientIndex.MARKO, DemoAPI.ClientIndex.PETRA, DemoAPI.ClientIndex.IVANA)
                .sortedByDescending { messageSpecs(it).lastOrNull()?.date?.toEpochSecond() ?: 0L }
        }

    val conversations: List<JsonObject> get() = chatClients.map { conversation(it) }

    val chatUnreadCount: Int
        get() = chatClients.sumOf { index -> messageSpecs(index).count { it.fromCoach != isCoach && it.readAfter == null } }

    val contacts: List<JsonObject>
        get() {
            if (!isCoach) {
                return listOf(json(
                    "id" to DemoAPI.coachId,
                    "userId" to DemoAPI.coachUserId,
                    "fullName" to DemoAPI.coach.fullName,
                    "conversationId" to DemoAPI.anaConversationId,
                    "isEnabled" to true
                ))
            }
            val chats = chatClients
            return DemoAPI.clients.indices.map { index ->
                json(
                    "id" to DemoAPI.clientId(index),
                    "userId" to DemoAPI.userIdOfClient(index),
                    "fullName" to DemoAPI.clients[index].fullName,
                    "conversationId" to if (index in chats) DemoAPI.conversationId(index) else null,
                    "isEnabled" to true
                )
            }
        }

    // Message the logged user just sent: text, a shared workout or a picture.
    fun sentMessage(conversationId: String, content: String, workoutId: String? = null, isImage: Boolean = false): JsonObject {
        val sharedWorkout = workoutNumber(workoutId)
        val fields = mutableListOf<Pair<String, Any?>>(
            "id" to UUID.randomUUID().toString().uppercase(Locale.ROOT),
            "conversationId" to conversationId,
            "senderUserId" to if (isCoach) DemoAPI.coachUserId else DemoAPI.anaUserId,
            "content" to content,
            "createdAt" to iso(now),
            "readAt" to null,
            "type" to if (isImage) "image" else if (sharedWorkout == null) "text" else "workout"
        )
        sharedWorkout?.let { number -> workout(number)?.let { fields += "workout" to it } }
        if (isImage) fields += "attachment" to attachmentJson(UUID.randomUUID().toString().uppercase(Locale.ROOT))
        return json(*fields.toTypedArray())
    }

    // MARK: Saved changes

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    // Exercises of a training or template from a POST or PUT body.
    private fun savedExercises(value: JsonElement?): List<JsonObject> =
        ((value as? JsonArray) ?: JsonArray(emptyList())).mapIndexedNotNull { index, element ->
            val exercise = element as? JsonObject ?: return@mapIndexedNotNull null
            val workoutId = exercise.string("workout") ?: UUID.randomUUID().toString()
            val sets = ((exercise["sets"] as? JsonArray) ?: JsonArray(emptyList())).mapIndexedNotNull { setIndex, setElement ->
                val set = setElement as? JsonObject ?: return@mapIndexedNotNull null
                json(
                    "id" to UUID.randomUUID().toString(),
                    "order" to setIndex + 1,
                    "reps" to set["reps"],
                    "weight" to set["weight"],
                    "isDropSet" to (set.bool("isDropSet") ?: false),
                    "durationSeconds" to set["durationSeconds"],
                    "distanceMeters" to set["distanceMeters"]
                )
            }
            json(
                "id" to UUID.randomUUID().toString(),
                "workoutId" to workoutId,
                "workoutName" to (workoutNumber(workoutId)?.let { workoutName(it) } ?: text("", "")),
                "order" to index + 1,
                "note" to exercise["note"],
                "sets" to sets,
                "isSupersetWithPrevious" to (exercise.bool("isSupersetWithPrevious") ?: false),
                "paceType" to workoutNumber(workoutId)?.let { DemoAPI.paceTypes[it] }
            )
        }

    fun savedTraining(id: String, input: JsonObject): JsonObject {
        val index = clientIndex(input.string("client")) ?: DemoAPI.ClientIndex.ANA
        val isCompleted = input.bool("isCompleted") ?: false
        return json(
            "id" to id,
            "name" to (input.string("name") ?: ""),
            "date" to (input.string("date") ?: iso(now)),
            "note" to input["note"],
            "isCompleted" to isCompleted,
            "completedAt" to if (isCompleted) iso(now) else null,
            "client" to person(index),
            "exercises" to savedExercises(input["exercises"])
        )
    }

    fun savedTemplate(id: String, input: JsonObject): JsonObject = json(
        "id" to id,
        "name" to (input.string("name") ?: ""),
        "note" to input["note"],
        "exercises" to savedExercises(input["exercises"]),
        "authorName" to DemoAPI.coach.fullName,
        "canEdit" to true,
        "createdAt" to iso(now),
        "updatedAt" to iso(now)
    )

    fun savedWorkout(id: String, input: JsonObject): JsonObject {
        val typeIds = ((input["workoutTypes"] as? JsonArray) ?: JsonArray(emptyList()))
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.uppercase(Locale.ROOT) }
        val types = DemoAPI.workoutTypes.filter { DemoAPI.id(DemoAPI.Kind.WORKOUT_TYPE, it.first) in typeIds }
        // "" records no distance; workouts with a pace type are timed, like on the API.
        val paceType = input.string("paceType")?.takeIf { it.isNotEmpty() }
        return json(
            "id" to id,
            "name" to (input["name"] ?: text("", "")),
            "description" to (input["description"] ?: text("", "")),
            "thumbnailUrl" to (input.string("thumbnailUrl") ?: ""),
            "videoUrl" to (input.string("videoUrl") ?: ""),
            "workoutTypes" to types.map { workoutType(it.first) },
            "muscles" to (input["muscles"] as? JsonArray ?: JsonArray(emptyList())),
            "secondaryMuscles" to (input["secondaryMuscles"] as? JsonArray ?: JsonArray(emptyList())),
            "isTimed" to ((input.bool("isTimed") ?: false) || paceType != null),
            "category" to (input.string("category") ?: "StrengthConditioning"),
            "paceType" to paceType
        )
    }

    // MARK: Routes

    fun reply(method: String, path: String, query: Map<String, String>, body: String?): DemoAPI.Reply {
        val segments = path.split("/").filter { it.isNotEmpty() }
        val input: JsonObject = body?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: JsonObject(emptyMap())

        // Captured "*" segments when the request matches, e.g. "workouts/*".
        fun route(expectedMethod: String, pattern: String): List<String>? {
            if (method != expectedMethod) return null
            val parts = pattern.split("/")
            if (parts.size != segments.size) return null
            val captured = mutableListOf<String>()
            for ((part, segment) in parts.zip(segments)) {
                if (part == "*") captured += segment else if (part != segment) return null
            }
            return captured
        }

        fun ok(element: JsonElement) = DemoAPI.Reply.json(element)
        fun ok(list: List<JsonElement>) = DemoAPI.Reply.json(JsonArray(list))

        // Staff ask for one client; only Ana has reviews and records.
        val asksForAna = query["clientId"]?.equals(DemoAPI.anaClientId, ignoreCase = true) ?: true
        val statusReply = ok(json("id" to UUID.randomUUID().toString()))

        // The chat hub (SignalR) isn't faked, it fails quietly and retries.
        if (segments.firstOrNull() == "hubs") return DemoAPI.Reply.notFound

        // Login: a username with "coach" in it is the coach, any other Ana.
        if (route("POST", "authentication/login") != null) {
            val username = input.string("username").orEmpty().lowercase(Locale.ROOT)
            return ok(json("token" to DemoAPI.token(if ("coach" in username) DemoAPI.Role.COACH else DemoAPI.Role.CLIENT)))
        }

        // Cooperation rules: the coach has none to accept, Ana accepted them.
        if (route("GET", "rules") != null || route("POST", "rules/accept") != null) return ok(rules)

        // People
        if (route("GET", "profile") != null) return ok(profile)
        if (route("GET", "profile/picture") != null) return DemoAPI.Reply.notFound
        if (route("GET", "clients/current-client") != null) return ok(client(DemoAPI.ClientIndex.ANA))
        if (route("GET", "coaches/current-coach") != null) return ok(coach)
        if (route("GET", "coaches/all") != null || route("GET", "coaches/client") != null) return ok(listOf(coach))
        if (route("GET", "clients") != null) return ok(DemoAPI.clients.indices.map { client(it, withMeasurements = true) })

        // Body, goal, reviews and records
        if (route("GET", "body-measurements") != null) {
            return ok(measurements(DemoAPI.ClientIndex.ANA).reversed().map { measurementJson(it, "createdAt") })
        }
        if (route("GET", "client-goals/my") != null) return ok(anaGoal)
        route("GET", "client-goals/*")?.let { ids ->
            return ok(if (ids[0].equals(DemoAPI.anaClientId, ignoreCase = true)) anaGoal else JsonObject(emptyMap()))
        }
        if (route("GET", "monthly-reviews") != null) return ok(if (asksForAna) anaReviews else emptyList())
        if (route("GET", "personal-records") != null) return ok(if (asksForAna) anaRecords else emptyList())
        if (route("GET", "functional-movement-screens") != null) return ok(if (isCoach) allScreens else anaScreens)

        // Trainings and workouts
        if (route("GET", "trainings") != null) {
            // Clients only get completed trainings.
            return ok(
                if (isCoach) anaTrainings + otherTrainings
                else anaTrainings.filter { (it["isCompleted"] as? JsonPrimitive)?.booleanOrNull == true }
            )
        }
        if (route("GET", "training-templates") != null) return ok(templates)
        if (route("GET", "workouts") != null) return ok(allWorkouts)
        route("GET", "workouts/*")?.let { ids ->
            val found = workoutNumber(ids[0])?.let { number -> workout(number) } ?: return DemoAPI.Reply.notFound
            return ok(found)
        }
        if (route("GET", "workout-types") != null) return ok(DemoAPI.workoutTypes.map { workoutType(it.first) })

        // Appointments
        if (route("GET", "appointments") != null) return ok(appointmentsStatus)
        if (route("GET", "appointments/my-requests") != null) return ok(if (isCoach) emptyList() else anaMyRequests)
        if (route("GET", "appointment-requests") != null) return ok(if (isCoach) emptyList() else anaSentRequests)
        if (route("GET", "appointment-requests/pending") != null) return ok(if (isCoach) pendingRequests else emptyList())
        if (route("GET", "appointment-types") != null) return ok(appointmentTypes)
        if (route("GET", "recurring-appointments/generate-next-week") != null) {
            // Number of appointments created.
            return DemoAPI.Reply(body = "3".toByteArray())
        }
        // Approving, declining, canceling and joining are GET requests.
        if (route("GET", "appointments/*/*") != null || route("GET", "appointment-requests/*/*") != null) return statusReply
        if (route("POST", "time-slots/available") != null) return ok(timeSlots(input.string("day")))

        // Notifications
        if (route("GET", "notifications") != null) return ok(notifications)
        if (route("GET", "notifications/unread-count") != null) return ok(json("count" to unreadNotificationCount))

        // Chat
        if (route("GET", "chat/conversations") != null) return ok(conversations)
        if (route("GET", "chat/contacts") != null) return ok(contacts)
        if (route("GET", "chat/unread-count") != null) return ok(json("count" to chatUnreadCount))
        route("GET", "chat/conversations/*/messages")?.let { ids ->
            // One page holds the whole conversation, there are no older messages.
            val index = conversationIndex(ids[0])
            if (query.containsKey("before") || index == null) return ok(emptyList())
            return ok(messages(index))
        }
        if (route("GET", "chat/conversations/*/attachments/*") != null) {
            return DemoAPI.Reply(contentType = "image/jpeg", body = DemoAPI.imageBytes())
        }
        route("POST", "chat/conversations/*/messages")?.let { ids ->
            return ok(sentMessage(ids[0], input.string("content").orEmpty(), input.string("workoutId")))
        }
        route("POST", "chat/conversations/*/images")?.let { ids ->
            return ok(sentMessage(ids[0], "", isImage = true))
        }
        if (route("POST", "chat/conversations/start") != null) return ok(conversation(DemoAPI.ClientIndex.ANA))
        if (route("POST", "chat/conversations") != null) {
            val conversation = conversation(clientIndex(input.string("clientId")) ?: DemoAPI.ClientIndex.ANA)
            return ok(JsonObject(conversation + ("isEnabled" to JsonPrimitive(input.bool("isEnabled") ?: true))))
        }
        route("POST", "chat/conversations/*/block")?.let { ids ->
            val isBlocked = input.bool("isBlocked") ?: false
            val conversation = conversation(conversationIndex(ids[0]) ?: DemoAPI.ClientIndex.ANA)
            return ok(JsonObject(conversation + mapOf("isBlockedByClient" to JsonPrimitive(isBlocked), "isEnabled" to JsonPrimitive(!isBlocked))))
        }

        // Changes answered with what the app decodes.
        if (route("PUT", "trainings/*/completion") != null) {
            val isCompleted = input.bool("isCompleted") ?: true
            return ok(json("isCompleted" to isCompleted, "completedAt" to if (isCompleted) iso(now) else null))
        }
        if (route("PUT", "trainings/*/times") != null) {
            val endedAt = input.string("endedAt")
            return ok(json(
                "startedAt" to input.string("startedAt"),
                "endedAt" to endedAt,
                "heartRate" to null,
                "isCompleted" to (endedAt != null),
                "completedAt" to endedAt
            ))
        }
        if (route("POST", "trainings") != null) return ok(savedTraining(UUID.randomUUID().toString(), input))
        route("PUT", "trainings/*")?.let { ids -> return ok(savedTraining(ids[0], input)) }
        if (route("POST", "training-templates") != null) return ok(savedTemplate(UUID.randomUUID().toString(), input))
        route("PUT", "training-templates/*")?.let { ids -> return ok(savedTemplate(ids[0], input)) }
        if (route("POST", "training-templates/*/assign") != null) {
            val clients = (input["clients"] as? JsonArray)?.size ?: 0
            return ok(json("trainingIds" to List(clients) { UUID.randomUUID().toString() }))
        }
        if (route("PUT", "client-goals/*") != null) {
            val fields = mutableListOf<Pair<String, Any?>>("updatedAt" to iso(now))
            for (key in listOf("goal", "currentBlock", "focus", "nextAssessment", "nextAssessmentDate")) {
                fields += key to input[key]
            }
            return ok(json(*fields.toTypedArray()))
        }
        if (route("PUT", "monthly-reviews") != null) {
            return ok(json(
                "id" to UUID.randomUUID().toString(),
                "clientId" to (input.string("client") ?: DemoAPI.anaClientId),
                "year" to (input.string("year")?.toIntOrNull() ?: now.year),
                "month" to (input.string("month")?.toIntOrNull() ?: now.monthValue),
                "content" to input.string("content").orEmpty(),
                "updatedAt" to iso(now)
            ))
        }
        if (route("POST", "workouts/import") != null) {
            return ok(json("id" to UUID.randomUUID().toString(), "status" to true, "queuedWorkoutsCount" to 0))
        }
        if (route("POST", "workouts/guess-muscles") != null) {
            return ok(json("isFullBody" to false, "muscles" to emptyList<String>(), "secondaryMuscles" to emptyList<String>()))
        }
        if (route("POST", "workouts") != null) return ok(savedWorkout(UUID.randomUUID().toString(), input))
        route("PUT", "workouts/*")?.let { ids -> return ok(savedWorkout(ids[0], input)) }
        if (route("PUT", "profile") != null) {
            val updated = profile.toMutableMap()
            for (key in listOf("email", "firstName", "lastName", "phone")) input[key]?.let { updated[key] = it }
            return ok(JsonObject(updated))
        }
        route("PUT", "clients/*")?.let { ids ->
            val updated = client(clientIndex(ids[0]) ?: DemoAPI.ClientIndex.ANA, withMeasurements = true).toMutableMap()
            for (key in listOf("firstName", "lastName", "email", "phone", "plan", "credits")) input[key]?.let { updated[key] = it }
            return ok(JsonObject(updated))
        }

        if (method == "GET") {
            android.util.Log.w("DemoAPI", "No demo data for GET $path")
            return DemoAPI.Reply.notFound
        }
        // Other changes succeed without changing the demo data.
        return statusReply
    }
}
