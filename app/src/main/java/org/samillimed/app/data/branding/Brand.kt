package org.samillimed.app.data.branding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.facility.FacilityScope
import java.io.ByteArrayOutputStream

/** What an administrator sets for the facility. Colours are written like #0D4F37. */
class BrandDoc(val displayName: String, val logoBase64: String, val primaryHex: String, val accentHex: String)

fun decodeLogo(base64: String): ImageBitmap? {
    if (base64.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** The facility's name, logo and colours, kept on the phone too so they show at once on the next start. */
object Brand {
    private const val PREFS = "brand_cache"

    var name by mutableStateOf("")
    var primaryHex by mutableStateOf("")
    var accentHex by mutableStateOf("")
    var logo by mutableStateOf<ImageBitmap?>(null)
    var logoBase64: String = ""
        private set

    val isSet: Boolean get() = name.isNotBlank() || logo != null || primaryHex.isNotBlank()

    fun loadCached(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        name = p.getString("name", "").orEmpty()
        primaryHex = p.getString("primary", "").orEmpty()
        accentHex = p.getString("accent", "").orEmpty()
        logoBase64 = p.getString("logo", "").orEmpty()
        logo = decodeLogo(logoBase64)
    }

    fun update(context: Context, doc: BrandDoc) {
        name = doc.displayName
        primaryHex = doc.primaryHex
        accentHex = doc.accentHex
        if (doc.logoBase64 != logoBase64) {
            logoBase64 = doc.logoBase64
            logo = decodeLogo(logoBase64)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", name)
            .putString("primary", primaryHex)
            .putString("accent", accentHex)
            .putString("logo", logoBase64)
            .apply()
    }
}

class BrandRepository {
    private val db get() = FacilityScope.firestore()

    private fun ref() = db.collection(FacilityScope.path("settings")).document("branding")

    /** Live, one read. A first answer from the phone's own copy that says "nothing" is ignored. */
    fun flow(): Flow<BrandDoc> = callbackFlow {
        val registration = ref().addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            if (!snap.exists() && snap.metadata.isFromCache) return@addSnapshotListener
            trySend(
                BrandDoc(
                    displayName = snap.getString("displayName").orEmpty(),
                    logoBase64 = snap.getString("logoBase64").orEmpty(),
                    primaryHex = snap.getString("primaryHex").orEmpty(),
                    accentHex = snap.getString("accentHex").orEmpty(),
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    fun save(doc: BrandDoc, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (!CurrentUser.isAdmin) return onFailure("Only an administrator can change the branding.")
        ref().set(
            mapOf(
                "displayName" to doc.displayName,
                "logoBase64" to doc.logoBase64,
                "primaryHex" to doc.primaryHex,
                "accentHex" to doc.accentHex,
                "updatedAtMillis" to System.currentTimeMillis(),
                "updatedBy" to FacilityScope.auth().currentUser?.email.orEmpty(),
            ),
        ).addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Could not save. Check the connection and try again.") }
    }
}

/** Keeps the facility's branding up to date while someone is signed in. Call once, after sign-in. */
@Composable
fun rememberBrandLoaded() {
    val context = LocalContext.current
    val repository = remember { BrandRepository() }
    LaunchedEffect(FacilityScope.facilityId) {
        repository.flow().collect { Brand.update(context, it) }
    }
}

object LogoProcessor {
    /** Shrinks the chosen picture to fit 256 pixels and returns it as text, or null if it cannot be read. */
    fun toBase64(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        var size = 256
        while (size >= 96) {
            val scale = minOf(size / raw.width.toFloat(), size / raw.height.toFloat(), 1f)
            val bitmap = Bitmap.createScaledBitmap(
                raw,
                (raw.width * scale).toInt().coerceAtLeast(1),
                (raw.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            if (out.size() <= 120_000) return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            size -= 48
        }
        return null
    }
}
