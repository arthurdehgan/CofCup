package app.cupnlog

import app.cupnlog.data.CoffeeEntry
import app.cupnlog.data.CupColors
import app.cupnlog.data.CuppingPlan
import app.cupnlog.data.SavedSession
import app.cupnlog.data.SessionStatus
import app.cupnlog.data.ShareCodec
import app.cupnlog.data.ShareCodec.DecodeResult
import app.cupnlog.data.ShareCodec.ShareError
import app.cupnlog.data.ShareCodec.SharedSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

/**
 * Pure-JVM tests for the QR share codec (phase 3): round-trips, the three
 * accepted entry forms, strict validation, blind-payload privacy and the
 * decompression-bomb cap. Uses the same org.json API as the Android runtime.
 */
class ShareCodecTest {

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private val coffees = listOf(
        CoffeeEntry(name = "Ethiopia", label = "1", colorArgb = 0xFFFF5252L),
        CoffeeEntry(name = "", label = "2", colorArgb = 0xFFFF9800L),
        CoffeeEntry(name = "Kenya AA", label = "", colorArgb = 0xFF43A047L),
    )

    private fun session(
        blind: Boolean = false,
        cupCount: Int = 3,
        sessionName: String = "Sunday cupping",
        coffees: List<CoffeeEntry> = this.coffees,
    ) = SavedSession(
        id = "11111111-2222-3333-4444-555555555555",
        createdAtMs = 1_700_000_000_000,
        plan = CuppingPlan(
            cupSizeMl = 150,
            cupCount = cupCount,
            coffees = coffees.take(cupCount),
            sessionName = sessionName,
            blind = blind,
        ),
        status = SessionStatus.PLANNED,
        blind = blind,
    )

    /** Compresses + base64url-encodes like [ShareCodec], for crafted payloads. */
    private fun payload(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteArrayOutputStream().also { out ->
                DeflaterOutputStream(out).use { it.write(json.encodeToByteArray()) }
            }.toByteArray(),
        )

    private fun decodeError(input: String): ShareError =
        (ShareCodec.decode(input) as DecodeResult.Failure).error

    private fun decodeSetup(input: String): SharedSetup =
        (ShareCodec.decode(input) as DecodeResult.Success).setup

    private fun encoded(session: SavedSession, hostName: String = "Arthur") =
        ShareCodec.encodeSetup(session, hostName)

    // ------------------------------------------------------------------
    // Round-trips
    // ------------------------------------------------------------------

    @Test
    fun fullSetup_roundTrips() {
        val encode = encoded(session())
        val decoded = decodeSetup(encode.url)
        assertEquals("11111111-2222-3333-4444-555555555555", decoded.hostSessionId)
        assertEquals("Arthur", decoded.hostName)
        assertEquals("Sunday cupping", decoded.sessionName)
        assertEquals(150, decoded.cupSizeMl)
        assertEquals(3, decoded.cupCount)
        assertFalse(decoded.blind)
        assertEquals(coffees, decoded.coffees)
    }

    @Test
    fun fullSetup_urlMatchesAppLinkShape() {
        val encode = encoded(session())
        assertTrue(encode.url.startsWith("https://arthurdehgan.github.io/cupnlog/cofcup/i?v=1&d="))
        assertTrue(encode.fitsQr)
        // The bare code shown under the QR is exactly the URL's d= payload.
        assertEquals(encode.url.substringAfter("d="), encode.code)
    }

    @Test
    fun blankNameAndHost_dropped() {
        val decoded = decodeSetup(
            encoded(session(sessionName = ""), hostName = "").url,
        )
        assertEquals("", decoded.sessionName)
        assertEquals("", decoded.hostName)
    }

    @Test
    fun sixteenCups_fitInQrGuard() {
        val encode = encoded(session(cupCount = 16, coffees = CupColors.defaultCoffees(16)))
        assertTrue(encode.fitsQr)
        assertTrue(encode.url.length <= ShareCodec.QR_SAFE_URL_CHARS)
        assertEquals(16, decodeSetup(encode.url).coffees.size)
    }

    // ------------------------------------------------------------------
    // Entry-form equivalence
    // ------------------------------------------------------------------

    @Test
    fun threeEntryForms_decodeIdentically() {
        val encode = encoded(session())
        val expected = decodeSetup(encode.url)
        assertEquals(
            expected,
            decodeSetup("cofcup://import?v=1&d=${encode.code}"),
        )
        assertEquals(expected, decodeSetup(encode.code))
        // A "d=" prefix on the pasted code also works.
        assertEquals(expected, decodeSetup("d=${encode.code}"))
    }

    @Test
    fun foreignHost_rejected() {
        val code = encoded(session()).code
        assertEquals(
            ShareError.NOT_A_COFCUP_CODE,
            decodeError("https://example.com/cupnlog/cofcup/i?v=1&d=$code"),
        )
    }

    @Test
    fun wrongPath_rejected() {
        val code = encoded(session()).code
        assertEquals(
            ShareError.NOT_A_COFCUP_CODE,
            decodeError("https://arthurdehgan.github.io/other/cofcup/i?v=1&d=$code"),
        )
    }

    @Test
    fun nonCofCupText_rejected() {
        assertEquals(
            ShareError.NOT_A_COFCUP_CODE,
            decodeError("hello, this is not a code"),
        )
        assertEquals(
            ShareError.NOT_A_COFCUP_CODE,
            decodeError(""),
        )
    }

    // ------------------------------------------------------------------
    // Version / kind checks
    // ------------------------------------------------------------------

    @Test
    fun newerVersion_rejected() {
        assertEquals(
            ShareError.UNSUPPORTED_VERSION,
            decodeError(payload("""{"v":2,"kind":"setup","sid":"x","ml":150,"cups":2}""")),
        )
        assertEquals(
            ShareError.UNSUPPORTED_VERSION,
            decodeError(payload("""{"kind":"setup","sid":"x","ml":150,"cups":2}""")),
        )
    }

    @Test
    fun unknownKind_rejected() {
        // "ratings" and "group_results" arrive in phase 4; they must not decode now.
        assertEquals(
            ShareError.UNKNOWN_KIND,
            decodeError(payload("""{"v":1,"kind":"ratings","sid":"x","ml":150,"cups":2}""")),
        )
        assertEquals(
            ShareError.UNKNOWN_KIND,
            decodeError(payload("""{"v":1,"kind":"group_results","sid":"x","ml":150,"cups":2}""")),
        )
    }

    // ------------------------------------------------------------------
    // Payload validation
    // ------------------------------------------------------------------

    @Test
    fun missingOrOversizedSid_rejected() {
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(payload("""{"v":1,"kind":"setup","ml":150,"cups":2}""")),
        )
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(payload("""{"v":1,"kind":"setup","sid":"${"x".repeat(65)}","ml":150,"cups":2}""")),
        )
    }

    @Test
    fun outOfRangeCupsAndSize_rejected() {
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(payload("""{"v":1,"kind":"setup","sid":"x","ml":99,"cups":2}""")),
        )
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(payload("""{"v":1,"kind":"setup","sid":"x","ml":401,"cups":2}""")),
        )
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(payload("""{"v":1,"kind":"setup","sid":"x","ml":150,"cups":0}""")),
        )
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(payload("""{"v":1,"kind":"setup","sid":"x","ml":150,"cups":17}""")),
        )
    }

    @Test
    fun coffeeArrayMismatch_rejected() {
        // Two entries for a three-cup setup — positions must cover 1…N.
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(
                payload(
                    """{"v":1,"kind":"setup","sid":"x","ml":150,"cups":3,"co":[""" +
                        """{"n":"A","c":1},{"n":"B","c":2}]}""",
                ),
            ),
        )
    }

    @Test
    fun oversizedNameOrLabel_rejected() {
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(
                payload("""{"v":1,"kind":"setup","sid":"x","ml":150,"cups":1,"co":[{"n":"${"x".repeat(41)}","c":1}]}"""),
            ),
        )
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(
                payload("""{"v":1,"kind":"setup","sid":"x","ml":150,"cups":1,"co":[{"l":"${"x".repeat(7)}","c":1}]}"""),
            ),
        )
    }

    @Test
    fun colorOutOfRange_rejected() {
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(
                payload("""{"v":1,"kind":"setup","sid":"x","ml":150,"cups":1,"co":[{"n":"A","c":4294967296}]}"""),
            ),
        )
    }

    // ------------------------------------------------------------------
    // Blind identities (carried, 2026-11-13 decision)
    // ------------------------------------------------------------------

    @Test
    fun blindRoundTrip_carriesIdentities() {
        val encode = encoded(session(blind = true))
        // The blind payload carries the full identities so the joiner's copy
        // can run the same end-of-session identify-by-label as the host; only
        // the row's blind flag (the kind) differs from a full share.
        val decoded = decodeSetup(encode.url)
        assertTrue(decoded.blind)
        assertEquals(3, decoded.cupCount)
        assertEquals(coffees, decoded.coffees)
    }

    @Test
    fun blindPayload_rejectsOnlyStructuralIssues() {
        // Identity data is no longer special-cased for blind: a blind payload
        // fails on the same structural rules as a full one (e.g. array/count
        // mismatch), never because it carries names or colours.
        assertEquals(
            ShareError.INVALID_DATA,
            decodeError(
                payload(
                    """{"v":1,"kind":"blind_setup","sid":"x","ml":150,"cups":2,"co":[""" +
                        """{"n":"Ethiopia","l":"1","c":-1614531},{"n":"Kenya","l":"2","c":-1},{"n":"X","c":-2}]}""",
                ),
            ),
        )
    }

    // ------------------------------------------------------------------
    // Size guards
    // ------------------------------------------------------------------

    @Test
    fun decompressionBomb_rejected() {
        // A tiny compressed payload that inflates past the 64 KB cap.
        val junk = "{\"v\":1,\"kind\":\"setup\",\"junk\":\"${"a".repeat(70_000)}\"}"
        assertEquals(ShareError.TOO_LARGE, decodeError(payload(junk)))
    }

    @Test
    fun damagedCode_rejected() {
        // Truncated base64 → undecodable.
        assertEquals(
            ShareError.MALFORMED,
            decodeError(encoded(session()).code.dropLast(3)),
        )
    }

    @Test
    fun longHostAndName_truncated() {
        val decoded = decodeSetup(
            encoded(session(sessionName = "n".repeat(60)), hostName = "h".repeat(60)).url,
        )
        assertEquals(40, decoded.sessionName.length)
        assertEquals(40, decoded.hostName.length)
    }
}
