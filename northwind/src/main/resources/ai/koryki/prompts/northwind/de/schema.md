# northwind
## Tabellen

Die northwind Databenbank speichert Verkäufe für die Northwind Firma.
Northwind verkauft Produkte an andere Kundenfirmen und kauft Produkte bei Herstellerfirmen ein.
Northwind Mitarbeiter bearbeiten Bestellungne und versenden die Produkte über Spediteure an die Kunden.

## kategorien
Produkte werden in Kategorien verwaltet.
- kategorie_id: Die eindeutige Kategorie Id.

- kategorie_name: Name der Kategorie

- beschreibung: Beschreibung der Kategorie

- bild: Ein Bild um die Kategorie zu veranschaulichen

## kunden
Speichert Information zu den Firmen der Kunden mit Feldern wie firma, kontakt_name.
- kunden_id: Die eindeutige Kundennummer.

- firma: Der Firmenname des Kunden

- kontakt_name: Name der Kontaktperson

- kontakt_titel: Der Titel der Kontaktperson,  (z.B. Owner, Sales Representative, Marketing Manager).

- adresse: Anschrift des Kunden.

- stadt: Stadt der Kundenanschrift.

- region: Region der Kundenanschrift.

- plz: Postleitzahl der Kundenanschrift.

- land: Land der Kundenanschrift.

- telefon: Telefonnummer der Kontaktperson.

- mail: Mail der Kontaktperson.

## kunde_segment
Speichert das Kundesegment.
- kunde_id: Der Fremdschlüssel zum Kunden.

- kunden_typ_id: Fremdschlüssel zum Kundentyp.

## kundensegment
Das Kundensegment, z.B. Retail, Wholesale, Corporate, etc.
- kundenbeschreibung: Die Beschreibung des Kundensegments.

## mitarbeiter
Speichert die Information zu Mitarbeitern wie Namen und Titel.
- mitarbeiter_id: Die eindeutige Mitarbeiternummer.

- nachname: Nachname des Mitarbeiters.

- vorname: Vorname des Mitarbeiters.

- titel: Titel des Mitarbeiters für die Rolle in der Firma.

- anrede: Die Anrede

- geburtsdatum: Geburtsdatum des Mitarbeiters.

- einstellungsdatum: Einstellungsdatum des Mitarbeiters.

- arbeitsbeginn: Tägliche Arbeitszeit von

- arbeitsende: Tägliche Arbeitszeit von.

- pausen_minuten: Pause in Minuten.

- adresse: Anschrift des Mitarbeiters.

- stadt: Stadt der Mitarbeiteranschrift

- region: Region Mitarbeiteranschrift

- plz: Postleitzahl Mitarbeiteranschrift.

- land: Land Mitarbeiteranschrift

- telefon_privat: private Telefonnummer des Mitarbeiters.

- durchwahl: Die interne Telefondurchwahl des Mitarbeiters.

- foto: Portraitfoto des Mitarbeiters

- notiz: Notizen zum Mitarbeiter

- foto_pfad: URL zum Foto

## mitarbeiter_gebiet
Speichert die Zuordnung von Mitarbeiten zu Gebieten.
- mitarbeiter_id: Der Fremdschlüssel zum Mitarbeiter.

- gebiet_id: Der Fremdschlüssel zum Gebiet.

## bestellungen
Bestellung zu Kunden, Fremdschlüssel zur Bestellung und Kunden.
- bestell_id: Die eindeutige Bestellnummer.

- bestelldatum: Das Bestelldatum

- wunsch_datum: Das Datum für die gewünschte Lieferung

- versand_datum: Das Versanddatum.

- zustell_datum: Das Zustelldatum.

- spediteur_via: Der Fremdschlüssel zum Spediteur.

- fracht: Die für die Bestellung berechneten Frachtkosten, ein Betrag und kein Gewicht.

- spediteur: Name des Spediteur.

- versand_adresse: Versandadresse, erste Zeile.

- versand_stadt: Stadt der Versandadresse.

- versand_region: Region Versandadresse.

- versand_plz: ; Postleitzahl Versandadresse.
- versand_stadt: ; Stadt Versandadresse

- versand_land: Das Land der Firma, an die versandt wird.
## bestellposition
Die Bestellpositoin verknüpft die Bestellung mit den Produkten und weiteren Informationen zur Bestellung
- bestell_id: Fremdschlüssel für die Bestellung.

- preis_je_einheit: Preis je Einheit.

- menge: Anzahl der bestellten Einheiten.

- rabatt: Der gewährte Rabatt für das Produkt in dieser Bestellung.

## produkte
Speichert Produkte einschließlich Namen, Lieferanten, Kategorie und Preis je Einheit
- produkt_id: Die eindeutige Produktnummer.

- produkt_name: Produktname

- menge_je_einheit: Menge je Einheit.

- preis_je_einheit: Preis je Einheit

- anzahl_im_lager: Anzahl Einheiten im Lager.

- anzahl_in_bestellung: Anzahl der bestellten Einheiten.

- bestell_level: Anzahl der minimalen Einheiten im Lager bevor das Produkt beim Lieferanten neu bestellt werden soll

- ausgelistet: Gibt an ob das Produkt noch lieferbar ist, ausgelistete Produkte sind nicht lieferbar

## region
Die Region in der das Produkt verkauft wird
- region_id: Die eindeutige Id der Region.

- region_beschreibung: Beschreibung Region

## spediteur
Speichert die Information zu Spediteuren
- spediteur_id: Die eindeutige Id des Spediteurs.

- firma: Firmenname des Spediteur

- telefon: Telefonnummer.

## hersteller
Speichert die Hersteller für Produkte
- hersteller_id: Die eindeutige Herstellerid.

- firma: Firmenname des Herstellers

- kontakt: Name des Ansprechpartners, Kontaktperson

- kontakt_titel: Titel der Kontaktperson

- adresse: Adresse der Kontaktperson

- stadt: Stadt der Adresse der Kontaktperson

- region: Region des Herstellers.

- plz: Postleitzahl des Herstellers.

- land: Land des Herstellers for the supplier.

- telefon: Telefonnummer der Kontaktperson des Herstellers

- mail: Mail des Herstellers.

- homepage: Homepage des Herstellers.

## gebiete
Speichert die Gebiete.
- gebiet_id: Die eindeutige Id für das Gebiet.

- gebiet_beschreibung: Description of the territory.

## us_staaten
US Staaten.
- staat_id: Ein Surrogat-Primärschlüssel, der jeden Bundesstaat eindeutig identifiziert.

- staat_name: Der Name des US-Bundesstaates.

## laender
Referenztabelle der Länder, auf die Kunden-, Lieferanten-, Mitarbeiter- und Lieferadressen verweisen, mit geographischen Koordinaten und Umriss für Kartenvisualisierungen.
- land_name: Der Ländername, wie er in den Adressspalten verwendet wird (z. B. 'Germany', 'UK', 'USA').

- iso_code: Der ISO-3166-1-alpha-2-Code des Landes.

- kontinent: Der Kontinent, zu dem das Land gehört.

- breitengrad: Breitengrad des Beschriftungspunkts (Zentroid) des Landes, in Grad.

- laengengrad: Längengrad des Beschriftungspunkts (Zentroid) des Landes, in Grad.

- geometrie: Vereinfachter Landesumriss als GeoJSON-(Multi)Polygon für Choroplethenkarten (DRAW spatial).

## Verknüpfungen / Links
## gleiches_produkt

Beide Tabellen verweisen auf den gleichen Datensatz für das Produkt

### Verknüpfungen / Links:
- bestellposition - produkte
- produkte - produkte


## gleiches_kundensegment

Beide Tabellen verweisen auf den gleichen Datensatz für das Kundensegment

### Verknüpfungen / Links:
- kunde_segment - kundensegment
- kundensegment - kundensegment


## gleicher_spediteur

Beide Tabellen verweisen auf den gleichen Datensatz für den Spediteur

### Verknüpfungen / Links:
- bestellungen - spediteur
- spediteur - spediteur


## basiskategorie

Die Kategorie ist eine Unterkategorie der Wurzelkategorie
Die Verknüpfung is gerichtet, die Reihenfolge muss beachtet werden. Die zweite Kategorie ist die Wurzelkategorie.

### Verknüpfungen / Links:
- kategorien - kategorien


## gehoert_zu

Die Kategorie ist eine Unterkategorie der Wurzelkategorie
Die Verknüpfung is gerichtet, die Reihenfolge muss beachtet werden. Die zweite Kategorie ist die Wurzelkategorie.

### Verknüpfungen / Links:
- kategorien - kategorien


## gleicher_lieferant

Beide Tabellen verweisen auf den gleichen Datensatz für den Lieferenten

### Verknüpfungen / Links:
- produkte - hersteller
- hersteller - hersteller


## gleicher_mitarbeiter

Beide Tabellen verweisen auf den gleichen Datensatz für den Mitarbeiter

### Verknüpfungen / Links:
- mitarbeiter_gebiet - mitarbeiter
- bestellungen - mitarbeiter
- mitarbeiter - mitarbeiter


## gleiches_gebiet

Beide Tabellen verweisen auf den gleichen Datensatz für das Gebiet

### Verknüpfungen / Links:
- mitarbeiter_gebiet - gebiete
- gebiete - gebiete


## gleiches_kunden_kundensegment

Beide Tabellen verweisen auf den gleichen Datensatz für das Kundensegment

### Verknüpfungen / Links:
- kunde_segment - kunde_segment


## gleicher_staat

Beide Tabellen verweisen auf den gleichen Datensatz für den Staat

### Verknüpfungen / Links:


## gleiche_region

Beide Tabellen verweisen auf den gleichen Datensatz für die Region.

### Verknüpfungen / Links:
- gebiete - region
- region - region


## gleiche_bestellposition

Beide Tabellen verweisen auf den gleichen Datensatz für die Bestellposition.

### Verknüpfungen / Links:
- bestellposition - bestellposition


## berichtet_an

Der erste Mitarbeiter berichtet an den zweiten Mitarbeiter.
Die Verknüpfung ist gerichtet, die Reihenfolge muss beachtet werden. Der erste Mitarbeiter ist Teammitglied, der zweite Mitarbeiter ist Teamleiter oder Vorgesetzter. Die entgegengesetzte Verknüpfungsrichtung ist 'vorgesetzter_von'

### Verknüpfungen / Links:
- mitarbeiter - mitarbeiter


## vorgesetzter_von

Der erste Mitarbieter ist der Vorgesetzte des zweien Mitarbeiters.
Die Verknüpfung ist gerichtet, die Reihenfolge muss beachtet werden. Die entgegengesetzte Verknüpfungsrichtung ist 'berichtet_an'.

### Verknüpfungen / Links:
- mitarbeiter - mitarbeiter


## uebergeordnete_kategorie

Die erste Produktkategorie ist eine Kind-Kategorie der zweiten Produktkategorie.
Die Verknüpfung ist gerichtet, die Reihenfolge muss beachtet werden. Wurzelkategorien haben keine übergeordnete Produktkategorie.

### Verknüpfungen / Links:
- kategorien - kategorien


## kind_von

this first category is a subcategory of the second.
The link is directed, order of categories matter. Root categories have not parent categories

### Verknüpfungen / Links:
- kategorien - kategorien


## gleiches_mitarbeitergebiet

Beide Tabellen verweisen auf den gleichen Datensatz für das Gebiet

### Verknüpfungen / Links:
- mitarbeiter_gebiet - mitarbeiter_gebiet


## gleiche_kategorie

Beide Tabellen verweisen auf den gleichen Datensatz für die Produktkategorie

### Verknüpfungen / Links:
- produkte - kategorien
- kategorien - kategorien


## gleiche_bestellung

Beide Tabellen verweisen auf den gleichen Datensatz für die Bestellung

### Verknüpfungen / Links:
- bestellposition - bestellungen
- bestellungen - bestellungen


## gleicher_kunde

Beide Tabellen verweisen auf den gleichen Datensatz für den Kunden

### Verknüpfungen / Links:
- kunde_segment - kunden
- bestellungen - kunden
- kunden - kunden


## gleiches_land

Beide Datensätze verweisen auf das gleiche Land; Adressland und Lieferland verweisen namentlich auf die Länder-Referenztabelle

### Verknüpfungen / Links:
- kunden - laender
- hersteller - laender
- mitarbeiter - laender
- bestellungen - laender
- laender - laender


