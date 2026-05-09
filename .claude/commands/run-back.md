---
description: Démarre le backend Spring Boot (port 8080)
allowed-tools: Bash(./mvnw:*), Bash(cd:*)
---

Démarre `citybackend` :

```bash
cd citybackend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Avant de lancer :
1. Vérifier que PostgreSQL tourne (`pg_isready -h localhost -p 5432`).
2. Vérifier que `cityprojectdb` existe.
3. Si `BUILD FAILURE`, afficher les 30 dernières lignes pertinentes et proposer un diagnostic.

Le backend est accessible sur `http://localhost:8080/citybackend`. Swagger UI (si activé) : `/swagger-ui.html`.
