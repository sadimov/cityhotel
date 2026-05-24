# Prompt de reprise — City Hotel

> Fichier conservé à la racine pour faciliter la reprise du projet après
> une pause longue (mois / années). Copie le bloc ci-dessous dans une
> nouvelle session Claude Code (ou tout LLM avec accès au repo), remplis
> le bloc « CE QUE JE VEUX FAIRE MAINTENANT » et envoie.

---

## Version longue (recommandée pour démarrer une session sérieuse)

```
Je reprends le projet City Hotel après une pause. C'est une application
Spring Boot 3.4.5 / Angular 21.2 / PostgreSQL 16, multi-tenant DISCRIMINATOR
Hibernate, déployée selon deux modes (LOCAL pour 1 hôtel on-premise, SAAS
pour le serveur central multi-hôtels).

État au moment de la pause : "presque mature, presque finale".
Branche active : fix/menage-completion (~25 commits depuis Tour 41).
Tests : 433 Surefire verts. Build vert backend + frontend.

AVANT TOUTE MODIFICATION

1. Lis IMPÉRATIVEMENT la section §0 "Statut à la reprise" de chaque CLAUDE.md :
   - CLAUDE.md (racine) → état global + tableau corrections systémiques
   - citybackend/CLAUDE.md → patterns backend (DTOs enrichis, recalibrage
     NumerotationService, exceptions SecurityConfig, etc.)
   - cityfrontend/CLAUDE.md → patterns front (input UNCONTROLLED, mapping
     backend→front admin, modes user-form, etc.)

2. Lis les §1+ uniquement si la §0 te renvoie vers un point précis.

3. Vérifie l'état git :
   git status
   git log --oneline main..HEAD | head -20

4. Si tu vas modifier du code : lance d'abord les tests pour confirmer la baseline
   cd citybackend && ./mvnw test
   cd cityfrontend && npx tsc --noEmit

CONVENTIONS À RESPECTER (extraits clés)

- Multi-tenant @TenantId : JAMAIS de hotelId dans les payloads HTTP/DTO.
  Le tenant vient du JWT via TenantContext (cf. §6.1 racine).
- Numérotations factures/réservations/clients/etc. : passer par
  NumerotationService.next(TypeNumerotation, [discriminant]). Jamais
  d'incrémentation manuelle ni de SEQUENCE Postgres custom.
- DTOs avec noms résolus (anti-N+1) : suivre le pattern withResolvedNames
  documenté en citybackend/CLAUDE.md §0.1.
- Input recherche live front : pattern UNCONTROLLED (pas de [value] bindé
  à un async pipe — cf. cityfrontend/CLAUDE.md §0.1).
- Mapping backend→front admin : déjà fait dans HotelsAdminService /
  RolesAdminService. NE PAS toucher les composants qui consomment Hotel/Role.
- Pas de rétrogradation sous Java 21 / Spring Boot 3.4 / Angular 21.2 /
  Node 22 LTS / PostgreSQL 16.

MODE DE TRAVAIL ATTENDU

- Délègue aux sous-agents quand c'est pertinent : backend-spring,
  frontend-angular, db-postgres, multitenant-guardian, code-auditor.
- Lance /multitenant-check après toute modif touchant entité/repo/service.
- Avant un commit important : /audit-module ou code-auditor.
- Commits : Conventional Commits (feat / fix / docs / refactor / test),
  message en français, mention Co-Authored-By: Claude Opus.

CE QUE JE VEUX FAIRE MAINTENANT

[décris ici ton objectif de la session — ex: "fixer le bug X", "ajouter
le module notification", "préparer le déploiement chez l'hôtel Y", etc.]
```

---

## Version courte (pour micro-tâche ou question rapide)

```
Reprends le projet City Hotel — lis §0 des 3 CLAUDE.md, vérifie
git status, et regarde-moi [ICI le sujet précis].
```

---

## Comment utiliser ce fichier

**Au démarrage d'une nouvelle session Claude Code** :

```bash
# Windows PowerShell
Get-Content REPRISE.md | Set-Clipboard

# Linux/Mac
cat REPRISE.md | pbcopy   # Mac
cat REPRISE.md | xclip    # Linux
```

Puis colle dans la session. Tu retrouves toujours le même point d'entrée
propre, peu importe combien de mois se sont écoulés.

## Pourquoi ce prompt fonctionne

- **Auto-suffisant** : un LLM frais sans contexte préalable peut le suivre.
- **Pointe vers la mémoire vivante** : les §0 des `CLAUDE.md` sont mis à
  jour à chaque grosse étape (cf. commit `135853a` du 2026-05-24).
- **Garde-fous explicites** : multi-tenant, numérotation, patterns
  d'input — les pièges connus sont nommés.
- **Mode de travail** : sous-agents, audits, conventions de commit —
  pour éviter qu'un nouveau Claude « invente » ses propres habitudes.
- **Concis** : ~60 lignes utiles, copie-colle rapide.

## Maintenir ce fichier

Si tu fais une nouvelle grosse étape (Vague 3 livrée, migration palier 2,
refonte architecturale…), mets à jour :

1. Les sections §0 des 3 `CLAUDE.md` (c'est la mémoire vivante)
2. Le « nombre de commits depuis Tour 41 » + branche active dans ce prompt
3. Le statut tests / build dans ce prompt

Le reste du fichier `REPRISE.md` peut rester stable plusieurs mois.
