# Règles de transition — Statuts de facture

> Source de vérité : code backend `citybackend/`.
> - `entity/finance/StatutFacture.java` (javadoc doctrinal)
> - `entity/finance/TypeFacture.java` (FACTURE / AVOIR / PROFORMA / FACTURE_FOURNISSEUR)
> - `service/finance/FactureServiceImpl.java` (création, émission, annulation, transfert de lignes, addLigneService)
> - `service/finance/PaiementServiceImpl.java` (transitions vers PARTIELLEMENT_PAYEE / PAYEE via affectation)
> - `service/finance/ReservationFinanceServiceImpl.java` (transition PAYEE via check-out express / transfert société)
>
> Doctrine produit (Tour 20, 2026-05-07) : City Hotel = **comptabilité auxiliaire client uniquement**. La comptabilité générale (SYSCOHADA) est externalisée vers Dolibarr (cf. `CLAUDE.md` racine §6.2).
>
> Dernière mise à jour : 2026-05-15.

---

## 1. Les 5 statuts possibles

| Statut                | Sens métier                                                                       | Terminal ? |
|-----------------------|-----------------------------------------------------------------------------------|------------|
| `BROUILLON`           | Facture créée, modifiable, **aucune** écriture comptable n'a été générée          | non        |
| `EMISE`               | Facture validée, transmise au client, écriture VTE générée. **Plus modifiable**  | non        |
| `PARTIELLEMENT_PAYEE` | Au moins un paiement affecté, `montantPaye < montantTtc`                          | non        |
| `PAYEE`               | `montantPaye >= montantTtc`                                                       | **oui**    |
| `ANNULEE`             | Annulation (sans paiement). La facture reste en base pour audit                   | **oui**    |

> **Pas de delete physique** d'une facture. Toute annulation se traduit par `ANNULEE` + contre-passation de l'écriture VTE si elle existait.

---

## 2. État initial à la création

Toute nouvelle facture naît en **`BROUILLON`**, peu importe la source. Quatre chemins de création :

| Méthode | Résultat final |
|---|---|
| `FactureServiceImpl.create(dto)` (`FactureServiceImpl.java:213`) | reste en `BROUILLON` — émission à la main via `emettre(id)` |
| `FactureServiceImpl.fromReservation(reservationId)` (`FactureServiceImpl.java:366` puis `:429`) | `BROUILLON` puis **transition auto vers `EMISE`** dans la même transaction |
| `FactureServiceImpl.fromCommande(...)` (`FactureServiceImpl.java:481` puis `:521`) | `BROUILLON` → `EMISE` auto |
| `FactureServiceImpl.fromBonCommande(...)` (`FactureServiceImpl.java:573` puis `:597`) | `BROUILLON` → `EMISE` auto (facture fournisseur) |

Numérotation : `numerotationService.next(TypeNumerotation)` — séquence pessimiste par hôtel + exercice (`FACT-2026-MR-000001`, `AVOIR-2026-MR-000001`, etc.). Pas de trou possible.

---

## 3. Matrice des transitions autorisées

| Départ → Arrivée | Déclencheur | Garde / précondition | Effets de bord |
|---|---|---|---|
| **BROUILLON → EMISE** | `emettre(factureId)` (`FactureServiceImpl.java:255`) **OU** automatique en fin de `fromReservation` / `fromCommande` / `fromBonCommande` | `statut == BROUILLON` (sinon `error.facture.emission.statutInvalide` — idempotence anti-doublon d'écriture) | (1) `recordDebitOnAccountIfApplicable` : **DEBIT** compte auxiliaire client (Tour 22.1) si `TypeFacture.FACTURE` ET `clientId != null`. (2) `ecritureGenerationService.emettreEcritureFacture` génère l'écriture VTE (`411xxx` D / `706xxx` C) **uniquement pour `TypeFacture.FACTURE`** (pas AVOIR). Si échec (exercice clos…), TX rollback → reste `BROUILLON`. (3) `setEcritureEmissionId(ecritureId)` |
| **BROUILLON → ANNULEE** | `annuler(factureId)` (`FactureServiceImpl.java:294`) | `statut != ANNULEE` (sinon `error.facture.dejaAnnulee`) ET `statut != PARTIELLEMENT_PAYEE` / `PAYEE` (sinon `error.facture.annulation.statutInvalide`) ET `montantPaye == 0` (sinon `error.facture.annulation.dejaPayee`) | Pas d'écriture à contre-passer (BROUILLON n'en a jamais généré). Audit `AuditFinanceAction("FACTURE_ANNULATION")`. |
| **EMISE → ANNULEE** | `annuler(factureId)` | Identique. **Doctrine** : `ANNULEE` autorisé uniquement si **aucun paiement** n'a encore été affecté. | `ecritureComptableService.contrePasser(ecritureEmissionId)` sur **exercice ouvert courant** (cf. `FactureServiceImpl.java:314-318`). |
| **EMISE → PARTIELLEMENT_PAYEE** | `PaiementServiceImpl.affecterUneAffectation(...)` (`PaiementServiceImpl.java:250`) | `statut ∈ {EMISE, PARTIELLEMENT_PAYEE}` (sinon `error.paiement.facture.statutInvalide`) ET `montant <= montantRestant + 0.01` (sinon `error.paiement.depasseMontantRestant`). Si `ligneFactureId` fourni, il doit appartenir à la facture (`error.paiement.ligne.factureMismatch`). | Création `AffectationPaiement`. Mise à jour `montantPaye`. **CREDIT** compte auxiliaire client (Tour 22.1) si `TypeFacture.FACTURE` + `clientId` présent. Transition décidée par : `nouveauPaye >= montantTtc` → `PAYEE`, sinon → `PARTIELLEMENT_PAYEE` (`PaiementServiceImpl.java:280-283`). |
| **EMISE → PAYEE** | Idem (paiement intégral immédiat) | `nouveauPaye >= montantTtc` | Mêmes effets que ci-dessus. |
| **EMISE → PAYEE** (cas particulier) | `ReservationFinanceServiceImpl` lors d'un check-out express avec transfert vers compte société (`ReservationFinanceServiceImpl.java:199-202`) | Le transfert est assimilé à un encaissement intégral | `DEBIT` compte société + `CREDIT` compte client (si rattaché) du montant restant. `setMontantPaye(montantPaye + restant)`. |
| **PARTIELLEMENT_PAYEE → PAYEE** | `PaiementServiceImpl.affecterUneAffectation(...)` | `nouveauPaye >= montantTtc` | Idem affectation. |

---

## 4. États terminaux et règle de l'AVOIR

`PAYEE` et `ANNULEE` n'ont **aucune transition sortante**.

`PARTIELLEMENT_PAYEE` et `PAYEE` **ne peuvent jamais être annulées** (`annuler()` les refuse explicitement) : la doctrine impose d'émettre un **AVOIR** (`TypeFacture.AVOIR`) référençant la facture originale via `factureReferenceId`. C'est documenté en clair dans `StatutFacture.java:20-22` :

> *« Une facture déjà `PARTIELLEMENT_PAYEE` ou `PAYEE` ne peut être annulée : elle doit donner lieu à un AVOIR. »*

L'AVOIR a sa propre numérotation (`TypeNumerotation.AVOIR`, ex. `AVOIR-2026-MR-000001`) et son propre flow comptable inverse (contre-passation explicite, prévue Tour finance-2).

---

## 5. Refus en cascade : opérations bloquées sur statut terminal

Les services métier appliquent des gardes supplémentaires liées aux statuts pour préserver l'intégrité comptable :

| Opération | Statuts refusés | Clé d'erreur | Référence |
|---|---|---|---|
| `addLigneService(...)` (ajout d'un service hôtelier à une facture) | `PAYEE`, `ANNULEE` | `error.facture.statut.cloturee` | `FactureServiceImpl.java:906-909` |
| `transfererLignes(...)` — facture cible | `PAYEE`, `ANNULEE` | `error.facture.transfert.factureCibleTerminated` | `FactureServiceImpl.java:776` |
| `transfererLignes(...)` — toute facture source | `PAYEE`, `ANNULEE` | `error.facture.transfert.factureSourceTerminated` | `FactureServiceImpl.java:805` |
| `transfererLignes(...)` — ligne déjà payée | n/a (vérif sur affectation) | `error.facture.transfert.lignePayee` | `FactureServiceImpl.java:794-796` |
| `affecterUneAffectation(...)` (paiement) | tout sauf `EMISE` et `PARTIELLEMENT_PAYEE` | `error.paiement.facture.statutInvalide` | `PaiementServiceImpl.java:253-256` |
| `fromReservation(reservationId)` | toute réservation **déjà** facturée | `error.facture.reservation.dejaFacturee` | `FactureServiceImpl.java:334-336` |

---

## 6. Schéma synthétique

```
                          (emettre / fromReservation auto / fromCommande auto / fromBonCommande auto)
CREATE -> BROUILLON --------------------------------------------------------------> EMISE
              |                                                                       |
              |                                                                       |
              | (annuler, montantPaye=0)                          (affecter paiement) |
              |                                                                       |
              v                                                  paiement < TTC --> PARTIELLEMENT_PAYEE
           ANNULEE                                               paiement >= TTC --> PAYEE
                                                                                       ^
                                                                                       |
                                                          (affecter paiement complementaire)
                                                                                       |
                                                                       PARTIELLEMENT_PAYEE --> PAYEE
                                                                                       ^
                                                                                       |
                                              (check-out express : transfert societe assimile encaissement)
                                                                                EMISE --> PAYEE

  Etats terminaux : PAYEE, ANNULEE
  PARTIELLEMENT_PAYEE / PAYEE -> ANNULEE INTERDIT : emettre un AVOIR (TypeFacture.AVOIR) a la place.
```

---

## 7. Articulation avec la comptabilité (Tour 22.1 + Tour 19/20)

### 7.1 À l'émission (`BROUILLON → EMISE`)
1. **Auxiliaire client** (City Hotel local) : `DEBIT` sur le compte du client si `TypeFacture.FACTURE` ET `clientId != null`. No-op pour AVOIR / facture cash anonyme / facture fournisseur (via `fournisseurId`).
2. **Écriture VTE** (City Hotel local) : `ecritureGenerationService.emettreEcritureFacture` génère `411xxx D / 706xxx C` **uniquement pour `TypeFacture.FACTURE`**. AVOIR : traité dans Bloc B2 (écriture inverse explicite, différé Tour finance-2).
3. **Atomicité** : si la génération d'écriture échoue (exercice clos, compte invalide…), la TX rollback et la facture reste `BROUILLON`.

### 7.2 À l'affectation d'un paiement
- Affectation possible **uniquement** sur facture `EMISE` ou `PARTIELLEMENT_PAYEE` (anti double-pose d'écriture).
- `CREDIT` proportionnel sur le compte auxiliaire client.
- Recalcul `montantPaye` + bascule de statut selon comparaison à `montantTtc`.

### 7.3 À l'annulation (`{BROUILLON, EMISE} → ANNULEE`)
- Refus si paiement déjà affecté.
- Si la facture avait une `ecritureEmissionId` (cas `EMISE`), `ecritureComptableService.contrePasser(...)` génère une écriture inverse **sur l'exercice ouvert courant** (pas sur l'exercice d'origine si celui-ci est clos).

### 7.4 Externalisation Dolibarr (à venir, cf. CLAUDE.md racine §6.2)
- City Hotel pousse Facture + Paiement vers Dolibarr via Feign (`DolibarrSyncService` à implémenter).
- Statut sync : `PENDING` / `SYNCED` / `FAILED` avec retry Resilience4j.
- Le Plan Comptable Général (PCG) est tenu **uniquement** côté Dolibarr.

---

## 8. Garanties multi-tenant

- L'entité `Facture` implémente `TenantAware` et porte `@TenantId` sur `hotelId` (`updatable = false`).
- `FactureServiceImpl` et `PaiementServiceImpl` sont annotés `@RequireTenant` au niveau classe.
- Hibernate filtre automatiquement toutes les requêtes par `hotel_id` via le `CityTenantIdentifierResolver`.
- La numérotation `FACT-2026-MR-XXXXXX` inclut le code hôtel — séquence pessimiste **par hôtel et par exercice** (cf. `CLAUDE.md` racine §6.2). Aucun risque de collision cross-tenant.
- Toute requête `findByReservationId(...)`, `findByFournisseurId(...)`, etc. est implicitement filtrée tenant.

---

## 9. Clés d'erreur i18n associées

### Création / lecture
| Clé | Contexte |
|---|---|
| `error.facture.notFound` | ID inconnu ou hors tenant |
| `error.facture.reservation.dejaFacturee` | `fromReservation` sur réservation déjà rattachée |
| `error.facture.reservation.aucuneNuiteeAFacturer` | Aucune nuitée `CONSOMMEE` non facturée |

### Émission
| Clé | Contexte |
|---|---|
| `error.facture.emission.statutInvalide` | `emettre()` sur statut ≠ BROUILLON |

### Annulation
| Clé | Contexte |
|---|---|
| `error.facture.dejaAnnulee` | `annuler()` sur facture déjà ANNULEE |
| `error.facture.annulation.statutInvalide` | `annuler()` sur PARTIELLEMENT_PAYEE ou PAYEE |
| `error.facture.annulation.dejaPayee` | `annuler()` avec `montantPaye > 0` (cas de bord) |

### Modification structurelle
| Clé | Contexte |
|---|---|
| `error.facture.statut.cloturee` | `addLigneService` sur PAYEE / ANNULEE |
| `error.facture.transfert.factureCibleTerminated` | Transfert vers facture PAYEE / ANNULEE |
| `error.facture.transfert.factureSourceTerminated` | Transfert depuis facture PAYEE / ANNULEE |
| `error.facture.transfert.lignePayee` | Transfert d'une ligne déjà payée |
| `error.facture.transfert.requestRequired` / `.lignesRequired` / `.factureCibleRequired` | Payload invalide |

### Paiement / affectation
| Clé | Contexte |
|---|---|
| `error.paiement.facture.statutInvalide` | Affectation sur statut ≠ EMISE / PARTIELLEMENT_PAYEE |
| `error.paiement.depasseMontantRestant` | Montant affecté > restant + 1 centime |
| `error.paiement.ligne.factureMismatch` | `ligneFactureId` n'appartient pas à la facture |

---

## 10. Points d'attention pour les évolutions futures

1. **AVOIR comptable** (Tour finance-2) : implémenter `emettreEcritureAvoir` pour générer l'écriture inverse à l'émission d'un AVOIR (actuellement skip explicite dans `emettre()`).
2. **`recalcMontantsFacture` ne touche pas au statut** malgré ce que suggère le javadoc de `FactureRecalcInternalService` ("ajuste éventuellement le statut") — c'est `PaiementServiceImpl` qui décide. Doc à clarifier ou implémentation à compléter dans un cleanup.
3. **PROFORMA** : valeur de `TypeFacture` mais hors scope Tour 19 — workflow à définir si besoin futur (devis valant facture, pas d'écriture comptable).
4. **Bridge Dolibarr** : la doctrine multi-tenant Dolibarr (1 instance par hôtel vs 1 instance partagée + ventilation analytique) est à acter au tour bridge.
5. **Aucun statut intermédiaire entre EMISE et la première affectation** : si on devait introduire une notion d'« en relance » ou « en contentieux », ce serait un statut additif sans bouleverser la matrice.
