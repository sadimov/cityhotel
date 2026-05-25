# Guide utilisateur — City Hotel

Documentation fonctionnelle destinée aux utilisateurs métier
(réception, gérance, administration).

## Fichiers

| Fichier | Usage |
|---|---|
| `01-cycle-vie-reservation.md` | Source Markdown (lecture directe sur GitHub, édition future) |
| `01-cycle-vie-reservation.html` | Version mise en page avec CSS print-friendly |
| `generate_pdf.bat` | Script Windows : double-clic → PDF généré automatiquement |
| `generate_pdf.py` | Alternative Python avec ReportLab (cross-platform) |

## Générer le PDF — 3 options au choix

### Option 1 — Script Windows (le plus simple)

**Double-cliquer sur `generate_pdf.bat`**.

Le script détecte automatiquement Chrome ou Edge (présents par défaut sur
Windows) et génère `01-cycle-vie-reservation.pdf` dans ce dossier. Aucune
installation requise.

### Option 2 — Impression depuis un navigateur (universel)

1. Ouvrir `01-cycle-vie-reservation.html` dans un navigateur (double-clic).
2. `Ctrl + P` (ou `Cmd + P` sur Mac).
3. Destination : **Enregistrer au format PDF**.
4. Marges : **Défaut** (la mise en page CSS gère déjà tout).
5. Cliquer sur **Enregistrer**.

Fonctionne sur Chrome, Edge, Firefox, Safari, sans dépendance.

### Option 3 — Script Python (cross-platform)

Si Python + ReportLab sont installés :

```bash
pip install reportlab    # une seule fois
python generate_pdf.py
```

Génère un PDF avec mise en page native (pas via navigateur).

## Mettre à jour le contenu

La source de vérité est **`01-cycle-vie-reservation.md`**.

Après modification :
- Le HTML devra être mis à jour à la main (ou régénéré depuis le Markdown
  via pandoc / Marp / autre outil de votre choix).
- Le PDF se régénère via une des 3 méthodes ci-dessus.

## Prochaines fiches prévues

- `02-cycle-vie-facture.md` — Émission, encaissement, avoir.
- `03-cycle-vie-tache-menage.md` — Génération automatique, planification, suivi.
- `04-night-audit-detail.md` — Procédure complète, alertes, ré-exécution.
