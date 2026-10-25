package nl.micha.padelscore.update

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/** Laatste status van een installatie, zodat het scherm die kan tonen. */
object UpdateEvents {
    val status = MutableStateFlow<String?>(null)
}

/**
 * Haalt nieuwe versies op uit de GitHub Releases van de repo en installeert ze.
 * Horloge en telefoon hebben elk hun eigen releases, herkenbaar aan [tagPrefix] en [assetName].
 */
class Updater(
    private val context: Context,
    private val tagPrefix: String,
    private val assetName: String,
) {

    /** Nieuwere versie, of null als de app actueel is of er geen verbinding is. */
    suspend fun check(currentVersionCode: Int): UpdateInfo? =
        runCatching { checkOrThrow(currentVersionCode) }.getOrNull()

    /** Zoals [check], maar een netwerkfout geeft een exception in plaats van null. */
    suspend fun checkOrThrow(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open(RELEASES_URL)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val releases = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        // De volgorde van GitHub is niet op datum te vertrouwen: zoek zelf de hoogste versie.
        var best: UpdateInfo? = null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            val tag = release.getString("tag_name")
            if (release.optBoolean("draft") || !tag.startsWith(tagPrefix)) continue
            val code = ReleaseVersion.codeFromTag(tag) ?: continue
            if (code <= currentVersionCode || code <= (best?.versionCode ?: 0)) continue
            val assets = release.getJSONArray("assets")
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (asset.getString("name") == assetName) {
                    best = UpdateInfo(code, tag.removePrefix(tagPrefix), asset.getString("browser_download_url"))
                }
            }
        }
        best
    }

    /**
     * Downloadt de APK in een installatiesessie. Als Android het toestaat gebeurt dat zonder
     * bevestiging; anders vraagt Android erom (via een melding als [fromBackground]).
     */
    suspend fun install(info: UpdateInfo, fromBackground: Boolean = false) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Werkt alleen als deze app zelf de vorige versie installeerde; anders vraagt Android alsnog.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            open(info.apkUrl).inputStream.use { input ->
                session.openWrite("padelscore.apk", 0, -1).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(context, InstallStatusReceiver::class.java)
                .putExtra(InstallStatusReceiver.EXTRA_BACKGROUND, fromBackground)
            val pending = PendingIntent.getBroadcast(
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
        private const val RELEASES_URL =
            "https://api.github.com/repos/micha35fey/apphorloge/releases?per_page=100"

        /**
         * De eerste keer moet je toestaan dat de app zijn eigen update installeert.
         * Geeft false terug als de gebruiker eerst naar de instellingen is gestuurd.
         */
        fun ensureInstallAllowed(activity: Activity): Boolean {
            if (activity.packageManager.canRequestPackageInstalls()) return true
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            )
            return try {
                activity.startActivity(intent)
                false
            } catch (e: ActivityNotFoundException) {
                // Geen instellingenscherm (sommige horloges): gewoon proberen, Android vraagt het dan zelf.
                true
            }
        }
    }
}
