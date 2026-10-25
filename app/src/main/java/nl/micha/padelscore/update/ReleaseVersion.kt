package nl.micha.padelscore.update

// Releases heten "v1.<buildnummer>"; het buildnummer is ook de versionCode van de APK.
object ReleaseVersion {
    fun codeFromTag(tag: String): Int? =
        tag.removePrefix("v").substringAfterLast('.', "").toIntOrNull()
}
