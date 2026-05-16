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

### 3.1 Cibles autorisées par statut courant

| Statut courant   | Cibles autorisées                              |
|------------------|------------------------------------------------|
| `DISPONIBLE`     | `OCCUPEE`, `NETTOYAGE`, `MAINTENANCE`          |
| `OCCUPEE`        | `DISPONIBLE`, `NETTOYAGE`                      |
| `NETTOYAGE`      | `DISPONIBLE`, `MAINTENANCE`                    |
| `MAINTENANCE`    | `DISPONIBLE`, `HORS_SERVICE`                   |
| `HORS_SERVICE`   | `MAINTENANCE` (uniquement)                     |

Règles implicites :
- `actuel == nouveau` → no-op silencieux (`ChambreServiceImpl.java:259-261`).
- Toute autre cible non listée → `BusinessException` avec une clé i18n **résolue par la cible** (cf. §3.3 + §7).

### 3.2 Matrice complète 5×5 avec erreurs retournées

Toute case ❌ produit une `BusinessException` avec la clé indiquée (la clé est mappée par la cible refusée, cf. `ChambreServiceImpl.CLES_ERREUR_PAR_CIBLE` lignes 72-77).

| Depuis ↓ / Vers → | `DISPONIBLE` | `OCCUPEE` | `NETTOYAGE` | `MAINTENANCE` | `HORS_SERVICE` |
|---|---|---|---|---|---|
| **`DISPONIBLE`**    | (no-op) | ✅ | ✅ | ✅ | ❌ `error.chambre.transition.outOfServiceRequiresMaintenance` |
| **`OCCUPEE`**       | ✅ ⚠️ *écart doctrine* | (no-op) | ✅ | ❌ `error.chambre.transition.maintenanceFromOccupied` | ❌ `error.chambre.transition.outOfServiceRequiresMaintenance` |
| **`NETTOYAGE`**     | ✅ | ❌ `error.chambre.transition.toOccupied` | (no-op) | ✅ | ❌ `error.chambre.transition.outOfServiceRequiresMaintenance` |
| **`MAINTENANCE`**   | ✅ | ❌ `error.chambre.transition.toOccupied` | ❌ `error.chambre.transition.invalidToCleaning` | (no-op) | ✅ |
| **`HORS_SERVICE`**  | ❌ `error.chambre.transition.fromOutOfService` | ❌ `error.chambre.transition.toOccupied` | ❌ `error.chambre.transition.invalidToCleaning` | ✅ | (no-op) |

### 3.3 Mécanique de résolution de la clé d'erreur

`checkTransition(actuel, nouveau)` (`ChambreServiceImpl.java:258-268`) :
1. Si `actuel == nouveau` → return silencieux.
2. Sinon recherche `nouveau` dans `TRANSITIONS_AUTORISEES.get(actuel)`.
3. Si refusé → résout la clé via `CLES_ERREUR_PAR_CIBLE.getOrDefault(nouveau, "error.chambre.transition.unknown")` et lève `BusinessException(clé)`.

Cible refusée → clé d'erreur :

| Cible refusée   | Clé i18n levée                                              |
|-----------------|-------------------------------------------------------------|
| `OCCUPEE`       | `error.chambre.transition.toOccupied`                       |
| `DISPONIBLE`    | `error.chambre.transition.fromOutOfService`                 |
| `MAINTENANCE`   | `error.chambre.transition.maintenanceFromOccupied`          |
| `NETTOYAGE`     | `error.chambre.transition.invalidToCleaning`                |
| `HORS_SERVICE`  | `error.chambre.transition.outOfServiceRequiresMaintenance`  |
| *(cible inconnue, sécurité)* | `error.chambre.transition.unknown`             |

> **Note** : les noms de clés portent parfois la source historique (`fromOutOfService`, `maintenanceFromOccupied`) mais la sélection se fait en réalité **par cible**. C'est une dette de nommage assumée pour préserver la compat des assertions de tests (`ReservationServiceTests#T4`).

### ⚠️ Écart code / doctrine à connaître

Le javadoc de `StatutChambre.java` indique : *« `OCCUPEE → DISPONIBLE` sans passer par `NETTOYAGE` est interdit (règle housekeeping) »*.

**La matrice code autorise pourtant `OCCUPEE → DISPONIBLE` directement.** Ce point est explicitement documenté en commentaire dans `ChambreServiceImpl.java:51-53` :

> *« la transition OCCUPEE → DISPONIBLE n'est PAS bloquée par le code actuel même si la doctrine encourage le passage par NETTOYAGE »*

Conséquence : c'est la doctrine d'usage (déclencheurs métier) qui garantit en pratique le passage par `NETTOYAGE` — le `checkOut` met systématiquement la chambre en `NETTOYAGE`, jamais en `DISPONIBLE`.

---

## 4. Déclencheurs des transitions

### 4.1 Module hébergement (transitions "métier")

| Transition | Déclencheur | Erreur propagée si refusée par la matrice | Référence |
|---|---|---|---|
| `DISPONIBLE → OCCUPEE` | `ReservationServiceImpl.checkIn(id)` — appelle `chambreService.changerStatut(chambreId, OCCUPEE)` pour chaque chambre de la résa | `error.chambre.transition.toOccupied` (si chambre en `MAINTENANCE`/`NETTOYAGE`/`HORS_SERVICE`) — propage et bloque le check-in | `ReservationServiceImpl.java:520-523` |
| `OCCUPEE → NETTOYAGE` | `ReservationServiceImpl.checkOut(id)` ou `checkoutExpress(id)` | `error.chambre.transition.invalidToCleaning` (théoriquement impossible : chambre forcément `OCCUPEE` pour pouvoir check-out) | `ReservationServiceImpl.java:546-550` |
| `OCCUPEE → NETTOYAGE` (cas particulier) | `ReservationServiceImpl.cancel(id, motif)` quand la réservation est en `ARRIVEE` au moment de l'annulation | Idem ci-dessus | `ReservationServiceImpl.java:651-655` |

### 4.2 Module ménage (transitions "opérationnelles", Tour 30)

`ChambreStatutListener` réagit à des `@TransactionalEventListener` (phase `AFTER_COMMIT`, `REQUIRES_NEW`) publiés par le module ménage :

| Transition | Event déclencheur | Condition | Erreur si transition refusée | Référence |
|---|---|---|---|---|
| `NETTOYAGE → DISPONIBLE` | `TacheTermineeEvent` avec `TypeNettoyage = QUOTIDIEN` ou `GRAND_MENAGE` | tâche ménage finalisée | `error.chambre.transition.fromOutOfService` (si chambre déjà en autre statut) — **loggée en WARN, non remontée** | `ChambreStatutListener.java:78-80` |
| `MAINTENANCE → DISPONIBLE` | `TacheTermineeEvent` avec `TypeNettoyage = MAINTENANCE` | maintenance finalisée | Idem — **WARN, non remontée** | `ChambreStatutListener.java:81-83` |
| `DISPONIBLE → MAINTENANCE` | `TacheCommenceeEvent` avec `TypeNettoyage = MAINTENANCE` | démarrage d'une intervention (blocage immédiat) | `error.chambre.transition.maintenanceFromOccupied` (si chambre `OCCUPEE`) — **WARN, non remontée** | `ChambreStatutListener.java:100-120` |
| _(no-op)_ | `TacheCommenceeEvent` avec `TypeNettoyage = QUOTIDIEN` ou `GRAND_MENAGE` | chambre déjà `NETTOYAGE` post check-out, rien à faire | n/a | `ChambreStatutListener.java:104-108` |

**Résilience du listener** : `tryChangerStatut` (`ChambreStatutListener.java:143-153`) capture explicitement deux exceptions et les log en `WARN` **sans rethrow** :

| Exception capturée | Cause | Conséquence |
|---|---|---|
| `BusinessException` (avec clé `error.chambre.transition.*`) | Transition refusée par la matrice | Log WARN, la TX du listener commit quand même la fin/début de tâche |
| `ResourceNotFoundException` (`error.chambre.notFound`) | Chambre supprimée entre la pose de l'event et son traitement | Log WARN, idem |

> Toute autre `RuntimeException` (NPE, etc.) reste relayée pour faire échouer la TX `REQUIRES_NEW` — c'est un bug à détecter. La TX d'origine (qui a publié l'event) reste committée car la phase est `AFTER_COMMIT`.

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

| Aspect | Détail |
|---|---|
| **Garde** | `statut != OCCUPEE` |
| **Erreur si violée** | `error.chambre.cannotDeactivateOccupied` |
| **Effets** | `actif = false` **et** `statut = HORS_SERVICE` (forcé, peu importe l'état de départ) |
| **Justification** | Acte d'administration, pas un workflow métier — `checkTransition` est court-circuité |

### 5.2 `reactivate(chambreId)` — `ChambreServiceImpl.java:220-234`

| Aspect | Détail |
|---|---|
| **Garde** | aucune (idempotent : no-op si `actif == true`) |
| **Erreur** | aucune — opération toujours acceptée |
| **Effets** | `actif = true` **et** `statut = DISPONIBLE` (forcé) |
| **Justification** | Commentaire ligne 228-231 : la matrice refuserait `HORS_SERVICE → DISPONIBLE` direct (`error.chambre.transition.fromOutOfService`), mais la réactivation administrative l'autorise explicitement.

---

## 6. Garanties multi-tenant

- L'entité `Chambre` implémente `TenantAware` et porte `@TenantId` sur `hotelId` (`updatable = false`).
- `ChambreServiceImpl` est annoté `@RequireTenant` au niveau classe — tout appel sans `TenantContext` lève `error.tenant.missing`.
- Le listener `ChambreStatutListener` repositionne explicitement `TenantContext` depuis le payload de l'event (`event.hotelId()`) avant d'appeler `changerStatut`, et restaure le contexte précédent en `finally`.
- Hibernate filtre automatiquement toutes les requêtes par `hotel_id` via le `CityTenantIdentifierResolver` (cf. `CLAUDE.md` racine §6.1).

---

## 7. Clés d'erreur i18n associées (récap)

Toutes les clés d'erreur retournées par les règles ci-dessus, dans un seul tableau de référence.

### Transitions de statut (résolues par la cible refusée)
Définies dans `ChambreServiceImpl.CLES_ERREUR_PAR_CIBLE` (`ChambreServiceImpl.java:72-77`).

| Clé                                                          | Règle violée                                                |
|--------------------------------------------------------------|-------------------------------------------------------------|
| `error.chambre.transition.toOccupied`                        | Cible `OCCUPEE` depuis `NETTOYAGE` / `MAINTENANCE` / `HORS_SERVICE` |
| `error.chambre.transition.fromOutOfService`                  | Cible `DISPONIBLE` depuis `HORS_SERVICE`                    |
| `error.chambre.transition.maintenanceFromOccupied`           | Cible `MAINTENANCE` depuis `OCCUPEE`                        |
| `error.chambre.transition.invalidToCleaning`                 | Cible `NETTOYAGE` depuis `MAINTENANCE` / `HORS_SERVICE`     |
| `error.chambre.transition.outOfServiceRequiresMaintenance`   | Cible `HORS_SERVICE` depuis `DISPONIBLE` / `OCCUPEE` / `NETTOYAGE` |
| `error.chambre.transition.unknown`                           | Fallback (cible non listée dans la table) — théoriquement inatteignable |

### Court-circuits administratifs
| Clé                                       | Règle violée                                  |
|-------------------------------------------|-----------------------------------------------|
| `error.chambre.cannotDeactivateOccupied`  | `deactivate()` sur chambre en `OCCUPEE`       |

### Validation à la création / mise à jour
| Clé                                       | Règle violée                                  |
|-------------------------------------------|-----------------------------------------------|
| `error.chambre.numero.alreadyExists`      | Numéro déjà pris dans l'hôtel (unicité)       |
| `error.chambre.type.inactive`             | `TypeChambre` désactivé                       |
| `error.typeChambre.notFound`              | `typeId` inexistant ou hors tenant            |

### Recherche / lecture
| Clé                                       | Règle violée                                  |
|-------------------------------------------|-----------------------------------------------|
| `error.chambre.notFound`                  | ID inconnu ou hors tenant                     |
| `error.disponibilite.dates.required`      | `findDisponibles` appelé sans dates           |
| `error.disponibilite.dates.invalid`       | `dateFin <= dateDebut`                        |

### Infrastructure
| Clé                                       | Règle violée                                                |
|-------------------------------------------|-------------------------------------------------------------|
| `error.tenant.missing`                    | Appel d'un service `@RequireTenant` sans `TenantContext`    |

---

## 8. Points d'attention pour les évolutions futures

1. **Aligner code et doctrine sur `OCCUPEE → DISPONIBLE`** : soit retirer cette cible de `TRANSITIONS_AUTORISEES`, soit mettre à jour le javadoc de `StatutChambre`. À traiter dans un tour de cleanup.
2. **Toute nouvelle source de transition de statut chambre** (ex. import OTA, batch night audit chambres) doit passer par `chambreService.changerStatut(...)` pour respecter la matrice — ne **jamais** faire `chambre.setStatut(...) + save()` directement.
3. **Évolution de la matrice** : modifier `TRANSITIONS_AUTORISEES` et `CLES_ERREUR_PAR_CIBLE` ensemble, et mettre à jour les tests qui assertent sur les clés (`ReservationServiceTests#T4` notamment).
