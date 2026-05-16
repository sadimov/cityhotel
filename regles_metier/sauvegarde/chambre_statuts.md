# Règles de transition — Statuts de chambre

> Source de vérité : code backend `citybackend/`.
> - `entity/hebergement/StatutChambre.java` (javadoc doctrinal)
> - `service/hebergement/ChambreServiceImpl.java` (matrice effective `TRANSITIONS_AUTORISEES`, refactor Tour 40bis H6)
> - `service/hebergement/ReservationServiceImpl.java` (déclencheurs check-in / check-out / cancel)
> - `service/menage/ChambreStatutListener.java` (déclencheurs ménage et maintenance, Tour 30)
>
> Dernière mise à jour : 2026-05-15.

---

## 1. Les 5 statuts possibles

| Statut         | Sens métier                                                          |
|----------------|----------------------------------------------------------------------|
| `DISPONIBLE`   | Chambre libre, vendable, prête à accueillir un client                |
| `OCCUPEE`      | Client check-in, séjour en cours                                     |
| `NETTOYAGE`    | Post check-out, en attente de ménage                                 |
| `MAINTENANCE`  | Intervention technique programmée                                    |
| `HORS_SERVICE` | Indisponibilité de durée indéterminée (gros défaut, fermeture, etc.) |

> Aucun de ces 5 statuts n'est terminal au sens "définitif" — toute chambre peut potentiellement revenir à `DISPONIBLE`. La seule "sortie" est la désactivation administrative (`actif = false`).

---

## 2. État initial à la création

`ChambreServiceImpl.create(...)` force `statut = DISPONIBLE` si le DTO ne fournit pas de statut explicite (`ChambreServiceImpl.java:109-111`). `actif = true` par défaut.

---

## 3. Matrice des transitions autorisées

Définie dans `ChambreServiceImpl.TRANSITIONS_AUTORISEES` (Map<StatutChambre, Set<StatutChambre>>, Tour 40bis refactor H6, `ChambreServiceImpl.java:55-65`).

| Statut courant   | Cibles autorisées                              |
|------------------|------------------------------------------------|
| `DISPONIBLE`     | `OCCUPEE`, `NETTOYAGE`, `MAINTENANCE`          |
| `OCCUPEE`        | `DISPONIBLE`, `NETTOYAGE`                      |
| `NETTOYAGE`      | `DISPONIBLE`, `MAINTENANCE`                    |
| `MAINTENANCE`    | `DISPONIBLE`, `HORS_SERVICE`                   |
| `HORS_SERVICE`   | `MAINTENANCE` (uniquement)                     |

Règles implicites :
- `actuel == nouveau` → no-op silencieux (`ChambreServiceImpl.java:259-261`).
- Toute autre cible non listée → `BusinessException` avec clé i18n par cible (cf. §7).

### ⚠️ Écart code / doctrine à connaître

Le javadoc de `StatutChambre.java` indique : *« `OCCUPEE → DISPONIBLE` sans passer par `NETTOYAGE` est interdit (règle housekeeping) »*.

**La matrice code autorise pourtant `OCCUPEE → DISPONIBLE` directement.** Ce point est explicitement documenté en commentaire dans `ChambreServiceImpl.java:51-53` :

> *« la transition OCCUPEE → DISPONIBLE n'est PAS bloquée par le code actuel même si la doctrine encourage le passage par NETTOYAGE »*

Conséquence : c'est la doctrine d'usage (déclencheurs métier) qui garantit en pratique le passage par `NETTOYAGE` — le `checkOut` met systématiquement la chambre en `NETTOYAGE`, jamais en `DISPONIBLE`.

---

## 4. Déclencheurs des transitions

### 4.1 Module hébergement (transitions "métier")

| Transition | Déclencheur | Référence |
|---|---|---|
| `DISPONIBLE → OCCUPEE` | `ReservationServiceImpl.checkIn(id)` — appelle `chambreService.changerStatut(chambreId, OCCUPEE)` pour chaque chambre de la résa | `ReservationServiceImpl.java:520-523` |
| `OCCUPEE → NETTOYAGE` | `ReservationServiceImpl.checkOut(id)` ou `checkoutExpress(id)` | `ReservationServiceImpl.java:546-550` |
| `OCCUPEE → NETTOYAGE` (cas particulier) | `ReservationServiceImpl.cancel(id, motif)` quand la réservation est en `ARRIVEE` au moment de l'annulation | `ReservationServiceImpl.java:651-655` |

### 4.2 Module ménage (transitions "opérationnelles", Tour 30)

`ChambreStatutListener` réagit à des `@TransactionalEventListener` (phase `AFTER_COMMIT`, `REQUIRES_NEW`) publiés par le module ménage :

| Transition | Event déclencheur | Condition | Référence |
|---|---|---|---|
| `NETTOYAGE → DISPONIBLE` | `TacheTermineeEvent` avec `TypeNettoyage = QUOTIDIEN` ou `GRAND_MENAGE` | tâche ménage finalisée | `ChambreStatutListener.java:78-80` |
| `MAINTENANCE → DISPONIBLE` | `TacheTermineeEvent` avec `TypeNettoyage = MAINTENANCE` | maintenance finalisée | `ChambreStatutListener.java:81-83` |
| `DISPONIBLE → MAINTENANCE` | `TacheCommenceeEvent` avec `TypeNettoyage = MAINTENANCE` | démarrage d'une intervention (blocage immédiat) | `ChambreStatutListener.java:100-120` |
| _(no-op)_ | `TacheCommenceeEvent` avec `TypeNettoyage = QUOTIDIEN` ou `GRAND_MENAGE` | chambre déjà `NETTOYAGE` post check-out, rien à faire | `ChambreStatutListener.java:104-108` |

**Résilience du listener** : `tryChangerStatut` capture `BusinessException` (transition refusée par la matrice) et `ResourceNotFoundException` (chambre supprimée entre-temps) en `WARN` sans rethrow. La TX du listener (`REQUIRES_NEW`) commit indépendamment de la TX qui a publié l'event : un échec ici n'invalide pas la fin/début de tâche ménage. Toute autre `RuntimeException` reste relayée (bug à détecter).

**Sauvegarde TenantContext / MDC** : le listener tourne sur le même thread que l'appelant en mode synchrone. Il snapshot/restore `TenantContext` et `MDC["hotel_id"]` pour ne pas écraser le contexte de l'appelant (`ChambreStatutListener.java:70-95`).

### 4.3 Synthèse circuit nominal

```
   reservation.checkIn         reservation.checkOut         Tache QUOTIDIEN/GRAND_MENAGE TERMINEE
DISPONIBLE -----------> OCCUPEE -------------> NETTOYAGE -----------------------------> DISPONIBLE
                                                                                            |
                                                          Tache MAINTENANCE COMMENCEE       |
                                                          <-------------------------- (depuis DISPONIBLE)
                                                                MAINTENANCE
                                                                    |
                                                          (Tache MAINTENANCE TERMINEE)
                                                                    v
                                                                DISPONIBLE
                                                                    |
                                                              passage direct admin
                                                                    v
                                                              HORS_SERVICE
                                                                    ^
                                                            MAINTENANCE <----- (retour obligatoire)
```

---

## 5. Cas particuliers (court-circuits administratifs)

Deux opérations administratives **ignorent la matrice `checkTransition`** et forcent un statut :

### 5.1 `deactivate(chambreId)` — `ChambreServiceImpl.java:207-216`

- **Garde** : refuse si `statut == OCCUPEE` → `error.chambre.cannotDeactivateOccupied`.
- **Effets** : `actif = false` **et** `statut = HORS_SERVICE` (forcé, peu importe l'état de départ).
- Justification : acte d'administration, pas un workflow métier.

### 5.2 `reactivate(chambreId)` — `ChambreServiceImpl.java:220-234`

- **Idempotent** : no-op si `actif == true`.
- **Effets** : `actif = true` **et** `statut = DISPONIBLE` (forcé).
- Justification explicite (commentaire ligne 228-231) : la matrice refuserait `HORS_SERVICE → DISPONIBLE` direct, mais la réactivation administrative l'autorise.

---

## 6. Garanties multi-tenant

- L'entité `Chambre` implémente `TenantAware` et porte `@TenantId` sur `hotelId` (`updatable = false`).
- `ChambreServiceImpl` est annoté `@RequireTenant` au niveau classe — tout appel sans `TenantContext` lève `error.tenant.missing`.
- Le listener `ChambreStatutListener` repositionne explicitement `TenantContext` depuis le payload de l'event (`event.hotelId()`) avant d'appeler `changerStatut`, et restaure le contexte précédent en `finally`.
- Hibernate filtre automatiquement toutes les requêtes par `hotel_id` via le `CityTenantIdentifierResolver` (cf. `CLAUDE.md` racine §6.1).

---

## 7. Clés d'erreur i18n associées

Définies dans `ChambreServiceImpl.CLES_ERREUR_PAR_CIBLE` (`ChambreServiceImpl.java:72-77`), résolues par la cible de la transition refusée :

| Clé                                                          | Cible refusée    |
|--------------------------------------------------------------|------------------|
| `error.chambre.transition.toOccupied`                        | `OCCUPEE`        |
| `error.chambre.transition.fromOutOfService`                  | `DISPONIBLE`     |
| `error.chambre.transition.maintenanceFromOccupied`           | `MAINTENANCE`    |
| `error.chambre.transition.invalidToCleaning`                 | `NETTOYAGE`      |
| `error.chambre.transition.outOfServiceRequiresMaintenance`   | `HORS_SERVICE`   |
| `error.chambre.transition.unknown`                           | fallback         |

Autres clés métier de `ChambreServiceImpl` :

| Clé                                       | Contexte                                  |
|-------------------------------------------|-------------------------------------------|
| `error.chambre.notFound`                  | ID inconnu ou hors tenant                 |
| `error.chambre.numero.alreadyExists`      | Numéro déjà pris dans l'hôtel             |
| `error.chambre.type.inactive`             | `TypeChambre` désactivé                   |
| `error.chambre.cannotDeactivateOccupied`  | `deactivate()` sur chambre en `OCCUPEE`   |
| `error.typeChambre.notFound`              | `typeId` inexistant ou hors tenant        |
| `error.disponibilite.dates.required`      | `findDisponibles` sans dates              |
| `error.disponibilite.dates.invalid`       | `dateFin <= dateDebut`                    |

---

## 8. Points d'attention pour les évolutions futures

1. **Aligner code et doctrine sur `OCCUPEE → DISPONIBLE`** : soit retirer cette cible de `TRANSITIONS_AUTORISEES`, soit mettre à jour le javadoc de `StatutChambre`. À traiter dans un tour de cleanup.
2. **Toute nouvelle source de transition de statut chambre** (ex. import OTA, batch night audit chambres) doit passer par `chambreService.changerStatut(...)` pour respecter la matrice — ne **jamais** faire `chambre.setStatut(...) + save()` directement.
3. **Évolution de la matrice** : modifier `TRANSITIONS_AUTORISEES` et `CLES_ERREUR_PAR_CIBLE` ensemble, et mettre à jour les tests qui assertent sur les clés (`ReservationServiceTests#T4` notamment).
