---
description: Vérifie l'isolation multi-tenant sur un fichier, un module, ou tout le backend
argument-hint: <chemin> (fichier ou dossier — si vide, scan citybackend entier)
allowed-tools: Read, Bash(grep:*), Bash(rg:*), Bash(find:*)
---

Audit ciblé **multi-tenant** sur `$ARGUMENTS` (par défaut `citybackend/`).

## ⚠️ Note sur les modules récemment intégrés depuis `/CLIENTS/`, `/INVENTORY/`, `/FINANCE/`, `/HEBERGEMENT/`, `/MENAGE/`, `/RESTAURANT/`

Ces dossiers source contiennent souvent du code mal classé (un service de paiement dans `/CLIENTS/`, une entité réservation dans `/RESTAURANT/`, etc.). Si l'intégration n'a pas correctement aiguillé chaque fichier vers son module réel, on peut se retrouver avec un service interrogeant la mauvaise entité, contournant le filtre tenant attendu.

→ Croiser le périmètre avec `CARTOGRAPHIE_MODULES.md` (racine). Tout fichier dont l'emplacement final dans `citybackend/` ne correspond pas au `Domaine réel` cartographié → **finding ÉLEVÉE** (risque indirect : le filtre tenant peut viser la mauvaise table).

## Checklist d'isolation

Pour chaque entité hôtel-scopée, vérifier :

### 1. Couche entité
- [ ] Champ `hotel_id` (Long, NOT NULL) présent.
- [ ] Index sur `hotel_id` (recommandé pour perf).
- [ ] Pas de relation `@ManyToOne` vers une entité d'un autre hôtel sans contrôle.

### 2. Couche repository
Cherche les méthodes qui accèdent à des entités hôtel-scopées **sans `hotelId`** :
```
rg "Page<\w+>\s+findAll\s*\(" citybackend
rg "Optional<\w+>\s+findById\s*\(\s*Long" citybackend --files-with-matches
```
- [ ] Toute méthode custom comporte `hotelId` en paramètre **OU** un `Specification` qui l'injecte.
- [ ] `findAll(Pageable)` et `findById(id)` du `JpaRepository` ne sont PAS appelés directement depuis les services métier (ils ignorent le tenant).

### 3. Couche service
- [ ] Le `hotelId` provient de `TenantContext.getCurrentHotelId()`.
- [ ] **Jamais** de `dto.getHotelId()` ou `request.getHotelId()`.
- [ ] À la création, l'entité reçoit `setHotelId(TenantContext.getCurrentHotelId())`.
- [ ] À la lecture, le repo est appelé avec une variante `*ByIdAndHotelId(...)` ou `findByHotelId(...)`.

### 4. Couche controller
- [ ] `@PreAuthorize` sur chaque méthode publique.
- [ ] Pas de paramètre `hotelId` dans la signature (path/query/body) — sinon, le **lever** comme erreur critique.

### 5. Filter / Context
- [ ] `JwtAuthFilter` alimente `TenantContext` à partir de la claim JWT `hotelId`.
- [ ] `TenantContext.clear()` appelé en `finally`.
- [ ] MDC `hotel_id` posé pour les logs.

### 6. Sécurité de référence croisée
Cas typique : un endpoint `/api/factures/{id}/lignes` charge la facture, puis ses lignes.
- [ ] La facture est chargée via `findByIdAndHotelId(id, hotelId)` (et pas seulement `findById(id)` puis check ensuite).
- [ ] Si une ligne référence un produit, vérifier que le produit appartient au même hôtel (sinon 403 ou 404).

## Sortie

Pour chaque violation détectée :
```
[GRAVITÉ] fichier:ligne
  > extrait de code (3 lignes max)
  Correction : <suggestion concise>
```

Gravités :
- **CRITIQUE** : fuite de données entre hôtels possible (findAll non filtré, hotelId dans DTO entrant…).
- **ÉLEVÉE** : risque indirect (méthode legacy non utilisée mais publique).
- **MOYENNE** : pattern non conforme mais sans fuite directe (ex. méthode privée).

Conclure par : `Multi-tenant : N CRITIQUES / M ÉLEVÉES / K MOYENNES`.

**Si N > 0 → ne pas commit avant correction.**
