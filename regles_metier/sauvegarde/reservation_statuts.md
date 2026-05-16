# Règles de transition — Statuts de réservation

> Source de vérité : code backend `citybackend/`.
> - `entity/hebergement/StatutReservation.java` (javadoc officiel)
> - `service/hebergement/ReservationServiceImpl.java` (transitions manuelles)
> - `service/hebergement/NightAuditServiceImpl.java` (transition automatique NO_SHOW)
>
> Dernière mise à jour : 2026-05-15.

---

## 1. Les 5 états possibles

| Statut       | Sens métier                                          | Terminal ? |
|--------------|------------------------------------------------------|------------|
| `CONFIRMEE`  | Réservation enregistrée, avant arrivée du client    | non        |
| `ARRIVEE`    | Client check-in, séjour en cours                     | non        |
| `PARTIE`     | Check-out effectué, séjour clôturé                   | **oui**    |
| `ANNULEE`    | Annulation (avec motif), avant ou pendant séjour     | **oui**    |
| `NO_SHOW`    | Client absent à la date d'arrivée (night audit midi) | **oui**    |

> **Note Tour 12bis (2026-05-05)** : `EN_ATTENTE` a été retiré. Aucun workflow d'approbation n'existe — toute nouvelle réservation naît directement en `CONFIRMEE`.

---

## 2. État initial à la création

`ReservationServiceImpl.create(...)` force `statut = CONFIRMEE` à la persistance (`ReservationServiceImpl.java:297`). Pas d'autre état initial possible.

---

## 3. Matrice des transitions autorisées

| Départ → Arrivée | Déclencheur | Garde / précondition | Effets de bord |
|---|---|---|---|
| **CONFIRMEE → ARRIVEE** | `checkIn(id)` (`ReservationServiceImpl.java:505`) | `statut == CONFIRMEE` (sinon `error.reservation.checkin.invalidStatus`) **ET** `dateArrivee <= today` (sinon `error.reservation.checkin.tooEarly`) | Chambres → `OCCUPEE` (validation par `ChambreService` — si chambre `MAINTENANCE`, le check-in est bloqué). Nuitées du jour → `CONSOMMEE`. Event calendrier `UPDATED` (Tour 44). |
| **CONFIRMEE → ANNULEE** | `cancel(id, motif)` ou `delete()` (soft-delete) (`ReservationServiceImpl.java:643`) | Pas `PARTIE`, pas `ANNULEE` (sinon `error.reservation.cancel.alreadyTerminated`). | Motif trim ; défaut `"(non specifie)"` si null. Concaténation dans `commentaires`. |
| **CONFIRMEE → NO_SHOW** | `NightAuditServiceImpl` — scheduler cron **midi** (`Africa/Nouakchott`) — `NightAuditServiceImpl.java:113` | `statut == CONFIRMEE` ET `dateArrivee < today`. Opération **idempotente** : une réservation déjà `NO_SHOW` n'est pas retraitée. | La chambre **reste consommée et facturable** (politique no-show classique). Pas de libération automatique de chambre. |
| **ARRIVEE → PARTIE** | `checkOut(id)` (`ReservationServiceImpl.java:533`) ou `checkoutExpress(id)` (`ReservationServiceImpl.java:917`) | `statut == ARRIVEE` (sinon `error.reservation.checkout.invalidStatus` / `error.checkoutExpress.statut.invalid`) | Chambres → `NETTOYAGE`. Nuitées restantes `PREVUE` → `CONSOMMEE`. Publication `ReservationCheckedOutEvent` (`AFTER_COMMIT`, `REQUIRES_NEW`) → 1 tâche ménage `QUOTIDIEN` par chambre libérée. Event calendrier `UPDATED`. |
| **ARRIVEE → ANNULEE** (exceptionnel) | `cancel(id, motif)` | `statut != PARTIE` et `statut != ANNULEE` | **Branche spécifique** (`ReservationServiceImpl.java:651`) : libère d'abord les chambres en `NETTOYAGE` avant de basculer en `ANNULEE`. |

---

## 4. États terminaux

`PARTIE`, `ANNULEE` et `NO_SHOW` n'ont **aucune transition sortante**.

- `update(...)` rejette toute modification : `error.reservation.update.terminated` (`ReservationServiceImpl.java:575-578`).
- `cancel(...)` rejette `PARTIE` et `ANNULEE` : `error.reservation.cancel.alreadyTerminated` (`ReservationServiceImpl.java:646-648`).
- `addChambres` / `removeChambre` rejettent également les 3 états terminaux (`ReservationServiceImpl.java:754-756`).
- `NightAuditServiceImpl` est idempotent : ne re-traite jamais un `NO_SHOW` existant.

---

## 5. Schéma synthétique

```
                    +-- (cancel, motif) ------------------> ANNULEE  (terminal)
                    |
CREATE -> CONFIRMEE +-- (checkIn, J atteint) --> ARRIVEE --+-- (checkOut) --> PARTIE   (terminal)
                    |                                      |
                    |                                      +-- (cancel) ----> ANNULEE (libère chambres)
                    |
                    +-- (night audit midi, J dépassé) -----> NO_SHOW (terminal, facturable)
```

---

## 6. Garanties multi-tenant

Aucune transition n'altère le champ `hotel_id` :
- L'entité `Reservation` implémente `TenantAware` et porte `@TenantId` sur `hotelId` (`updatable = false`).
- Hibernate ajoute automatiquement `WHERE hotel_id = ?` à toutes les requêtes via le `CityTenantIdentifierResolver` (cf. `CLAUDE.md` racine §6.1).
- `ReservationServiceImpl` est annoté `@RequireTenant` au niveau classe : tout appel sans `TenantContext` lève `error.tenant.missing`.
- Une transition entre hôtels est **structurellement impossible** : aucun endpoint ne reçoit `hotelId` en entrée, et l'INSERT/UPDATE est filtré par le tenant courant.

---

## 7. Clés d'erreur i18n associées

| Clé                                                | Contexte                                              |
|----------------------------------------------------|-------------------------------------------------------|
| `error.reservation.notFound`                       | ID inconnu ou hors tenant                             |
| `error.reservation.checkin.invalidStatus`          | Check-in tenté sur statut ≠ CONFIRMEE                 |
| `error.reservation.checkin.tooEarly`               | Check-in avant `dateArrivee`                          |
| `error.reservation.checkout.invalidStatus`         | Check-out tenté sur statut ≠ ARRIVEE                  |
| `error.checkoutExpress.statut.invalid`             | Check-out express tenté sur statut ≠ ARRIVEE          |
| `error.reservation.cancel.alreadyTerminated`       | Annulation tentée sur PARTIE / ANNULEE                |
| `error.reservation.update.terminated`              | Update tenté sur PARTIE / ANNULEE / NO_SHOW           |
| `error.reservation.dates.invalid`                  | `dateDepart <= dateArrivee` sur update                |
