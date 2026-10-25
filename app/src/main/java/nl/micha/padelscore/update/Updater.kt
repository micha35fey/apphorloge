package nl.micha.padelscore.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.micha.padelscore.MainActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/** Haalt nieuwe versies op uit de GitHub Releases van de repo en installeert ze. */
class Updater(private val context: Context) {

    suspend fun check(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(LATEST_RELEASE_URL)
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val release = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = release.getString("tag_name")
            val code = ReleaseVersion.codeFromTag(tag)
            if (code == null || code <= currentVersionCode) return@runCatching null
            val assets = release.getJSONArray("assets")
            val apk = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?: return@runCatching null
            UpdateInfo(code, tag.removePrefix("v"), apk.getString("browser_download_url"))
        }.getOrNull()
    }

    /** Downloadt de APK in een installatiesessie. Android vraagt daarna zelf om bevestiging. */
    suspend fun install(info: UpdateInfo) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            open(info.apkUrl).inputStream.use { input ->
                session.openWrite("padelscore.apk", 0, -1).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            // De status komt terug bij MainActivity (onNewIntent).
            val intent = Intent(context, MainActivity::class.java).setAction(ACTION_INSTALL_STATUS)
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }

    companion object {
        const val ACTION_INSTALL_STATUS = "nl.micha.padelscore.INSTALL_STATUS"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/micha35fey/apphorloge/releases/latest"
    }
}
