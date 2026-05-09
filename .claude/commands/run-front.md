---
description: Démarre le frontend Angular (port 4200)
allowed-tools: Bash(npm:*), Bash(ng:*), Bash(cd:*)
---

Démarre `cityfrontend` :

```bash
cd cityfrontend && npm start
```

Vérifier au préalable :
- `node --version` ≥ 22, `npm --version` ≥ 10.
- Présence de `node_modules/` (sinon `npm install` d'abord).
- Le backend tourne sur `http://localhost:8080` (sinon les appels HTTP échoueront).

Affiche un résumé des erreurs de compilation Angular si présentes, en pointant fichier:ligne.
