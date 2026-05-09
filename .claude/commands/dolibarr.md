---
description: Génère ou valide le client REST Dolibarr (factures, clients, paiements) sans embarquer de code Dolibarr
argument-hint: <ressource>  (factures | clients | paiements | produits | comptabilite)
allowed-tools: Read, Edit, Write, WebFetch
---

Intervient sur l'intégration Dolibarr REST (cf. `prompt_module_finance.txt`).

## Contexte

- Dolibarr est un **logiciel externe**. Aucun code PHP Dolibarr n'est embarqué dans `citybackend`.
- Communication via API REST : `<server>/api/index.php/<endpoint>`.
- Header obligatoire : `DOLAPIKEY: <api_key>` (par hôtel — chaque hôtel a SON instance Dolibarr ou son entité Dolibarr distincte).
- Format JSON. Méthodes GET/POST/PUT/DELETE.
- Doc : `https://wiki.dolibarr.org/index.php/Module_Web_Services_API_REST_(developer)`.
- Explorer live : `https://demo.dolibarr.org/api/index.php/explorer/`.

## Travaux à mener pour `$ARGUMENTS`

### 1. Configuration multi-tenant Dolibarr

Vérifier qu'une table `core.hotel_dolibarr_config` existe :
```
hotel_id (FK), base_url, api_key (chiffré), entity_id, comptable_active (boolean)
```

Sinon, proposer un changeset Liquibase pour la créer.

### 2. Client Feign

Dans `citybackend/src/main/java/com/cityprojects/finance/dolibarr/` :
- `DolibarrFeignClient.java` (déclaration `@FeignClient(name="dolibarr", url="dynamic")`).
- `DolibarrClientFactory.java` qui construit un client par hôtel à partir de la config.
- DTOs miroir des objets Dolibarr (Invoice, Thirdparty, Payment, Product, AccountingAccount).

### 3. Service de synchro

`DolibarrSyncService` avec mappings :
- `Client (city) → Thirdparty (Dolibarr)` : créer/MAJ à la création/MAJ d'un client city.
- `Facture (city) → Invoice (Dolibarr)` : à la validation d'une facture city, créer dans Dolibarr et stocker `dolibarr_id` côté city.
- `Paiement (city) → Payment (Dolibarr)`.
- Plan comptable : mapper les comptes city aux comptes Dolibarr conformément à `plan_comptable_mauritanien.pdf`.

### 4. Fiabilité

- Retry exponentiel sur `5xx` et timeouts (Resilience4j).
- File Kafka `dolibarr-sync-retry` pour les échecs persistants.
- Idempotence : utiliser un identifiant métier (`numero` facture city) comme clé de déduplication.

### 5. Sécurité

- API key Dolibarr chiffrée en base (Jasypt ou SecretBox).
- **Jamais** exposer la clé dans les logs.
- Le user city ne fournit JAMAIS la clé via une requête — elle est posée par l'admin lors de l'abonnement de l'hôtel.

### 6. Tests

- `WireMock` pour mocker l'API Dolibarr.
- Cas testés : création thirdparty, création invoice + lignes, paiement partiel, paiement complet, échec API, réconciliation.

## Sortie attendue

Pour `$ARGUMENTS = factures` par exemple : générer le DTO `DolibarrInvoice`, la méthode Feign, la méthode `DolibarrSyncService.syncInvoice(Long factureId)`, le test unitaire WireMock.

Si l'utilisateur veut faire un appel **interactif** (test), proposer un curl prêt à l'emploi.
