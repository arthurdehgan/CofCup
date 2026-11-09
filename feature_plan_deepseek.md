# CofCup — Feature Plan: Saved Set-ups, Sharing, Ratings, Blind Mode, Group QR

> **Status:** planning only. No implementation is included in this document.
>
> **Date:** 2026-11-09
>
> **Scope:** the five proposed features, the architecture changes they need, and a phased delivery plan.

---

## 0. Decision summary

| Area | Decision |
|---|---|
| Saved set-ups | A separate **Saved set-ups** screen, with its own Home entry. History remains "completed sessions only". |
| Sharing | QR code + Android share text + a `cofcup://` custom link. No hosted web page, App Links, or backend in v1. |
| Ratings | Overall **1–5**, optional aroma/flavour/acidity/sweetness/body/aftertaste scores (also 1–5), plus free-text notes. Ratings are optional. |
| Ratings in history | Horizontal coffee cards on the session-detail screen, with score boxes and notes. |
| Group comparison | **QR result exchange first**: participants export a result QR, the host scans and merges them. No live network in v1. |
| Blind mode | During the session and rating, only **Cup 1…N by left-to-right position** is visible. Names, colours, and custom cup numbers are hidden until an explicit reveal. |
| Blind sharing | Guest devices receive only an **anonymous plan** (positions, cup size, cup count). The host keeps the private name/colour mapping. |
| Reveal | Explicit action, not automatic at Done. Persisted once done. Host-controlled for group sessions. |
| 12 vs 16 cups | Reconcile before building ratings/sharing. Recommendation: extend the colour palette to 16 and keep `MAX_CUPS = 16`. |
| Live Wi-Fi / Bluetooth | Not in v1. Same-Wi-Fi live sync is a later optional phase; Bluetooth is the last resort. |

---

## 1. Current baseline

### 1.1 Existing flow

```
Home
  ├─ Start Cupping ──────────────► Live session (no cups, quick start)
  └─ Set up cupping
        └─ Setup (cup size/count)
              └─ Label coffees (name, number, colour)
                    └─ StartCheck (summary + Start)
                          └─ LiveSession pre-start
                                └─ first pour = 0:00
                                      └─ running timeline
                                            └─ Done = save to history
```

- `CuppingDraft` holds the set-up in memory in `CuppingViewModel`.
- `CuppingPlan` is the immutable snapshot used by a running/finished session.
- `SavedSession` currently stores only `id`, `createdAtMs`, and `plan`.
- History is a JSON array in DataStore (`history_v1`), capped at 200 sessions, decoded with `org.json`.
- `SessionScreen` owns the step chimes, `keepScreenOn`, and the `LaunchedEffect` that saves the session when Done is reached.
- Navigation is a single `Screen` sealed interface plus `handleBack()`; there is no navigation-compose dependency.
- The only permission is `VIBRATE`; the activity is portrait-locked.

### 1.2 Constraints and invariants to preserve

- History contains **finished** sessions only, saved when Done is reached.
- A session abandoned before Done is discarded.
- There is no Room and no serialization plugin; persistence stays DataStore + `org.json`.
- All displayed quantities still come from `CuppingMath`.
- The setup screen and StartCheck must **not** show a "Coffee total" line; the only total is on the past-session detail screen.
- Step chimes fire only for timer-driven step advances, never for skips.
- Settings is reachable only from live-session screens.
- The launcher icon must stay rendered through `AndroidView`/`ImageView`, not `painterResource()`.
- `handleBack()` owns system back; new destinations must be added there.

### 1.3 Things to fix before building on top

1. **Timer ownership.** Move Done detection, chime playback, and keep-screen-on out of `SessionScreen`. A rating route, Settings route, or import dialog must not stop the timer's chimes or delay saving.
2. **12 vs 16 cups.** `CupColors.palette` and `CupColors.defaultCoffees()` produce 12 entries, while `CuppingDraft.MAX_CUPS` is 16. `plan.cupCount` can exceed `plan.coffees.size`. Ratings keyed by position and 16-cup reveals need this fixed.
3. **Navigation origin.** Screens such as a saved-set-up detail, import preview, and rating screen need to know where they came from so Back is sensible.
4. **DataStore growth.** Ratings and group data make the history JSON considerably larger. Keep caps and debounce note writes.
5. **Anonymous identity representation.** A blind imported plan must be able to say "identity unknown until reveal" explicitly, without accidentally falling back to "Unnamed coffee" or a transparent colour.

---

## 2. Product principles

- **Offline and local-first.** No accounts, no cloud, no analytics. QR codes and, later, local-network traffic only.
- **History means completed.** Saved/imported set-ups never masquerade as finished sessions.
- **Blind means blind, not just visually hidden.** During a blind session, guest devices must not contain the coffee names or colours at all until the host sends a reveal.
- **Ratings are optional.** A session with no ratings is still a valid, complete session.
- **Low permission footprint.** Camera is needed only for scanning. QR generation, paste-link import, and all solo features work without it.
- **No silent data loss.** Saving a set-up, rating changes, and group merges must be explicit and persistent.

---

## 3. Blind mode specification

### 3.1 Definition

A session is either **blind** or **not blind**. The flag is set in Setup (or at StartCheck before the timer starts) and is part of the plan/session snapshot.

In a blind session:

- The only visible coffee identifier is **Cup 1…N**, where N is `cupCount` and the order is left-to-right tasting order.
- Coffee names, colours, and custom numbers are not shown on any session, rating, history, saved-set-up, share, or group screen until an explicit reveal.
- Guest devices receive an anonymous plan that **contains no names or colours**.
- The host/preparer device may contain the private mapping, but the app must not display it in session-facing screens.
- The physical cups should be numbered 1…N from left to right. Colours should not be used as physical labels in a blind session.
- The custom `CoffeeEntry.number` is private metadata for the reveal. The visible number is the position number. If the custom number would also be shown at reveal, it is shown then, not before.

### 3.2 Why position numbers, not custom numbers

The user requirement is "just the number identifies the order of the coffees from left to right". That is exactly the session **position**. Using position numbers avoids ambiguity if a user entered custom numbers such as 7 and 42, or left a number blank.

- Blind UI: `Cup 1`, `Cup 2`, … `Cup N`.
- Setup copy: "Number the physical cups 1…N from left to right."
- At reveal: show the coffee name, colour, and custom number if it differs from the position.

For clarity, blind mode should prefill/normalise `CoffeeEntry.number` to the position and treat custom numbering as a non-blind feature. This is a small simplification that keeps the blind flow unambiguous.

### 3.3 Visibility matrix

| Surface | Blind, unrevealed | After reveal |
|---|---|---|
| Setup / Label coffees (preparation) | Allowed. This is where the host enters the private mapping. Show a warning that the details will be hidden afterwards. | Allowed |
| Saved set-ups list | No names, no colours, no swatches, no leaking title. Show "Blind cupping · N cups · date" and a lock. | May show the normal summary |
| Saved set-up detail | No coffee list. Show a blind notice and Start/Share. Editing requires an explicit confirmation. | May show the coffee list |
| StartCheck | Cup count, cup size, water/coffee quantities, neutral numbered circles. No names/colours. | May show normal swatches/names |
| Live pre-start | No names/colours | May show normal details |
| Running session | No names/colours anywhere, including instructions and Done copy | May show normal details |
| Rating screen | `Cup N` only; neutral number, no swatch, no name | May show normal details |
| Finished screen | No coffee list; "Reveal coffee identities" action | Normal coffee list |
| History list | No colour dots/names; "Blind cupping · N cups" | Normal summary |
| Session detail | Neutral numbered rating cards; no CoffeeListCard | Normal cards |
| Share preview / QR | Blind public payload only, containing no names/colours | A full/reveal payload may be shared intentionally |
| Group invite | Anonymous plan only | n/a |
| Group results | `Cup N` until host reveal | Names/colours if host included the reveal |
| Accessibility/content descriptions | Must not mention hidden names/colours. Say "Cup 1" only. | Normal descriptions |
| Logs/debug output | Must not print hidden identities | Normal |

### 3.4 Public vs private data

Blind mode introduces a deliberate split:

- **Private plan** — the host's complete data: names, colours, custom numbers, plan.
- **Anonymous public plan** — positions 1…N, cup size, cup count, blind flag. No coffee identity.
- **Reveal payload** — position → name/colour/custom number mapping. Sent only when the host chooses to reveal.
- **Result payload** — participant ratings keyed by position. No coffee identity.

A guest's app should never receive the private plan for a blind session. This is stronger than hiding it in the UI and is the only way to make a blind group tasting meaningful.

### 3.5 Reveal behaviour

- Reveal is **explicit**, not automatic when Done is reached. The phone may still be passed around, and automatic names appearing would spoil the tasting.
- Solo blind: the Finished screen and the unrevealed session-detail screen both offer **Reveal coffee identities** with a confirmation.
- Group blind: only the host can reveal; guests receive the reveal as an import (QR or final results payload).
- Persist `revealedAtMs`. Once revealed, the session detail stays revealed.
- Do not support re-hiding once revealed in v1; once someone has seen the mapping, hiding it again provides little value.
- A guest who never receives a reveal keeps an anonymous history record. The app should say "Reveal not received — ask the host" rather than inventing fallback names.

### 3.6 Human limitation

The person who enters the names and colours knows them. The app can keep them out of every session and rating screen, and it can keep them out of guest payloads, but it cannot erase the preparer's memory.

- For a genuinely blind host, someone else should prepare the cups.
- For a group, guests' devices can be genuinely blind because the host holds the mapping.
- The UI copy should say this plainly rather than implying a cryptographic guarantee.

### 3.7 Copy changes required

The current copy assumes colour + number identification. Blind mode needs alternate text in:

- `LabelScreen`: "Blind cupping: number the cups 1…N left to right. Names and colours are private and will be hidden until the reveal."
- `StartCheckScreen`: replace "Your cups on the table" swatches with neutral numbered circles; no names.
- `CuppingTimeline` Done instruction: the current "check the coffee list below… by colour and number" must have a blind variant (or the screen must override it). The timeline file remains the source of truth for step text.
- `SessionScreen` FinishedContent: no immediate CoffeeListCard; show the reveal gate.
- `HistoryScreen`: blind unrevealed rows show no colour dots/names.
- `SessionDetailScreen`: blind unrevealed title/notice instead of the coffee identification card.
- Share/import screens: "Blind set-up — coffee identities are hidden" instead of a coffee list.
- Group screens: "Cup N" until reveal.

### 3.8 Blind leak checklist

Before shipping any blind feature, verify:

- No screen outside Setup/Label displays a hidden name or colour.
- No content description or accessibility announcement contains a hidden name/colour.
- No debug log prints the private plan.
- No blind share payload contains `name` or `color` fields.
- The import preview for a blind payload cannot show names/colours.
- The history list does not show the coffee colour dots.
- The saved-set-up list does not show a title or swatch that leaks identity.
- The reveal control is the only way to show identities, and it requires a deliberate action.
- The host's private mapping is never sent to a guest before reveal.

## 4. Data model

### 4.1 Core entities

#### CuppingDraft / CuppingPlan

Add one field:

- `blind: Boolean = false`

`CuppingDraft.toPlan()` copies it into the immutable plan. Old stored JSON without the field decodes as `false`. Quick-start plans also default to `false`.

#### CoffeeEntry

Keep the existing fields and add an explicit identity state:

- `name: String`
- `number: Int`
- `colorArgb: Long`
- `identityKnown: Boolean = true`

For an anonymous imported plan, `identityKnown = false`, `name = ""`, `colorArgb = 0L` (unknown sentinel), and `number = position`. Blind UI must never render `displayName` or `colorArgb` when `identityKnown` is false. New JSON decodes `identityKnown` with `optBoolean(..., true)`, so all old data is considered known.

This is intentionally a small change. The alternative — nullable name/colour or a separate anonymous-plan type — is semantically cleaner but touches many more call sites. The important requirement is that "unknown until reveal" is explicit and not a fallback string.

#### SavedSetup

A reusable plan, not a history session:

- `id: String` (UUID)
- `title: String?` (optional; hidden in blind list/detail until reveal)
- `plan: CuppingPlan`
- `createdAtMs: Long`
- `updatedAtMs: Long`
- `origin: LOCAL | SHARED`
- `sharedFromName: String?` (host name for a shared set-up)
- `groupId: String?` (present if this set-up came from a group invitation)
- `revealedAtMs: Long?` (for a guest blind set-up whose identities have since been revealed)
- `lastStartedAtMs: Long?` (optional convenience)

#### SavedSession

The existing record plus ratings and group data:

- `id: String`
- `createdAtMs: Long` (completion time, as today)
- `plan: CuppingPlan`
- `revealedAtMs: Long?`
- `ratingSets: List<RatingSet>` (own ratings always; host stores all participant sets)
- `group: GroupInfo?`
- `blind` is derived from `plan.blind`; do not store a second conflicting copy.

For a solo session, `ratingSets` contains one local set. For a group guest, it contains the guest's own set and, after importing final results, the other participants' sets. For a group host, it contains every set the host has collected.

#### CupRating

One cup's ratings. Keyed by **1-based position**, not by coffee name or colour:

- `position: Int` (1…cupCount)
- `overall: Int?` (1–5)
- `aroma: Int?`
- `flavour: Int?`
- `acidity: Int?`
- `sweetness: Int?`
- `body: Int?`
- `aftertaste: Int?`
- `notes: String` (empty when absent; cap 500 characters)

`null` means "not rated". A rating set may be empty or partial. Do not use 0 as "not rated".

#### RatingSet

- `participantId: String` (UUID)
- `displayName: String` (optional; default "Guest")
- `isLocal: Boolean`
- `submittedAtMs: Long?`
- `shareNotes: Boolean = false`
- `ratings: List<CupRating>`

The local participant's set is always saved. The host's session can hold multiple sets.

#### Participant / GroupInfo

- `Participant`: `id`, `displayName`, `isHost`, optional `joinedAtMs`, optional `submittedAtMs`.
- `GroupInfo`: `groupId`, `hostParticipantId`, `hostName`, `createdAtMs`, `participants`. Reveal state lives on `SavedSession.revealedAtMs` (session-level, authoritative).

Group IDs are per event, not per saved set-up. If the same set-up is reused for a new group cupping, generate a new group ID. A guest's saved set-up from a group invitation keeps the old group ID; starting it again later should be a local session unless a new group invitation is created.

#### Profile

- `participantId: String` (random UUID, persisted)
- `displayName: String` (optional, default "Guest")

The profile is used for group results and participant QR codes. It is not an account and never leaves the devices except inside a QR payload.

### 4.2 Rating rules

- Ratings are optional and can be partial.
- Overall is 1–5. Attributes are 1–5. No half points, no SCA weighting, no 100-point conversion in v1.
- Notes are one free-text field per cup, with a placeholder covering aroma/flavour/aftertaste. Structured attribute notes can come later.
- Ratings are attached to a position, so blind sessions do not need coffee identities to rate.
- Do not recompute an "overall" from attributes; the overall score is the user's own judgement.
- A cup can be explicitly "Not rated" and still be part of the session.
- Solo ratings are editable after the session.
- Group ratings are editable until the guest submits and the host reveals. After reveal, group results are read-only in v1. The host can edit their own ratings until reveal.
- When a guest resubmits before reveal, replace their previous set (same participant ID) rather than appending duplicates.

### 4.3 Group comparison rules

- Averages use only the participants who actually rated that cup/attribute; do not treat missing as zero.
- Show the average overall per cup, optional per-attribute averages, and the per-participant breakdown.
- Rank order per participant is useful because absolute scales differ (one person's 4 is another's 2). Show raw scores and, where useful, each participant's ranking.
- "Most agreed cup" (lowest variance) and "most divisive cup" (highest variance) are a nice phase-4.5 addition, not a v1 blocker.
- Notes are shared only when a participant opts in. Default off for group.
- Participant display names are optional; default "Guest N" rather than exposing a device name.

### 4.4 Data invariants

- A plan's `cupCount` equals the number of positions available for ratings (1…N).
- `plan.coffees.size` equals `cupCount` for any newly created or imported set-up. Fix the 12/16 mismatch before relying on this.
- Ratings never reference a position outside 1…cupCount.
- A blind anonymous plan has `blind = true` and no known identities.
- A revealed session has `revealedAtMs != null`; before reveal it is `null`.
- Once `revealedAtMs` is set, it is not cleared by normal UI.
- Group result merges are idempotent by `groupId` + `participantId`.

---

## 5. Persistence

### 5.1 DataStore keys

| Key | Content |
|---|---|
| `history_v1` | Existing JSON array of completed sessions, extended with optional fields |
| `setups_v1` | JSON array of saved set-ups |
| `profile_v1` | JSON object with local participant ID/name |
| `sound_on_next_step` | Existing boolean |
| `vibrate_on_next_step` | Existing boolean |
| `draft_v1` | Optional; only if draft autosave is added later |

No Room, no serialization plugin. Keep the same `cofcup_store` DataStore.

### 5.2 History JSON changes

Existing session objects gain optional fields:

- `"blind": true`
- `"revealedAt": 0` or epoch ms
- `"ratingSets": [...]`
- `"group": {...}`

Coffee entries gain optional `"identityKnown": false`. Old records without these fields decode with defaults (`blind=false`, `revealedAt=null`, no ratings, no group, `identityKnown=true`). No migration is required.

Decoder behaviour:

- Keep the existing per-record `runCatching` so one malformed session does not destroy the rest.
- Use `optBoolean`, `optLong`, `optJSONArray`, and `optJSONObject` for every new field.
- If a rating is malformed, skip that rating rather than the whole session.
- If a group object is malformed, keep the session but drop the group data (with no crash).
- Preserve the current top-level fallback of an empty list on unrecoverable JSON failure.

### 5.3 Saved set-ups JSON

A separate array with the same style:

- `id`, optional `title`, `createdAt`, `updatedAt`, `origin`
- optional `sharedFromName`, `groupId`, `revealedAt`, `lastStartedAt`
- `cupSizeMl`, `cupCount`, `blind`
- `coffees`: name, number, colour, identityKnown

Cap at 50 saved set-ups. Upsert by `id`. Deleting a saved set-up never touches history.

### 5.4 Storage limits

- History: keep 200 sessions.
- Saved set-ups: 50.
- Cups per session: 16.
- Participants per group: 16.
- Coffee name: 40 characters (existing).
- Notes: 500 characters.
- Payload decompressed size: 64 KB hard limit; QR display limit around 2,000 encoded bytes for practical scanning.

Ratings make the history string larger. A full 200-session history with 16 ratings and notes each is still manageable, but DataStore rewrites the entire string on every edit. Therefore:

- Debounce notes writes; do not save on every keystroke.
- Save ratings on screen exit, on Done, and on explicit edits.
- If history size becomes a problem, split group results into a separate key rather than adopting Room prematurely.

### 5.5 Repository operations

The repository should continue to expose flows and suspend functions, adding:

- `setups: Flow<List<SavedSetup>>`
- `profile: Flow<Profile?>`
- `saveSetup`, `deleteSetup`, `updateSetup`
- `saveSession` (upsert by ID), `deleteSession`
- `updateSessionRatings`, `updateSessionGroup`, `revealSession`
- `setProfileName`
- Optional: `saveGroupResults` if group data is stored separately

All writes happen inside DataStore `edit` so read-modify-write is atomic. Upserts must not duplicate sessions or set-ups.

## 6. Sharing payload and QR specification

### 6.1 Link format

Use one custom scheme with a single import entry point:

```
cofcup://import?d=<base64url(payload)>
```

- The QR code encodes the full link, so a generic camera/QR app can offer to open CofCup.
- The same link is used for Android share text and "Copy link".
- The payload's `kind` field tells the app what to do.
- No HTTPS/App Links, no hosted page, no backend in v1.
- Add a "Paste link" path in the Saved set-ups screen; scanning must never be mandatory.
- If the app is not installed, a custom link cannot open it. This is accepted for v1.

### 6.2 Envelope

A versioned JSON object, compressed and Base64-url encoded:

| Field | Required | Meaning |
|---|---|---|
| `v` | yes | Payload format version, currently `1` |
| `kind` | yes | `setup`, `blind_setup`, `group_invite`, `reveal`, `ratings`, `group_results` |
| `setupId` | setup/group invite | Stable ID for de-duplication |
| `groupId` | group kinds | Stable group-event ID |
| `createdAt` | no | Epoch ms; useful for display/ordering |
| `title` | no | Optional private label for full set-ups only; never in a blind payload |
| `host` | group invite | `{ "id": "...", "name": "..." }` |
| `participant` | ratings | `{ "id": "...", "name": "..." }` |
| `plan` | setup/group invite | Plan object |
| `cups` | full setup/reveal/results | Position → identity mapping |
| `ratings` | ratings/group results | Per-cup ratings |
| `participants` | group results | Participant rating sets |
| `revealed` | group results | Whether identities are included |
| `notesShared` | ratings/group results | Whether notes are intentionally included |

### 6.3 Plan object

Full plan:

```json
{
  "blind": false,
  "cupSizeMl": 150,
  "cupCount": 2,
  "cups": [
    { "position": 1, "name": "Ethiopia", "number": 1, "color": "#FFFF5252" },
    { "position": 2, "name": "Colombia", "number": 2, "color": "#FF42A5F5" }
  ]
}
```

Blind public plan:

```json
{
  "blind": true,
  "cupSizeMl": 150,
  "cupCount": 4,
  "positions": [1, 2, 3, 4]
}
```

Rules:

- A blind public plan must not contain a `cups` array with names/colours. The parser rejects/ignores identity fields in `blind_setup` and blind `group_invite` payloads.
- A full plan must contain exactly `cupCount` cups, positions 1…N, each with a name (may be blank), a number, and a colour.
- `color` is `#AARRGGBB` or `#RRGGBB`; the app's existing colours are opaque, so `#RRGGBB` is sufficient, but 8-digit support is safer.
- `number` is 0–99. In blind mode the visible identifier is still position; custom numbers only appear at reveal.
- `title` is optional on full set-up payloads. Do not include a title in a blind payload; it can leak identity.

### 6.4 Kinds

#### `setup`

- Full plan.
- Used for non-blind "Share set-up".
- Import preview can show names/colours.
- `setupId` for duplicate detection.

#### `blind_setup`

- Anonymous plan only.
- Used for "Share blind set-up".
- Import preview shows only "Blind cupping · N cups".
- No title, names, colours, or custom numbers.

#### `group_invite`

- A plan (full or blind) plus `groupId` and host identity.
- Used for "Invite to group cupping".
- A blind group invite must use the anonymous plan.
- Importing it creates a Saved set-up with `groupId`, origin `SHARED`, and the appropriate blind flag.
- The guest's copy is marked "Shared" and can be started at any time; there is no live lobby in v1.

#### `reveal`

- Maps positions to identities after a blind session.
- Fields: `groupId` or `setupId`, and `cups` with `position`, `name`, `number`, `color`.
- No ratings.
- Import updates the matching Saved set-up and/or finished session, sets `revealedAtMs`, and marks identities as known.
- A reveal preview may show the mapping because showing it is the entire point of the action.

#### `ratings`

- One participant's ratings for one group event.
- Fields: `groupId`, `participant`, `ratings`, `notesShared`, `submittedAt`.
- Ratings are keyed by `position`; no coffee names/colours.
- Import on the host merges the participant's set idempotently.
- If the same participant ID is scanned again before reveal, replace their previous set after a confirmation.
- If the host has already revealed, reject or warn that the group is locked.

#### `group_results`

- Host's combined results for guests (optional in v1, but useful).
- Fields: `groupId`, optional `cups` if `revealed=true`, `participants` with their rating sets, `notesShared`, `revealed`.
- Guest import attaches the group results to the matching session and, if identities are included, updates the plan's identities and `revealedAtMs`.
- If notes are excluded, guests see scores only.

### 6.5 Encoding, size, and fallbacks

Pipeline:

1. Build JSON.
2. UTF-8 encode.
3. Deflate/gzip.
4. Base64-url encode without padding.
5. Put into `cofcup://import?d=...`.

Guards:

- Decompressed JSON must not exceed 64 KB.
- Encoded payload should not exceed 2,000 bytes for QR display. QR version 40-L can hold more, but dense codes become hard to scan on a phone screen.
- If a payload is too large for a comfortable QR, offer **Share as text** and **Copy link**; do not display an unscannable code.
- For a `ratings` payload with long notes, offer a **scores only** fallback (omit notes). A large `group_results` payload should be shared as text if it exceeds the QR limit.
- Multi-frame/animated QR is explicitly out of scope.

### 6.6 Validation and security

QR/link content is untrusted input.

- Reject unknown `v` with "Update CofCup to open this link."
- Reject unknown `kind`.
- Clamp/validate `cupSizeMl` to 100–400 and `cupCount` to 1–16.
- Positions must be unique and inside 1…cupCount.
- Full plans must have exactly `cupCount` cups; blind plans must not contain identity fields.
- Names are trimmed and capped at 40 characters; notes at 500.
- Colours must match a hex pattern; otherwise treat as unknown.
- Participant count ≤ 16.
- Ratings scores must be 1–5 or null.
- Bound array lengths before allocating.
- Never auto-save, auto-start, auto-reveal, or auto-merge from a scanned link. Always show a preview and require confirmation.
- Do not render payload HTML; everything is plain text in Compose.
- A custom scheme can be invoked by any app. The preview screen is the security boundary.
- QR-only group results cannot be cryptographically authenticated. The host should confirm the participant name/ID; this is acceptable for a trusted tasting group. Live Wi-Fi could add a session token later.

### 6.7 QR generation

- Use ZXing core to generate the matrix; render it as a high-contrast bitmap or Compose canvas on a white card with a quiet zone.
- Use error correction M for a balance of density and scannability.
- Keep the share screen awake while the QR is visible; optionally raise screen brightness.
- Show the payload kind and a short human summary above the QR: "Full set-up · 4 cups", "Blind set-up · 4 cups", "Group invitation · blind".
- Provide Share text / Copy link / Done actions.
- For blind payloads, never render coffee names or colour swatches on the share screen.

### 6.8 QR scanning

Recommended v1: **ZXing Android Embedded** for speed of delivery. It supplies its own scanner activity, handles the camera permission, and works offline. Trade-offs: an extra activity/theme, possible AppCompat dependency, and an older-looking scanner UI.

Fallback/modern alternative: **CameraX + ML Kit barcode scanning** with a Compose preview. More code and APK size (bundled model if offline-first), but a polished in-app experience. Prototype both if the scanner UI becomes a concern; do not let the choice block the payload work.

Scanner requirements:

- Declare `CAMERA` permission; declare the camera feature as not required.
- Offer "Paste link" when permission is denied or the camera is unavailable.
- Validate the decoded text before showing the preview.
- Handle both a full `cofcup://import?...` URI and a bare `d=` payload if a scanner returns only part of it.
- Provide a torch toggle if the library supports it (nice-to-have).
- Do not keep camera frames or images.

### 6.9 Deep-link handling

- Add a `VIEW` intent filter for `cofcup://import` to `MainActivity`; the activity is already exported.
- Parse the URI in `MainActivity.onCreate` and `onNewIntent`.
- Pass the raw URI/payload to the ViewModel; do not parse business data in the Activity.
- Cold start: open the normal Home screen first, then show the Import Preview once the ViewModel is ready.
- Warm start: show the preview immediately unless a live session is running.
- While a live session is running, queue the import and show a dialog/snackbar: "A CofCup link was opened. Import it after this session?" Do not replace the live screen.
- Back from Import Preview returns to Home or to the screen that launched the scanner; never silently discards the payload without confirmation if the user has already chosen "Save".
- No App Links or HTTPS domain in v1.

### 6.10 Duplicate behaviour

- Saved set-up with the same `setupId` already present: offer "Update existing" or "Save another copy".
- Group invite with the same `groupId`: offer "Open existing group set-up" or "Replace".
- Ratings from the same participant/group before reveal: replace after confirmation.
- Reveal for an already-revealed session: update identities if they differ, or show "Already revealed"; do not downgrade `revealedAtMs`.
- Group results for a group with no matching local session: show "No matching group session found" and keep the import available if the user first imports/opens the group set-up.

## 7. Screen-by-screen specification

### 7.1 Home

**Purpose:** entry point for starting, setting up, history, and saved set-ups.

**Changes:**

- Keep the existing Start Cupping and Set up cupping buttons.
- Add a Saved set-ups action in the top bar alongside History (bookmark/folder icon). A badge/count is optional.
- Do not add a giant third button unless usability testing says the top-bar icon is not discoverable.
- If a deep link arrives while Home is visible, show the Import Preview.
- Home itself never displays coffee names/colours, so blind mode adds nothing here.

**Back:** exits the app as today.

### 7.2 Saved set-ups screen

**Purpose:** list reusable/imported plans without mixing them into completed history.

**Content per card:**

- Non-blind: title (or "Cupping · N cups"), date saved, cup count/size, coffee swatches.
- Blind unrevealed: lock icon, "Blind cupping · N cups", date saved, no title, no swatches, no names. If the host's title could leak, hide it until reveal.
- Shared: "Shared" badge and optional host name.
- Group: "Group" badge and participant count if known.

**Actions:**

- Tap → Saved set-up detail.
- Overflow/delete → confirmation; deleting does not touch history.
- Top bar/empty state: "Scan QR" and "Paste link".
- Empty state: "No saved set-ups yet. Save one from the ready screen, or import a shared one."

**Blind:** no names/colours/leaking titles. The card is identifiable by date/cup count.

**Back:** Home.

### 7.3 Saved set-up detail

**Purpose:** review and act on a saved plan.

**Non-blind content:**

- Title/summary, cup size/count, water/coffee quantities, coffee identification list.
- Start, Edit, Share, Delete.
- If group: "Invite to group cupping" or "Collect ratings" as appropriate.

**Blind content before reveal:**

- Summary only; no coffee list, no swatches, no title if it leaks.
- Notice: "Blind cupping. Coffee identities stay hidden until the reveal."
- Start, Share blind set-up, Delete.
- Edit requires an explicit confirmation: "This will show the coffee names and colours. Continue?" It is a deliberate preparatory reveal, not a normal edit. Warn that if the host is also tasting, blind mode is compromised for them.
- For an imported guest blind set-up (`identityKnown=false`), editing identities is disabled until a reveal arrives. Cup size/count editing may also be disabled to keep the imported plan consistent; Start/Delete/Share remain.

**After reveal:**

- The normal summary and coffee list may be shown.
- The saved set-up does not need to become non-blind automatically; reuse can still hide identities in the UI. However, anyone who saw the reveal already knows the mapping, so a later run is no longer a true blind tasting. Warn if the user starts it again expecting a blind session.
- If the user wants a clean blind reuse, they need a fresh/duplicated set-up whose identities have not been revealed to the tasters.

**Back:** Saved set-ups.

### 7.4 Setup

**Purpose:** cup size and cup count.

**Changes:**

- Add a **Blind tasting** switch with a short explanation: "Names and colours stay hidden during the session and rating. Only cup numbers 1…N are shown."
- When editing an existing saved set-up, prefill and track `editingSetupId`.
- Keep the existing water/coffee-per-cup summary. Do not add a coffee-total line.
- Primary action: "Next: name the coffees."
- Optional secondary: "Save draft" if draft autosave is added; otherwise rely on explicit Save at StartCheck.
- Back returns to the previous screen (Home, Saved set-up detail, or wherever editing started), not always Home.

**Blind:** Setup itself has no coffee names/colours yet, so only the toggle and copy change.

### 7.5 Label coffees

**Purpose:** enter the private mapping: name, number, colour.

**Changes:**

- Add a blind banner when `blind=true`: "Blind mode: number the physical cups 1…N left to right. Names and colours are private and will be hidden until the reveal."
- In blind mode, prefill/force the visible number to the position and explain that custom numbering is revealed later. The field can remain editable if custom numbers are needed for the private mapping, but the rating UI ignores it.
- Keep names/colours editable here because this is the preparation screen.
- Primary action: "Next: start cupping."
- Saving for later can be offered here via a top-bar icon or at StartCheck; StartCheck is the required completion point.

**Blind caveat:** the preparer sees the mapping here. The UI should not pretend otherwise.

### 7.6 StartCheck

**Purpose:** final pre-session review and the gate to start/save/share.

**Non-blind content:** as today: cup count, size, water, coffee per cup, "Your cups on the table" swatches, start instructions.

**Blind content:**

- Cup count, size, water, coffee per cup.
- A row of neutral numbered circles: 1, 2, … N. No swatches, no names.
- Instruction: "Number the cups 1…N left to right. Their names and colours are hidden until the reveal."
- Do not show the CoffeeListCard.

**Actions:**

- Primary: Start cupping.
- Secondary: **Save for later** (create or update SavedSetups; stay on StartCheck or navigate to Saved set-ups with a confirmation).
- Secondary: **Share set-up** (opens Share screen; blind set-ups share the anonymous payload).
- Optional: **Invite to group** in phase 4.
- Back: Label coffees.

**Save semantics:**

- If `editingSetupId` is set, update that record.
- If new, create a SavedSetup with a fresh UUID and `createdAt`.
- Saving must not create a history record or start the timer.
- Show a clear confirmation ("Saved to Saved set-ups") so the user knows it persisted.

### 7.7 Share screen / dialog

**Purpose:** show a QR and provide link/text alternatives.

**Content:**

- Title/kind: "Share full set-up", "Share blind set-up", "Group invitation".
- Summary: cups, size, and for non-blind the coffee count (not necessarily every name).
- QR on a white card with a quiet zone.
- Warning for blind: "Coffee names and colours are not included."
- Actions: Share text, Copy link, Done.
- If the payload is too large/dense: explain and replace the QR with a text-share prompt.

**Blind:**

- Use `blind_setup` or an anonymous `group_invite`.
- Never render swatches or names on this screen.
- The QR contains no identity fields; validation enforces this.

**Group invite:**

- Show "Friends scan this to copy the blind set-up and rate Cups 1…N."
- Explain that there is no live lobby in v1: joining copies the set-up; scores are collected afterward by QR.
- Generate a `groupId` if this is a new group event. If no SavedSetup exists, auto-save one (with a confirmation) because the host needs a local record for collection.

**Keep screen on** while the QR is visible. Optionally raise brightness temporarily.

### 7.8 Import Preview

**Purpose:** inspect and confirm any scanned/pasted payload.

**By kind:**

- `setup`: "Full set-up · 4 cups · 150 ml", coffee names/colours visible. Actions: Save to Saved set-ups, Start now.
- `blind_setup`: "Blind set-up · 4 cups · 150 ml", no names/colours. Actions: Save, Start now.
- `group_invite`: "Join [host]'s blind cupping · 4 cups", group note, no identities if blind. Actions: Save group set-up, Start now.
- `reveal`: "Reveal for [group/set-up]", show the position → name/colour mapping. Action: Apply reveal.
- `ratings`: "Ratings from [participant] · 4 cups", show scores only; no coffee identities. Action: Merge into group session.
- `group_results`: "Results from [group] · N participants", show whether identities are revealed. Action: Import results.

**Rules:**

- No auto-save/start/reveal/merge.
- Show a clear warning for group data: "Your ratings will be shared with the host" / "These results will be added to your local history."
- Duplicate handling per section 6.10.
- Invalid/unsupported payload: plain error, no crash.
- Back returns to the launch origin.

**Blind:** the preview must not reveal any identity that the payload does not contain. Do not infer names from history.

### 7.9 Live session

**Pre-start (ReadyToStartContent):**

- Existing readiness instructions and big Start button.
- Blind: no coffee list/swatches; instructions refer to numbered cups only. The current ready screen does not list coffees, but its copy should not imply names/colours.
- Add a "Rate cups" path only after the timer is running; rating before 0:00 is pointless.

**Running:**

- Keep the timer, current step, next-step countdown, progress ring, steps card, and instructions.
- Top bar: add a star/"Rate cups" action next to Settings. It opens the Rating screen.
- Optionally show a small "Rated 2/4" chip in the current-step card.
- Blind: no coffee names/colours anywhere. The Done instruction must use the blind variant.
- Keep Skip step behaviour and silent skips.
- Chimes, vibration, and keep-screen-on move to the app/session shell so they survive navigation to Rating, Settings, or an import dialog.
- The timer must keep running while the Rating screen is open.

**Done / FinishedContent:**

- Non-blind: existing "Cupping complete", CoffeeListCard, New cupping/History.
- Blind unrevealed: "Cupping complete. The session was saved. Coffee identities are hidden." Actions:
  - Reveal coffee identities (confirmation; persists).
  - Rate cups / Review ratings (if desired).
  - Share my ratings (group guest, after ratings).
  - Collect ratings (group host).
  - History.
- Blind revealed: show the CoffeeListCard and normal actions.
- Empty quick-start session: unchanged; no coffee list and no meaningful rating flow.

**Leave dialog:**

- Existing "Nothing will be saved" is still true before Done. If ratings were entered, say "Your ratings for this unfinished session will be lost."
- Blind: do not reveal identities in the dialog.

**Back:** an unstarted session returns Home as today; a running session shows the leave dialog.

### 7.10 Rating screen

**Purpose:** quick, optional per-cup ratings during or after a session.

**Access:**

- Live running session: star/"Rate cups" in the top bar.
- Finished session: "Add ratings" / "Review ratings".
- History detail: "Edit ratings" for solo sessions.
- Group guest after finishing: same screen plus "Share my ratings".

**Header:**

- Sticky compact timer: elapsed time, current step, next-step countdown.
- If the session is finished, show "Session complete" instead of the countdown.
- Back returns to LiveSession or SessionDetail, depending on the target.

**Cup presentation:**

- Horizontal pager or a vertical list, one cup at a time. The pager is better for a focused rating; a "Cup 1 of N" indicator is required.
- Tasting order is position order; do not sort by name/score during entry.
- Non-blind: coffee swatch, name, custom number, position.
- Blind: a neutral circle containing the position number, label "Cup N". No swatch, no name, no custom number.
- Do not use the coffee colour as a background/theme in blind mode.

**Controls:**

- Overall: 1–5, large touch targets. A "Not rated" / clear action.
- Details (expandable): Aroma, Flavour, Acidity, Sweetness, Body, Aftertaste, each 1–5 with a clear/unset option.
- Notes: multiline, placeholder "Aroma, flavour, aftertaste…", cap 500 characters.
- Navigation: Previous/Next cup, and a "Done" action. Swiping is optional.
- Show a subtle "Rated" check on cups that have any score/notes.

**Persistence:**

- Live: update `LiveSession` ratings in the ViewModel; save to DataStore on screen exit/Done. Do not write on every keystroke.
- Finished/history: update the existing `SavedSession` (upsert) on exit or explicit save.
- Process death during a live session still loses the live session (existing behaviour); do not silently claim ratings are saved until the session reaches Done.
- Group: before submission, ratings are editable. After submission and host reveal, group results lock.

**Blind:**

- No hidden identity in text, semantics, content descriptions, or accessibility announcements.
- "Reveal" is not available from this screen during a live blind session. It belongs to the Finished/detail flow.

### 7.11 History

**Purpose:** completed sessions only.

**Changes:**

- Add an optional rating indicator to rows: "avg 4.2 · 4 rated" or a small star row.
- Blind unrevealed rows: no coffee colour dots, no names. Show "Blind cupping · N cups" and a lock icon.
- Revealed blind rows: normal summary/swatches.
- Empty state remains.
- Delete confirmation mentions ratings/group data if present.
- No saved/planned set-ups in this list.

**Back:** currently goes to Setup; consider changing to Home while touching navigation, but do not make it part of this feature unless the user agrees.

### 7.12 Session detail

**Purpose:** read-only past session, now with ratings and group results.

**Top summary:**

- Keep the existing Card with cups, water used, coffee per cup, and the coffee total (the only place the total is allowed).
- Blind unrevealed: do not show the CoffeeListCard. Show a blind notice and a Reveal action if the identity data is available locally or via a received reveal.
- Guest without reveal: "Coffee identities have not been revealed. Ask the host for the reveal QR."

**Ratings section:**

- Horizontal row of coffee cards, in tasting order.
- Use `Row` + `horizontalScroll` rather than `LazyRow`: at most 16 cards, stable heights, no nested-scroll issues.
- Card anatomy:
  - Header: swatch + name + custom number/position (non-blind); neutral "Cup N" (blind unrevealed).
  - Overall score prominently: "4 / 5" or a star row.
  - Attribute boxes: Aroma, Flavour, Acidity, Sweetness, Body, Aftertaste; show "—" for unrated.
  - Notes: up to ~3 lines, expandable; hidden/empty when absent.
  - "Not rated" muted state when the cup has no ratings.
- After reveal, blind cards become normal coffee cards.
- Optional session summary: "4 cups rated · average 4.1".

**Group section:**

- Host: "Group results · N participants" with collected/expected count; open Group Results.
- Guest with imported results: same, read-only.
- Guest without results: "No group results imported yet. Ask the host for the result QR."
- Blind before reveal: results use Cup N; after reveal, names/colours.

**Actions:**

- Edit ratings (solo; group only if not locked).
- Reveal identities (if applicable).
- Share my ratings (group guest).
- Collect ratings (host).
- Delete session.

**Blind:** all identity rendering goes through the reveal gate; no fallback names/colours.

### 7.13 Group Results

**Purpose:** compare participant scores for one group event.

**Host view:**

- Header: group name/date, participant count, submission status (e.g., "3 of 4 submitted").
- Participant list: display name, local/host badge, submitted state.
- Per-cup list/cards:
  - Cup N (or name/colour after reveal).
  - Average overall; number of raters.
  - Per-attribute averages if useful.
  - Per-participant scores, either inline or expandable.
  - Shared notes, if any.
- "Most agreed" / "most divisive" optional.
- Actions:
  - Reveal identities (host only; confirmation; if not all submitted, warn but allow override).
  - Share results (QR/text) with optional reveal and optional notes.
  - Back to session detail.

**Guest view:**

- Same read-only comparison after importing `group_results`.
- If no identities were shared, remain Cup N-only.
- If the host reveals later, import the reveal/results to update.

**Privacy:**

- Scores are shared with the host by joining the group. Show this on the group invite import.
- Notes are off by default; each participant can opt in before submitting.
- Participant names are optional; default "Guest N".

### 7.14 Settings

- Keep sound/vibration toggles.
- Optional: "Your name" for group results, if it can be reached outside a live session or set via the group flow.
- Do not add account/login settings.
- Back returns to LiveSession as today; if Settings gains other entry points later, make Back context-aware.

## 8. Feature requirements and acceptance criteria

### 8.1 Feature 1 — save a set-up without starting

**Requirements:**

- “Save for later” at StartCheck (required) and optionally a save icon on Setup/Label.
- Multiple saved set-ups with separate IDs.
- Save does not start the timer and does not create a history record.
- Saved set-ups persist across process death and app restarts.
- Saved set-ups screen lists them; detail supports Start, Edit, Share, Delete.
- Editing updates the same record unless the user chooses “Save as new”.
- Starting loads the exact cup size, cup count, names, numbers, colours, and blind flag into the draft, then goes to StartCheck.
- Blind set-ups show no names/colours/title in the list/detail until an explicit reveal/confirmation.
- Deleting a saved set-up does not affect history.
- The 200-session history invariant is untouched.

**Acceptance:**

- Save a 4-cup set-up, force-stop the app, reopen: the set-up is present.
- Start it: the plan matches exactly; the timer does not start until the user presses Start.
- Save a blind set-up: its card shows “Blind cupping · 4 cups”, no swatches/names.
- Delete it: history count unchanged.

### 8.2 Feature 2 — share/import a set-up

**Requirements:**

- Share screen with QR, Android share text, copy link.
- Full and blind set-up payloads; a blind payload contains no identities.
- In-app scanner plus paste-link fallback.
- Import preview; no auto-save/start.
- Duplicate detection/update choice.
- Custom `cofcup://` deep link handled cold and warm.
- Invalid/oversized payloads fail gracefully.
- The QR contains a self-contained payload; no server/network.
- Non-blind full set-ups can be imported into another device and started.
- Blind set-ups can be imported and started with only Cup 1…N visible.
- A separate reveal payload can later update a blind set-up/session.

**Acceptance:**

- Scan a full 4-cup QR: preview shows names/colours; saving creates a Saved set-up; starting uses the same plan.
- Scan a blind QR: preview/list/start show no names/colours; rating shows Cup N only.
- Open a `cofcup://import?...` link from cold start and warm start: preview appears; Back returns sensibly.
- Deny camera permission: paste-link still works.
- 16 cups with long names: QR uses compression and either scans or offers text fallback.
- A blind payload with an injected `cups` identity array is rejected/ignored; no leak.

### 8.3 Feature 3 — ratings during a session

**Requirements:**

- Optional, partial ratings per cup; no required fields.
- Overall 1–5; optional six attributes; notes.
- Rating screen accessible from the live session and after finishing.
- Blind mode shows only Cup N; no names/colours/custom numbers.
- Non-blind shows the normal coffee identity.
- Timer stays accurate; chimes/keep-screen-on continue while rating.
- Done while rating still saves the session, including current ratings.
- Ratings survive navigation between live/rating/settings during the session.
- Ratings can be edited after a finished solo session.
- Group guest can submit ratings via QR after finishing.
- Leaving before Done warns that ratings/session will be lost.
- No "Coffee total" is added to Setup/StartCheck.

**Acceptance:**

- Rate cup 2 only, leave Done: history shows cup 2 rated, others not.
- Open Rating at 9:00, wait until Done: session saves exactly once; names reveal gate appears if blind.
- Blind rating screen has no name/colour in visible UI, semantics, or content descriptions.
- Notes/rating changes persist after closing and reopening the session detail (finished session).
- Chimes still fire when a step advances while the Rating screen is open.

### 8.4 Feature 4 — ratings in history

**Requirements:**

- Session detail shows a horizontal card per coffee.
- Name/swatch/position for non-blind; Cup N for blind unrevealed.
- Overall score and attribute boxes; notes when present; “Not rated” state.
- Optional average/rated count.
- Blind reveal action; after reveal, cards become normal coffee cards.
- Group results section when available.
- History list hides names/colours for blind unrevealed sessions.
- Delete still works; no crash with old sessions lacking ratings.

**Acceptance:**

- A 4-cup session with ratings shows 4 cards in tasting order.
- A blind unrevealed session shows Cup 1…4 with scores but no names/colours.
- Reveal updates the detail and persists after app restart.
- A session saved by an older app version decodes and shows “Not rated” without crashes.

### 8.5 Feature 5 — group QR comparison

**Requirements:**

- Host creates a group invitation (full or blind) with a group ID.
- Guest imports it into Saved set-ups; can start independently.
- Guest ratings are keyed by position; guest result QR contains no coffee identities.
- Host scans result QRs, validates group ID/participant, merges idempotently.
- Host sees participant submission status and per-cup comparison.
- Host can reveal identities and share final results/reveal.
- Guest can import final results and see the comparison.
- Notes sharing is opt-in; default off.
- Works offline/airplane mode; camera only.
- No live lobby or timer sync in v1.
- A guest who never gets a reveal keeps an anonymous history record.

**Acceptance:**

- Host + 2 guests: both import the blind invite; each rates; host scans both result QRs; comparison shows 3 rating sets (host + 2 guests).
- Guest devices never contain coffee names/colours after importing a blind group invite.
- Host reveals: host comparison shows names/colours; final QR updates guests.
- Scan the same guest result twice: no duplicate; replacement confirmation.
- Scan a result from a different group: rejected.
- Notes off by default; no notes appear in host results.

---

## 9. Group QR flow in detail

### 9.1 Host creates the group event

1. Host has a set-up (full or blind) in Saved set-ups / StartCheck.
2. Host chooses **Invite to group**.
3. If the set-up is not saved, auto-save a copy (confirm) because the host needs a local record.
4. App generates a new `groupId` (UUID) for this event.
5. App shows a QR containing `group_invite` with the full or anonymous plan and host identity.
6. Copy says: “Friends scan this to copy the set-up. There is no live connection; collect their ratings by QR after the cupping.”

### 9.2 Guest joins

1. Guest scans/pastes the invite.
2. Preview shows “Join [host]'s blind cupping · 4 cups”.
3. Guest confirms; a Saved set-up with `groupId`, `origin=SHARED`, `blind=true` is created. No identities are stored.
4. Guest may start immediately or later; the timer is independent.
5. Guest's Saved set-up card shows “Blind cupping · 4 cups”, no names/colours.

### 9.3 Guest cups and rates

- StartCheck is blind-safe.
- Rating screen shows Cup 1…N only.
- Guest may submit partial ratings.
- The guest's own history session gets `groupId` and their local rating set.

### 9.4 Guest shares ratings

1. After Done, the Finished screen and session detail offer **Share my ratings**.
2. If the guest has no profile name, prompt (optional; default “Guest”).
3. The QR/payload is `ratings`: group ID, participant ID/name, per-cup scores, optional notes.
4. No coffee identities are included.
5. The guest can rescan/replace before the host reveals.

### 9.5 Host collects

1. Host finishes their own session (or opens the group session detail).
2. **Collect ratings** opens the scanner.
3. Host scans each guest result QR.
4. Validation:
   - group ID matches;
   - participant ID/name present;
   - positions/scores valid;
   - duplicate participant → replace after confirmation;
   - already revealed → reject/lock.
5. Host sees “2 of 3 submitted” (host counts as one).
6. Host can open Group Results at any time.

### 9.6 Host compares and reveals

1. Before reveal, the comparison uses Cup N.
2. Host taps **Reveal identities**; confirm. If not all guests submitted, warn but allow.
3. `revealedAtMs` is set on the host session; names/colours appear on the host.
4. Host can share final results/reveal:
   - `group_results` with `revealed=true` and identities, or
   - a separate `reveal` payload plus anonymous results.
5. Guests import; their matching session/set-up is updated; their history cards reveal.
6. If a guest never imports, their history remains anonymous with “Ask the host for the reveal.”

### 9.7 Matching and edge cases

- Match by `groupId` first, then `setupId`.
- If the guest has both a Saved set-up and a finished session with the same group ID, update both: the set-up for reuse/reference, the session for history.
- If multiple finished sessions share a group ID (should not happen because each group event is unique), update the most recent and warn.
- If no match, show “No matching group session. Open or import the group set-up first.”
- If the host deletes their session, guests retain their own data.
- If a guest leaves before Done, no history session is saved; they can still rate again in a future session. Their result QR is only offered for finished sessions.
- If the host reveals before all guests submit, later submissions are rejected with “Results already revealed.”
- If a guest wants to remain anonymous, use “Guest” and do not share notes.

### 9.8 Comparison calculations

- Overall average per cup = mean of non-null overall scores for that position.
- Attribute average = mean of non-null values for that attribute.
- Show the denominator/rated count.
- Per-participant breakdown: list each participant's overall score per cup; expand for attributes/notes.
- Optional: each participant's cup ranking; “most agreed”/“most divisive”.
- Do not normalise scores silently. If showing rankings, label them as rankings, not converted scores.

### 9.9 Timer synchronisation

Not included in v1. Each participant starts their own timer from the same set-up. A later enhancement could include a planned start epoch/countdown (“start at 14:30”) in the group invite, or a host start signal over a live LAN connection. Neither is required for score comparison.

---

## 10. Optional live local sync (later phase, not v1)

### 10.1 Assessment

| Approach | Works offline | Friction | Complexity | Recommendation |
|---|---|---|---|---|
| QR result exchange | Yes, no network | Manual scanning | M | **v1** |
| Same-Wi-Fi host server | Yes, same network | Join via QR/IP | L | Best live option later |
| Google Nearby Connections | Yes, BT/Wi-Fi | Permission dialogs, Play Services | L | Alternative later |
| Bluetooth Classic / BLE | Yes | Pairing/discovery/permission friction | XL | Avoid unless required |
| Wi-Fi Direct / local hotspot | Yes | OEM-dependent, complex | XL | Niche fallback |

### 10.2 If same-Wi-Fi is added later

- Host-authoritative server; guests join via QR containing `IP:port` and a one-time token.
- Raw TCP with newline-delimited JSON or WebSocket; prefer a protocol that avoids cleartext-HTTP policy complications.
- Participants send ratings; host merges; host broadcasts reveal/results.
- Permissions: `INTERNET`, network state; additional local-network permission may be required by the target Android version.
- Handle AP/client isolation, guest reconnects, duplicate submissions, host screen-off, and app backgrounding.
- A foreground service would be needed for reliable screen-off hosting (notification permission and service type extra scope).
- Plaintext LAN traffic is acceptable for coffee scores only with a session token and clear consent.
- Test on real devices over a real router; emulator networking is awkward.
- Bluetooth/Nearby is not recommended as the first live transport; discovery/pairing/permission friction is high.

## 11. Technical refactors and integration

### 11.1 Session shell refactor (Phase 0)

**Problem:** `SessionScreen` currently owns:

- `SoundPool` and the `stepChimes` collector;
- `keepScreenOn`;
- the `LaunchedEffect` that calls `onCuppingComplete()` when Done becomes current.

If Rating, Settings, or an import preview replaces `SessionScreen`, chimes stop, the screen can sleep, and the session may not save at Done.

**Required changes:**

- Move Done detection into the ViewModel ticker (`refreshElapsed` or a dedicated completion check). It must be idempotent and save exactly once.
- Hoist chime playback and vibration into a composable/effect that remains composed for LiveSession, Rating, and Settings (e.g. a `SessionHost` wrapper in `CuppingApp`, keyed by a running live session).
- Hoist `keepScreenOn` to the same shell while a live session is running/rating/QR-sharing as appropriate.
- Keep `stepChimes` semantics: timer-driven advances only; skips silent.
- Preserve the existing `extraOffsetMs` elapsed-time model.
- Add tests/manual checks for: Done while in Rating; Done while in Settings; skip step; chime settings on/off; process death.

### 11.2 ViewModel and navigation

New destinations to add to `Screen`:

- `SavedSetups`
- `SavedSetupDetail(setupId)`
- `ImportPreview(payload, returnTo)`
- `Rating(target)` where target is Live or Session(sessionId)
- `GroupResults(sessionId)` or `GroupResults(groupId, sessionId?)`

`handleBack()` additions:

| Current | Back target |
|---|---|
| SavedSetups | Home |
| SavedSetupDetail | SavedSetups |
| ImportPreview | Its `returnTo` (Home/SavedSetups) |
| Rating(Live) | LiveSession |
| Rating(Session) | SessionDetail(sessionId) |
| GroupResults | SessionDetail |

Additional ViewModel state:

- `savedSetups: StateFlow<List<SavedSetup>>`
- `editingSetupId: String?`
- `draftOrigin` / return-to metadata
- `importPreview: ImportPreviewState?`
- `profile: StateFlow<Profile?>`
- Live ratings (inside `LiveSession` or a dedicated flow)
- Group collection state (participant sets, reveal state)
- Pending deep-link payload when a live session is running

Keep one screen orchestrator unless it becomes unwieldy. Group/network/QR logic belongs in separate helpers/repositories, not in Composables.

### 11.3 MainActivity and deep links

- Parse `intent.data` in `onCreate` and `onNewIntent`.
- Pass the URI to the ViewModel.
- Do not parse JSON or mutate repositories in the Activity.
- Cold start: show Home, then the import preview.
- Warm start during a live session: queue and prompt; do not interrupt the timer.
- Add the `VIEW` intent filter for the `cofcup` scheme to the manifest.
- No App Links/HTTPS verification in v1.

### 11.4 Dependencies and permissions

**QR generation:** ZXing core (small, no permissions).

**QR scanning:** recommended v1 is ZXing Android Embedded. It may add an activity/theme and AppCompat; verify target-SDK 37/build compatibility and release minification. Fallback: CameraX + ML Kit bundled barcode scanning.

**Manifest:**

- `CAMERA` permission (runtime).
- `uses-feature android:name="android.hardware.camera" android:required="false"`.
- `cofcup` VIEW intent filter.
- Keep `VIBRATE`.
- No `INTERNET` in v1; add it only for the optional LAN phase.
- Optional LAN later: `ACCESS_NETWORK_STATE` plus any local-network permission required by the target Android version.
- No Bluetooth permissions in v1.

**Build:**

- Add dependencies through `gradle/libs.versions.toml` and the Compose BOM style already used.
- No Room, no kotlinx.serialization.
- Test the release build (`isMinifyEnabled=true`) with QR/scanner libraries; add keep rules only if needed.

### 11.5 File impact map

| File | Impact |
|---|---|
| `data/Models.kt` | `blind`, `identityKnown`, `SavedSetup`, `CupRating`, `RatingSet`, `GroupInfo`, `Profile` |
| `data/CuppingRepository.kt` | `setups_v1`, `profile_v1`, new fields, upserts, reveal/merge operations, tolerant decode |
| `data/CuppingTimeline.kt` | Blind-aware Done instruction/variant |
| `data/SharePayload.kt` (new) | Envelope, encoding/decoding, validation, compression, message kinds |
| `data/QrCode.kt` (new) | QR matrix/bitmap generation |
| `data/GroupMath.kt` (new) | Averages, rankings, variance, merge rules (pure, testable) |
| `ui/CuppingViewModel.kt` | Screens/state, completion ticker, ratings, saved set-ups, import, group collection |
| `ui/CuppingApp.kt` | Routing, session shell/chimes/keep-screen-on, deep-link preview |
| `ui/HomeScreen.kt` | Saved set-ups entry |
| `ui/SavedSetupsScreen.kt` (new) | List/import/empty state |
| `ui/SavedSetupDetailScreen.kt` (new) | Start/edit/share/delete, blind gate |
| `ui/SetupScreen.kt` | Blind toggle, edit context |
| `ui/LabelScreen.kt` | Blind banner, number handling |
| `ui/StartCheckScreen.kt` | Save/share actions, blind summary |
| `ui/ShareScreen.kt` (new) | QR, text share, copy link |
| `ui/ImportPreviewScreen.kt` (new) | Kind-specific preview/confirm |
| `ui/SessionScreen.kt` | Rate action, blind finished/reveal, remove session-owned chime/save logic |
| `ui/RatingScreen.kt` (new) | Per-cup rating UI |
| `ui/Finished` (inside SessionScreen or new) | Reveal gate, group actions |
| `ui/HistoryScreen.kt` | Blind rows, rating indicator |
| `ui/SessionDetailScreen.kt` | Rating cards, blind/reveal, group section |
| `ui/GroupResultsScreen.kt` (new) | Comparison/reveal/share |
| `ui/SettingsScreen.kt` | Optional profile name |
| `ui/components/Common.kt` | Rating boxes, neutral cup number, blind notice, card components |
| `MainActivity.kt` | Deep-link intent handling |
| `AndroidManifest.xml` | Camera, custom scheme |
| `gradle/libs.versions.toml` | ZXing/scanner dependency |
| `app/src/test/...` | Codec, validation, merge, aggregation, schema tests |

---

## 12. Edge cases

| Case | Expected behaviour |
|---|---|
| Save a set-up with blank names | Allowed; names are optional; displayName fallback only outside blind mode |
| Save a blind set-up | List/detail hide names/colours/title; StartCheck blind-safe |
| Edit a blind set-up | Confirmation that identities will be shown; warn about spoiling the preparer's blind |
| Import a blind guest set-up | No identities stored; editing identities disabled until reveal |
| Import a full set-up | Editable like a local set-up; blind=false |
| Full QR with 16 long names | Compress; if too dense, offer share text / copy link |
| Blind QR | Small payload; no identities |
| Blind payload with injected names | Reject/ignore; never render |
| Notes make result QR too large | Offer scores-only or share text |
| Group result scanned twice | Replace before reveal after confirmation; no duplicate |
| Result from another group | Reject |
| Reveal before all guests submit | Warn, allow host override, then lock later submissions |
| Guest never receives reveal | Anonymous history remains; show "ask the host" |
| Guest receives reveal before starting | Update the Saved set-up identities, mark it revealed, and warn that starting it later is no longer a blind tasting; `plan.blind` remains true until the set-up is duplicated/reset |
| Guest receives reveal after finishing | Update the matching session and Saved set-up; set revealedAtMs |
| Host deletes session | Guests retain their own data |
| Guest leaves before Done | No history session; result QR not offered; ratings lost (warning) |
| Done reached while Rating open | Session saves once with current ratings; reveal gate appears |
| Done reached while Settings open | Session saves once; no lost session |
| Deep link while live session runs | Queue/prompt; do not replace the live screen |
| Camera permission denied | Paste-link fallback |
| Unsupported payload version | "Update CofCup to open this link" |
| Malformed history/rating | Skip the bad record/rating; no crash |
| Old history JSON | Decodes with defaults; "Not rated" |
| 16 cups | Palette extended or capped; no missing coffee entries |
| Quick-start empty session | No rating flow; no coffee list |
| Process death during live rating | Live session/ratings lost (existing limitation); finished-session edits saved on exit |
| Duplicate setupId | Update vs save another copy |
| Same set-up reused for a new group | New groupId per event |
| Participant name blank | "Guest N" |
| Notes privacy | Not included unless shareNotes=true |

---

## 13. Testing plan

### 13.1 Unit tests (JVM, `app/src/test`)

**Payload codec:**

- Round-trip each kind: full setup, blind setup, group invite, reveal, ratings, group results.
- Malformed Base64, malformed JSON, unknown kind/version.
- Compression bomb guard; decompressed-size cap.
- Max cups (16), long names, emoji, blank names, max notes.
- Injected identity fields in a blind payload are rejected/ignored.
- Position uniqueness/range validation.
- Colour validation.
- Duplicate/merge idempotence.

**Data/schema:**

- Old `history_v1` JSON decodes with defaults.
- New optional fields round-trip.
- A malformed rating/group object does not destroy the session.
- `SavedSetup` upsert/delete.
- History cap 200; setups cap 50.
- `identityKnown` default true for old records.

**Ratings/group math:**

- Partial ratings average correctly.
- Missing scores are excluded, not zero.
- Per-cup/per-attribute averages, participant ranking, variance.
- Merge replacement by participant ID.
- Reveal state transitions.

**Blind redaction:**

- Pure functions/UI models return only Cup N in blind/unrevealed mode.
- No name/colour/custom number in blind view models or content descriptions.
- Reveal changes the rendered identity only after the flag is set.

**Timer:**

- Completion detection is idempotent and saves once.
- Done while on a non-session screen.
- Skip still does not chime.

### 13.2 UI/manual tests

- Blind visual audit of every screen: Saved set-ups, detail, StartCheck, live, rating, finished, history, detail, share preview/QR, import preview, group results.
- TalkBack/content-description audit on the blind rating and history screens.
- Camera permission granted/denied; paste link; scanner cancellation.
- Deep link cold start and warm start; deep link during a live session.
- QR scanning at realistic distances/brightness; 16-cup long payload; blind payload.
- Two-device group flow in airplane mode (Wi-Fi/Bluetooth off).
- Host + 2 guests: invite, rate, submit, merge, reveal, final results.
- Duplicate submission, wrong group, malformed result.
- Done while rating; chime while rating; screen stays on.
- Process death/restart: saved set-ups, finished sessions/ratings, no live-session claim.
- Old-data upgrade: install over a previous version with history.
- Regression: timer steps, skip, sounds/vibration, quick start, history delete, settings, no coffee total on Setup/StartCheck, launcher icon.

### 13.3 Release checks

- `gradle :app:assembleDebug`
- `gradle :app:testDebugUnitTest`
- Release build with minification and the QR/scanner libraries.
- Verify no hidden identities in release logs.
- Verify APK size impact is acceptable.

## 14. Phased delivery plan

### Phase 0 — Foundations (prerequisite)

**Tasks:**

- Decide and implement the 12 vs 16 cup reconciliation. Recommendation: extend the palette to 16, keep `MAX_CUPS=16`, update helper text.
- Add `blind` to draft/plan and `identityKnown` to coffee entries.
- Add `revealedAtMs`, rating-set/group model shapes (even if unused yet).
- Refactor the session shell: Done detection in ViewModel, chime/keep-screen-on hoisting.
- Add `setups_v1` and `profile_v1` storage scaffolding; tolerant decode for new optional fields.
- Add pure blind-redaction helpers and unit tests.
- Fix navigation origins for detail/import/rating routes.
- Verify old history still decodes and all existing flows behave identically.

**Acceptance:**

- Existing timer, chimes, skip, quick start, settings, and history work unchanged.
- Reaching Done while on Settings/Rating saves exactly once.
- Old history loads; new optional fields round-trip.
- A 16-cup plan has 16 coffee entries and 16 colours.
- Blind view-model tests show only Cup N for unrevealed plans.

### Phase 1 — Saved set-ups

**Tasks:**

- SavedSetups screen, detail screen, Home entry.
- Save for later from StartCheck; edit/update; delete; start into StartCheck.
- Blind-aware list/detail/StartCheck.
- Empty states and confirmation dialogs.

**Acceptance:** section 8.1.

### Phase 2 — Sharing and importing

**Tasks:**

- Payload envelope/codec for full setup, blind setup, group invite (data only), reveal.
- Compression/Base64/link wrapper; validation/size guards.
- QR generation; share text; copy link; paste link.
- Scanner (ZXing Embedded recommended) with camera permission and fallback.
- Import Preview for the four setup/reveal kinds.
- Custom scheme intent filter + MainActivity deep links.
- Duplicate detection.
- Blind payload leak tests.

**Acceptance:** section 8.2; no identities in blind payloads/previews.

### Phase 3 — Ratings and history

**Tasks:**

- Rating model + persistence in live session.
- Rating screen with blind/non-blind variants, attributes, notes.
- Access from live, finished, and history.
- Blind reveal gate at Done and in session detail.
- Horizontal rating cards in SessionDetail.
- History list blind/rating indicators.
- Post-session solo editing.
- Timer/chime/keep-screen-on integration.

**Acceptance:** sections 8.3 and 8.4.

### Phase 4 — Group QR comparison

**Tasks:**

- Profile/display name prompt and storage.
- Group invite generation from Saved set-ups/StartCheck.
- Group guest import (blind/full), groupId storage.
- Guest result QR (`ratings`) with optional notes.
- Host collection scanner, validation, idempotent merge.
- Group Results screen: participants, averages, breakdown, reveal.
- Reveal/final results payload; guest import/merge.
- Group privacy copy and notes opt-in.
- Multi-device test pass.

**Acceptance:** section 8.5.

### Phase 5 — Optional live local sync

Only if QR collection proves too manual. Same-Wi-Fi host/guest, live lobby, submissions, reveal. Not required for the feature set to be useful. Bluetooth/Nearby remains a separate decision.

### Phase 6 — Polish (optional)

- Rating list indicators and session averages.
- “Most agreed/most divisive cup”.
- Planned start countdown in group invites.
- Accessibility pass.
- Export/share a human-readable results summary.
- Better saved-set-up labels/duplication.

---

## 15. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Blind identity leaks through an overlooked screen/content description | Visibility matrix + explicit redaction helpers + screen-by-screen visual/TalkBack audit |
| Host knows the mapping | Clear copy; recommend someone else prepares; guest payloads contain no identities |
| QR payload too dense for long names/notes | Compression + practical size guard + text/scores-only fallback |
| DataStore history grows/rewrite cost | Caps, debounce notes, measure; split keys if needed |
| Timer/chime regression after refactor | Phase 0 tests + manual Done-while-rating/settings checks |
| Navigation/back-stack complexity | Explicit `returnTo`/target data in screens; handleBack tests |
| Group scope expands into networking | QR result exchange is the committed v1; LAN is a separate phase |
| Camera permission friction | Paste-link fallback; camera optional |
| Notes/privacy in group results | Default off; per-participant opt-in |
| Custom scheme not clickable everywhere | QR + paste link + share text; no promise of a web link |
| No iPhone/web participants | Explicitly out of scope; revisit only with a web join page/backend |
| Release minification breaks scanner | Release build test + keep rules |
| 12/16 mismatch causes missing ratings/cards | Phase 0 reconciliation |
| Process death loses live ratings | Documented limitation; save finished edits; optional live-state persistence later |

---

## 16. Open decisions and recommended defaults

| Decision | Recommendation |
|---|---|
| Palette 16 vs cap 12 | Extend palette to 16; keep MAX_CUPS 16. |
| Scanner library | ZXing Android Embedded for v1; CameraX + ML Kit if its UI/build compatibility is poor. |
| Reveal timing | Explicit button; never automatic. |
| Blind set-up title | Hide title in blind list/detail until reveal; use neutral "Blind cupping · N cups". |
| Blind numbers | Position 1…N is the only visible number; custom numbers are private/revealed. |
| Rating scale | Overall 1–5; six optional attributes 1–5; notes. |
| Notes cap | 500 characters. |
| Group notes | Off by default; per-participant opt-in. |
| Participant name | Prompt on first group share; default "Guest". |
| Host reveal threshold | Allow after any submissions with a warning; discourage before all submit. |
| Guest resubmit after reveal | No; group results lock after reveal. |
| Post-session solo rating edit | Yes. |
| Post-reveal solo rating edit | Yes unless the user chooses to lock a session later. |
| Saved set-up title | Optional; useful for non-blind; hidden for blind. |
| Draft autosave | Not required for v1; explicit save is the feature. |
| Planned start countdown | Later enhancement. |
| Live same-Wi-Fi | Later optional phase. |
| Bluetooth/Nearby | Not planned; revisit only if Wi-Fi/QR fail. |
| Web link/App Links/iPhone | Out of scope for v1. |
| Group final results to guests | Yes, via QR/text; anonymous if the host has not revealed. |
| Anonymous group participant | Allowed (default "Guest"); scores still shared. |
| History cap | Keep 200; monitor size. |
| Coffee total on Setup/StartCheck | Still forbidden; only Session Detail shows it. |

---

## 17. Out of scope

- User accounts, cloud sync, analytics, or any backend.
- HTTPS/App Links, a hosted landing page, or a web join page.
- iOS/web participants.
- Live synchronized timer/start signal in v1.
- Bluetooth, BLE, Wi-Fi Direct, or Nearby Connections in v1.
- Full SCA scoring (100-point scale, weighted attributes, descriptors).
- Multi-frame/animated QR.
- Photo capture, label printing, OCR, or coffee inventory.
- Social sharing beyond Android text/QR.
- Editing a completed session's plan (ratings only).
- Importing/exporting the whole history.
- Automatic reveal at Done.
- Encryption of the host's private mapping (the human preparer still knows it).

---

## 18. Glossary

- **Saved set-up:** a reusable plan, stored outside history.
- **Full set-up:** names, colours, numbers, size, count.
- **Blind set-up:** positions/size/count only; no identities.
- **Blind mode:** a session mode where only Cup 1…N is visible during the session/rating.
- **Position:** the 1-based left-to-right cup order; canonical blind identifier.
- **Private mapping:** the host's position → name/colour/number data.
- **Reveal:** the explicit action/payload that exposes the private mapping.
- **Rating set:** one participant's per-cup ratings.
- **Group invite:** a payload that copies a set-up and carries a group ID.
- **Group results:** combined participant scores; optionally includes the reveal.
- **Identity known:** a flag saying whether a coffee's name/colour/number are available locally.
- **Anonymous plan:** a plan whose coffee identities are unknown until a reveal.

---

## 19. Example payloads

### 19.1 Blind group invite

```json
{
  "v": 1,
  "kind": "group_invite",
  "groupId": "9f2c...",
  "setupId": "5a81...",
  "createdAt": 1762700000000,
  "host": { "id": "aa11...", "name": "Arthur" },
  "plan": {
    "blind": true,
    "cupSizeMl": 150,
    "cupCount": 4,
    "positions": [1, 2, 3, 4]
  }
}
```

### 19.2 Guest ratings result

```json
{
  "v": 1,
  "kind": "ratings",
  "groupId": "9f2c...",
  "participant": { "id": "bb22...", "name": "Sam" },
  "submittedAt": 1762703600000,
  "notesShared": true,
  "ratings": [
    { "position": 1, "overall": 4, "aroma": 4, "flavour": 4, "acidity": 3, "sweetness": 4, "body": 3, "aftertaste": 4, "notes": "Jasmine, citrus" },
    { "position": 2, "overall": 3, "aroma": 3, "flavour": 3, "acidity": 4, "sweetness": 2, "body": 3, "aftertaste": 3, "notes": "" }
  ]
}
```

When `notesShared=false`, note text is omitted from the payload (or ignored on import); this example opts in so the notes shape is visible.

### 19.3 Reveal

```json
{
  "v": 1,
  "kind": "reveal",
  "groupId": "9f2c...",
  "cups": [
    { "position": 1, "name": "Ethiopia Guji", "number": 1, "color": "#FFFF5252" },
    { "position": 2, "name": "Colombia Huila", "number": 2, "color": "#FF42A5F5" }
  ]
}
```

---

## 20. Recommended implementation order (one-line summary)

**Phase 0 foundations → saved set-ups → QR share/import → ratings and blind reveal → group QR comparison → optional live Wi-Fi.**

This order keeps the highest-risk work (live networking) optional, reuses QR infrastructure for sharing and group results, and makes blind mode a coherent property of the whole app rather than a last-minute UI toggle.







