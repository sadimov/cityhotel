---
description: Scaffold complet d'une nouvelle entité backend (entity + repo + service + controller + DTO + mapper + changeset Liquibase)
argument-hint: <NomEntite> <module> [--fields="champ1:String,champ2:BigDecimal,..."]
allowed-tools: Read, Edit, Write, Bash(ls:*), Bash(cat:*)
---

Génère le squelette **complet** d'une nouvelle entité **$ARGUMENTS** dans `citybackend`.

## Fichiers à produire

Tous dans `citybackend/src/main/java/com/cityprojects/<module>/` :

### 1. `entity/<NomEntite>.java`
- Hériter de `AuditableEntity` (createdAt, updatedAt, createdBy, updatedBy).
- `@Table(name = "<snake_case>", schema = "<module>")`.
- Champ `Long hotelId` **NOT NULL** sauf si entité globale (te le dire et demander confirmation).
- Lombok : `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (pas `@Data`).
- Relations en `LAZY` par défaut.

### 2. `repository/<NomEntite>Repository.java`
- `extends JpaRepository<<NomEntite>, Long>, JpaSpecificationExecutor<<NomEntite>>`.
- Méthodes : `findByIdAndHotelId`, `findByHotelId(Long hotelId, Pageable)`, `existsByHotelIdAnd<UniqueField>`.

### 3. `dto/<NomEntite>Dto.java`, `<NomEntite>CreateDto.java`, `<NomEntite>UpdateDto.java`
- `record` Java pour les DTOs.
- Validations Jakarta : `@NotNull`, `@NotBlank`, `@Size`, `@Positive`, `@Email`…
- **Pas de champ `hotelId`** dans les DTOs entrants.

### 4. `mapper/<NomEntite>Mapper.java`
- Interface MapStruct, `@Mapper(componentModel = "spring")`.
- Méthodes : `toDto`, `toEntity(CreateDto)`, `void update(UpdateDto, @MappingTarget entity)`.
- Ignorer `id`, `hotelId`, audit fields à l'entrée.

### 5. `service/<NomEntite>Service.java` + `<NomEntite>ServiceImpl.java`
- Interface + impl, `@Service` `@RequiredArgsConstructor`.
- `@Transactional(readOnly = true)` au niveau classe.
- Méthodes CRUD : `findById`, `findAll(Pageable)`, `create`, `update`, `delete`.
- `hotelId` via `TenantContext.getCurrentHotelId()`.
- Lever `NotFoundException("error.<entite>.notFound")` si introuvable.

### 6. `controller/<NomEntite>Controller.java`
- `@RestController @RequestMapping("/api/<plural-kebab>")`.
- Toutes les méthodes annotées `@PreAuthorize(...)`. Demander à l'utilisateur quels rôles autorisés si pas évident d'après le module.
- `@Tag(name = "<Module> - <Entité>")` pour OpenAPI.
- Codes HTTP : 200 GET/PUT, 201 POST + Location, 204 DELETE.

### 7. Liquibase changeset
- `citybackend/src/main/resources/db/changelog/changes/<numéro>-<entite>.xml`.
- Référencé dans `db.changelog-master.xml`.
- Création de table + index sur `hotel_id` + contraintes FK.

### 8. Test unitaire service
- `citybackend/src/test/java/.../<NomEntite>ServiceImplTest.java`.
- Au minimum : test création (vérifie hotelId injecté), test findById (vérifie 404 si autre hôtel).

## Workflow

1. Demande à l'utilisateur les **champs métier** s'ils ne sont pas dans `--fields`.
2. Demande les **rôles** autorisés à manipuler l'entité.
3. Demande s'il y a des **relations** (FK vers autres entités).
4. Génère tous les fichiers.
5. Compile : `cd citybackend && ./mvnw compile -q`.
6. Affiche un récap des fichiers créés.

## Important

- Respecter les conventions du `citybackend/CLAUDE.md`.
- Ne **pas** générer de logique métier exotique — squelette CRUD strict, l'utilisateur ajoutera les règles métier ensuite.
- Si un fichier existe déjà → demander : compléter, écraser, ou abandonner.
