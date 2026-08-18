package android.net

import android.os.Parcel

/**
 * A test-only Uri implementation that lives in the android.net package
 * to access the package-private Uri constructor.
 *
 * Each instance is identified by a string. Equality is based on the identifier string,
 * allowing FileSessionManager.findByUri() to correctly match duplicate URIs in tests.
 */
class TestUri(
    private val identifier: String,
    private val lastPathSeg: String? = null
) : Uri() {
    override fun toString(): String = identifier
    override fun hashCode(): Int = identifier.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestUri) return false
        return identifier == other.identifier
    }

    // Parcelable
    override fun writeToParcel(dest: Parcel, flags: Int) {}
    override fun describeContents(): Int = 0

    // Uri abstract members
    override fun buildUpon(): Builder? = null
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = lastPathSeg
    override fun getPath(): String? = null
    override fun getPathSegments(): MutableList<String> = mutableListOf()
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = "content"
    override fun getSchemeSpecificPart(): String? = null
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
}
