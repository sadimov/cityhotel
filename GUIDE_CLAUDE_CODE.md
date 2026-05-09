# Guide d'utilisation — Claude Code pour City Hotel

Ce document explique **concrètement** comment utiliser Claude Code (terminal) pour continuer le développement de ton projet `city`.

---

## 1. Préparer le workspace

Place ton dossier de travail comme suit :

```
city-workspace/
├── citybackend/                                # ton projet Spring Boot
├── cityfrontend/                               # ton projet Angular
├── CLIENTS/  FINANCE/  HEBERGEMENT/            # code chatbot à intégrer
├── INVENTORY/  MENAGE/  RESTAURANT/
├── Tech_DevOPS/
│   └── TECHNOLOGIES_DEVOPS_A_UTILISER.md
├── PROMPTS/
│   └── prompt_*.txt
├── consignes_design_interface_graphique.txt
├── modes_paiements.txt
├── règles_night_audit.txt
├── roles_utilisateurs.txt
├── plan_comptable_mauritanien.pdf
├── ERREURS_AUDIT_A_EVITER.html
├── structure_cityprojectdb*.sql
│
├── CLAUDE.md                                   # ← fourni par moi (mémoire racine)
└── .claude/                                    # ← fourni par moi
    ├── settings.json
    ├── commands/                               # 9 slash commands
    └── agents/                                 # 6 sous-agents
```

> Les fichiers `CLAUDE.md` et tout le contenu de `.claude/` te sont livrés dans ce setup. Dépose-les à la **racine** du workspace.
> Les `CLAUDE.md` spécifiques à `citybackend/` et `cityfrontend/` doivent être posés dans leurs dossiers respectifs.

## 2. Lancer Claude Code

Depuis le terminal, à la racine du workspace :

```bash
cd ~/city-workspace
claude
```

À la première session, Claude Code te demandera :
- Confirmer le projet (répondre `Y`).
- Te connecter (Anthropic).
- Choisir le modèle. Pour ce projet, **Claude Opus 4.7** est recommandé pour les tâches d'architecture et de comptabilité ; **Claude Sonnet 4.6** suffit pour les tâches CRUD répétitives.

```bash
# Pour fixer le modèle pour la session :
claude --model claude-opus-4-7

# Ou via le fichier settings.json (déjà configuré).
```

## 3. Les commandes slash personnalisées

| Commande | Usage |
|----------|-------|
| `/integrate-module <NOM>` | Intègre le code de `/<NOM>/` dans `citybackend`/`cityfrontend` sans écraser. Ex. `/integrate-module INVENTORY`. |
| `/audit-module <module>` | Audit complet contre `ERREURS_AUDIT_A_EVITER.html` et les `CLAUDE.md`. |
| `/multitenant-check [chemin]` | Vérifie l'isolation multi-tenant. À lancer avant CHAQUE commit. |
| `/new-entity <Nom> <module>` | Scaffolde une entité backend complète (entity + repo + service + controller + DTO + mapper + Liquibase + test). |
| `/new-component <nom> <feature>` | Scaffolde un composant Angular avec service + i18n + routing + guards. |
| `/sync-tech [--apply]` | Compare les libs avec `Tech_DevOPS/` et propose les ajustements. |
| `/dolibarr <ressource>` | Génère le code d'intégration Dolibarr REST. |
| `/run-back` / `/run-front` | Démarre le backend / frontend. |
| `/db-validate` | Vérifie la cohérence schémas SQL ↔ entités JPA ↔ Liquibase. |
| `/prep-commit [scope]` | Audit + tests + propose un message Conventional Commits. |

Ces commandes sont des **fichiers Markdown** dans `.claude/commands/`. Tu peux les éditer pour les ajuster à ta pratique. Tu peux aussi en créer de nouveaux (créer un fichier `.md` dans le même dossier et il devient une slash command).

## 4. Les sous-agents spécialisés

Claude Code délègue automatiquement à un sous-agent quand sa description correspond à la tâche. Tu peux aussi forcer manuellement :

```
> use the backend-spring agent to add CRUD endpoints for Fournisseur
> use multitenant-guardian to scan citybackend/src/main/java/com/cityprojects/inventory
```

Sous-agents disponibles :

- **`backend-spring`** — implémentation Spring Boot, JPA, sécurité, Kafka.
- **`frontend-angular`** — composants Angular, services, formulaires, DataTables, i18n.
- **`db-postgres`** — schéma SQL, Liquibase, index, perf.
- **`multitenant-guardian`** — chasse aux fuites de données entre hôtels (à invoquer **systématiquement** avant un commit).
- **`code-auditor`** — revue qualité transversale.
- **`dolibarr-integrator`** — synchronisation Dolibarr REST.
- **`hotel-business`** — arbitrage de règles métier.

Liste les agents disponibles dans Claude Code :
```
/agents
```

## 5. Workflow type — intégrer un module existant

Exemple : intégrer le code généré par le chatbot pour le module **INVENTORY**.

```
> /integrate-module INVENTORY
```

Claude :
1. Liste les fichiers de `/INVENTORY/`.
2. Compare avec `citybackend` et `cityfrontend`.
3. Te demande confirmation pour les conflits.
4. Copie ce qui manque.
5. Lance la compilation pour vérifier.

Ensuite :
```
> /audit-module inventory
> /multitenant-check citybackend/src/main/java/com/cityprojects/inventory
```

Si tout est vert :
```
> /prep-commit inventory
```

## 6. Workflow type — nouvelle fonctionnalité

Exemple : ajouter le module **Reporting** depuis zéro.

```
> consult the hotel-business agent: what KPIs should the reporting dashboard expose for a hotel manager?
```

Claude répond avec une liste arbitrée. Ensuite :

```
> use db-postgres to design the materialized views and tables needed for these KPIs
```

Puis :

```
> use backend-spring to implement the ReportingService and the GET /api/reports/dashboard endpoint
```

Puis frontend :

```
> /new-component reporting-dashboard reporting --type=dashboard
```

Avant de committer :

```
> /audit-module reporting
> /multitenant-check
> /prep-commit reporting
```

## 7. Bonnes pratiques

### Donne du contexte explicite
Plutôt que :
> ajoute une endpoint pour les paiements

Préférer :
> add a POST /api/paiements endpoint that records a payment linked to one or more invoice lines, conforming to §6.2 of CLAUDE.md (numérotation séquentielle par hôtel) and using the dolibarr-integrator agent to push the payment to Dolibarr after persistence

### Cite les fichiers
Claude Code peut lire n'importe quel fichier mais devine moins bien. Cite explicitement :
> as defined in PROMPTS/prompt_module_finance.txt and plan_comptable_mauritanien.pdf, ...

### Demande un plan d'abord pour les tâches lourdes
Pour toute tâche > 200 lignes ou impactant plusieurs modules :
> propose a plan first, then wait for my OK before generating code

### Garde les CLAUDE.md à jour
Si tu introduis un pattern nouveau, demande à Claude de mettre à jour le `CLAUDE.md` correspondant dans le même commit.

### Utilise `/clear` entre tâches indépendantes
Pour vider le contexte et éviter les confusions sur de longues sessions.

### Exporte les sessions importantes
```
/export
```
…produit un fichier Markdown de la conversation, utile pour la doc projet ou la revue d'équipe.

## 8. Sécurité du workspace

Le `.claude/settings.json` que je te livre **bloque** :
- Toute commande `rm -rf` à la racine ou home.
- Toute commande `sudo`.
- La lecture de fichiers `.env`, `.key`, `.pem`.
- Les commandes contenant `drop database` ou `truncate`.

Il **autorise** sans demander :
- Les commandes Maven, npm, Angular CLI, Git lecture.
- La lecture/écriture sur le projet.

Pour toute autre commande shell, Claude Code te demandera confirmation avant exécution. Réponds prudemment.

## 9. Checklist avant chaque commit

```
[ ] /audit-module <module-modifié>      → 0 bloquant
[ ] /multitenant-check                   → 0 critique
[ ] ./mvnw test (backend)                → vert
[ ] npm test (frontend)                  → vert
[ ] git diff revu manuellement
[ ] message au format Conventional Commits
[ ] CLAUDE.md mis à jour si patterns introduits
```

## 10. Astuces

**Conserver l'historique d'un module** : crée un fichier `MODULE_LOG.md` dans chaque feature et ajoute-y une entrée à chaque évolution. Demande à Claude de le faire en fin de session.

**Onboarding équipe** : un nouveau dev fait `claude` à la racine, lit le `CLAUDE.md`, exécute `/agents` et `/commands` pour découvrir l'outillage.

**Sessions parallèles** : pour deux tâches indépendantes (ex. backend finance + frontend ménage), ouvre **deux terminaux** avec deux sessions Claude Code. Chacun peut éditer ses fichiers ; merge via Git.

**Escape hatch** : si Claude part dans une mauvaise direction, `Esc Esc` interrompt et te laisse rectifier ; `/undo` (ou Git) revient à l'état précédent.

**Mode plan** : `Tab Tab` ou `--plan-mode` au démarrage pour qu'il propose toujours un plan avant d'éditer.

---

## Annexe — Cycle de vie typique d'une session

```bash
$ cd city-workspace
$ claude

> /clear
> show me the current state of the inventory module backend
[…]

> /integrate-module INVENTORY
[…]

> use frontend-angular to complete the missing components for inventory based on prompt_inventory_frontend.txt
[…]

> /audit-module inventory
[…]

> /multitenant-check citybackend/src/main/java/com/cityprojects/inventory
[…]

> /prep-commit inventory
[…]

# l'utilisateur valide la commande proposée
$ git add citybackend/src/main/java/com/cityprojects/inventory ...
$ git commit -m "feat(inventory): complete CRUD endpoints and frontend list components"
```

Bon dev ! 🚀
