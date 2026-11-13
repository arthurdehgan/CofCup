# CofCup — feature plan (v3, phase 4 onwards; trimmed 2026-11-13)

Planning document. Phases 0–3 are implemented and committed, so this file now
carries **only phase 4 and beyond**. Facts from earlier phases that phase 4
depends on are summarized in §1–§4 (settled decisions, carried-over rules,
data model, phase-3 context). The full v2 plan — the deepseek reconciliation
and the phase 0–3 specs — lives in this file's git history.

**Status (2026-11-13).** Phases 0, 1, 2 and 3 are implemented and committed
(phase 3: share codec `2959368`, share/join screens `d65a6b5`, Pages site
`a93ea9b`, deep-link fixes `63d7430`; StartCheck share action `2ad14b2`
completed the last listed follow-up). Phase 3 open items — **non-blocking for
phase 4 code**:

- enable GitHub Pages in the repo settings (Source: GitHub Actions) so
  `assetlinks.json` goes live and the App Link verifies;
- a real two-device check of the scanner flow and the https-entry form (paste
  + custom-scheme deep link were verified on the emulator, incl. idempotent
  re-join and queue-during-live-session).

## 1. Settled decisions (all standing; numbering matches v2)

1. **Rating model**: SCA-*like*, not full SCA — per cup, six attributes rated
   1–10 (Aroma, Flavor, Acidity, Sweetness, Mouthfeel, Aftertaste) that are
   purely informational, plus a **separate, strictly manual Overall (1–10)**.
   No computed overall from the attributes — on any screen, in any payload,
   ever. Rating is optional; free-text notes per cup.
2. **Saved setups** appear on a **Home row** *and* carry a **"Planned" badge
   in History**. (Background for phase 4: the planned→done lifecycle below
   means the host's session id uniquely identifies an event.)
3. **Lifecycle**: a planned session is **converted into done** when its run
   reaches Done — same row, same `id`, status flips. Consequence phase 4
   relies on: the host's session id is unique per event, so one id carries
   the group semantics — no `groupId` entity.
4. **Multi-participant** scores happen via **QR exchange only** for now (no
   live networking).
5. **No backend, ever; QR is an https App Link.** The QR encodes an
   `https://…` **Android App Link** URL with the payload in its query string.
   App installed → resolves straight into CofCup (full payload in the URL;
   still passes through the confirm preview). App not installed → a **static**
   GitHub Pages landing page (install link + "re-scan or tap again"). No web
   backend, no processing, no link shortener; the plain forms keep working:
   every incoming path still accepts `cofcup://import?…` and the bare `d=`
   code.
6. After the host has merged everyone's ratings, the host **can broadcast
   merged results** as a second QR round so participants get the full
   comparison on their own phones (reuses the identical merge path — included
   in phase 4).
7. **QR scanning happens in-app** (settled 2026-11-09, shipped in phase 3):
   `CAMERA` runtime permission + ZXing Android Embedded. Paste import stays
   as the permission-free and camera-less fallback, never gated behind the
   scanner.
8. **Tasting notes are included in shared results by default** (settled
   2026-11-09): each participant can exclude their notes with a per-person
   toggle (`shareNotes`), and a participant's own device always shows their
   own notes regardless.

## 2. Carried-over rules from the v2 reconciliation (phase-4 relevant)

Adopted from the deepseek review where they verified real behavior:

- **Tolerant decode granularity**: a malformed *rating* skips the rating, not
  the session or the payload; per-record `runCatching` stays as-is.
- **Rater identity by stable ID**: merges and re-submissions are idempotent
  **by `raterId`** (persisted random `my_id` + display name `my_name`) — two
  "Sam"s no longer collide, renames don't orphan ratings.
- **Explicit reveal, never automatic** (deepseek §3.5): a blind session's
  identities are revealed only by a deliberate action — the identify flow for
  solo sessions, host-controlled for groups; persisted via `revealedAtMs`
  exactly once, not reversible. A guest who never receives a reveal keeps an
  anonymous copy with "Ask the host for the reveal" copy rather than invented
  names.
- **Notes are shared on consent** (decision 8).
- **DataStore write discipline**: ratings/notes are kept in the VM during a
  live session and written at Done / explicit save / screen exit — never on
  every keystroke (the whole history string is rewritten per edit).
- **Navigation-origin metadata (`returnTo`)** on every new screen so
  `handleBack()` stays sensible.

Rejected — do not reintroduce:

- Overall on a 1–5 scale, or computed from the attributes (conflicts with
  decision 1).
- A separate saved-setups store / "history means completed only" (conflicts
  with decision 2/3).
- Deepseek's per-participant `RatingSet` nesting — the flat `ratings` list
  keyed by `raterId` is the model (replace-per-rater is one filter operation).
- Deepseek's group `groupId`+`setupId` entities — a single `sessionRef` (host
  session id) carries the same semantics with fewer entities.

**Blind payloads carry the full coffee identities** (amended 2026-11-13): the
joiner's blind copy renders positions only on every pre-reveal surface —
because the `kind` marks the row blind, not because data is withheld — and the
QR is the answer key for anyone who decodes it. Phase 4's `group_results`
reveal mapping is the channel through which guests ever get a reveal.

## 3. Data model (authoritative)

`SavedSession` carries optional fields, stored in the **same** DataStore key
(`cupnlog_store` / `history_v1`), decoded with `opt*` calls so existing
history decodes untouched. No Room, no serialization plugin, no migration
script. **All of the below already exists in code** (`Models.kt`,
`HistoryCodec.kt`).

```json
{
  "id": "uuid",
  "createdAt": 1700000000000,
  "status": "planned" | "done",              // absent = done (old rows)
  "cupSizeMl": 150,
  "cupCount": 4,
  "blind": false,                            // absent = false
  "revealedAt": 0,                           // absent/null = not revealed (blind only)
  "origin": "own" | "joined",                // absent = own; "hostName" set when joined
  "sessionRef": "",                          // host's session id; "" for own sessions
  "coffees": [ { "name": "Ethiopia", "number": 1, "color": -1614531 } ],
  "ratings": [
    { "cup": 0,
      "raterId": "4f8b…",                    // stable participant id
      "rater": "Arthur",                     // display name at export time
      "aroma": 8, "flavor": 8, "acidity": 7, "sweetness": 8, "mouthfeel": 7, "aftertaste": 8,
      "overall": 8,                          // strictly manual, never derived
      "notes": "florals, tea-like" }
  ]
}
```

### Rules that follow from the decisions

- **Overall is manual only.** Never computed from the six attributes, on any
  screen, in any payload, ever. (A display *average across raters* of manual
  overalls is fine — see §5.6.)
- **"Rated" definition**: a cup counts as rated if *any* score (attribute,
  overall, or notes) exists. Nothing gates the Done step on ratings.
- **Rater identity**: two persisted DataStore preferences in the same store —
  `my_id` (random UUID, generated on first use) and `my_name`. Locally stored
  ratings carry `raterId` always; `rater` display name is stamped from
  `my_name` when known. Merge/re-submission idempotency keys on
  `raterId + sessionRef`.
- **ID topology**: the host's session row keeps its own UUID in `id` (with
  `sessionRef = ""`). Every joiner's copy is a *new local row* (own UUID for
  `id`) carrying the host's id in `sessionRef`. One universal match rule
  everywhere: `session.id == ref || session.sessionRef == ref`. Because
  planned→done converts the same row, the host's id is unique per event.
- **Attribute terms are the user's**: Aroma, Flavor, Acidity, Sweetness,
  Mouthfeel, Aftertaste, displayed exactly as listed; payload keys stay the
  short lowercase forms in the JSON above.

## 4. Phase-3 facts phase 4 builds on

- **Link shape** (`data/ShareCodec.kt`): the QR encodes
  `https://arthurdehgan.github.io/cupnlog/cofcup/i?v=1&d=<base64url(deflate(json))>`.
  Three accepted incoming forms decode identically: the https App Link URL,
  `cofcup://import?v=1&d=…`, and the bare `d=` payload. Strict
  version/kind checks; bounds: name 40, sid 64, host 40, cup size 100–400,
  cups 1–16; decompression-bomb cap 64 KB; `QR_SAFE_URL_CHARS = 2_000`
  practical QR guard (over → share text / copy link; phase 4 adds
  "scores only").
- **`kind` today**: `"setup"` and `"blind_setup"` only. `"ratings"` and
  `"group_results"` are **rejected with `UNKNOWN_KIND`** — that rejection is
  the documented phase-4 extension point (ShareCodec header comment says so).
- **One entry point for every incoming form**:
  `CuppingViewModel.handleIncoming` → `IncomingShare` (currently
  `Setup | Error`) → confirm-only `Screen.ImportPreview` — never
  auto-saves/auto-starts/auto-merges. A live session queues imports with a
  prompt dialog instead of replacing the timer.
- **Scanner** (`Screen.Scanner`, ZXing Embedded): decoded text goes through
  the same codec path; non-CofCup codes show a plain hint and keep scanning.
  Reused by "Collect ratings" in phase 4.
- **Join semantics**: Accept saves a `planned`/`joined` row (own UUID in
  `id`, host id in `sessionRef`); re-joining the same host session replaces
  the previous joined planned copy (idempotent on `sessionRef` +
  `status=planned`).
- **Share screen shape** (`ui/ShareSetupScreen.kt`): big QR + "Share text" +
  "Copy link" + the bare code under the QR — the phase-4
  show-merged-results screen reuses this shape.
- **Reveal plumbing**: `SavedSession.reveal(nowMs)` (idempotent; sets
  `revealedAtMs` exactly once); solo reveal happens through the identify flow
  (`IdentifyCups`); joined copies wait for the host's phase-4 broadcast.
- **Replace-per-rater merge already exists in the VM**
  (`persistMyRatingsIfFinished`: `filterNot { it.raterId == myId } + mine`)
  — the QR merge follows the same rule.

## 5. Phase 4 — QR ratings exchange + comparison (M)

Same muscle memory as phase 3, opposite direction: setups went host → guest;
ratings go guest → host, then merged results go host → guest.

### 5.1 Ratings export payload (`kind: "ratings"`)

```
{"v":1,"kind":"ratings","sid":"<host session id>","rater":{"id":"…","name":"…"},"notesShared":true,
"rs":[{"c":0,"ar":8,"f":8,"a":7,"sw":8,"m":7,"at":8,"o":8,"n":"…"}]}
```

- Attributes + manual overall + notes; **only** the sharing device's own set
  (one rater per payload). Wrapped in the same https App Link; the three
  accepted incoming forms decode identically.
- `notesShared` reflects the per-person toggle (default **on**, decision 8):
  the owning device always shows its own notes locally; merged devices see
  notes unless a participant chose to exclude theirs.

### 5.2 `shareNotes` toggle

- Persisted DataStore preference in the same store, **default on** (decision
  8). Toggle sits on the share/collect screen and in Settings ("Include my
  notes when sharing").
- When off, the payload omits the notes (`n`) fields / sets
  `notesShared=false`; the owner's device still shows their own notes locally.

### 5.3 Codec extension (`data/ShareCodec.kt`)

- New `encodeRatings(...)` producing the payload above; accept
  `kind: "ratings"` in decode (today: `UNKNOWN_KIND`).
- **Merge validation**: match `session.id == sid || session.sessionRef ==
  sid`; reject other-event payloads ("Ratings from a different cupping.");
  reject unknown versions; keep positions inside 1…`cupCount`; merge
  **idempotently by `raterId`** (a re-scan replaces that rater's previous
  set; duplicate confirmation on re-submission). Merges only happen through
  the confirmed preview — never auto-merged.
- Tolerant granularity: a malformed rating entry skips the entry, not the
  payload. Rater id/name/notes caps per the existing bound discipline; the
  bomb cap and the ~2 KB QR guard carry over.
- Injected identity fields in a ratings payload are rejected/ignored —
  identities may only arrive via the `group_results` reveal mapping (§5.5).
- **Oversize fallback**: "scores only" (drop notes) when the merged payload
  exceeds the QR guard, then share-as-text / copy-link as in phase 3.

### 5.4 Collect ratings (host side)

- From a **DONE** session, the host scans each participant's ratings QR —
  the in-app scanner from phase 3, funneling through `handleIncoming` →
  `ImportPreview` — never auto-merged.
- The preview shows exactly what's incoming: which rater, N cups rated,
  notes included or not; confirm applies the merge (replace-per-`raterId`).
- Re-scanning the same rater replaces their previous set (idempotent), with
  an explicit duplicate/replace confirmation on re-submission.

### 5.5 Broadcast round (decision 6 — with reveal folded in)

- After the host is done collecting, `SessionDetailScreen` offers
  **"Show merged results"** — a `group_results`-style QR with all raters'
  entries (including the host's own). Exact key layout at implementation,
  mirroring the ratings entry shape per rater; no new payload kind beyond
  `group_results`.
- If the host opted to include identities for the *newer* blind session, the
  payload includes the reveal mapping (position → name/color/custom number)
  and `revealed = true`; each guest's import then backfills the mapping into
  their copy **and sets `revealedAtMs`** — the same effect as a dedicated
  reveal payload, without a new kind.
- Before a reveal is included, guests' comparison views use Cup N labels; a
  guest who never receives a reveal keeps an anonymous copy with "Ask the
  host for the reveal." (Rather than inventing names.)
- After reveal, group results are read-only for guests in v1 (deepseek); solo
  ratings remain editable.

### 5.6 Comparison view (build rater-aware from day one)

- `SessionDetailScreen` coffee cards gain: a row of **Overall boxes — one per
  rater** (rater name attached), a bold average in front (means of non-null,
  rated-only entries with the house `HALF_UP` discipline), then the viewer's
  own six attribute chips and notes underneath. Unrated cups render an
  explicit quiet "No rating" slot so the row shape stays stable across
  states.
- The average is a dedicated pure display-average helper in the data layer —
  **not** in `CuppingMath`, **not** inline in UI (guardrails §6) — unit-tested
  with representable values (`BigDecimal(Double)` binary caveats, see the
  notes in `CuppingMathTest`).
- Blind sessions render all identity (names/colors) only post-reveal; guests'
  anonymous copies render Cup N throughout until a reveal lands.
- This layout is built rater-aware from day one so a later LAN/Nearby option
  would slot in without redesign.

### 5.7 Files touched (phase 4)

- Edited: `data/ShareCodec.kt` (ratings + group_results encode/decode),
  `ui/CuppingViewModel.kt` (`IncomingShare` gains a Ratings variant; merge /
  reveal-backfill handlers; screen enum + `handleBack` cases;
  `shareNotes` pref exposure), `ui/SessionDetailScreen.kt` (comparison view
  + Collect + Show-merged-results entry points), `ui/SettingsScreen.kt`
  (notes toggle in the "You" card), `data/CuppingRepository.kt`
  (`share_notes` preference, default on).
- New: `ui/ReceiveResultsScreen.kt` (ratings import preview variant of
  `ImportPreview`), the show-merged-results screen reusing the
  `ShareSetupScreen` shape (+ blind-reveal absorption), and the
  display-average helper in the data layer.
- **No manifest or MainActivity changes** — the deep-link entry point,
  intent filters, and queueing are already phase-3-complete.

## 6. Invariant guardrails — do not break

- `CuppingMath` untouched; no rating math inside it and none recomputed in
  UI (the display average lives in its own pure helper).
- No "Coffee total / total to grind" line on setup/start-check (the share
  screens inherit the same rule); the only total is on SessionDetail, as
  today.
- Chimes fire only when the timer itself advances; skipped steps stay silent
  (shell owns this — phase 4 must not touch it).
- Sessions save onDone only — planned row converted in place when one
  exists; save-once semantics owned by the VM.
- 200-session cap, `org.json` only, single `CuppingViewModel` + sealed
  `Screen`, no navigation-compose, portrait lock, `keepScreenOn` on
  live-related screens via the shell.
- Nothing from a link is ever auto-applied: every merge/reveal goes through
  a confirmed preview.
- Blind surfaces render cup identity only through `data/Blind.kt` helpers
  (`blindedByPosition()`, `BlindCupDisplay`) — no names/colours/labels in
  copy, content descriptions, or previews pre-reveal.

## 7. Unit tests (pure JUnit, `app/src/test`; phase 4 scope)

- Codec ratings/group_results round-trip; version/kind rejection; blind
  identity fields injected into a ratings payload rejected/ignored.
- Entry-form equivalence: https App-Link URL, `cofcup://import?d=…`, and bare
  `d=` payload all decode to the same result after validation.
- Payload validation: bounded sizes, position range vs `cupCount`, rater
  id/name/notes caps, decompression-bomb cap; oversized payload → scores
  only / text fallback.
- Merge idempotency (re-scan replaces per `raterId`); other-event payload
  rejection; replace-per-rater with `shareNotes=false` vs `true` surfaces.
- Display-average helper rounding (`HALF_UP` precedent; representable
  values — see the notes in `CuppingMathTest` about `BigDecimal(Double)`
  binaries).
- Reveal transitions: guest reveal-backfill on import sets `revealedAtMs`
  exactly once; "ask the host" copy state before; revealed stays
  irreversible.
- Existing suites (`ShareCodecTest`, `HistoryCodecTest`, `CupRatingTest`,
  `BlindRedactionTest`, …) must keep passing; extend `ShareCodecTest` for the
  new kinds.

## 8. Testing notes

- Paste-code import is the reliable emulator/emulator automation path (system
  clipboard is not shared between emulators; scanner flows need real devices).
  Two-phone manual testing is the realistic scenario for collect + broadcast.
- The blind-reveal absorption in group results needs the same
  visual/accessibility care as the phase-2 blind audit: every surface,
  content descriptions, TalkBack — no identity leaks pre-reveal.
- Queue-during-live-session behavior must hold for ratings payloads too
  (already covered by the shared `handleIncoming` path — verify, don't
  rebuild).

## 9. Explicitly deferred (phase 5+; revisit only if QR exchange feels too manual)

- Live sync: `NSDManager` + `ServerSocket` over the **host's phone hotspot**
  (no client isolation, works outdoors), JSON-lines protocol, foreground-only
  while the session screen is up (no Service), `INTERNET` permission added
  then.
- Nearby Connections API option (GMS dependency) — heavy for this app's
  spirit.
- Classic Bluetooth RFCOMM (pairing friction) and custom BLE (painful) —
  avoid.
- Scanner-library experiment (CameraX + ML Kit swap) only if ZXing
  Embedded's surface/compat proves insufficient; not a plan fork.
- Planned-start countdown, "most agreed / most divisive cup" (variance
  views), exported summary files — deepseek phase-6 polish ideas, revisit
  after v1 ship.
- Post-reveal re-hide / re-blind reuse — out of v1 (a duplicate setup is the
  workaround, recorded for later).

## 10. Where to start, when the go is given

Phase 4 is the only remaining phase; order within it:

1. `ShareCodec` ratings encode/decode + validation (+ tests).
2. `shareNotes` preference + Settings/share-screen toggle.
3. Collect: receive-results preview + replace-per-rater merge.
4. Broadcast: group_results encode + guest reveal-backfill.
5. Comparison view + display-average helper (+ tests).
6. Two-device manual pass (once GitHub Pages is enabled and the phase-3
   two-device check is done — see status above).

Each step leaves the app fully working; step 1–2 alone are shippable.
