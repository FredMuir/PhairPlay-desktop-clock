package com.phairplay.integrations

import android.content.Context
import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore

data class ICloudCalendar(val href: String, val name: String)
data class ICloudEvent(val title: String, val startMillis: Long, val allDay: Boolean)

/** Direct, read-only iCloud Calendar client over standard CalDAV. */
object ICloudCalDav {
    private const val KEY_ALIAS = "phairplay_icloud_caldav_password"
    private const val PREFS = "icloud_caldav_secret"
    private const val PASSWORD_KEY = "app_specific_password"
    private val xmlType = "application/xml; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun savePassword(context: Context, password: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getKey()) }
        val bytes = cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PASSWORD_KEY, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).apply()
    }

    fun password(context: Context): String? = runCatching {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PASSWORD_KEY, null) ?: return null
        val bytes = android.util.Base64.decode(saved, android.util.Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    fun clearPassword(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PASSWORD_KEY).apply()
    }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    fun discoverCalendars(email: String, appPassword: String): List<ICloudCalendar> {
        require(email.contains('@'))
        val base = "https://caldav.icloud.com/".toHttpUrl()
        val discovery = """
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:current-user-principal/><c:calendar-home-set/></d:prop>
            </d:propfind>
        """.trimIndent()
        val first = propfind(base, email, appPassword, discovery, "0")
        val principal = first.firstNotNullOfOrNull { it.principalHref }
        val home = first.firstNotNullOfOrNull { it.calendarHomeHref }
            ?: principal?.let { href -> propfind(resolve(base, href), email, appPassword, discovery, "0")
                .firstNotNullOfOrNull { it.calendarHomeHref } }
            ?: error("Calendar home not found")
        val homeUrl = resolve(base, home)
        val calendarsXml = """
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:resourcetype/><d:displayname/></d:prop>
            </d:propfind>
        """.trimIndent()
        return propfind(homeUrl, email, appPassword, calendarsXml, "1")
            .filter { it.isCalendar && !it.href.isNullOrBlank() }
            .map {
                val calendarUrl = resolve(homeUrl, it.href!!).toString()
                ICloudCalendar(calendarUrl, it.displayName?.takeIf(String::isNotBlank) ?: calendarUrl.substringAfterLast('/').ifBlank { "iCloud Calendar" })
            }
            .distinctBy { it.href }
    }

    fun fetchEvents(email: String, appPassword: String, calendarHrefs: List<String>, startMillis: Long, endMillis: Long): List<ICloudEvent> {
        val start = formatUtc(startMillis)
        val end = formatUtc(endMillis)
        val query = """
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:getetag/><c:calendar-data/></d:prop>
              <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT">
                <c:time-range start="$start" end="$end"/>
              </c:comp-filter></c:comp-filter></c:filter>
            </c:calendar-query>
        """.trimIndent()
        val root = "https://caldav.icloud.com/".toHttpUrl()
        return calendarHrefs.flatMap { href ->
            val calendarUrl = resolve(root, href)
            report(calendarUrl, email, appPassword, query).flatMap { resource -> parseIcs(resource.calendarData.orEmpty()) }
        }.filter { it.startMillis in startMillis..endMillis }
            .distinctBy { it.title to it.startMillis }
            .sortedBy { it.startMillis }
    }

    private data class DavResource(
        var href: String? = null,
        var principalHref: String? = null,
        var calendarHomeHref: String? = null,
        var displayName: String? = null,
        var isCalendar: Boolean = false,
        var calendarData: String? = null
    )

    private fun propfind(url: HttpUrl, user: String, pass: String, xml: String, depth: String): List<DavResource> =
        davRequest(url, user, pass, "PROPFIND", xml, depth)

    private fun report(url: HttpUrl, user: String, pass: String, xml: String): List<DavResource> =
        davRequest(url, user, pass, "REPORT", xml, "1")

    private fun davRequest(url: HttpUrl, user: String, pass: String, method: String, xml: String, depth: String): List<DavResource> {
        var target = url
        repeat(6) { redirectCount ->
            require(target.isHttps && (target.host == "icloud.com" || target.host.endsWith(".icloud.com"))) {
                "CalDAV redirect left Apple's HTTPS service"
            }
            val request = Request.Builder().url(target)
                .header("Authorization", Credentials.basic(user, pass))
                .header("Depth", depth)
                .method(method, xml.toRequestBody(xmlType)).build()
            client.newCall(request).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    if (redirectCount == 5) error("Too many CalDAV redirects")
                    target = target.resolve(response.header("Location") ?: error("Missing redirect location"))
                        ?: error("Invalid CalDAV redirect")
                    return@repeat
                }
                if (!response.isSuccessful) error("CalDAV HTTP ${response.code}")
                return parseDav(response.body?.string().orEmpty())
            }
        }
        error("Unable to reach iCloud CalDAV")
    }

    private fun parseDav(xml: String): List<DavResource> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val resources = mutableListOf<DavResource>()
        var current: DavResource? = null
        var principal = false
        var calendarHome = false
        var inResourceType = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "response" -> current = DavResource()
                    "current-user-principal" -> principal = true
                    "calendar-home-set" -> calendarHome = true
                    "resourcetype" -> inResourceType = true
                    "calendar" -> if (inResourceType) current?.isCalendar = true
                    "href" -> {
                        val value = parser.nextText().trim()
                        if (principal) current?.principalHref = value
                        else if (calendarHome) current?.calendarHomeHref = value
                        else if (current?.href == null) current?.href = value
                    }
                    "displayname" -> current?.displayName = parser.nextText()
                    "calendar-data" -> current?.calendarData = parser.nextText()
                }
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "current-user-principal" -> principal = false
                    "calendar-home-set" -> calendarHome = false
                    "resourcetype" -> inResourceType = false
                    "response" -> current?.let { resources += it; current = null }
                }
            }
            event = parser.next()
        }
        return resources
    }

    private fun resolve(base: HttpUrl, href: String): HttpUrl = base.resolve(href) ?: error("Invalid CalDAV URL")

    private fun formatUtc(millis: Long): String = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(millis)

    private fun parseIcs(ics: String): List<ICloudEvent> {
        val lines = mutableListOf<String>()
        ics.replace("\r\n", "\n").lineSequence().forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && lines.isNotEmpty()) {
                lines[lines.lastIndex] += line.drop(1)
            } else lines += line
        }
        val events = mutableListOf<ICloudEvent>()
        var inEvent = false
        var start: Long? = null
        var allDay = false
        var title = ""
        for (line in lines) {
            if (line.equals("BEGIN:VEVENT", true)) { inEvent = true; start = null; allDay = false; title = ""; continue }
            if (line.equals("END:VEVENT", true)) {
                if (inEvent && start != null) events += ICloudEvent(title.ifBlank { "(Untitled)" }, start, allDay)
                inEvent = false
                continue
            }
            if (!inEvent) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon)
            val value = line.substring(colon + 1)
            when {
                key.startsWith("DTSTART", true) -> {
                    allDay = key.contains("VALUE=DATE", true) || Regex("^\\d{8}$").matches(value)
                    val tzid = Regex("TZID=([^;:]+)", RegexOption.IGNORE_CASE).find(key)?.groupValues?.get(1)
                    start = parseIcsDate(value, allDay, tzid)
                }
                key.startsWith("SUMMARY", true) -> title = unescapeIcs(value)
            }
        }
        return events
    }

    private fun parseIcsDate(value: String, allDay: Boolean, tzid: String?): Long? = runCatching {
        val pattern = when { allDay -> "yyyyMMdd"; value.endsWith("Z") -> "yyyyMMdd'T'HHmmss'Z'"; value.length == 13 -> "yyyyMMdd'T'HHmm"; else -> "yyyyMMdd'T'HHmmss" }
        SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = when { value.endsWith("Z") -> TimeZone.getTimeZone("UTC"); !tzid.isNullOrBlank() -> TimeZone.getTimeZone(tzid); allDay -> TimeZone.getDefault(); else -> TimeZone.getDefault() }
        }.parse(value)?.time
    }.getOrNull()

    private fun unescapeIcs(value: String): String = value
        .replace("\\n", "\n", true).replace("\\N", "\n", true)
        .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
