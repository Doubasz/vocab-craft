# Vocab Craft — Minecraft Japanese Vocabulary Mod
### Detailed design & implementation plan

---

## 1. Concept

While you play, the mod randomly interrupts you with a short Japanese quiz. You have a few seconds to answer.

There are **two kinds of challenges**:

| Challenge | What you're asked | Penalty if wrong / too slow |
|---|---|---|
| **Item challenge** | The name of an item **in your inventory** (e.g. "ダイヤモンド" → `diamond`, or "Bread" → `pan`) | You **lose that item** |
| **Vocab challenge** | Any word from your vocabulary decks — verbs, adjectives, nouns, phrases (e.g. "食べる" → `to eat`) | You **lose a heart** |

Both work in both directions:
- **EN → JP**: shown English, type Japanese (romaji, hiragana or katakana all accepted)
- **JP → EN**: shown Japanese, type English

After every challenge, the correct answer is shown, so a failure still teaches you something.

---

## 2. Modding primer (how this will actually work)

- Minecraft Java is a Java program. **Fabric** (the mod loader) starts first, loads every `.jar` in `mods/`, and calls your mod's **entry points** (`ModInitializer`, `ClientModInitializer`).
- Your code reacts to game moments through **Fabric API events** (server tick, chat message, command registration, HUD render, networking). **Mixins** (bytecode injection into Minecraft's own methods) exist for cases with no event — this mod should not need any.
- **Mappings** turn Minecraft's obfuscated names into readable ones; Fabric Loom sets this up automatically.
- **Logical sides** — the most important concept:
  - **Server** = the truth (health, inventory, timers). Runs even in singleplayer (integrated server).
  - **Client** = display and input (screens, HUD, keyboard).
  - They communicate with **packets** (custom payloads).
- Rule for this mod: **the server decides everything** (when, what, right/wrong, penalty). The client only shows the question and sends back what you typed.

---

## 3. Tech stack

| Thing | Choice | Why |
|---|---|---|
| Loader | Fabric + Fabric API | Lightweight, events for everything we need |
| Minecraft version | Latest version well supported by Fabric at start — then **freeze it** | Avoid chasing updates mid-project |
| Language | Java (version required by that MC version, Java 21+) | Standard |
| Build | Gradle + Fabric Loom (from the official example mod template) | `./gradlew runClient` dev loop |
| IDE | IntelliJ IDEA Community + "Minecraft Development" plugin | Templates, mixin/event autocompletion |
| JSON | Gson (already bundled with Minecraft) | No extra dependency |
| Config UI (optional) | Mod Menu + Cloth Config, or just a JSON file | JSON file first, UI later |

---

## 4. Gameplay design

### 4.1 Trigger / scheduling
- Per-player countdown in **server ticks** (20 ticks = 1 s).
- Next challenge fires after a random delay in `[minInterval, maxInterval]` (default 3–8 min).
- Using ticks means **pausing the game pauses everything** (singleplayer).
- **Skip & reschedule** (short retry, ~30 s) if:
  - player is dead, in creative or spectator
  - a challenge is already active
  - player just joined (grace period, ~60 s)
  - optional: a screen like a chest/crafting table is open (config)

### 4.2 Choosing the challenge type
- Weighted random: `itemChallengeWeight` vs `vocabChallengeWeight` (default 50/50).
- If inventory has **no eligible item** → fall back to a vocab challenge.
- If no vocab deck is enabled → item challenges only.

### 4.3 Item challenge — picking the item
1. Collect all non-empty slots: hotbar, main inventory, armor, offhand (each category toggleable in config).
2. Keep only items that **have a known Japanese name** (see §6).
3. Optional difficulty filter: `easy` = curated dictionary only, `hard` = curated + vanilla fallback names.
4. Optional weighting: more valuable items (diamonds, netherite, enchanted gear) are picked more often → more tension. (Config: `valueWeighting: true/false`.)
5. Store in the challenge: **slot index + item type + stack count at that moment**.

### 4.4 Vocab challenge — picking the word
- From all **enabled decks** (config list).
- Weighted by spaced repetition (§8, phase 6): words you failed recently show up more often.
- Optional filters by tag: `N5`, `verbs`, `food`, …

### 4.5 Answering
- Time limit: default **15 s** (config), separate values allowed for item/vocab and per direction (typing kana is slower).
- Server adds a small **latency grace** (~0.5 s) before declaring timeout.
- One attempt by default. Option: `attempts: 2`.
- Option: **hint** command/button → reveals first character, costs half the time remaining.

### 4.6 Penalties
**Vocab challenge → lose a heart**
- Deal 2 HP of damage with a **custom damage type** (`vocabcraft:quiz_fail`) that bypasses armor and enchantments → always exactly one heart.
- Custom death message: *"Ferhat forgot how to say 食べる"*.
- Config `canKill`: if `false`, damage is capped so you stay at ≥ ½ heart.

**Item challenge → lose the item**
- Config `itemPenaltyMode`:
  - `ONE` — remove 1 item from the stack
  - `STACK` — remove the whole stack (default)
  - `ALL_OF_TYPE` — remove every item of that type in the inventory
- Removal logic (anti-cheese):
  1. Check the original slot — if it still holds that item type, remove from there.
  2. Otherwise search the whole inventory for that item type and remove from the first match.
  3. If the item is gone completely (dropped, placed, put in a chest during the countdown) → **fall back to the heart penalty**. No escaping.
- Optional fun: the lost item "burns" — fire particles + sound instead of silently vanishing.

### 4.7 Feedback
| Event | Feedback |
|---|---|
| Challenge starts | Sound ping + prompt UI |
| Correct | Pleasant sound, green message, small XP reward (config, default 0) |
| Wrong / timeout | Bad sound, red message: `✗ ダイヤモンド = daiyamondo / diamond` |
| Item lost | Item name in the message + particles |

---

## 5. Architecture

### 5.1 Package layout
```
src/main/java/com/ferhat/vocabcraft/
├── VocabCraftMod.java                  // ModInitializer: registers everything (server/common)
├── config/
│   └── VocabCraftConfig.java           // load/save config/vocabcraft/config.json (Gson)
├── challenge/
│   ├── Challenge.java              // record: id, type, direction, prompt, accepted answers,
│   │                               //         deadlineTick, ItemTarget (nullable), sourceWordId
│   ├── ChallengeType.java          // ITEM, VOCAB
│   ├── Direction.java              // EN_TO_JP, JP_TO_EN
│   ├── ChallengeManager.java       // Map<UUID, Challenge> active; tick(); onAnswer(); resolve()
│   ├── ChallengeScheduler.java     // Map<UUID, Long> nextTriggerTick; skip rules
│   ├── ItemChallengeFactory.java   // picks inventory item → Challenge
│   └── VocabChallengeFactory.java  // picks deck word → Challenge
├── penalty/
│   ├── Penalty.java                // interface: apply(ServerPlayerEntity, Challenge)
│   ├── HeartPenalty.java
│   └── ItemPenalty.java            // with fallback to HeartPenalty
├── vocab/
│   ├── VocabEntry.java             // kanji, kana, romaji[], en[], type, tags[]
│   ├── VocabRepository.java        // loads bundled + user decks
│   ├── ItemDictionary.java         // item id → VocabEntry (curated + vanilla fallback)
│   └── AnkiCsvImporter.java        // phase 6
├── text/
│   ├── KanaConverter.java          // romaji ↔ hiragana ↔ katakana
│   └── AnswerMatcher.java          // normalize + compare
├── net/
│   ├── ChallengeStartPayload.java  // S2C: id, prompt, direction, type, timeLimitTicks, item icon id
│   ├── AnswerPayload.java          // C2S: id, answer text
│   └── ChallengeResultPayload.java // S2C: id, correct?, correct answer display
├── command/
│   └── VocabCraftCommand.java          // /vocabcraft trigger|item|vocab|skip|stats|reload
└── stats/
    └── PlayerStats.java            // phase 6: accuracy, streaks, per-word Leitner box

src/client/java/com/ferhat/vocabcraft/client/
├── VocabCraftClient.java               // ClientModInitializer: packet receivers, HUD
├── ClientChallengeState.java       // current challenge shown on this client
├── QuizScreen.java                 // text field + countdown bar + prompt (+ item icon)
└── QuizHud.java                    // small countdown when screen is closed (optional)

src/main/resources/
├── fabric.mod.json
├── assets/vocabcraft/lang/en_us.json   // UI strings + death message
├── assets/vocabcraft/sounds.json       // (optional custom sounds)
├── data/vocabcraft/damage_type/quiz_fail.json
├── data/minecraft/tags/damage_type/bypasses_armor.json   // add vocabcraft:quiz_fail
└── vocabcraft/
    ├── items_curated.json          // hand-made item dictionary
    ├── items_vanilla_ja.json       // generated (see §6.2)
    └── decks/
        ├── verbs_n5.json
        ├── adjectives_n5.json
        └── nouns_n5.json
```

### 5.2 Flow (one challenge)
```
SERVER                                         CLIENT
──────                                         ──────
tick → scheduler says "fire" for player
choose ITEM or VOCAB, build Challenge
store in ChallengeManager (deadline = now+T)
send ChallengeStartPayload  ─────────────────▶ open QuizScreen, start visual countdown
                                               player types, presses Enter
                            ◀───────────────── AnswerPayload(id, text)
AnswerMatcher.check(text, challenge)
  ✓ → reward, resolve
  ✗ → Penalty.apply(), resolve
(or tick passes deadline → Penalty.apply())
send ChallengeResultPayload ─────────────────▶ close screen, show result message/sound
reschedule next challenge
```
**Important:** the client's countdown is only visual. The server's deadline is the only one that counts.

### 5.3 Fabric hooks used
| Need | Hook |
|---|---|
| Tick timers | `ServerTickEvents.END_SERVER_TICK` |
| Player join/leave cleanup | `ServerPlayConnectionEvents.JOIN / DISCONNECT` |
| Death cancels challenge | `ServerLivingEntityEvents.AFTER_DEATH` (or check `isDead` on tick) |
| Chat-answer MVP | `ServerMessageEvents.ALLOW_CHAT_MESSAGE` (intercept + hide the answer) |
| Commands | `CommandRegistrationCallback.EVENT` |
| Packets | `PayloadTypeRegistry` + `ServerPlayNetworking` / `ClientPlayNetworking` |
| HUD | Fabric's HUD render callback/registry for the MC version you pick |
| Config/data reload | `/vocabcraft reload` command (simple) |

---

## 6. Data

### 6.1 Vocabulary entry format (used for decks AND curated items)
```json
{
  "id": "taberu",
  "kanji": "食べる",
  "kana": "たべる",
  "romaji": ["taberu"],
  "en": ["to eat", "eat"],
  "type": "verb",
  "tags": ["N5", "food"]
}
```
Curated item entries add the Minecraft id:
```json
{
  "item": "minecraft:diamond",
  "kana": "ダイヤモンド",
  "romaji": ["daiyamondo"],
  "en": ["diamond"],
  "tags": ["easy"]
}
```
- `kanji` optional (many items are katakana-only).
- The **prompt** in JP→EN shows kanji if present (with kana as furigana-style hint if config `showKanaHint: true`), else kana.

### 6.2 Item names: curated + vanilla fallback
- **Curated (`items_curated.json`)**: ~100–150 common items written by hand with clean names and romaji (pickaxe, bread, iron ingot, torch, apple…). Used in `easy` mode.
- **Vanilla fallback (`items_vanilla_ja.json`)**: Minecraft ships an official Japanese translation (`ja_jp.json`) for every item, but language files are **client-side assets** — the server can't read them.
  → Solution: a small **dev-time script** (Python is fine) that reads `ja_jp.json` + `en_us.json` from the game assets (Loom cache or the `.minecraft/assets` folder), and generates `items_vanilla_ja.json` with kana + auto-generated romaji.
  → Used in `hard` mode. Accept vanilla names as-is.
  → ⚠️ Fine for personal use; if you ever publish the mod publicly, regenerate at runtime on the client instead of bundling Mojang's translations.

### 6.3 User decks
- Bundled decks are copied on first launch to `config/vocabcraft/decks/` so you can **edit them and add your own**.
- Config lists which decks are enabled.
- Phase 6: **Anki CSV import** — `/vocabcraft import anki <file.csv>` → maps columns (configurable) to `kana / kanji / en`, generates romaji automatically.

---

## 7. Answer matching (the trickiest pure-logic part)

### 7.1 Normalization pipeline
For **Japanese answers** (EN→JP):
1. Trim, lowercase, remove spaces/apostrophes/hyphens.
2. If input contains Latin letters → convert **romaji → hiragana** via `KanaConverter`.
3. Convert any **katakana → hiragana** (Unicode offset `0x60`).
4. Normalize long vowels: `ー` → repeat previous vowel's kana equivalent (so `ダイヤモンド` / `だいやもんど` / `daiyamondo` all compare equal).
5. Compare against every accepted form (kana and kanji, also normalized). Kanji answers accepted as-is if typed.

For **English answers** (JP→EN):
1. Trim, lowercase, collapse spaces.
2. Strip leading `to ` (verbs), `a `, `an `, `the `.
3. Optional: allow 1 typo for words ≥ 6 letters (Levenshtein ≤ 1, config `typoTolerance`).
4. Compare against every entry in `en[]`.

### 7.2 Romaji converter must accept variants
- `shi/si`, `chi/ti`, `tsu/tu`, `fu/hu`, `ji/zi`, `ja/zya/jya`, `sha/sya`, `cha/tya`…
- Double consonant → っ (`kitte` → きって)
- `n` before consonant or end → ん; `nn` → ん; `n'` → ん
- Long vowels: `ou` / `oo` / `ō` / `oh` all OK; `ei` / `ee` OK
- Unit-test this heavily (§10).

---

## 8. Implementation phases

### Phase 0 — Environment (½ day)
- [x] Install JDK, IntelliJ + Minecraft Development plugin
- [x] Clone Fabric example mod, rename to `vocab-craft` (mod id `vocabcraft`), set mod id/package
- [x] `./gradlew runClient` → game launches with mod in mod list
- [x] Log "Vocab Craft loaded" from `onInitialize()`
- **Done when:** you can change a log message and see it after relaunch.

### Phase 1 — Chat MVP, vocab only (1–2 days)
- [ ] Hardcode ~10 words in Java
- [ ] `ChallengeScheduler` with short interval (30 s for testing)
- [ ] Send prompt as a chat message to the player
- [ ] Intercept the player's next chat message as the answer (hide it from chat)
- [ ] Exact-match check (no normalization yet)
- [ ] Wrong/timeout → `HeartPenalty` (plain damage for now)
- [ ] `/vocabcraft trigger` command for instant testing
- **Done when:** you can play, get quizzed, and lose a heart for a wrong answer.

### Phase 2 — Item challenges (1–2 days)
- [ ] `items_curated.json` with ~30 items to start
- [ ] `ItemDictionary` loader
- [ ] `ItemChallengeFactory`: inventory scan + eligible items + random pick
- [ ] `ItemPenalty` with the 3-step removal + heart fallback
- [ ] Weighted choice between ITEM and VOCAB
- [ ] `/vocabcraft item` and `/vocabcraft vocab` to force a type
- **Done when:** a wrong answer deletes the item, even if you move it to another slot; dropping it costs a heart.

### Phase 3 — Real data & matching (2–3 days)
- [ ] `VocabEntry` + `VocabRepository` (JSON decks, copied to config folder)
- [ ] Write starter decks: ~50 verbs, ~30 adjectives, ~50 nouns (N5)
- [ ] `KanaConverter` + `AnswerMatcher` with unit tests
- [ ] Both directions (EN→JP, JP→EN)
- [ ] Custom damage type `vocabcraft:quiz_fail` + death message + bypass armor tag
- [ ] Python script → `items_vanilla_ja.json`; `easy`/`hard` difficulty
- **Done when:** `taberu`, `たべる`, `タベル`, `食べる` all pass; armor doesn't reduce damage.

### Phase 4 — Proper UI with packets (2–3 days)
- [ ] Register the 3 payloads
- [ ] `QuizScreen`: prompt (big), item icon for item challenges, text field auto-focused, countdown bar, Enter to submit
- [ ] Non-pausing screen (world keeps running → real pressure)
- [ ] Result message + sounds
- [ ] **Test Japanese IME input early** in the text field; romaji must always work regardless
- [ ] Keep chat answering as a config fallback (`inputMode: SCREEN | CHAT`)
- **Done when:** the popup appears, counts down, and closes with the result.

### Phase 5 — Config & robustness (1–2 days)
- [ ] `config/vocabcraft/config.json` with every option from §9
- [ ] `/vocabcraft reload`, `/vocabcraft skip`
- [ ] All edge cases in §11
- [ ] Multiplayer-safe (per-UUID state everywhere)
- **Done when:** you can tune everything without recompiling.

### Phase 6 — Learning features (open-ended)
- [ ] `PlayerStats` saved per world/player (accuracy, streak, items lost, hearts lost)
- [ ] `/vocabcraft stats`
- [ ] **Leitner spaced repetition**: each word has a box 1–5; fail → box 1; success → box +1; pick probability ∝ 1/box
- [ ] Anki CSV import
- [ ] Hint system
- [ ] Streak rewards (e.g. 5 correct in a row → regeneration for 5 s)

### Phase 7 — Stretch ideas
- Conjugation challenges: "past form of 食べる?" → 食べた
- "What are you looking at?" challenges (block/mob under crosshair)
- Mob names (クリーパー, ゾンビ) when a mob is near
- Kanji reading mode: show 水 → type `mizu`
- Furigana rendering in the prompt
- Hardcore mode: timer shrinks as your streak grows

---

## 9. Config file (target)
```json
{
  "minIntervalSeconds": 180,
  "maxIntervalSeconds": 480,
  "joinGraceSeconds": 60,
  "timeLimitSeconds": { "EN_TO_JP": 20, "JP_TO_EN": 15 },
  "itemChallengeWeight": 50,
  "vocabChallengeWeight": 50,
  "directionWeights": { "EN_TO_JP": 50, "JP_TO_EN": 50 },
  "difficulty": "easy",
  "enabledDecks": ["verbs_n5", "adjectives_n5", "nouns_n5"],
  "itemPenaltyMode": "STACK",
  "includeArmor": true,
  "includeOffhand": true,
  "valueWeighting": true,
  "canKill": true,
  "attempts": 1,
  "typoTolerance": true,
  "showKanaHint": true,
  "inputMode": "SCREEN",
  "skipWhenContainerOpen": false,
  "xpReward": 0
}
```

---

## 10. Testing strategy
- **Unit tests (JUnit, no Minecraft needed):** `KanaConverter`, `AnswerMatcher`, deck JSON parsing. This is where most bugs hide — write tests first for these two classes.
  - e.g. `daiyamondo == ダイヤモンド`, `kitte == きって`, `konnichiwa == こんにちは`, `to eat == eat`, `shinbun == shimbun == しんぶん`
- **In-game manual tests** with debug commands:
  - `/vocabcraft trigger`, `/vocabcraft item`, `/vocabcraft vocab`, `/vocabcraft skip`
  - Debug config: interval 10 s for fast iteration
- **Edge-case checklist** (§11) run before calling a phase done.
- Test in both **singleplayer** and a **local dedicated server** (`./gradlew runServer` + client) — this catches client/server side mistakes.

---

## 11. Edge cases checklist
| Case | Behavior |
|---|---|
| Player dies during challenge | Cancel, no penalty, reschedule |
| Player disconnects during challenge | Cancel (no penalty, or penalty on rejoin — config) |
| Item moved to another slot | Removed from new slot (§4.6) |
| Item dropped / stored in chest / placed | Heart penalty instead |
| Inventory empty at trigger | Vocab challenge instead |
| Creative / spectator | No challenges |
| Answer arrives after deadline | Ignored (already resolved) |
| Answer with wrong challenge id | Ignored |
| Player at ½ heart & `canKill=false` | No damage, message only |
| Screen closed with Escape | Counts as giving up → penalty (or reopen — config) |
| Game paused (singleplayer) | Timer frozen automatically (tick-based) |
| Deck file broken JSON | Log error, skip that deck, warn player once |

---

## 12. Risks
| Risk | Mitigation |
|---|---|
| Japanese IME doesn't work in MC text fields on your OS | Romaji always accepted; test in phase 4 first thing |
| Romaji edge cases frustrate you | Strong unit tests, generous variants, typo tolerance |
| Client/server confusion | Server-authoritative rule; test on dedicated server |
| Fabric API names change between versions | Freeze the MC version; check Fabric docs for that version |
| Vanilla JP item names too hard / long | `easy` mode = curated list only |
| Scope creep | Phases 0–3 = playable mod. Everything after is bonus |

---

## 13. Milestone summary
| Milestone | After phase | You have |
|---|---|---|
| **Playable** | 2 | Chat quiz, lose hearts and items |
| **Usable daily** | 4 | Real decks, smart matching, popup UI |
| **Polished** | 5 | Configurable, robust, multiplayer-safe |
| **Learning tool** | 6 | Stats, spaced repetition, Anki import |

Estimated time for phases 0–5: **~2 weeks of evenings**.
