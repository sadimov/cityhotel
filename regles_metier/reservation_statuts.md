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

| Départ → Arrivée | Déclencheur | Garde / précondition | Erreur si garde violée | Effets de bord |
|---|---|---|---|---|
| **CONFIRMEE → ARRIVEE** | `checkIn(id)` (`ReservationServiceImpl.java:505`) | `statut == CONFIRMEE` **ET** `dateArrivee <= today` | `error.reservation.checkin.invalidStatus` (statut ≠ CONFIRMEE) **·** `error.reservation.checkin.tooEarly` (arrivée future) | Chambres → `OCCUPEE` (validation par `ChambreService` — si chambre `MAINTENANCE`, le check-in est bloqué et propage l'erreur chambre). Nuitées du jour → `CONSOMMEE`. Event calendrier `UPDATED` (Tour 44). |
| **CONFIRMEE → ANNULEE** | `cancel(id, motif)` ou `delete()` (soft-delete) (`ReservationServiceImpl.java:643`) | `statut != PARTIE` ET `statut != ANNULEE` | `error.reservation.cancel.alreadyTerminated` | Motif trim ; défaut `"(non specifie)"` si null. Concaténation dans `commentaires`. |
| **CONFIRMEE → NO_SHOW** | `NightAuditServiceImpl` — scheduler cron **midi** (`Africa/Nouakchott`) — `NightAuditServiceImpl.java:113` | `statut == CONFIRMEE` ET `dateArrivee < today`. Opération **idempotente** : pas retraitée si déjà `NO_SHOW`. | *(pas d'erreur — réservations non éligibles sont silencieusement ignorées par le scheduler)* | La chambre **reste consommée et facturable** (politique no-show classique). Pas de libération automatique de chambre. |
| **ARRIVEE → PARTIE** | `checkOut(id)` (`ReservationServiceImpl.java:533`) ou `checkoutExpress(id)` (`ReservationServiceImpl.java:917`) | `statut == ARRIVEE` | `error.reservation.checkout.invalidStatus` (via `checkOut`) **·** `error.checkoutExpress.statut.invalid` (via `checkoutExpress`) | Chambres → `NETTOYAGE`. Nuitées restantes `PREVUE` → `CONSOMMEE`. Publication `ReservationCheckedOutEvent` (`AFTER_COMMIT`, `REQUIRES_NEW`) → 1 tâche ménage `QUOTIDIEN` par chambre libérée. Event calendrier `UPDATED`. |
| **ARRIVEE → ANNULEE** (exceptionnel) | `cancel(id, motif)` | `statut != PARTIE` ET `statut != ANNULEE` | `error.reservation.cancel.alreadyTerminated` | **Branche spécifique** (`ReservationServiceImpl.java:651`) : libère d'abord les chambres en `NETTOYAGE` avant de basculer en `ANNULEE`. |

---

## 4. États terminaux

`PARTIE`, `ANNULEE` et `NO_SHOW` n'ont **aucune transition sortante**. Les opérations suivantes sont bloquées sur les terminaux avec un code d'erreur distinct :

| Opération bloquée | Statuts refusés | Erreur retournée | Référence |
|---|---|---|---|
| `update(id, dto)` | `PARTIE`, `ANNULEE`, `NO_SHOW` | `error.reservation.update.terminated` | `ReservationServiceImpl.java:575-578` |
| `cancel(id, motif)` | `PARTIE`, `ANNULEE` | `error.reservation.cancel.alreadyTerminated` | `ReservationServiceImpl.java:646-648` |
| `changerChambre(id, request)` | `PARTIE`, `ANNULEE`, `NO_SHOW` | `error.reservation.changerChambre.terminated` | `ReservationServiceImpl.java:754-757` |
| `NightAuditServiceImpl` | `NO_SHOW` (déjà terminé) | *(idempotent, no-op silencieux)* | `NightAuditServiceImpl.java` |

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

## 7. Clés d'erreur i18n associées (récap)

Toutes les clés d'erreur retournées par les règles ci-dessus, dans un seul tableau de référence.

### Transitions de statut
| Clé                                                | Règle violée                                            |
|----------------------------------------------------|---------------------------------------------------------|
| `error.reservation.checkin.invalidStatus`          | Check-in tenté sur statut ≠ CONFIRMEE                   |
| `error.reservation.checkin.tooEarly`               | Check-in avant `dateArrivee`                            |
| `error.reservation.checkout.invalidStatus`         | Check-out tenté sur statut ≠ ARRIVEE                    |
| `error.checkoutExpress.statut.invalid`             | Check-out express tenté sur statut ≠ ARRIVEE            |
| `error.reservation.cancel.alreadyTerminated`       | Annulation tentée sur PARTIE / ANNULEE                  |

### Opérations bloquées sur statut terminal
| Clé                                                | Règle violée                                            |
|----------------------------------------------------|---------------------------------------------------------|
| `error.reservation.update.terminated`              | Update tenté sur PARTIE / ANNULEE / NO_SHOW             |
| `error.reservation.changerChambre.terminated`      | Changement de chambre sur PARTIE / ANNULEE / NO_SHOW    |
| `error.reservation.changerChambre.aucuneChambre`   | Changement de chambre sans pivot existant               |

### Validation métier (hors transitions)
| Clé                                                | Règle violée                                            |
|----------------------------------------------------|---------------------------------------------------------|
| `error.reservation.notFound`                       | ID inconnu ou hors tenant                               |
| `error.reservation.dates.invalid`                  | `dateDepart <= dateArrivee` sur update                  |

### Infrastructure
| Clé                                                | Règle violée                                            |
|----------------------------------------------------|---------------------------------------------------------|
| `error.tenant.missing`                             | Appel d'un service `@RequireTenant` sans `TenantContext`|
