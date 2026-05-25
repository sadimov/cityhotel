# Cycle de vie d'une réservation — City Hotel

> Guide utilisateur — Hôtel multi-tenant SaaS
> Public visé : réceptionniste, gérant, administrateur d'hôtel.

---

## 1. Vue d'ensemble du cycle de vie

Une réservation traverse 5 statuts possibles. Le chemin nominal est en gras.

```
                                ┌──────────┐
                                │ CONFIRMEE│  ← création
                                └────┬─────┘
                                     │ check-in
                                     ▼
                                ┌──────────┐
                                │  ARRIVEE │  ← client présent dans l'hôtel
                                └────┬─────┘
                                     │ check-out
                                     ▼
                                ┌──────────┐
                                │  PARTIE  │  ← séjour terminé (état final nominal)
                                └──────────┘

Branches d'exception (à tout moment AVANT check-out) :
  CONFIRMEE / ARRIVEE  ──── annulation manuelle ────► ANNULEE
  CONFIRMEE            ──── night audit (midi UTC) ──► NO_SHOW (arrivée non honorée)
```

### Numérotation automatique

Chaque réservation reçoit un numéro unique généré côté serveur, scopé par
hôtel et par exercice :

```
RES-{exercice}-{codePays}-{6 chiffres}
RES-2026-MR-000123
```

Pas d'intercalaire, pas de trou. La séquence est protégée par un lock
pessimiste BD pour éviter les collisions sous concurrence.

---

## 2. Créer une réservation

### 2.1 Méthode 1 — Depuis le calendrier (la plus rapide)

1. Aller dans **Hébergement → Calendrier** (`/hebergement/calendar`).
2. Repérer la **ligne de la chambre** souhaitée et la **date d'arrivée**.
3. **Cliquer-glisser** depuis la cellule "date d'arrivée" jusqu'à la veille
   du jour de départ. La sélection s'affiche en surbrillance.
4. La **modale "Nouvelle réservation"** s'ouvre automatiquement.
5. Remplir les champs obligatoires :
   - **Client principal** : taper le nom dans le champ de recherche, ou
     cliquer sur *"+ Nouveau client"* pour ouvrir le quick-create.
   - **Société** (optionnel, pour le B2B) : idem avec quick-create
     possible via le bouton *"+ Nouvelle société"*.
   - **Dates d'arrivée et de départ** : déjà pré-remplies par la sélection
     calendrier, modifiables.
   - **Nombre d'adultes / d'enfants**.
6. Champs optionnels :
   - Motif du séjour (loisirs, affaires, ...).
   - Canal de distribution (Direct, Booking.com, agence, ...).
   - Commentaires libres.
   - Réduction en %.
7. Cliquer sur **"Créer la réservation"**.

Le numéro de réservation est généré et la réservation apparaît en rouge
(statut `CONFIRMEE`) sur le calendrier.

#### Quick-create client embarqué

Si le client n'existe pas, le panel quick-create dans la modale demande :
prénom, nom, téléphone, email, pièce d'identité, **nationalité** (champ
filtrable parmi ~249 pays ISO 3166-1 — taper "France", "Mauritanie",
"Sénégal", etc.).

Le client créé est automatiquement sélectionné dans la réservation.

#### Quick-create société

Pour les réservations payées par une société (notes de frais, contrat
B2B) : nom, NIF, contact, adresse. Idem auto-sélectionnée.

### 2.2 Méthode 2 — Depuis le menu Réservations

1. **Hébergement → Réservations → Nouvelle réservation**
   (`/hebergement/reservations/new`).
2. Formulaire complet, plus structuré (utile pour les saisies "à la main"
   sans contexte calendrier).
3. Sélection des chambres dans une liste paginée.

### Rôles autorisés à créer

`SUPERADMIN`, `ADMIN`, `GERANT`, `RECEPTION`, `RESREC`.

---

## 3. Modifier une réservation

### 3.1 Modification rapide (depuis le calendrier)

1. **Clic droit** sur la réservation dans le calendrier → menu contextuel.
2. Cliquer sur **"Modifier"**.
3. La modale "Modifier la réservation" s'ouvre.
4. Champs éditables :
   - Client principal et société (changement possible).
   - Dates d'arrivée / départ.
   - Nombre d'adultes / d'enfants.
   - Motif, commentaires, réduction, canal.
5. Cliquer sur **"Enregistrer"**.

**Sémantique partial** : seuls les champs réellement modifiés sont envoyés
au serveur. Si vous ne touchez pas à la date d'arrivée, elle ne sera pas
re-validée.

### 3.2 Changer de chambre (action dédiée)

Le changement de chambre n'est **pas** dans le formulaire de modification
standard — il a sa propre action pour traçabilité.

1. **Clic droit** sur la réservation → **"Modifier"**.
2. Dans la modale, cliquer sur le bouton **"Changer chambre"**.
3. Sélectionner la nouvelle chambre dans la liste.
4. Saisir la **raison** du changement (obligatoire).
5. Confirmer.

Le système vérifie :
- Absence de conflit sur la nouvelle chambre pour la période.
- Statut de la réservation autorise la transition.

Effet :
- Ancienne chambre libérée (`NETTOYAGE` ou `DISPONIBLE` selon contexte).
- Nouvelle chambre marquée `OCCUPEE` si réservation déjà `ARRIVEE`.
- Pivot `ReservationChambre` mis à jour, nuitées non facturées
  réaffectées, trace dans `commentaires`.

### 3.3 Modifier les nuitées (montants individuels)

Pour ajuster le prix d'une ou plusieurs nuits (geste commercial, erreur
de tarif…) :

1. **Clic droit** sur la réservation → **"Modifier les nuitées"**.
2. Tableau ligne par ligne des nuits, avec colonne montant éditable.
3. Modifier les valeurs, cliquer sur **"Enregistrer"**.

**Bloqué si** : facture déjà émise sur ces nuitées (cohérence comptable).

### Rôles autorisés à modifier

`SUPERADMIN`, `ADMIN`, `GERANT`, `RECEPTION`, `RESREC` (lecture étendue).

---

## 4. Check-in (arrivée du client)

Deux entrées possibles.

### 4.1 Depuis le calendrier

1. **Clic droit** sur la réservation → **"Check-in"**.
2. SweetAlert de confirmation → cliquer sur **"Check-in"**.

### 4.2 Depuis la page Check-in

1. **Hébergement → Check-in / Check-out** (`/hebergement/check-in`).
2. Taper le numéro de réservation ou le nom du client.
3. Sélectionner la réservation dans les résultats.
4. Cliquer sur **"Confirmer le check-in"**.

### Effets serveur

- **Statut** : `CONFIRMEE` → `ARRIVEE`.
- **Chambres** : `DISPONIBLE` → `OCCUPEE`.
- **Nuitées** : créées en statut `PREVUE` (consommées au check-out).

### Rôles autorisés

`SUPERADMIN`, `ADMIN`, `GERANT`, `RECEPTION`, `RESREC`, `NIGHTAUDIT`
(le night audit peut finaliser les arrivées tardives).

---

## 5. Gérer les paiements

Pendant le séjour (statut `ARRIVEE`), plusieurs encaissements possibles.

### Modes de paiement supportés (12)

| Mode | Description |
|---|---|
| `ESPECES` | Espèces |
| `CHEQUE` | Chèque |
| `CARTE_BANCAIRE` | Carte bancaire (TPE) |
| `VIREMENT` | Virement bancaire |
| `BANKILY` | Mobile money — Bankily (Banque Mauritanienne) |
| `MASRIVI` | Mobile money — MASRIVI |
| `SEDAD` | Mobile money — SEDAD |
| `CLICK` | Mobile money — CLICK |
| `AMANETY` | Mobile money — AMANETY |
| `BFI_CASH` | BFI Cash |
| `MOOV_MONEY` | Moov Money |
| `GAZAPAY` | GazaPay |

### Modale Paiements (depuis le calendar)

1. **Clic droit** sur la réservation → **"Paiements"**.
2. La modale 3 sections s'ouvre :
   - **Détails réservation** (chambre, dates, montants).
   - **Folio** : liste factures + paiements consolidée.
   - **Lignes facturées** : tableau par ligne avec sélection.
3. Actions possibles :
   - **Paiement global** : encaissement libre, ventilé automatiquement.
   - **Paiement par ligne** : sélectionner les lignes, encaisser le total.
   - **Transférer des lignes** sur une autre facture/compte.

### Devise

**MRU (Ouguiya mauritanienne)** sur toute l'application.

### Rôles autorisés (lecture/écriture paiements)

`SUPERADMIN`, `ADMIN`, `GERANT`, `RECEPTION`, `RESREC`.

---

## 6. Check-out (départ du client)

### 6.1 Check-out standard

1. **Clic droit** sur la réservation → **"Check-out"**.
2. Confirmation → cliquer sur **"Check-out"**.

#### Effets serveur

- **Statut** : `ARRIVEE` → `PARTIE`.
- **Chambres** : `OCCUPEE` → `NETTOYAGE` (génère une tâche ménage
  automatique pour le personnel d'entretien).
- **Nuitées** : `PREVUE` → `CONSOMMEE`.
- Notification temps réel au calendrier (mise à jour des grilles
  ouvertes).

### 6.2 Check-out express (transfert sur société)

Pour les clients corporate où la société règle l'addition après coup :

1. **Clic droit** → **"Check-out"** → bouton **"Check-out express"** dans
   la modale paiements.
2. Sélectionner la société qui assume la dette.
3. Confirmer.

Effets supplémentaires :
- Le reste-à-payer est transféré sur le compte de la société.
- La facture est marquée `PARTIELLEMENT_PAYEE` (ou `PAYEE` si déjà soldé).
- La société sera relancée séparément.

### Rôles autorisés

- Check-out standard : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC, NIGHTAUDIT.
- Check-out express : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESREC
  (sans NIGHTAUDIT — décision financière hors périmètre audit).

---

## 7. Annuler une réservation

Possible à tout moment **avant** un check-out (`PARTIE`).

### Marche à suivre

1. **Clic droit** sur la réservation → **"Annuler la réservation"**
   (bouton rouge en bas du menu contextuel).
2. **SweetAlert** demande un motif d'annulation (champ textarea
   obligatoire, max 500 caractères).
3. Saisir le motif (ex. "Client a annulé pour raisons personnelles",
   "Erreur de saisie", "No-show", ...).
4. Confirmer.

### Effets serveur

- **Statut** : `CONFIRMEE` / `ARRIVEE` → `ANNULEE`.
- Si statut était `ARRIVEE` (client déjà checké-in) → les chambres
  passent en `NETTOYAGE` automatiquement.
- **Suivi structuré** (depuis le tour récent) :
  - `motif_annulation` (texte 500 chars).
  - `date_annulation` (horodatage UTC).
  - `annule_par_user_id` (auteur — FK vers utilisateurs).
- Le motif est aussi annexé au champ `commentaires` pour visibilité
  rétro-compatible.
- La réservation **disparaît du calendrier** (les statuts `ANNULEE` et
  `NO_SHOW` ne sont pas affichés).

### Rôles autorisés

`SUPERADMIN`, `ADMIN`, `GERANT`, `RECEPTION`.

Le bouton est **masqué** pour les autres rôles (RESREC, NIGHTAUDIT,
RESTAURANT, MENAGE, MAGASIN).

### Cas du DELETE physique

Le DELETE physique (suppression complète de la ligne BD) **n'est pas
exposé** dans l'interface — c'est une opération réservée aux
SUPERADMIN/ADMIN via API directe. Le service la traite comme une
annulation avec motif technique fixe ("Suppression via API DELETE").

---

## 8. Night audit (automatique, minuit du Maghreb)

Procédure quotidienne **automatique** déclenchée par le scheduler serveur.

### Planning

- **Alerte préalable** : 11:57 UTC (envoyée aux SUPERADMIN/ADMIN/NIGHTAUDIT
  via notification interne).
- **Exécution** : 12:00 UTC.
- **Timezone serveur** : `Africa/Nouakchott` (équivalent UTC sans DST).

### Effets

1. Identifie toutes les réservations en statut `CONFIRMEE` dont la date
   d'arrivée est **strictement antérieure à aujourd'hui** (= no-show
   présumé).
2. Bascule leur statut en `NO_SHOW`.
3. Libère les chambres qui leur étaient affectées.

### Déclenchement manuel

Un utilisateur SUPERADMIN/ADMIN/GERANT/NIGHTAUDIT peut **déclencher le
night audit à la demande** depuis la page **Hébergement → Night audit**
(`/hebergement/night-audit`) — utile en cas de re-traitement manuel.

---

## 9. Matrice rôles × actions

| Action | SUPERADMIN | ADMIN | GERANT | RECEPTION | RESREC | NIGHTAUDIT |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Voir le calendrier | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Créer une réservation | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Modifier (partial) | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Changer de chambre | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Modifier les nuitées | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Check-in | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Check-out | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Check-out express | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Gérer paiements | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| **Annuler** | ✓ | ✓ | ✓ | ✓ | — | — |
| DELETE physique (API) | ✓ | ✓ | — | — | — | — |
| Déclencher night audit | ✓ | ✓ | ✓ | — | — | ✓ |
| Reporting hébergement | ✓ | ✓ | ✓ | — | — | ✓ |

---

## 10. Annexe — Référence technique

### 10.1 Endpoints REST principaux

| Méthode | Path | Action |
|---|---|---|
| `POST` | `/api/hebergement/reservations` | Créer |
| `PUT` | `/api/hebergement/reservations/{id}` | Modifier (partial) |
| `PATCH` | `/api/hebergement/reservations/{id}/chambre` | Changer chambre |
| `POST` | `/api/hebergement/reservations/{id}/check-in` | Check-in |
| `POST` | `/api/hebergement/reservations/{id}/check-out` | Check-out |
| `POST` | `/api/hebergement/reservations/{id}/check-out-express` | Check-out express |
| `POST` | `/api/hebergement/reservations/{id}/cancel` | Annuler (motif obligatoire) |
| `DELETE` | `/api/hebergement/reservations/{id}` | Suppression logique (= cancel) |
| `GET` | `/api/hebergement/reservations/{id}/paiements-recap` | Récap paiements |
| `GET` | `/api/hebergement/reservations/arrivees-today` | Arrivées du jour |
| `GET` | `/api/hebergement/reservations/departs-today` | Départs du jour |
| `GET` | `/api/hebergement/reservations/check-ins-retard` | Check-ins en retard |

### 10.2 Statuts détaillés

| Statut | Couleur calendrier | Description |
|---|---|---|
| `CONFIRMEE` | Rouge | Réservation créée, client non encore arrivé |
| `ARRIVEE` | Vert | Check-in effectué, client présent |
| `PARTIE` | Orange | Check-out effectué, séjour terminé |
| `ANNULEE` | Masqué | Annulation manuelle, conservée en historique |
| `NO_SHOW` | Masqué | Non-présentation détectée par le night audit |

### 10.3 Isolation multi-tenant

Toutes les opérations sont **filtrées automatiquement par l'hôtel courant**
via JWT côté serveur. Un utilisateur de l'hôtel A ne peut jamais voir,
modifier ou annuler une réservation de l'hôtel B — même en forgeant
manuellement l'URL avec un identifiant cross-tenant.

### 10.4 Multi-occupants (clients additionnels)

Une réservation peut contenir plusieurs `ReservationClient` au-delà du
client principal (couples, familles, groupes). Chacun peut être marqué
"payant" avec un pourcentage de charge — utile pour les notes de frais
partagées.

---

*Document généré à partir du code source City Hotel — branche
`fix/menage-completion`, mai 2026.*
