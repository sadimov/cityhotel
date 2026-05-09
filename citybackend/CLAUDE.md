# citybackend — API Spring Boot

> Voir aussi le `CLAUDE.md` à la racine pour le contexte produit, l'architecture multi-tenant et les règles métier.

## 1. Stack & version cibles (mai 2026)

> **🎯 Stratégie en deux paliers** (arbitrage Tour 1, 2026-05-05) :
> - **Palier 1 (actuel)** : Java 21 + Spring Boot 3.4.5 + Spring Cloud 2024.0.1.
> - **Palier 2 (Q4 2026)** : Java 25 + Spring Boot 4.0.x + Spring Cloud 2025.0.x — déclenché quand l'écosystème (Resilience4j `spring-boot4`, JasperReports 7, MapStruct 1.7) sera stabilisé. Migration **avant** intégration finance/restaurant POS.

### Palier 1 — détail
- **Java 21 LTS** (sept 2023). **Jamais** Java 17 ou inférieur.
- **Spring Boot 3.4.5** — Spring Framework 6.2, Spring Security 6.4, Hibernate 6.6, Tomcat 10.1, Jakarta EE 10.
- **Spring Cloud 2024.0.1** ("Moorgate") BOM pour OpenFeign + Stream Kafka.
- **MapStruct 1.6.3**, **Lombok 1.18.34**, **jjwt 0.12.6**.
- **Resilience4j 2.2.0** (artifact `resilience4j-spring-boot3`) pour Dolibarr.
- **JasperReports 6.21.3** (v7 = nouvelle licence + iText fork ; reporté palier 2), **OpenPDF 2.0.3**, **Apache POI 5.3.0**.
- **Testcontainers 1.20.3** + **WireMock 3.9.1** (groupId `org.wiremock`).
- Maven (wrapper `./mvnw`, Maven **3.9.x** ou **4.0.x**), `maven-compiler-plugin 3.13.0` avec `<release>21</release>` (pas `<source>/<target>`).
- Build → JAR exécutable, image Docker via **Jib 3.4.4** (`com.google.cloud.tools:jib-maven-plugin`), base image `eclipse-temurin:21-jre-alpine`.
- Port `8080`, contexte `/citybackend`.

### Palier 2 — détail (cible Q4 2026)
- **Java 25 LTS** (sept 2025).
- **Spring Boot 4.0.x** — Spring Framework 7, Security 7, Hibernate 7, Tomcat 11, Jakarta EE 11.
- **Spring Cloud 2025.0.x** ("Northfield").
- **Lombok 1.18.36+** (parser Java 25), **MapStruct 1.7.0**, **JasperReports 7.0.x**, **Resilience4j 2.3.0** (`spring-boot4` artifact).
- Breaking attendus à anticiper : `DaoAuthenticationProvider` constructeur sans-args supprimé (impact `SecurityConfig`), Hibernate 7 strictness `AttributeConverter`, migration JasperReports v6 → v7 (APIs + licence).

> Voir `/sync-tech` pour le détail des versions cibles de chaque lib. Toute rétrogradation sous le palier 1 est refusée.

## 2. Arborescence des packages

```
com.cityprojects
├── CitybackendApplication.java
├── config/                  # SecurityConfig, CorsConfig, OpenApiConfig, JpaConfig
├── common/                  # exceptions, MapStruct base, paging, audit
│   ├── exception/           # BusinessException, NotFoundException, ForbiddenException
│   ├── audit/               # AuditableEntity (createdAt, updatedAt, createdBy, updatedBy)
│   ├── tenant/              # TenantContext (ThreadLocal hotel_id), TenantFilter Hibernate
│   └── dto/                 # PageDto, ErrorDto
├── auth/
│   ├── controller/AuthController.java
│   ├── service/JwtService.java, AuthService.java
│   └── filter/JwtAuthFilter.java
├── core/                    # DBUsers, Roles, Hotels, paramètres
│   ├── entity/  repository/  service/  controller/  dto/  mapper/
├── clients/
├── inventory/
├── finance/
├── hebergement/
├── restaurant/
├── menage/
├── reporting/
└── notification/            # Kafka producers, email
```

**Règle** : un module = un package racine sous `com.cityprojects`, contenant `entity`, `repository`, `service`, `controller`, `dto`, `mapper`. Pas de cross-référence d'entités d'un autre module dans une entité — passer par les **services**.

## 3. Patterns standards

### 3.1 Entité

> Multi-tenancy DISCRIMINATOR Hibernate natif via `@TenantId` (cf. CLAUDE.md racine §6.1). **Pas de `@Filter`/`@FilterDef`**.
> Lombok = palier 2 (Tour 2B). Pour le palier 1, getters/setters manuels.

```java
@Entity
@Table(name = "bon_commande", schema = "inventory")
public class BonCommande extends AuditableEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String numero;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;                  // discriminant tenant — populé par Hibernate

    @Enumerated(EnumType.STRING)
    private StatutBonCommande statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id")
    private Fournisseur fournisseur;

    // getters/setters manuels (palier 1 sans Lombok)
}
```

- Toujours `LAZY` pour `@ManyToOne` et `@OneToMany`.
- Toujours `AuditableEntity` (sauf entités très techniques).
- Toujours `implements TenantAware` + `@TenantId` sur `hotelId` pour les entités hôtel-scopées.
- **Ne JAMAIS** `setHotelId(...)` manuellement dans un service métier — Hibernate le populate via le resolver à l'INSERT.
- `hotel_id` **NOT NULL** + `updatable=false` + `CHECK (hotel_id > 0)` côté Liquibase (réserver `0L` au sentinel `ROOT`).
- Pas de `@Data` Lombok (équals/hashCode dangereux sur entités JPA).

### 3.2 Repository

> Avec `@TenantId`, **Hibernate ajoute automatiquement** la clause `WHERE hotel_id = ?` à TOUTES les requêtes (SELECT/UPDATE/DELETE). Plus besoin de méthodes `findByXxxAndHotelId` — Spring Data JPA standard suffit.

```java
public interface BonCommandeRepository extends JpaRepository<BonCommande, Long>,
                                                JpaSpecificationExecutor<BonCommande> {

    // Le filtre par hotelId est appliqué automatiquement par Hibernate.
    // Méthodes custom uniquement si critère métier supplémentaire :
    Optional<BonCommande> findByNumero(String numero);
    Page<BonCommande> findByStatut(StatutBonCommande statut, Pageable pageable);
    boolean existsByNumero(String numero);
}
```

⚠️ **Garde** : le service appelant DOIT être annoté `@RequireTenant` (au niveau classe) pour garantir qu'aucune requête ne tourne en mode `ROOT` (sentinel) par accident. L'aspect lève `IllegalStateException("error.tenant.missing")` sinon.

### 3.3 Service

```java
@Service
@RequireTenant                             // garde AOP — refuse l'appel sans TenantContext
@Transactional(readOnly = true)
public class BonCommandeServiceImpl implements BonCommandeService {

    private final BonCommandeRepository repo;
    private final BonCommandeMapper mapper;
    private final NumerotationService numerotation;

    public BonCommandeServiceImpl(BonCommandeRepository repo,
                                  BonCommandeMapper mapper,
                                  NumerotationService numerotation) {
        this.repo = repo;
        this.mapper = mapper;
        this.numerotation = numerotation;
    }

    @Override
    public BonCommandeDto findById(Long id) {
        // Hibernate ajoute automatiquement WHERE hotel_id = ? via @TenantId
        BonCommande bc = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("error.bonCommande.notFound"));
        return mapper.toDto(bc);
    }

    @Override
    @Transactional
    public BonCommandeDto create(BonCommandeCreateDto dto) {
        BonCommande bc = mapper.toEntity(dto);
        // PAS de bc.setHotelId(...) — Hibernate populate via le resolver à l'INSERT
        bc.setNumero(numerotation.nextBonCommande(TenantContext.get()));
        return mapper.toDto(repo.save(bc));
    }
}
```

- `@RequireTenant` au niveau classe — TOUS les services métier (clients, finance, hebergement, restaurant, inventory, menage, reporting). Pas sur auth, admin, hotel-management, schedulers, batchs.
- `@Transactional(readOnly = true)` au niveau classe, override en écriture.
- `TenantContext.get()` lève `IllegalStateException` si vide — utile quand on a besoin de la valeur (ex: génération de numéro). Pour Hibernate lui-même, le resolver fait son boulot tout seul.
- **Ne JAMAIS** lire `hotelId` depuis le DTO ni le passer en paramètre HTTP.
- Lever des exceptions métier (clés i18n) — le `@RestControllerAdvice` les traduit.
- Lombok (`@RequiredArgsConstructor`) reportable au palier 2 (Tour 2B). En palier 1, constructeur manuel.

### 3.4 Controller

```java
@RestController
@RequestMapping("/api/bons-commande")
@RequiredArgsConstructor
@Tag(name = "Inventory - Bons de commande")
public class BonCommandeController {

    private final BonCommandeService service;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonCommandeDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','GERANT','MAGASIN')")
    public ResponseEntity<BonCommandeDto> create(@Valid @RequestBody BonCommandeCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }
}
```

- `@PreAuthorize` **obligatoire** sur chaque méthode publique.
- `@Valid` sur les DTOs entrants.
- Pas de logique métier dans le controller (orchestration uniquement).

### 3.5 DTO + Mapper

```java
public record BonCommandeDto(Long id, String numero, StatutBonCommande statut,
                             FournisseurDto fournisseur, List<DemandeDto> demandes,
                             BigDecimal total, Instant createdAt) {}

public record BonCommandeCreateDto(@NotNull Long fournisseurId,
                                   @NotEmpty List<DemandeCreateDto> demandes) {}

@Mapper(componentModel = "spring", uses = FournisseurMapper.class)
public interface BonCommandeMapper {
    BonCommandeDto toDto(BonCommande entity);
    BonCommande toEntity(BonCommandeCreateDto dto);
}
```

## 4. Sécurité

- **JWT** stateless (`Authorization: Bearer ...`).
- Le filter `JwtAuthFilter` extrait `userId`, `hotelId`, `roles` et alimente :
  - `SecurityContextHolder` (auth Spring Security)
  - `TenantContext` (ThreadLocal pour `hotelId`)
  - MDC (`hotel_id`, `user_id`) pour les logs (cf. `application.yml` pattern)
- **Important** : nettoyer `TenantContext` et `MDC` en `finally` du filter.
- CORS : whitelist via `app.cors.allowed-origins` (déjà en place).

## 5. Numérotation comptable

Service central `NumerotationService` (package `finance`) — séquence par hôtel + exercice.

```
FACT-2026-MR-000001          # facture
PAY-2026-MR-000001           # paiement
BC-2026-000001               # bon de commande
BS-2026-000001               # bon de sortie
```

Implémentation : table `numerotation_sequence(hotel_id, type, exercice, last_value)` avec verrou pessimiste sur l'incrément.

## 6. Liquibase

```
src/main/resources/db/changelog/
├── db.changelog-master.xml
├── changes/
│   ├── 001-init-core.xml
│   ├── 002-clients.xml
│   ├── 003-inventory.xml
│   ├── 004-finance.xml
│   ├── 005-hebergement.xml
│   ├── 006-restaurant.xml
│   └── 007-menage.xml
```

- `liquibase.enabled: true` à activer dès la stabilisation des schémas.
- Un changeset = une modification atomique. **Jamais** de modification d'un changeset déjà appliqué — toujours un nouveau.

## 7. Tests

> **Convention Maven Surefire / Failsafe** (en place depuis Tour 3B finalisation, 2026-05-06) :
> - `*Test.java` ou `*Tests.java` → exécutés par **Surefire** sur `mvnw test` (tests unitaires rapides, pas de Spring boot).
> - `*IT.java` → exécutés par **Failsafe** sur `mvnw verify` (tests d'intégration avec Spring + JPA + H2/Testcontainers).
> - `mvnw test` ne lance PAS les `*IT` — utiliser `mvnw verify` en CI.

- **Unitaires (`*Tests`)** : services + mappers (Mockito), aspects (`AspectJProxyFactory` — voir `RequireTenantAspectTests`).
- **Intégration (`*IT`)** : controllers via `@SpringBootTest` + `@AutoConfigureMockMvc`, base **H2** (palier 1, profil `test`, voir `application-test.properties`) ou **Testcontainers PostgreSQL** (cible palier 2, Tour 2C). Voir `TenantMultiTenancyIT` comme template.
- **Sécurité** : `@WithMockUser` ou JWT factice — vérifier les 403 multi-tenant.
- **Multi-tenant** : test obligatoire sur chaque entité tenant — insérer 2 lignes pour hotel A + 1 pour hotel B, set `TenantContext`, `findAll()` doit retourner uniquement les bonnes. SQL doit montrer `WHERE hotel_id = ?` (preuve via `spring.jpa.show-sql=true`).
- Coverage cible : ≥ 70 % services, ≥ 50 % controllers.

## 8. Logs & monitoring

- SLF4J. Logs au format MDC déjà configuré.
- Niveaux : `INFO` métier, `DEBUG` dev, `WARN` cas dégradés, `ERROR` exceptions non récupérables.
- Actuator exposé : `health`, `info`, `metrics`, `prometheus` (cf. `application.yml`).

## 9. Erreurs récurrentes à NE PAS reproduire

Lire `ERREURS_AUDIT_A_EVITER.html` à la racine. Quelques classiques :
- Oublier `hotelId` dans une nouvelle requête.
- Renvoyer l'entité au lieu du DTO.
- Mettre une logique de calcul dans un mapper MapStruct.
- Utiliser `@Transactional` sur méthode `private` (no-op).
- `@OneToMany(fetch = EAGER)`.
- Catcher `Exception` sans relancer ni logger.

## 10. Pour démarrer un nouveau module backend

Utiliser la slash command `/new-entity` ou demander au sous-agent `backend-spring`. Squelette attendu :

```
<module>/
├── entity/<Entity>.java
├── repository/<Entity>Repository.java
├── dto/<Entity>Dto.java, <Entity>CreateDto.java, <Entity>UpdateDto.java
├── mapper/<Entity>Mapper.java
├── service/<Entity>Service.java, <Entity>ServiceImpl.java
└── controller/<Entity>Controller.java
```

Plus le changeset Liquibase + tests unitaires service.
