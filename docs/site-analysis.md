# Site analysis and data boundary

## Public structure observed

The public main site is organized around the following reading entrances:

- 读通鉴
- 资治通鉴
- 纪事本末
- 读通鉴论

The Zizhi Tongjian reading flow is hierarchical: volume, historical year, and entry. Public reading pages expose original text, vernacular translation, notes or Hu commentary, tags/topics, and reading aids such as ancient editions, decision cards, and the sandbox timeline. The separate `wiki.dutongjian.com` site is a knowledge index with entries such as people, wars, offices, locations, regimes, and allusions.

The project records these observations as product requirements, not as a claim to reproduce private membership data. The public login page exposes email/phone login and password reset entry points, but no stable public pricing, entitlement, payment callback, JSON API, or GraphQL schema has been confirmed.

## Runtime boundary

The Android app does not exchange data with either original site:

1. Retrofit targets only this project's FastAPI base URL.
2. Room is the offline source for cached items, favorites, and local reading state.
3. `source_url` is provenance metadata and is never used as an Android request URL.
4. There is no original-site login, upload, reading-history, favorite, payment, or membership request.

The optional sync process is a separate operator-controlled preparation step. It only accepts an explicitly selected public path, checks robots, restricts requests to the configured origin, applies a minimum interval, caches responses, and retries with backoff. It does not bypass authentication, CAPTCHA, rate limits, or paywalls, and it does not crawl private or paid pages.

## Native feature mapping

| Public reading concept | Independent app implementation |
| --- | --- |
| Original and vernacular text | Parallel, original-only, and translation-only reader modes |
| Notes / direct interpretation | Local notes panel backed by the item payload |
| Tags and topics | Reading tags and knowledge category chips |
| Volume / year navigation | Native Compose catalog screens backed by FastAPI and Room |
| Sandbox timeline | Local reading surface for event context and sequence |
| Decision card | Local prompt surface derived from available item metadata |
| Ancient edition | Independent reader mode entry; no restricted source is unlocked |
| Login / membership | Deliberately out of scope until a lawful, documented backend contract exists |

The advanced reading workspace is therefore an independent product feature. It must not be presented as access to the original site's paid entitlement or as a way to obtain content that the user is not authorized to access.

## Parser contract

`service/app/parsers.py` accepts HTML and a base URL and returns typed domain records. It uses semantic attributes and stable CSS selectors with safe fallbacks:

- `parse_main_catalog`: catalog links and section/volume/year classification.
- `parse_reading_entries`: original, translation, notes, summary, tags, provenance, and hierarchy IDs.
- `parse_knowledge_index`: knowledge title, category, summary, content, and provenance.

The parser performs no network I/O. `service/app/sync.py` is the only orchestration layer and requires an explicit path for each synchronization call.
