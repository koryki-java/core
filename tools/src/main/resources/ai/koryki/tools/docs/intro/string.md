String functions operate on text values (`TEXT` / `VARCHAR`). Character positions and the `start` of
a substring are **1-based**, and length is measured in **characters** (`char_length`, `length`) — use
`octet_length` or `bit_length` for byte- and bit-oriented sizes. The catalog covers length, case
conversion, trimming and padding, substring extraction and search, concatenation, replacement, and a
few encoding helpers (`ascii`, `chr`, `to_hex`, `md5`).

## Where the databases disagree

Text is the least uniform corner of SQL, so this page is franker than the others about it. Two kinds
of difference show up, and they are marked differently.

**Some functions do not exist everywhere.** Each one says so — look for *Unsupported* under the
function. `initcap`, `md5`, `translate`, `split_part`, `concat_ws`, `reverse`, `to_hex` and the
padding family (`lpad`, `rpad`, `repeat`, `overlay`) are each missing on at least one engine, and a
query using one is rejected there rather than quietly doing something else.

`reverse` is worth singling out. It is refused on **Oracle** not because Oracle lacks it, but because
Oracle's reverses *bytes* rather than characters: `reverse('Königlich')` comes back as corrupted text
instead of `hcilginöK`. Being told the function is unavailable is better than being handed mojibake.

**Case conversion is ASCII-only on SQLite.** `upper` and `lower` there fold `a`–`z` and leave
everything else untouched, so `upper('Ölsson-äöü')` is `ÖLSSON-äöü`. Core SQLite has no Unicode
casing at all. On every other supported engine, case conversion is Unicode-aware. If you sort or
group on an upper-cased key and SQLite is in scope, that difference is real.

**Comparison and sorting follow the database's collation**, not KQL — whether `'a' = 'A'`, and where
accented letters sort, is decided by the column's collation. That is a schema decision, and
deliberately not one this layer overrides.
