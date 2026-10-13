# TimiTrain – Spezifikation v1

Status: Entwurf auf Basis des Interviews. Offene Punkte stehen am Ende.

## 1. Überblick

Persönliche Android-App zum Planen, Durchführen und Auswerten von Krafttraining (plus Körpergewicht, Zeit- und Cardio-Übungen). Einzelnutzer, kein Account, alle Daten lokal. Sprache: nur Deutsch.

**Plattform:** nur Android
**Daten:** lokal auf dem Gerät, manueller Export/Import als Datei
**Navigation:** untere Leiste mit drei Tabs (Planen, Trainieren, Analysieren). Einstellungen erreichbar über ein Zahnrad-Icon in der oberen Leiste.

## 2. Begriffe

- **Übung:** Eintrag in der eigenen Übungsliste (Name + Typ).
- **Training:** benannte Vorlage aus geordneten Übungs-Slots. Lebt in der Trainings-Bibliothek.
- **Slot:** Platz im Training mit einer oder mehreren alternativen Übungen, die sich pro Session abwechseln.
- **Wochenplan:** Zuordnung Wochentag → Training oder Ruhetag.
- **Session:** ein tatsächlich durchgeführtes Training an einem Datum.

## 3. Übungstypen

Jede Übung hat genau einen Typ, der festlegt, welche Werte pro Satz erfasst werden:

| Typ | Werte pro Satz |
|---|---|
| Kraft mit Gewicht | Wiederholungen, Gewicht (kg) |
| Körpergewicht | Wiederholungen |
| Zeitbasiert | Dauer (Sekunden) |
| Cardio | Distanz (km) und/oder Dauer (Min.) |

### 3.1 Inkremente

Pro Übung wird für **jeden** erfassten Wert ein Inkrement definiert. Es bestimmt die Schrittweite der Plus/Minus-Buttons im Training:

- Gewicht: z.B. 0,5 / 1 / 2,5 kg
- Wiederholungen: z.B. 1
- Zeit: z.B. 5 Sekunden
- Distanz (Cardio): z.B. 0,1 km

Das Inkrement wird beim Anlegen der Übung festgelegt (mit sinnvollem Standard je Typ) und kann später geändert werden. Werte können nicht unter 0 gehen.

Übungen werden frei vom Nutzer angelegt (keine vordefinierte Bibliothek, keine Bilder). Bei Cardio sind Distanz und Dauer beide optional, mindestens eines muss gesetzt sein. Einheit ist fest kg (kein lbs).

## 4. Tab „Planen“

### 4.1 Trainings-Bibliothek
- Trainings anlegen, umbenennen, duplizieren, löschen.
- Ein Training besteht aus einer geordneten Liste von **Übungs-Slots** (Reihenfolge per Drag & Drop änderbar).
- Jeder Slot enthält eine oder mehrere **Alternativen**; eine Alternative ist eine Übung aus der Übungsliste.
- Pro Slot können Alternativen hinzugefügt, entfernt und umsortiert werden. Beim Hinzufügen wählt man aus bereits angelegten Übungen oder legt direkt eine neue Übung an.
- Pro Alternative: beliebig viele Sätze mit eigenen Planwerten gemäß Übungstyp. Alternativen im selben Slot dürfen unterschiedliche Typen haben.
- Pro Alternative optional: eigene Pausenzeit (überschreibt den globalen Standard).
- Das Inkrement je Wert (siehe 3.1) gehört zur Übung selbst, nicht zum Training.
- Dieselbe Übung darf in mehreren Slots und Trainings vorkommen.

### 4.1.1 Rotation der Alternativen
- Hat ein Slot mehrere Alternativen, wird pro Session genau eine davon trainiert, in Listenreihenfolge und zyklisch: 1. Session → Alternative 1, 2. Session → Alternative 2, … nach der letzten wieder Alternative 1. So entsteht Abwechslung.
- Jeder Slot führt einen eigenen Rotationszähler (nächste Alternative).
- Der Zähler wird weitergeschaltet, wenn eine Session gespeichert wird, in der mindestens ein Satz dieses Slots erledigt wurde. Verworfene Sessions und Slots ohne erledigten Satz schalten nicht weiter. Nachgeholte Sessions schalten wie normale Sessions weiter.
- Werden Alternativen hinzugefügt oder entfernt, wird der Zähler modulo der neuen Anzahl angewendet.
- Die Vorschau auf dem Startbildschirm zeigt pro Slot die heute fällige Alternative.
- Slots mit nur einer Alternative verhalten sich wie eine normale Übung.

### 4.2 Wochenplan
- Sieben Einträge, Montag bis Sonntag.
- Jeder Tag verweist auf **ein** Training aus der Bibliothek oder ist Ruhetag.
- Dasselbe Training darf mehreren Tagen zugewiesen werden.
- Wird ein Training geändert, gilt das für alle Tage, die darauf verweisen.
- Löschen eines Trainings, das im Wochenplan verwendet wird: Warnhinweis, danach werden die betroffenen Tage zu Ruhetagen.

## 5. Tab „Trainieren“

### 5.1 Startbildschirm
- Zeigt das heute geplante Training (Name, Übungsliste als Vorschau) und den Button **Start**.
- Kleiner Button „Anderes Training“ (immer sichtbar, auch wenn für heute etwas geplant ist): öffnet die Trainings-Bibliothek zur Auswahl. Das gewählte Training gilt nur für diese Session.
- Ist heute Ruhetag: Hinweis „Ruhetag“ plus derselbe Button „Anderes Training“.

### 5.1.1 Nachholen
- Ein ausgefallenes geplantes Training kann bis zu **7 Tage später** nachgeholt werden (der Ausfalltag zählt als Tag 0, Nachholen ist an Tag 1 bis 7 möglich).
- Nachholen ist nur an einem Tag **ohne geplantes Training** (Ruhetag) möglich.
- An einem solchen Tag zeigt der Startbildschirm zusätzlich zum Hinweis „Ruhetag“ die offenen, noch nachholbaren Trainings (mit Datum des Ausfalls). Antippen startet eine Session mit dem Training des ausgefallenen Tages.
- Die Session wird am tatsächlichen Datum gespeichert und erhält `makesUpForDate` = Datum des ausgefallenen Plantags. Der ausgefallene Tag gilt damit nicht mehr als ausgefallen.
- Mehrere ausgefallene Trainings können an verschiedenen Ruhetagen nachgeholt werden; pro Tag gilt weiterhin maximal eine Session.
- Fällt ein geplantes Training aus (Tagesende ohne Session), springt die Streak **sofort auf 0**.
- Wird das Training innerhalb der 7 Tage nachgeholt, wird die Streak **wiederhergestellt**: Der Ausfall gilt als aufgelöst, und die Streak hat den Wert, den sie ohne Ausfall hätte (Wert vor dem Ausfall plus nachgeholtes Training plus alle dazwischen absolvierten Trainings).
- Nach Ablauf der 7 Tage gilt der Ausfall endgültig, die Streak zählt ab dem Ausfall neu.

### 5.2 Ablauf einer Session
- Slots werden nacheinander durchlaufen; pro Slot wird die laut Rotation (4.1.1) fällige Alternative gezeigt, Sätze einzeln.
- Pro Satz werden angezeigt: Satznummer, **Planwerte** (groß) und **Werte vom letzten Mal** (klein, als Referenz; entfällt, wenn die Übung noch nie trainiert wurde).
- Bedienung bewusst groß und einfach (Nutzung mit verschwitzten Händen):
  - **Großer Haken:** Satz wurde genau wie im Plan gemacht. Speichert die Planwerte als tatsächliche Werte und schließt den Satz ab.
  - **Plus/Minus-Buttons (groß):** Erlauben, die tatsächliche Leistung anzupassen. Jeder Tipp ändert den Wert um das **Inkrement der Übung** (siehe 3.1). Startwert ist der Planwert. Erst der anschließende Haken schließt den Satz mit dem angepassten Wert ab.
  - Bei Übungen mit mehreren Werten (z.B. Wiederholungen und Gewicht) gibt es für jeden Wert ein eigenes Plus/Minus-Paar mit eigenem Inkrement.
- Nach jedem abgeschlossenen Satz startet **automatisch der Pausentimer** (siehe 5.3).
- Der Bildschirm bleibt während der aktiven Session an.
- Ablauf der Session wird nicht verändert (keine Übungen hinzufügen, überspringen oder umordnen in v1).

### 5.3 Pausentimer
- Startet automatisch nach jedem abgehakten Satz.
- Dauer: pro Übung überschreibbar, sonst globaler Standard aus den Einstellungen.
- Bei Ablauf: Signalton (keine Vibration, keine Benachrichtigung in v1).
- Timer sollte auch laufen und ertönen, wenn die App kurz im Hintergrund ist.

### 5.4 Beenden und Abbruch
- Beim Beenden oder Abbrechen werden alle bis dahin erledigten Sätze gespeichert.
- Die Session zählt im Kalender als Trainingstag, sobald mindestens ein Satz erledigt wurde.
- Sätze ohne Haken werden nicht gespeichert und verändern den Plan nicht.
- **Verwerfen (Discard):** Eine versehentlich gestartete Session kann komplett verworfen werden. Es wird nichts gespeichert, der Plan bleibt unverändert und es entsteht kein Kalendereintrag. Das Verwerfen ist beim Beenden als eigene Option neben „Speichern und beenden“ verfügbar und erfordert eine Bestätigung.
- Es gibt **maximal eine Session pro Kalendertag**. Ist für heute bereits eine Session gespeichert, zeigt der Startbildschirm „Heute erledigt“ statt des Start-Buttons.

## 6. Plan-Anpassungsregel

Nach Abschluss eines Satzes wird der Planwert dieses Satzes (derselbe Satz-Index in derselben Alternative des Slots) automatisch in Richtung des tatsächlich erreichten Werts verschoben:

```
neuer_plan = plan + 0,5 × (tatsächlich − plan)
```

Eigenschaften:
- Gilt für Gewicht, Wiederholungen, Dauer und Distanz.
- Gilt in beide Richtungen (Steigerung und Verringerung).
- Wird **pro Satz einzeln** berechnet.
- Rundung: Gewicht auf 1 kg, Wiederholungen und Sekunden auf ganze Zahlen, Distanz auf 0,1 km, Minuten auf ganze Zahlen.
- Beispiel: Plan 50 kg, gemacht 60 kg → neuer Plan 55 kg. Plan 50 kg, gemacht 44 kg → 47 kg.
- Nur erledigte Sätze verändern den Plan.
- Die Anpassung wirkt auf die Trainingsvorlage und damit auf alle Wochentage, die darauf verweisen.

## 7. Tab „Analysieren“ (v1)

- Monatskalender mit Häkchen an Tagen mit mindestens einer Session.
- Vor/Zurück-Navigation zwischen Monaten.
- Tag antippen zeigt Details der Session: Trainingsname, Übungen, Sätze mit tatsächlichen Werten.
- Zähler: Anzahl Trainings im aktuellen Monat und aktuelle Streak.
- **Streak:** Anzahl der absolvierten Trainings seit dem letzten ausgefallenen geplanten Training. Ruhetage zählen weder als Training noch als Ausfall. Ein Ausfall setzt die Streak sofort auf 0. Wird das Training nachgeholt (siehe 5.1.1), gilt der Ausfall als aufgelöst und die Streak wird wiederhergestellt. Formal: Streak = absolvierte Trainings (inkl. nachgeholter) seit dem letzten noch nicht aufgelösten Ausfall.
- Aus der Kalenderansicht können Sessions gelöscht werden (mit Bestätigung).
- Weitere Auswertungen (Diagramme, Fortschritt je Übung) sind für spätere Versionen vorgesehen.

## 8. Einstellungen

- **Standard-Pausenzeit** (Sekunden), globaler Default für den Timer.
- **Export:** gesamte Datenbasis (Übungen, Trainings, Wochenplan, Sessions) als JSON-Datei über den Android-Dateidialog.
- **Import:** JSON-Datei laden; ersetzt die vorhandenen Daten nach Bestätigung.

## 9. Datenmodell (Vorschlag)

- `Exercise` (id, name, type)
- `Workout` (id, name)
- `WorkoutSlot` (id, workoutId, position, rotationIndex)
- `SlotAlternative` (id, slotId, exerciseId, position, restSecondsOverride?)
- `PlannedSet` (id, alternativeId, position, reps?, weightKg?, durationSec?, distanceKm?)
- `WeekPlan` (weekday 1–7, workoutId?)
- `Session` (id, workoutId, date, startedAt, finishedAt, makesUpForDate?) – `makesUpForDate` verweist beim Nachholen auf den ausgefallenen Plantag und wird für die Streak-Berechnung genutzt.
- `SessionSet` (id, sessionId, exerciseId, position, reps?, weightKg?, durationSec?, distanceKm?)

Sessions speichern eine Kopie der Namen bzw. Werte, damit spätere Änderungen oder Löschungen die Historie nicht verfälschen. „Letztes Mal“ ergibt sich aus der jüngsten Session mit dieser Übung und Satznummer.

## 10. Technische Empfehlung

Da nur Android, lokal und ohne Backend: **Kotlin + Jetpack Compose** (Material 3), **Room** (SQLite) als Datenbank, **ViewModel + StateFlow** für Zustand, Timer über einen Foreground Service oder einen zeitstempelbasierten Timer mit Alarm, Wake-Lock bzw. `keepScreenOn` während der Session, Export/Import über das Storage Access Framework (JSON, z.B. mit kotlinx.serialization). Minimale SDK-Version z.B. Android 8 (API 26) oder neuer.

## 11. Offene Punkte

1. **Anzeige:** Planwerte groß, letztes Mal klein daneben. Bitte bestätigen (der Haken bedeutet „wie im Plan“).
2. **Rotation:** Aktuell pro Slot und nur weitergeschaltet, wenn mindestens ein Satz des Slots erledigt wurde. Soll man in der Session eine Alternative manuell tauschen können, oder erst später?
3. **Übungen umbenennen/löschen**, wenn sie in Trainings oder der Historie vorkommen: Verhalten festlegen.
4. **Neue Sätze/Übungen im Training** ohne Historie: Anzeige „Erstes Mal“ ok?
5. **Datum der Session** bei Training nach Mitternacht: Startdatum zählt?
6. **Look & Feel:** Theme (Hell/Dunkel nach System?), Farben, Icon.
7. Spätere Ideen (nicht v1): Vibration/Benachrichtigung am Timerende, Session spontan anpassen, freies Training, Diagramme, lbs-Umschaltung, Dark-Mode-Schalter.
