# CofCup — new features plan (v2, reconciled 2026-11-09)

Planning document. This is the reference for the ongoing implementation; it is
deliberately self-contained so it makes sense without the brainstorming
conversation behind it.

**Status (2026-11-11).** Phases 0, 1 and 2 are implemented and committed
(`7efd0ec`, `e63da8e`, `9d812da`). Phases 3–4 remain.

**v2 note.** This is the GLM plan after reconciliation with
`feature_plan_deepseek.md`. Deepseek's plan was reviewed against the actual code;
where it verified real bugs in the current app, its fixes are adopted (marked below).
Where it conflicts with locked user decisions, it is rejected. The deltas are listed
in §1; the rest of the document is the single authoritative spec.

## Decisions log (all settled)

1. **Rating model**: SCA-*like*, not full SCA — per cup, six attributes rated 1–10
   (Aroma, Flavor, Acidity, Sweetness, Mouthfeel, Aftertaste) that are purely
   informational, plus a **separate, strictly manual Overall (1–10)**. No computed
   overall. Rating is optional; free-text notes per cup.
2. **Saved setups** (set up without starting) appear on a **Home row** *and* carry a
   **"Planned" badge in History**.
3. **Lifecycle**: a planned session is **converted into done** when its run reaches
   Done — same row, same `id`, status flips.
4. **Multi-participant** scores happen via **QR exchange only** for now (no live
   networking).
5. **No backend, ever; QR is an https App Link** (revised 2026-11-09, user
   request; was "no online hosting at all, QR inside a `cofcup://` URI"). The QR
   encodes an `https://…` **Android App Link** URL with the payload in its query
   string. App installed → the external camera / Google Lens flow resolves it
   straight into CofCup (full payload in the URL; still passes through the
   `ImportPreview` confirm). App not installed → the link opens a **static**
   landing page hosted on GitHub Pages (install link + "the cupping setup is
   inside this link — install, then re-scan or tap the link again"). No web
   backend, no processing, no link shortener; plain schemes keep working: every
   incoming path (scanner, paste) still accepts the `cofcup://import?…` form.
6. After the host has merged everyone's ratings, the host **can broadcast merged
   results** as a second QR round so participants get the full comparison on their
   own phones (reuses the identical merge path — included).
7. **QR scanning happens in-app** (settled 2026-11-09): guests open CofCup and
   scan the host's code from inside the app; the `CAMERA` runtime permission and
   a scanner dependency are accepted for this. Paste import stays as the
   permission-free and camera-less fallback.
8. **Tasting notes are included in shared results by default** (settled
   2026-11-09): each participant can still exclude their notes with a
   per-person toggle (`shareNotes`), and a participant's own device always
   shows their own notes regardless.

## 1. Reconciliation with `feature_plan_deepseek.md`

### 1.1 Verified code catches — ADOPTED

1. **Session-shell ownership bug (deepseek §11.1) — verified in code.**
   Today `SessionScreen` is composed only while `screen == LiveSession`
   (`CuppingApp.kt:23`) and it owns three things that must survive a Ratings
   screen: `keepScreenOn` (`SessionScreen.kt:83–86`), the `SoundPool` +
   `vm.stepChimes.collect` playback (`:91–114`), and the `LaunchedEffect` that
   calls `vm.onCuppingComplete()` when the Done step is current (`:123–125`).
   The ViewModel only *emits* chimes (`replay = 0`, buffered emissions with no
   active collector are dropped), so a screen switch skips chimes, lets the screen
   sleep, and delays the Done save. **The gap already exists today on the Settings
   screen**, never mind Ratings. An earlier GLM claim that chimes were
   "VM-driven and screen-independent" was wrong and is corrected here.
   → Adopted as a phase-0 refactor: hoist chime playback, vibration,
   `keepScreenOn`, and Done-save into an app/session shell (or the VM ticker);
   make Done-save idempotent from the VM. LiveSession, Ratings, Settings, and the
   QR share screen all inherit the behavior.
2. **12 vs 16 palette mismatch (deepseek §1.3) — verified in code.**
   `CupColors.palette` has 12 entries while `MAX_CUPS = 16` (`Models.kt:25,65`); a
   13–16-cup draft ends up with `coffees.size == 12` but `cupCount == 16`, so any
   per-cup rating / card rendering runs off the end of the list.
   → Adopted in phase 0: extend the palette to 16, keep `MAX_CUPS = 16`, add a
   regression test. (If a user-visible 16-cup bug existentially pre-dates this
   plan, this is the first thing to fix.)
3. **Tolerant decode granularity.** A malformed *rating* must skip the rating, not
   the session; malformed *group* data drops group data, not the session;
   per-record `runCatching` stays as-is.
4. **Untrusted-payload hardening.** Single `cofcup://import?d=` entry point with a
   `kind` discriminator (simpler than separate `join`/`results` paths — adopted
   over GLM's original scheme); version/kind rejection with friendly copy;
   clamped cup size/count; position uniqueness/range checks; name/notes caps;
   bounded array lengths before allocation; bounded target sizes; compression
   (deflate) + base64url with a **~2 KB practical QR guard** (offer "share as
   text"/"scores only" instead of an unscannable QR); decompression-bomb cap
   (64 KB); never auto-save/auto-start/auto-reveal/auto-merge from a link — always
   a confirm preview.
5. **Deep-link while a live session runs:** queue the import and prompt
   ("Open a CofCup link after this session?") instead of replacing the live screen.
6. **Rater identity by stable ID.** A persisted random `my_id` (UUID) + display
   name; merges and re-submissions are idempotent **by `raterId`**, not by name
   strings (two "Sam"s no longer collide, renames don't orphan ratings).
7. **Explicit reveal, never automatic** (deepseek §3.5). A blind session's
   identities are revealed only by a deliberate action — host-controlled for
   groups, explicit button for solo. Persisted via `revealedAtMs` once done; not
   reversible. A guest who never receives a reveal keeps an anonymous copy with
   "Ask the host for the reveal" copy rather than invented names.
8. **Blind = position-based identity.** Visible identity in a blind session is
   **Cup 1…N by left-to-right position**; custom numbers, colors, and names are
   private metadata. Blind payloads contain **no identities at all** (stronger
   than GLM's original "strip names, keep numbers/colors" — adopted, simpler and
   strictly more private). Blind display must also stay out of content
   descriptions/accessibility and copy like the Done step instruction (handled as
   a blind-aware variant without moving step *windows* out of `CuppingTimeline`).
9. **Blind leak checklist + "history rows hide swatches for blind sessions"**
   (deepseek §3.3/§3.8) — a screen-by-screen audit gate before shipping blind
   mode; neutral "Blind cupping · N cups" labels everywhere identity would leak.
10. **Notes are shared on consent.** Own notes are always visible on the owner's
    device; inbound export/merge includes notes only with per-participant opt-in
    (`shareNotes`). Settled at review: default **on**, with a per-person opt-out
    (user decision 8).
11. **DataStore write discipline:** ratings/notes are kept in the VM during a
    live session and written at Done / explicit save / screen exit — never on
    every keystroke (the whole history string is rewritten per edit).
12. **Navigation-origin metadata (`returnTo`)** for the new screens so
    `handleBack()` stays sensible (Import preview, Sharing, Rating know where they
    came from).
13. **Acceptance-style edge-case table & QA list** (deepseek §8/§12/§13) folded in
    at the end of this document (§9).

### 1.2 Rejected — conflict with locked user decisions

1. **Overall 1–5 scale (deepseek throughout).** User decision #1 sets SCA-like
   **1–10** six attributes + strictly manual Overall. Deepseek's "no recompute of
   overall" is already part of decision #1 and is kept.
2. **Saved set-ups as a separate store/screen (`setups_v1`, "history means
   completed only").** User decision #2 locked planned rows into the same history
   list with a Home row and a History badge. Kept as one pipeline (`status`
   field, single `history_v1`), with deepseek's niceties (neutral blind labels,
   optional title) folded into the Home/History rendering.
3. **In-app QR scanner as the default path** (ZXing Embedded, adds `CAMERA`
   permission). Originally rejected here to preserve the `VIBRATE`-only
   permission footprint. **Settled the other way at review (2026-11-09, user
   decision 7):** the scanner is in v1, and paste import remains as the fallback
   for permission denial or missing cameras.
4. **Group `groupId`+`setupId` model.** With planned→done conversion (user
   decision #3), the host's session id is unique per event by construction, so a
   single `sessionRef` (host session id) carries the same semantics with fewer
   entities. Kept GLM's model.

### 1.3 Kept differences worth remembering later

- Deepseek's per-participant `RatingSet` nesting is replaced by flat `ratings`
  entries keyed by `raterId` (simpler decode/aggregation; replace-per-rater is one
  filter operation).
- Deepseek's "most agreed / most divisive cup" (variance) and ranking views stay
  optional polish (§9, phase 6), not v1.
- Deepseek's saved-setup `title` and `updatedAt` fields are adopted informally:
  planned rows display title-when-present, "Cupping · N cups" otherwise.

---

## 2. Final data model

`SavedSession` gains optional fields, stored in the **same** DataStore key
(`cofcup_store` / `history_v1`), decoded with `opt*` calls so existing history
decodes untouched. No Room, no serialization plugin, no migration script.

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
  screen, in any payload, ever (deepseek §4.2 agrees with decision #1).
- **"Rated" definition**: a cup counts as rated if *any* score (attribute,
  overall, or notes) exists. Nothing gates the Done step on ratings.
- **Rater identity**: two persisted DataStore preferences in the same store —
  `my_id` (random UUID, generated on first use) and `my_name` (asked lazily, e.g.
  at join confirm or first results export; editable in Settings). Locally stored
  ratings carry `raterId` always; `rater` display name is stamped from `my_name`
  when known. Merge/re-submission idempotency keys on `raterId + sessionRef`.
- **ID topology**: the host's session row keeps its own UUID in `id` (with
  `sessionRef = ""`). Every joiner's copy is a *new local row* (own UUID for `id`)
  carrying the host's id in `sessionRef`. One universal match rule everywhere:
  `session.id == ref || session.sessionRef == ref`. Because planned→done converts
  the same row, the host's id is unique per event — the equivalent of deepseek's
  "new groupId per event" without extra entities.
- **Attribute terms are the user's**: Aroma, Flavor, Acidity, Sweetness,
  Mouthfeel, Aftertaste (not deepseek's flavour/body naming), displayed exactly
  as listed; payload keys stay the short lowercase forms in the JSON above.

---

## 3. Phase 0 — foundations (mostly deepseek-finds, small)

1. **Session-shell refactor (deepseek §11.1) — verified in code.**
   A durable session shell keyed on "a live session exists": keeps the screen on,
   plays chimes/vibration from the VM's `stepChimes` flow, detects Done in the VM
   ticker (`refreshElapsed`) and saves exactly once — independent of which screen
   (LiveSession / Ratings / Settings / share / import-queued dialog) is visible.
   Removes the equivalent logic from `SessionScreen`. Existing semantics preserved:
   timer-driven advances chime, skipped steps stay silent, `extraOffsetMs` model
   untouched.
2. **Palette fix**: extend `CupColors.palette` to 16 entries; keep
   `MAX_CUPS = 16`; extend `defaultCoffees` accordingly; regression test for a
   16-cup plan having 16 coffee entries/colors.
3. **Tolerant decode granularity.** A malformed *rating* must skip the rating, not
   the whole session; malformed *group* data drops group data, not the session;
   per-record `runCatching` stays as-is.
4. **Profile preferences**: `my_id` (random UUID) and `my_name` (string, same
   DataStore store as sound/vibrate keys).
5. **Blind redaction helpers** (pure functions): the only object through which
   blind surfaces render is "Cup N by position"; a unit-tested leak audit for
   view-models/content descriptions.
6. **Navigation-origin metadata (`returnTo`)** for the new screens so
   `handleBack()` stays sensible (Import preview, Sharing, Rating know where they
   came from).
7. Unit tests: legacy decode tolerance; optional-field round-trip; palette 16;
   Done-save from a non-live screen; blind redaction.

## 4. Phase 1 — save setups without starting (S)

**What ships**
- `saveSetup()` on `CuppingViewModel`: serializes the current `CuppingDraft` into
  `SavedSession(status = planned)` and saves it.
- An explicit **"Save setup"** action on Setup and/or StartCheck. Back is *not*
  overloaded — the existing "back from unstarted discards" contract stays.
- **Home**: a "Saved setups" horizontal row/chip area under the existing actions.
  Tap chip → load draft into `_draft` → jump straight to `StartCheck` (with its
  existing "one big Start button" contract). Chip overflow: edit (→ Setup),
  delete, share (button becomes functional in phase 3). Adopted from deepseek:
  a blind planned chip shows only "Blind setup · N cups" with a lock icon — no
  names/numbers/colors that leak prior to a reveal the row may never get.
- **History**: "Planned" badge on rows; blind rows hide color dots and names
  (deepseek §7.11); planned rows open the existing detail read-only view plus a
  "Run this setup" entry point.

**Conversion rule (decision 3)**
- When a run started from a planned session reaches Done, the session is saved by
  **overwriting the same row** (`id` and `createdAt` preserved, `status = done`,
  ratings from that run merged in). No second artifact. (Deepseek views history
  as "completed only" — that conflicts with user decision #2 and is rejected; the
  Home row + badge rendering follows user decision #2.)
- "End & discard" on a run started from a planned session leaves the planned copy
  intact (resumable later). Only the ratings captured so far are lost with the
  run; if ratings existed, the leave dialog copy says
  "Your ratings for this unfinished session will be lost."

**Edge cases**
- Draft with all-default coffees (blank names) saves fine; `displayName` fallbacks
  already handle it.
- Quick-start "Empty Cupping Session" (`cupCount = 0`) is untouched: no setup row
  behind it, Home row, or rating entry.
- Planned and joined rows count toward the existing 200-session cap (each is a
  few hundred bytes — a non-issue; deepseek's
  "debounce notes writes" discipline in §2 already avoids rewrite churn).

## 5. Phase 2 — in-session ratings + blind tasting (M)

**Screens / navigation**
- New `Screen.Ratings` (with `returnTo` metadata). Entry: a small **"Rate"** chip
  on the live screen (hidden entirely when `cupCount == 0`). Back always returns
  to `LiveSession`, even pre-start — mirror the Settings precedent.
- The phase-0 shell refactor is what makes this screen safe: chimes, vibration,
  `keepScreenOn`, and the Done-save keep working while Ratings is visible. Manual
  checks: "Done is reached while on Rating", "…while on Settings", "skip step",
  "process death" (deepseek §11.1 test list).
- Rating UX: list of cup rows in timeline order; tapping a cup opens its editor —
  six 1–10 sliders (Aroma, Flavor, Acidity, Sweetness, Mouthfeel, Aftertaste) —
  informational only — plus a **manual Overall (1–10)** and a notes field (cap
  500 chars) for aroma/other impressions. Per-cup edits live in the VM
  immediately, persisted at Done (no keystroke writes). A cup counts as "rated"
  if *any* score/notes exists; rating stays fully optional, never gating Done.
- Reproduces deepseek §7.10 "header shows compact timer" so the person rating can
  see elapsed/current step without leaving the screen.

**Blind mode**
- New toggle on the Setup screen, default off; stored in the session (`blind`).
- **Blind = position-based identity (deepseek §3):** visible identity is
  **Cup 1…N by left-to-right position**; names, colors and custom numbers are
  private metadata that appears only after an explicit reveal. No countdown/flyby
  of hidden text; no color-coded backgrounds in blind mode.
- **Explicit reveal (deepseek §3.5)**, not automatic: the Finished screen (and
  the session detail if unreviewed) offers **"Reveal identities"** with a
  confirmation; persists via `revealedAtMs`; not reversible. Solo users see their
  own mapping after revealing; group guests keep an anonymous copy until the host
  shares the reveal (phase 4).
- A blind leak audit (deepseek §3.8) gates the phase: no name/color/custom number
  outside Setup/Label-of-own-device, no leaks in content descriptions or copy;
  the Done-step instruction uses a blind-aware variant without moving step
  *window* data out of `CuppingTimeline`.

## 6. Phase 3 — share / join via QR (M)

**No online hosting. The QR is the whole payload.**

- QR content: the QR encodes `https://<pages-host>/cofcup/i?v=1&d=<base64url
  payload>` — an Android App Link (user decision 5, revised 2026-11-09), so an
  external scanner resolves into the app or the static landing page. The URL
  carries the same `d=` payload with a `kind` discriminator. Adopted from
  deepseek — one parser, one validation path; `kind` values for this phase:
  `setup` (full, non-blind) and `blind_setup`; `ratings`/`group_results` arrive
  in phase 4. **Accepted incoming forms** (all equivalent once validated): the
  https App Link URL above, `cofcup://import?v=1&d=…`, or a bare `d=` payload.
  With compact keys, even 16-coffee setups stay inside scannable density (name
  cap 40 chars, cups max 16); the https wrapper adds ~20 characters and changes
  nothing about the ~2 KB guard.
- New `data/ShareCodec.kt`: envelope encode/decode, deflate + base64url without
  padding, strict version/kind check, validation per deepseek §6.6 (bound checks,
  position uniqueness/range, name/notes caps, decompression-bomb cap 64 KB,
  **reject/ignore identity fields injected into blind payloads**), ~2 KB
  practical QR guard (fallback: "share text"/"copy link"; "scores only" for
  oversize results in phase 4).
- Runtime dependency: **`com.google.zxing:core` only** — pure Java, no camera
  dependencies; render the BitMatrix to Canvas. Keep screen on while the QR shows
  (shell handles it), white card with quiet zone (deepseek §6.7), summary line
  above the QR: "Full set-up · 4 cups" / "Blind set-up · 4 cups (identities
  hidden)".
- `Screen.ShareSetup`: big QR + "Share text" (`ACTION_SEND`) + "Copy link" +
  Done. Reached from Home's saved-setup chips and from Setup/StartCheck.
- **Universal offline fallback (already GLM's)**: the raw code (payload without
  the URI wrapper) shown under the QR, and a **"Paste code" → `Screen.ImportPreview`**
  field on the saved-setups surface. Chat apps won't linkify custom schemes; the
  receiver copies the code into the import field.
- `Screen.ImportPreview` (deepseek §7.8 style): shows exactly what's incoming
  ("5 cups — Cup 1…Cup 5", host name if known) with a confirm; **never**
  auto-saves/auto-starts/auto-reveals. Blind previews display no identity the
  payload doesn't contain, and any injected identity field is rejected/ignored.
- **Deep links during a live session (deepseek §6.9):** queue + prompt dialog —
  never replace the live screen.
- **In-app scanner (settled, user decision 7):** the join path is "Scan a code"
  inside CofCup. Recommended library v1: **ZXing Android Embedded** (deepseek
  §6.8): its own scanner surface, handles the runtime permission, works offline;
  trade-offs (extra activity/theme, possible AppCompat, older-looking UI)
  accepted; verify target-SDK 37/build + release minification. If the
  compat/UI result disappoints, swap to CameraX + ML Kit (bundled model,
  Compose preview) — implementation detail, not a plan fork.
  - `CAMERA` runtime permission declared in the manifest with
    `uses-feature android:name="android.hardware.camera" android:required="false"`.
  - Permission denied / no camera → "Paste code" import stays fully functional
    (never gated behind the scanner).
  - Decoded text validated by the same codec as deep links: accept the https
    App Link URL, a full `cofcup://import?...` URI, or a bare `d=` payload;
    non-CofCup codes are ignored with a plain hint. Everything then funnels
    through the same `ImportPreview` confirm (never auto-imported).
  - Reachable from Home/saved-setups surfaces for joining, and reused by the
    host's "Collect ratings" in phase 4.
  - Same queueing rule as deep links: a scan offered while a live session is
    running queues + prompts, never interrupts the timer.
- **Join semantics (GLM history-tailoring)**: Accept saves a `planned`/`joined`
  row for the joiner (its own UUID in `id`), plus `sessionRef = host id`.
  Blind payloads contain **no identities at all** (strict deepseek version): names,
  numbers and colors stay host-side; the joiner's blind copy shows positions
  only. Re-scanning the same host session id **replaces** the previous joined
  planned copy (idempotent, keyed on `sessionRef` + `status=planned`).
- Manifest: one `VIEW` intent filter for `cofcup` (single `import` path) **and**
  a second, `autoVerify` `VIEW` intent filter for the https App Link
  (`https://<pages-host>/cofcup/i`), both routed through `MainActivity`
  (`onCreate`/`onNewIntent`) into the ViewModel without parsing in the Activity.
  Cold start shows Home then the preview; warm start works; portrait lock and
  other manifest intent stays as-is.
- **App Link wiring** (new, 2026-11-09):
  - `assetlinks.json` served from `https://<pages-host>/.well-known/` carrying
    the signing key's SHA-256 fingerprint (`gradle :app:signingReport` prints
    it; debug and release differ — publish both while the app ships as debug
    APK). Re-generate whenever the signing key changes.
  - A **static** GitHub Pages landing page (separate small Pages repo or this
    repo's Pages): "Get CofCup" + install link, plus "this link carries a
    cupping setup — install the app, then re-scan the code or tap the link
    again". Pure static files; nothing processes the query string server-side.
  - **Dependency**: the redirect only exists if CofCup is publicly installable
    (Play listing or a download link on the page). Until then the landing page
    is a shell; the App-Link-opens-app side works regardless of distribution
    channel as long as the fingerprint matches.

## 7. Phase 4 — QR ratings exchange + comparison (M)

Same muscle memory as phase 3, opposite direction.

**Export payload**: `cofcup://import?v=1&d=…` with
`{"v":1,"kind":"ratings","sid":"<host session id>","rater":{"id":"…","name":"…"},"notesShared":true,
"rs":[{"c":0,"ar":8,"f":8,"a":7,"sw":8,"m":7,"at":8,"o":8,"n":"…"}]}`
— attributes + manual overall + notes; **only** the sharing device's own set.
Deepseek's `shareNotes` toggle is adopted with the settled default **on**
(user decision 8): the owning device always shows its own notes locally; merged
devices see notes unless a participant chose to exclude theirs. The toggle sits
on the share/collect screen and in Settings ("Include my notes when sharing").
**Merge validation (deepseek §6.6/§9.5)**: match `session.id == sid ||
session.sessionRef == sid`; reject other-event payloads ("Ratings from a
different cupping."); reject unknown versions; keep positions inside
1…`cupCount`; merge **idempotently by `raterId`** (a re-scan replaces that
rater's previous set; duplicate confirmation on re-submission). Merges only
happen through the confirmed preview — never auto-merged.

**Broadcast round (decision 6 — with reveal folded in)**
- After the host is done collecting, `session detail` offers
  **"Show merged results"** — a `group_results`-style QR with all raters'
  entries (including the host's own). If the host opted to include identities for
  the *newer* blind session, the payload includes the reveal mapping (position →
  name/color/custom number) and `revealed = true`; each guest's import then
  backfills the mapping into their copy **and sets `revealedAtMs`** — the same
  effect as deepseek's dedicated `reveal` payload, without a new kind.
- Before a reveal is included, guests' comparison views use Cup N labels; a guest
  who never receives a reveal keeps an anonymous copy with "Ask the host for the
  reveal." (Rather than inventing names.)
- After reveal, group results are read-only for guests in v1 (deepseek); solo
  ratings remain editable.

**Comparison view (build rater-aware from day one)**
- `SessionDetailScreen` coffee cards gain: a row of **Overall boxes — one per
  rater** (rater name attached), a bold average in front (means of non-null,
  rated-only entries with the house `HALF_UP` discipline), then the viewer's own
  six attribute chips and notes underneath. Unrated cups render an explicit quiet
  "No rating" slot so the row shape stays stable across states.
- Blind sessions render all identity (names/colors) only post-reveal; guests'
  anonymous copies render Cup N throughout until a reveal lands.
- This layout is built rater-aware from day one so a later LAN/Nearby option
  would slot in without redesign.

## 8. Decisions settled at review (2026-11-09)

| # | Decision | GLM lean | Deepseek lean | Settled |
|---|---|---|---|---|
| 1 | **In-app scanner** | system camera + paste only | in-app scanner (ZXing Embedded + CAMERA) | **In-app scanner, v1** (user decision 7); paste stays as fallback |
| 2 | **Notes sharing default** | default on | opt-in, default off | **Default on, per-person opt-out** (user decision 8) |
| 3 | **Palette fix** | extend palette to 16 | extend to 16 (same) | **extend to 16** (keep `MAX_CUPS = 16`) — both plans agree |
| 4 | **Blind label on planned rows** | "Blind setup · N cups" | hide title; neutral label (same) | **neutral label + lock icon**; no leak before reveal (both plans agree) |
| 5 | **Post-reveal re-hide / re-blind reuse** | out of scope | explicit duplicate needed | **out of v1**; deepseek's duplicate-advice recorded for later |
| 6 | **QR entry point** | `cofcup://` scheme only | — | **https App-Link QR + static GitHub Pages landing** (user request, 2026-11-09; revises old decision 5); `cofcup://` / bare payload still accepted on every incoming path |

## 9. Cross-cutting summary, tests, and deferred work

### Cross-cutting

- New files: `data/ShareCodec.kt`, `ui/RatingsScreen.kt`, `ui/ShareSetupScreen.kt`,
  `ui/ImportPreviewScreen.kt` (GLM `JoinIncoming` merge), edited
  `ui/SessionScreen.kt` (shell hoisting), `ui/ReceiveResultsScreen.kt` and the
  broadcast show action reusing Share screens (+ blind-reveal absorption).
  Phase 3 additionally ships the static landing page + `.well-known/assetlinks.json`
  for GitHub Pages (fields: signing SHA-256; page needs no build tooling).
- Changed: `data/Models.kt`, `data/CuppingRepository.kt`, `ui/CuppingViewModel.kt`
  (screen enum + `returnTo`, `my_id`/`my_name`, draft save/load/convert,
  join/ratings/merge/reveal handlers, `handleBack` cases), `MainActivity.kt`
  (single `import` deep-link routing **plus the https App-Link `autoVerify`
  filter — both extract `d=` and hand the string to the same codec path**,
  queueing during a live session),
  `AndroidManifest.xml` (one intent filter for `cofcup` **and** the autoVerify
  https filter; CAMERA permission +
  `uses-feature … required=false`), `SetupScreen`
  (blind toggle), `HomeScreen`, `HistoryScreen`, `SessionDetailScreen`,
  `SessionScreen` (Rate chip + shell hoist), Settings (`my_name`, notes toggle).
- Dependencies added (phase 3): `com.google.zxing:core` for QR generation and
  **ZXing Android Embedded** for in-app scanning (user decision 7); swap
  candidate CameraX + ML Kit only if the scanner surface disappoints.
- New phase-3 surface: a thin scanner wrapper composable/screen in `ui/`
  (drives nothing itself — decoded payloads always go through `ShareCodec` +
  `ImportPreview`).

### Invariant guardrails — do not break

- `CuppingMath` untouched; no rating math inside it and none recomputed in UI.
- No "Coffee total / total to grind" line on setup/start-check (the share screen
  inherits the same rule); the only total is on SessionDetail, as today.
- Chimes fire only when the timer itself advances; skipped steps stay silent (the
  shell refactor preserves this; see phase-0 test list).
- Sessions save onDone only — now "onDone: convert planned row if one exists",
  save-once semantics owned by the VM after the refactor.
- 200-session cap, org.json only, single `CuppingViewModel` + sealed `Screen`,
  no navigation-compose, portrait lock, `keepScreenOn` on live-related screens via
  the shell.

### Unit tests (pure JUnit, `app/src/test`)

- Codec join/results round-trip; version/kind rejection; blind payload contains
  no identities (injected identity fields rejected/ignored).
- Entry-form equivalence: https App-Link URL, `cofcup://import?d=…`, and bare
  `d=` payload all decode to the same result after validation; unknown host-path
  or query-shape is rejected before payload processing.
- Payload validation: bounded sizes, position uniqueness/range, name/notes caps,
  decompression-bomb cap; oversized payload → scores only / text fallback.
- Legacy-history decode tolerance; full-model round-trip (incl. `revealedAt`,
  `raterId`).
- Palette 16 (16-cup plan has 16 entries/colors).
- planned→done conversion (same id, same createdAt, same-row overwrite).
- Join idempotency (re-join replaces, never duplicates); merge idempotency
  (re-scan replaces per `raterId`), other-event payload rejection.
- Merge replace-per-rater with `shareNotes=false` vs `true` surfaces.
- Scanner integration: non-CofCup barcodes ignored with a plain hint; CAMERA
  denied → paste path fully functional; scanned payload validated by the same
  rules as deep links (both entry points hit `ShareCodec`, same accepted forms).
- Shell refactor: Done detected while on Rating/Settings saves exactly once;
  skipped steps don't chime; chimes fire across the switch.
- Reveal transitions: revealedAt set once; unmixed after (irreversible); guest
  reveal-backfill on import; "ask the host" copy state before.
- Display-average helper rounding (HALF_UP precedent; representable values —
  see the notes in `CuppingMathTest` about `BigDecimal(Double)` binaries).

### Testing notes

- Paste-code import is the reliable emulator/emulator automation path (system
  clipboard is not shared between emulators; scanner flows need real devices).
  Two-phone manual testing is the realistic scenario for join + exchange.
- Blind visual/accessibility audit (deepseek §3.8/§13.2) before shipping blind
  mode: every surface, plus content descriptions and TalkBack.

### Explicitly deferred (revisit later, only if QR exchange feels too manual)

- Live sync: `NSDManager` + `ServerSocket` over the **host's phone hotspot** (no
  client isolation, works outdoors), JSON-lines protocol, foreground-only while
  the session screen is up (no Service), `INTERNET` permission added then.
- Nearby Connections API option (GMS dependency) — heavy for this app's spirit.
- Classic Bluetooth RFCOMM (pairing friction) and custom BLE (painful) — avoid.
- Scanner-library experiment (CameraX + ML Kit swap) only if ZXing Embedded's
  surface/compat proves insufficient; not a plan fork.
- Planned-start countdown, "most agreed / most divisive cup", exported summary
  files — deepseek phase-6 polish ideas, revisit after v1 ship.

## 10. Where to start, when the go is given

Implementation order matches phases 0 → 1 → 2 → 3 → 4 (phase 2/3 order is
swappable; phase 0 is always first). Each phase is independently shippable and
leaves the app fully working.
