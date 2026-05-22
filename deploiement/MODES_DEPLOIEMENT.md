# Modes de déploiement City Hotel

> Document de référence — consigne user 2026-05-22.
> Concerne la stratégie de numérotation et les choix d'hébergement.

## Deux modes de déploiement supportés

| Aspect | LOCAL | SAAS |
|---|---|---|
| Serveur | Sur site (chez le client hôtel) | Central City Hotel |
| Nombre d'hôtels par BD | **1** (l'hôtel local) | **N** (multi-tenants) |
| Tenant Hibernate `@TenantId` | Actif (1 seul `hotelId`) | Actif (N `hotelId`) |
| Sauvegardes | Responsabilité client | Centralisées City Hotel |
| Accès | LAN client uniquement | Internet + LAN client |
| Configuration `app.deployment.mode` | `LOCAL` | `SAAS` (défaut) |

**Le code applicatif est identique** dans les deux modes — la même image JAR fonctionne pour LOCAL et SAAS. La distinction se fait par la variable d'environnement `APP_DEPLOYMENT_MODE`.

## Garanties de numérotation — invariantes dans les 2 modes

**Consigne** : *Quelle que soit la façon d'utilisation, les numérotations (factures, réservations, produits, clients…) doivent être successives pour chaque hôtel à part. Il ne faut pas que les numéros interfèrent. Il ne faut pas que les séquences soit partagées entre les hôtels.*

### 1. Isolation par hôtel

La table `finance.numerotation_sequence` a un UNIQUE INDEX sur `(hotel_id, type, exercice, discriminant)`. Hibernate `@TenantId` ajoute automatiquement `WHERE hotel_id = ?` à toutes les requêtes lecture / écriture.

→ **Chaque hôtel a ses propres compteurs**. L'hôtel A ne peut pas voir ni incrémenter le compteur de l'hôtel B.

### 2. Successivité sans saut

`NumerotationServiceImpl.next()` utilise :
- `SELECT ... FOR UPDATE` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) — sérialise les transactions concurrentes
- `@Transactional` propagation `REQUIRED` — l'incrémentation rejoint la transaction de l'appelant. Si le caller fait rollback (ex. INSERT facture échoue), le compteur est rolled back avec → **pas de saut**

→ **Pas de sauts en cas d'échec** : si une création de facture plante après que le numéro a été réservé, la transaction globale rollback, et le compteur revient à son état initial.

### 3. Recalibrage automatique (cas migration)

Au démarrage d'un nouvel exercice ou d'un hôtel migrant avec des données existantes, le service interroge `MAX(numero)` dans la table cible :

```java
private long findMaxExistingValueForRecalibration(TypeNumerotation type, …)
```

Couvert pour FACT/AVOIR, PAY, RES, BC, BS, CLI, COMM. Le compteur se positionne sur `MAX(existant)` + 1 au prochain appel — **garantit l'unicité** en reprise de données.

→ **Pas d'interférence** entre numéros déjà émis et nouveaux : reprise BD sans collision UNIQUE.

### 4. Format des numéros

`TYPE-EXERCICE-CODEPAYS-NNNNNN` (ou `TYPE-DISC-EXERCICE-CODEPAYS-NNNNNN` pour les types segmentés type JRN).

Exemples :
- `FACT-2026-MR-000123` (facture, exercice 2026, code pays MR, séquence 123)
- `RES-2026-MR-000045` (réservation)
- `JRN-VTE-2026-MR-000007` (écriture journal Ventes)

Le `codePays` est lu sur `hotels.code_pays` — donc **chaque hôtel peut avoir son code pays** (MR, FR, MA…) et ses numéros sont préfixés en conséquence. Aucun risque d'interférence entre `FACT-2026-MR-000001` (hôtel mauritanien) et `FACT-2026-FR-000001` (hôtel français).

## Configuration

### Mode LOCAL

`application.yml` (ou variables d'environnement) :

```yaml
app:
  deployment:
    mode: LOCAL
    hotel-code: HOTEL01     # code de l'hôtel unique du serveur
```

Ou :

```powershell
$env:APP_DEPLOYMENT_MODE      = "LOCAL"
$env:APP_DEPLOYMENT_HOTEL_CODE = "HOTEL01"
java -jar citybackend-1.0.0.jar
```

Après création de l'hôtel via SUPERADMIN (1 fois), tous les utilisateurs créés sont rattachés à cet hôtel. Le JWT contient toujours `hotelId`, donc la chaîne tenant fonctionne normalement.

### Mode SAAS

```yaml
app:
  deployment:
    mode: SAAS              # défaut, peut être omis
```

Le SUPERADMIN crée les hôtels via `/admin/hotels`. Chaque hôtel a son `hotelCode` unique et son `codePays`. Les utilisateurs sont rattachés à un hôtel précis via `/admin/hotels/:hotelId/users` ou `/admin/users/new` (UI Tour 56).

## Procédure de migration d'un hôtel existant

Si un hôtel reprend ses données (factures, réservations existantes) :

1. **Restaurer** les données métier (clients, factures, réservations…) dans le nouveau schéma
2. **Ne PAS pré-remplir** `finance.numerotation_sequence` manuellement
3. **Démarrer** l'application
4. À la **première émission** d'un numéro pour ce (hotel, type, exercice), `NumerotationServiceImpl.next()` détecte qu'aucune séquence n'existe → calcule le `MAX(numero)` existant dans la table cible → recale le compteur dessus → émet `MAX + 1`

→ Aucune action manuelle nécessaire. La numérotation reprend là où elle s'était arrêtée.

**Pour les types non couverts par le recalibrage** (`JRN`, `PROD`) :

```sql
-- Recalibrage manuel JRN-VTE pour hôtel 1 exercice 2026
-- (séquence par journal — discriminant = code journal)
INSERT INTO finance.numerotation_sequence (hotel_id, type, exercice, discriminant, last_value)
SELECT 1, 'JRN', 2026, 'VTE', COALESCE(MAX(CAST(SUBSTRING(numero FROM '\d{6}$') AS integer)), 0)
FROM finance.ecritures_comptables
WHERE hotel_id = 1 AND EXTRACT(YEAR FROM date_ecriture) = 2026 AND numero LIKE 'JRN-VTE-%';
```

## Vérifier l'isolation des séquences (test de routine)

Sur une BD multi-hôtels (mode SAAS) :

```sql
SELECT hotel_id, type, exercice, discriminant, last_value
FROM finance.numerotation_sequence
ORDER BY hotel_id, type, exercice;
```

Chaque hôtel doit avoir ses propres lignes. **Aucune ligne ne doit avoir `hotel_id = NULL` ni `hotel_id = 0`** (le check constraint `chk_numerotation_hotel_id_positive` l'empêche déjà).

## Stack technique du module numérotation

| Composant | Rôle |
|---|---|
| `TypeNumerotation` (enum) | Familles : FACT, AVOIR, PAY, RES, BC, BS, CLI, COMM, JRN, PROD |
| `NumerotationSequence` (entity) | Compteur persisté `(hotel_id, type, exercice, discriminant, last_value)` |
| `NumerotationSequenceRepository` | `findByTypeExerciceAndDiscriminantForUpdate` avec `@Lock(PESSIMISTIC_WRITE)` |
| `NumerotationServiceImpl` | Service `@Transactional` qui incrémente, recalibre si nouveau, formate |
| `DeploymentProperties` | Mode LOCAL / SAAS, pour audit et observabilité |
| Liquibase 020 + 044 | Création table + UNIQUE `(hotel_id, type, exercice, discriminant)` |
