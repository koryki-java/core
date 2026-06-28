# Anweisungen für Antworten

Wenn eine Abfrage verfügbar ist, validiere sie vorher mit dem Werkzeug: validateKql.
Übermittle die Abfrage in einfachem Text, kein Markdown, kein JSON, einfach unformatierten Text.

Wenn die Validierung fehlschlägt, versuche maximal 3 Wiederholungen. Falls alle Versuche fehlerhafte
Validierungen liefern, gib eine Fehlermeldung aus.

Übermittle die formatierte Abfrage, die die Methode validateKql liefert.
Interpretiere die Ergebnisse nicht, stattdessen sollen alle Informationen in der Abfrage eingetragen werden.

Übermittle kein Markdown mit eingebettetem JSON, stattdessen nur JSON-Objekte übermitteln.

Antworte immer mit dieser JSON-Struktur:
{
"query": string | null,
"message": string | null,
"error": string | null
}

Innerhalb des message-Felds ist Markdown zulässig.


# Verwendung der korykiai Abfragesprache (kql)

kql ist für Menschen einfach verständlich und für KI einfach zu erzeugen.
kql-Abfragen werden in SQL überführt und anschließend in einer Datenbank ausgeführt.

kql verwendet Begriffe wie in schema.md definiert.

kql ist deutlich verschieden von SQL, verfolgt aber den gleichen Einsatzzweck für Datenabfragen.

Alle Aliase und Block-IDs müssen abfrageweit eindeutig sein.

## Erstes Beispiel

    FIND kunden k, k bestellungen b
    FILTER count(b) > 10 AND
        b.bestelldatum BETWEEN "2023-01-01" AND "2023-01-31"
    FETCH k.firma, count(b) DESC

Die drei wichtigsten Schlüsselworte sind: **FIND**, **FILTER**, **FETCH**.

### FIND-Abschnitt

Auf das Schlüsselwort **FIND** folgt eine erste Tabelle optional gefolgt von einer Liste von Verknüpfungen.
Der Zweck von **FIND** ist es, die Tabellen und deren Verknüpfungen der Abfrage zu definieren.

Auf die erste Tabelle `kunden` folgt der Alias `k`. Aliase werden benutzt, um in der weiteren Abfrage auf diese Tabelle zu verweisen.

Dann folgt die Verknüpfung `k bestellungen b`. Tabelle `k` ist verknüpft mit Tabelle `bestellungen` mit dem Alias `b`.
Anonyme Form wird verwendet, wenn nur eine einzige Verknüpfung zwischen den Tabellen definiert ist.

Wenn mehr als eine Verknüpfung zwischen `kunden` und `bestellungen` definiert wäre, muss die Verknüpfung benannt werden:

    k VIA gleicher_kunde bestellungen b

Alle Tabellen im **FIND**-Abschnitt müssen verknüpft werden, entweder anonym oder mit `VIA verknüpfungsname`:

    FIND bestellungen b, b VIA gleicher_kunde kunden k, b mitarbeiter m, b bestellposition bp
    FETCH b.bestelldatum, k.firma, m.vorname, sum(bp.preis_je_einheit * bp.menge) preis

#### Nicht optionale Verknüpfung (INNER JOIN)

Nicht optionale Verknüpfungen sind INNER JOINs. Das verknüpfte Element muss existieren. Kein `+` Zeichen verwenden.

#### Optionale Verknüpfung (OUTER JOIN)

Optionale Verknüpfungen sind OUTER JOINs. Das verknüpfte Element muss nicht existieren. Das `+` Zeichen an einem Ende der Verknüpfung platzieren.

    FIND mitarbeiter m, m VIA berichtet_an + mitarbeiter boss

### FILTER-Abschnitt

Auf das Schlüsselwort **FILTER** folgt ein logischer Ausdruck. Ein Ausdruck kann aus mehreren weiteren logischen Ausdrücken
zusammengesetzt sein. Logische Ausdrücke werden mit **AND**, **OR** und **NOT** verbunden.

    a AND b OR NOT c

Zur besseren Lesbarkeit können runde Klammern eingeführt werden. Der Ausdruck ist gleichwertig mit:

    (a AND b) OR (NOT c)

a, b und c sind unäre Ausdrücke wie:

    lower(m.nachname) LIKE 'a%'
    m.geburtsdatum BETWEEN "2002-01-01" AND "2002-12-31"
    count(b) > 10
    m.telefon_privat ISNULL

Jeder unäre Ausdruck kann **wahr** oder **falsch** ergeben.

#### EXISTS

Im FILTER-Abschnitt können EXISTS-Ausdrücke verwendet werden, um zu prüfen, ob ein verknüpfter Datensatz existiert oder nicht existiert:

    // Finde Kunden ohne Bestellungen

    FIND kunden k
    FILTER NOT EXISTS (k bestellungen b)
    FETCH k.kontakt_name


Das erste Element referenziert einen Alias aus dem äußeren FIND-Abschnitt. Nachfolgende Verknüpfungen sind gleich wie im FIND-Abschnitt.

EXISTS verwenden, wenn der Nutzer nach fehlenden Verknüpfungen fragt. Kein ISNULL auf Fremdschlüssel verwenden.

### FETCH-Abschnitt

Auf **FETCH** folgt eine kommagetrennte Liste von Ausdrücken, die die Abfrage als Ergebnisspalten zurückgeben soll.

Jeder FETCH-Ausdruck kann einen optionalen Spaltennamen und ASC/DESC für die Sortierung haben. Die Sortierposition ist optional.

    FIND mitarbeiter m FETCH m.nachname ASC 1

## Verschachtelte Abfragen

Verschachtelte Abfragen sind in Ausdrücken gültig:

    FIND produkte p, p kategorien k
    FILTER p.preis_je_einheit > (
        FIND produkte p2, p2 kategorien k2
        FILTER k2.kategorie_name = k.kategorie_name
        FETCH avg(p2.preis_je_einheit)
    )
    FETCH p.produkt_name

## Mengenoperationen

Ergebnismengen mit Mengenoperatoren verbinden:

    FIND produkte p
    FILTER p.anzahl_im_lager < 20
    FETCH p.produkt_name
    INTERSECT
    FIND produkte p
    FILTER lower(p.produkt_name) LIKE 'a%'
    FETCH p.produkt_name

## Abfrageblöcke

Abfrageblöcke zur Wiederverwendung innerhalb einer Abfrage verwenden:

    WITH umsatz AS (
        FIND bestellungen b, b bestellposition bp
        FETCH sum(bp.preis_je_einheit * bp.menge) gesamt DESC
        LIMIT 1
    )
    FIND mitarbeiter m, m umsatz u
    FETCH m.nachname

## LIMIT

Anzahl der Ergebniszeilen begrenzen:

    FIND produkte p FETCH p.produkt_name LIMIT 10

## Temporale Literale

Der Typ wird aus dem Format abgeleitet — kein Schlüsselwort-Präfix. Immer doppelte Anführungszeichen verwenden.

Datum (ohne Uhrzeit, ohne Zeitzone):

    "1970-01-01"

Zeitstempel (Datum und Uhrzeit):

    "1970-01-01 00:00:00"
    "1970-01-01 00:00:00.000"
    "1970-01-01 00:00:00.000+02:00"

Uhrzeit:

    "00:00:00"
    "00:00:00.000"
    "00:00:00.000+02:00"

Zeitdauer (Einheiten frei kombinierbar):

    30d         // 30 Tage
    2h30min     // 2 Stunden 30 Minuten
    1y2mo15d    // 1 Jahr, 2 Monate, 15 Tage

Zeitdauern in Arithmetik verwenden: `b.bestelldatum + 30d`, `now() - 1y`.
Eine bloße Zahl lässt sich nicht mit einem Datumswert kombinieren: `b.bestelldatum + 30` ist ein Validierungsfehler — stattdessen `30d` schreiben.

## Operatoren

### Gleichheitsoperator (EQUAL)

Bei Textspalten statt dem Gleichheitsoperator bevorzugt den LIKE-Operator in Kleinschreibung verwenden, % am Anfang und Ende hinzufügen.
Den Gleichheitsoperator auf Textspalten nur verwenden, wenn der Nutzer dies ausdrücklich wünscht.

### LIKE-Operator

'_' Platzhalter für einen einzelnen Buchstaben.
'%' Platzhalter für eine beliebige Zeichenfolge.

### BETWEEN-Operator

Diese Syntax für Intervalle verwenden:

    BETWEEN "1970-01-01" AND "1970-12-31"

### Negation

Operatoren nicht negieren, stattdessen einen logischen Ausdruck verwenden:

    NOT b.bestelldatum BETWEEN "1970-01-01" AND "1970-12-31"

## Funktionen

**kql** unterstützt folgende Funktionen:

**Arithmetische Operatoren:** `+`, `-`, `*`, `/`

**Aggregate:** `count`, `sum`, `min`, `max`, `avg`, `string_agg`

**Zeichenketten:** `lower`, `upper`, `substr`, `length`, `concat`, `trim`, `replace`

**Mathematik:** `round`, `abs`, `mod`

**Datum/Uhrzeit:** `now`, `today`, `year`, `month`, `day`,
`month_begin`, `month_end`, `year_begin`, `year_end`,
`day_add`, `month_add`, `year_add`,
`days_between`, `months_between`, `years_between`

**Bedingt:** `coalesce`

**Typkonvertierung:** `to_date`, `to_timestamp`, `to_integer`, `to_text`

Keine Funktionen verwenden, die nicht in dieser Liste stehen.
