---
name: dolibarr
description: Génère ou valide le client REST Dolibarr (factures, clients, paiements, produits, comptabilité) du projet city sans embarquer de code PHP Dolibarr. À utiliser pour scaffolder Feign + DTOs + service de sync, ou pour debug une intégration existante.
---

# dolibarr — Intégration REST Dolibarr (city ↔ Dolibarr)

Skill jumeau de `/dolibarr <ressource>`. Délègue à l'agent `dolibarr-integrator` pour la rédaction du code.

## Contexte non négociable

- Dolibarr est un **logiciel externe**. **Aucun** code PHP Dolibarr n'est embarqué dans `citybackend`.
- Communication via API REST uniquement : `<server>/api/index.php/<endpoint>`.
- Header obligatoire : `DOLAPIKEY: <api_key>`.
- Multi-tenant Dolibarr : chaque hôtel a SA config (URL + clé). Une table `core.hotel_dolibarr_config` la stocke avec la clé chiffrée.
- Documentation officielle : https://wiki.dolibarr.org/index.php/Module_Web_Services_API_REST_(developer)
- Explorer live : https://demo.dolibarr.org/api/index.php/explorer/

## Ressources couvertes

`factures` | `clients` | `paiements` | `produits` | `comptabilite`

## Checklist par ressource

### 1. Configuration multi-tenant Dolibarr (pré-requis)

Vérifier que le changeset Liquibase a créé :
```sql
CREATE TABLE core.hotel_dolibarr_config (
  hotel_id BIGINT PRIMARY KEY REFERENCES core.hotel(id),
  base_url VARCHAR(255) NOT NULL,
  api_key TEXT NOT NULL,            -- chiffré (Jasypt)
  entity_id INT NOT NULL DEFAULT 1, -- multi-entity Dolibarr
  comptable_active BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL
);
```

Sinon, proposer le changeset.

### 2. Client Feign

Package `com.cityprojects.citybackend.dolibarr.client` :
- `DolibarrFeignClient.java` (interface `@FeignClient(name="dolibarr", url="dynamic")`).
- `DolibarrClientFactory.java` qui construit un client par hôtel à partir de `HotelDolibarrConfig`.
- DTOs miroir : `DolibarrInvoice`, `DolibarrThirdparty`, `DolibarrPayment`, `DolibarrProduct`, `DolibarrAccountingAccount`.
- Auth via `RequestInterceptor` qui pose `DOLAPIKEY` à partir du `TenantContext`.

### 3. Service de synchro

Package `com.cityprojects.citybackend.dolibarr.service` :
- `DolibarrSyncService` :
  - `syncClient(Long clientId)` → POST/PUT `/thirdparties`.
  - `syncFacture(Long factureId)` → POST `/invoices` + lignes.
  - `syncPaiement(Long paiementId)` → POST `/invoices/{id}/payments`.
  - `syncProduit(Long produitId)` → POST/PUT `/products`.
  - `pullPlanComptable(Long hotelId)` → GET `/accountancy/accounts`.
- Stocker `dolibarr_id` côté city après sync (champ supplémentaire sur les entités concernées).

### 4. Mapping plan comptable

Réf : `plan_comptable_mauritanien.pdf`.
- Comptes produits : nuitée, restauration, mini-bar, services, etc.
- Comptes trésorerie : caisse, banque, Bankily, carte.
- Comptes clients : 411xxx.
- Le mapping est paramétrable par hôtel (table `finance.compte_mapping`).

### 5. Fiabilité (Resilience4j)

```java
@CircuitBreaker(name = "dolibarr")
@Retry(name = "dolibarr")
@Bulkhead(name = "dolibarr")
public DolibarrInvoice createInvoice(DolibarrInvoice invoice) { ... }
```

- Retry exponentiel sur 5xx et timeouts (max 3 tentatives, 1s/2s/4s).
- Circuit breaker : ouvre à 50% d'échecs sur 10 appels.
- Pour les échecs persistants : event Kafka `dolibarr.sync.failed` → rejouable manuellement par l'admin.
- Idempotence : utiliser `numero` city (FACT-2026-MR-000123) comme `ref_ext` Dolibarr pour déduplication.

### 6. Sécurité

- API key Dolibarr **chiffrée** en base via Jasypt (`ENC(...)`).
- **Jamais** de log de la clé (filter Logback).
- L'utilisateur city ne fournit JAMAIS la clé via une requête HTTP — elle est posée par le SUPERADMIN à l'abonnement de l'hôtel.

### 7. Tests (WireMock)

```java
@Test
void shouldCreateInvoiceInDolibarr() {
  stubFor(post("/api/index.php/invoices")
    .withHeader("DOLAPIKEY", equalTo("test-key"))
    .willReturn(okJson("{\"id\":42}")));

  Long dolibarrId = service.syncFacture(factureCity.getId());

  assertThat(dolibarrId).isEqualTo(42L);
  verify(postRequestedFor(urlEqualTo("/api/index.php/invoices")));
}
```

Cas couverts : création thirdparty, création invoice + lignes, paiement partiel, paiement complet, échec API, réconciliation, idempotence (rejouer une sync ne crée pas de doublon).

## Sortie attendue

Pour `dolibarr factures` (par exemple) :
1. Le DTO `DolibarrInvoice`.
2. La méthode Feign correspondante.
3. La méthode `DolibarrSyncService.syncFacture(Long)`.
4. Le test unitaire WireMock.
5. Un curl prêt à coller pour test manuel :
   ```bash
   curl -X POST "${DOLIBARR_URL}/api/index.php/invoices" \
     -H "DOLAPIKEY: ${DOLIBARR_KEY}" \
     -H "Content-Type: application/json" \
     -d '{"socid":"123","lines":[...]}'
   ```

## À ne JAMAIS faire

- ❌ Embarquer du code PHP Dolibarr.
- ❌ Faire un `findAll()` Dolibarr sans pagination ni filtre tenant.
- ❌ Commit la clé API en clair.
- ❌ Sync sans idempotence (= doublons côté Dolibarr).
- ❌ Hardcoder une URL ou une clé — toujours via `HotelDolibarrConfig`.
