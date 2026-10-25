package nl.micha.padelscore.update

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/**
 * Haalt nieuwe versies op uit de GitHub Releases van de repo en installeert ze.
 * Horloge en telefoon hebben elk hun eigen releases, herkenbaar aan [tagPrefix] en [assetName].
 */
class Updater(
    private val context: Context,
    private val tagPrefix: String,
    private val assetName: String,
    /** Activity die de installatiestatus ontvangt in onNewIntent (launchMode singleTop). */
    private val statusActivity: Class<out Activity>,
) {

    suspend fun check(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(RELEASES_URL)
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val releases = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            // GitHub geeft de nieuwste release eerst.
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                val tag = release.getString("tag_name")
                if (release.optBoolean("draft") || !tag.startsWith(tagPrefix)) continue
                val code = ReleaseVersion.codeFromTag(tag) ?: continue
                if (code <= currentVersionCode) return@runCatching null
                val assets = release.getJSONArray("assets")
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.getString("name") == assetName) {
                        return@runCatching UpdateInfo(
                            code,
                            tag.removePrefix(tagPrefix),
                            asset.getString("browser_download_url"),
                        )
                    }
                }
            }
            null
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
            val intent = Intent(context, statusActivity).setAction(ACTION_INSTALL_STATUS)
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
        private const val RELEASES_URL =
            "https://api.github.com/repos/micha35fey/apphorloge/releases?per_page=30"

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

        /** Verwerkt de status uit onNewIntent. Geeft een tekst voor de gebruiker, of null. */
        fun handleInstallStatus(activity: Activity, intent: Intent): String? {
            if (intent.action != ACTION_INSTALL_STATUS) return null
            return when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm != null) activity.startActivity(confirm)
                    "Bevestig de installatie"
                }
                PackageInstaller.STATUS_SUCCESS -> "Geïnstalleerd"
                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $status"
                    "Mislukt: $message"
                }
            }
        }
    }
}
