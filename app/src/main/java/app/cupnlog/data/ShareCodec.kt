package app.cupnlog.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/**
 * Encodes and decodes the QR / link payloads that move a cupping setup between
 * devices (phase 3). The QR encodes an https App Link whose query string carries
 * the whole payload — no backend, the link IS the data (user decision 5):
 *
 *   https://<pages-host><APP_LINK_PATH>?v=1&d=<base64url(deflate(json))>
 *
 * Three incoming forms are accepted everywhere (deep link, in-app scanner,
 * pasted text) and decode identically:
 *   - the https App Link URL above,
 *   - `cofcup://import?v=1&d=…`,
 *   - the bare `d=` payload (the raw code shown under the QR).
 *
 * Compact keys keep even a 16-cup setup inside scannable QR density:
 *   {"v":1,"kind":"setup","sid":"…","host":"…","name":"…","ml":150,"cups":4,
 *    "co":[{"n":"Ethiopia","l":"1","c":-1614531}]}
 *
 * `kind` discriminates the payload: "setup" (full, non-blind) and "blind_setup"
 * (no identities at all — names, labels and colours stay host-side; any
 * identity data injected into a blind payload is rejected). "ratings" and
 * "group_results" arrive in phase 4 and are rejected here.
 *
 * Everything is untrusted: strict version/kind checks, bounded sizes, cup
 * size/count ranges, per-coffee caps, a decompression-bomb cap, and a ~2 KB
 * practical QR guard for the generated link (fall back to share-as-text /
 * copy-link when over). Validation never auto-applies anything — the caller
 * always shows a confirm preview first.
 */
object ShareCodec {

    // ------------------------------------------------------------------
    // Link shape
    // ------------------------------------------------------------------

    /** GitHub Pages host serving the static landing page + assetlinks.json. */
    const val APP_LINK_HOST = "arthurdehgan.github.io"

    /** Path of the App Link entry on that host (repo: cupnlog). */
    const val APP_LINK_PATH = "/cupnlog/cofcup/i"

    /** Custom scheme fallback, still accepted on every incoming path. */
    const val CUSTOM_SCHEME = "cofcup://import"

    // ------------------------------------------------------------------
    // Bounds (untrusted input; generation caps at the same values)
    // ------------------------------------------------------------------

    /** Envelope version this codec speaks; anything else is rejected. */
    const val VERSION = 1

    private const val NAME_MAX = 40
    private const val SID_MAX = 64
    private const val HOST_MAX = 40
    private const val MIN_CUP_SIZE = CuppingDraft.MIN_CUP_SIZE   // 100
    private const val MAX_CUP_SIZE = CuppingDraft.MAX_CUP_SIZE   // 400
    private const val MIN_CUPS = CuppingDraft.MIN_CUPS           // 1
    private const val MAX_CUPS = CuppingDraft.MAX_CUPS           // 16

    /** Compressed payload longer than this is refused before decoding. */
    private const val MAX_ENCODED_CHARS = 16_384

    /** Decompression-bomb cap: an inflated payload may never exceed 64 KB. */
    private const val MAX_DECOMPRESSED_BYTES = 64 * 1024

    /**
     * Practical QR guard: links longer than this risk unscannable density.
     * Encode marks such payloads `fitsQr = false` so the share screen can fall
     * back to "Share text" / "Copy link" instead of rendering a dead QR.
     */
    const val QR_SAFE_URL_CHARS = 2_000

    // ------------------------------------------------------------------
    // Results
    // ------------------------------------------------------------------

    /** A validated inbound setup, ready for the confirm preview. */
    data class SharedSetup(
        /** Host's session id; becomes the joiner copy's `sessionRef`. */
        val hostSessionId: String,
        /** Host's display name, when the payload carried one. */
        val hostName: String = "",
        /** Optional session title; never leaks cup identity. */
        val sessionName: String = "",
        val cupSizeMl: Int,
        val cupCount: Int,
        /** True for blind payloads: [coffees] is position-only placeholder data. */
        val blind: Boolean,
        /**
         * The coffees as received. Non-blind: real names/labels/colours.
         * Blind: `cupCount` blank entries (the joiner renders Cup 1…N until
         * the host's reveal lands, phase 4).
         */
        val coffees: List<CoffeeEntry>,
    )

    sealed interface DecodeResult {
        data class Success(val setup: SharedSetup) : DecodeResult
        data class Failure(val error: ShareError) : DecodeResult
    }

    /** Friendly one-line reasons a payload was refused; shown on the preview. */
    enum class ShareError(val message: String) {
        NOT_A_COFCUP_CODE("That code isn't a CofCup share."),
        UNSUPPORTED_VERSION("This code comes from a newer version of CofCup. Update the app and try again."),
        UNKNOWN_KIND("This code isn't a shareable CofCup item."),
        MALFORMED("This code is damaged or incomplete. Ask for a fresh one."),
        INVALID_DATA("This code holds values CofCup doesn't accept."),
        BLIND_IDENTITY("This blind setup carries identity data it shouldn't have."),
        TOO_LARGE("This code is too large to scan reliably. Ask the host to share it as text."),
    }

    /** The encoded forms of a setup, produced by [encodeSetup]. */
    data class ShareEncode(
        /** The full https App Link URL — what the QR renders. */
        val url: String,
        /** The bare payload (the offline fallback code shown under the QR). */
        val code: String,
        /** False when the URL exceeds [QR_SAFE_URL_CHARS]; skip the QR then. */
        val fitsQr: Boolean,
    )

    // ------------------------------------------------------------------
    // Encode
    // ------------------------------------------------------------------

    /**
     * Encodes a saved session as a shareable setup. Intended for planned rows
     * (the host's `sid` must stay stable for join idempotency); a done row
     * encodes the same way — its id is just as unique per event.
     */
    fun encodeSetup(session: SavedSession, hostName: String): ShareEncode {
        val kind = if (session.blind) KIND_BLIND_SETUP else KIND_SETUP
        val json = JSONObject()
            .put("v", VERSION)
            .put("kind", kind)
            .put("sid", session.id.take(SID_MAX))
        if (hostName.isNotBlank()) json.put("host", hostName.take(HOST_MAX))
        if (session.plan.sessionName.isNotBlank()) {
            json.put("name", session.plan.sessionName.take(NAME_MAX))
        }
        json.put("ml", session.plan.cupSizeMl)
        json.put("cups", session.plan.cupCount)
        // Blind payloads carry NO identities: no "co" array at all.
        if (kind == KIND_SETUP) {
            val coffees = JSONArray()
            session.plan.coffees.take(session.plan.cupCount).forEach { coffee ->
                val entry = JSONObject()
                if (coffee.name.isNotBlank()) entry.put("n", coffee.name.take(NAME_MAX))
                if (coffee.label.isNotBlank()) entry.put("l", coffee.label.take(CoffeeEntry.MAX_LABEL_LENGTH))
                entry.put("c", coffee.colorArgb)
                coffees.put(entry)
            }
            json.put("co", coffees)
        }
        val code = compressAndEncode(json.toString())
        val url = "https://$APP_LINK_HOST$APP_LINK_PATH?v=$VERSION&d=$code"
        return ShareEncode(url = url, code = code, fitsQr = url.length <= QR_SAFE_URL_CHARS)
    }

    // ------------------------------------------------------------------
    // Decode (all three entry forms)
    // ------------------------------------------------------------------

    /** Decodes any accepted incoming form; every failure gets a friendly reason. */
    fun decode(input: String): DecodeResult = try {
        DecodeResult.Success(decodePayload(extractPayload(input)))
    } catch (e: ShareException) {
        DecodeResult.Failure(e.error)
    } catch (e: Exception) {
        DecodeResult.Failure(ShareError.MALFORMED)
    }

    private class ShareException(val error: ShareError) : Exception()

    /**
     * Pulls the bare `d=` payload out of the accepted forms:
     * the https App Link, the `cofcup://import?…` URI, or the payload itself.
     * Unknown hosts/paths are rejected before any payload processing.
     */
    private fun extractPayload(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) throw ShareException(ShareError.NOT_A_COFCUP_CODE)

        // The https App Link.
        val scheme = text.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme == "https" || scheme == "http") {
            val host = text.removePrefix("$scheme://").substringBefore('/').substringBefore(':').lowercase()
            val path = text.removePrefix("$scheme://").substringAfter('/', "").substringBefore('?')
            if (host != APP_LINK_HOST || path.trimEnd('/') != APP_LINK_PATH.trimStart('/')) {
                throw ShareException(ShareError.NOT_A_COFCUP_CODE)
            }
            return queryValue(text.substringAfter('?', ""), "d")
                ?: throw ShareException(ShareError.NOT_A_COFCUP_CODE)
        }

        // The custom scheme.
        if (text.startsWith(CUSTOM_SCHEME, ignoreCase = true)) {
            return queryValue(text.substringAfter('?', ""), "d")
                ?: throw ShareException(ShareError.NOT_A_COFCUP_CODE)
        }

        // The bare payload: optionally prefixed "d=", then pure base64url.
        val candidate = if (text.startsWith("d=")) text.removePrefix("d=") else text
        if (candidate.length >= 8 && BASE64URL_ONLY.matches(candidate)) return candidate

        throw ShareException(ShareError.NOT_A_COFCUP_CODE)
    }

    /** `v=1&d=…` — split on '&'/'='; values carry no reserved characters. */
    private fun queryValue(query: String, key: String): String? =
        query.split('&')
            .mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
            }
            .toMap()[key]

    private val BASE64URL_ONLY = Regex("^[A-Za-z0-9_-]+$")

    /** Inflates and validates the JSON envelope into a [SharedSetup]. */
    private fun decodePayload(encoded: String): SharedSetup {
        if (encoded.length > MAX_ENCODED_CHARS) throw ShareException(ShareError.TOO_LARGE)
        val compressed = try {
            Base64.getUrlDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw ShareException(ShareError.MALFORMED)
        }
        val json = JSONObject(inflate(compressed).decodeToString())

        if (json.optInt("v", -1) != VERSION) throw ShareException(ShareError.UNSUPPORTED_VERSION)
        val kind = json.optString("kind")
        if (kind != KIND_SETUP && kind != KIND_BLIND_SETUP) {
            throw ShareException(ShareError.UNKNOWN_KIND)
        }

        val sid = json.optString("sid").trim()
        if (sid.isEmpty() || sid.length > SID_MAX) throw ShareException(ShareError.INVALID_DATA)

        val cupSizeMl = json.optInt("ml", -1)
        if (cupSizeMl !in MIN_CUP_SIZE..MAX_CUP_SIZE) throw ShareException(ShareError.INVALID_DATA)
        val cupCount = json.optInt("cups", -1)
        if (cupCount !in MIN_CUPS..MAX_CUPS) throw ShareException(ShareError.INVALID_DATA)

        val blind = kind == KIND_BLIND_SETUP
        val coffees: List<CoffeeEntry> = if (blind) {
            // Strict privacy: a blind payload must carry no identities at all.
            // Any injected "co" array is rejected, not stripped — an injected
            // name/label/colour must never display.
            val injected = json.optJSONArray("co")
            if (injected != null && injected.length() > 0) {
                throw ShareException(ShareError.BLIND_IDENTITY)
            }
            List(cupCount) { CoffeeEntry() }
        } else {
            decodeCoffees(json.optJSONArray("co"), cupCount)
        }

        return SharedSetup(
            hostSessionId = sid,
            hostName = json.optString("host").trim().take(HOST_MAX),
            sessionName = json.optString("name").trim().take(NAME_MAX),
            cupSizeMl = cupSizeMl,
            cupCount = cupCount,
            blind = blind,
            coffees = coffees,
        )
    }

    /** Per-coffee bounds: array length == cup count, caps on name/label, Int color. */
    private fun decodeCoffees(coffees: JSONArray?, cupCount: Int): List<CoffeeEntry> {
        if (coffees == null || coffees.length() != cupCount) {
            throw ShareException(ShareError.INVALID_DATA)
        }
        return List(cupCount) { index ->
            val entry = coffees.optJSONObject(index) ?: throw ShareException(ShareError.INVALID_DATA)
            val name = entry.optString("n")
            if (name.length > NAME_MAX) throw ShareException(ShareError.INVALID_DATA)
            val label = entry.optString("l")
            if (label.length > CoffeeEntry.MAX_LABEL_LENGTH) {
                throw ShareException(ShareError.INVALID_DATA)
            }
            val color = entry.optLong("c", Long.MIN_VALUE)
            // ARGB colours live in 0..0xFFFFFFFF (the palette uses full-alpha
            // 0xFF… values as *positive* longs — Int would truncate them).
            if (color !in 0L..0xFFFFFFFFL) {
                throw ShareException(ShareError.INVALID_DATA)
            }
            CoffeeEntry(
                name = name.trim(),
                label = label.trim(),
                colorArgb = color,
            )
        }
    }

    // ------------------------------------------------------------------
    // Compression + base64url
    // ------------------------------------------------------------------

    private const val KIND_SETUP = "setup"
    private const val KIND_BLIND_SETUP = "blind_setup"

    private fun compressAndEncode(text: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(text.encodeToByteArray()))

    private fun deflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION)).use { stream ->
            stream.write(data)
        }
        return out.toByteArray()
    }

    /** Inflates with the [MAX_DECOMPRESSED_BYTES] bomb cap; anything larger is refused. */
    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw ShareException(ShareError.MALFORMED)
                    }
                    continue
                }
                out.write(buffer, 0, read)
                if (out.size() > MAX_DECOMPRESSED_BYTES) {
                    throw ShareException(ShareError.TOO_LARGE)
                }
            }
            return out.toByteArray()
        } catch (e: ShareException) {
            throw e
        } catch (e: Exception) {
            throw ShareException(ShareError.MALFORMED)
        } finally {
            inflater.end()
        }
    }
}
