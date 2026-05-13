package com.cityprojects.citybackend.mapper.menage;

import com.cityprojects.citybackend.dto.menage.TacheCreateDto;
import com.cityprojects.citybackend.dto.menage.TacheDto;
import com.cityprojects.citybackend.entity.hebergement.Chambre;
import com.cityprojects.citybackend.entity.menage.Personnel;
import com.cityprojects.citybackend.entity.menage.StatutTache;
import com.cityprojects.citybackend.entity.menage.Tache;
import com.cityprojects.citybackend.repository.hebergement.ChambreRepository;
import com.cityprojects.citybackend.repository.menage.PersonnelRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Mapper MapStruct entre {@link Tache} et ses DTOs.
 *
 * <p>{@code hotelId} jamais mappe depuis le DTO. Les heures reelles
 * ({@code heureDebutReelle}, {@code heureFinReelle}) sont posees par le service
 * lors des transitions {@code commencer()}/{@code terminer()}, pas via le
 * DTO.</p>
 *
 * <h3>Sous-tour A2 (mapper enrichi)</h3>
 * <p>9 champs derives ajoutes au {@link TacheDto} :</p>
 * <ul>
 *   <li>{@code numeroChambre} : jointure {@code chambreRepository.findById}.</li>
 *   <li>{@code nomPersonnel} : jointure {@code personnelRepository.findById}
 *       (utilise {@link Personnel#getNomComplet()} = "prenom + nom").</li>
 *   <li>{@code codeStatut} : {@code statut.name()}.</li>
 *   <li>{@code libelleStatut} : libelle FR via switch.</li>
 *   <li>{@code libellePriorite} : "Normale" (1) / "Urgente" (2) / "Critique" (3).</li>
 *   <li>{@code dureeMinutes} : {@code Duration.between(debut, fin).toMinutes()}
 *       si les deux instants sont presents.</li>
 *   <li>{@code enRetard} : la tache n'est ni TERMINEE ni ANNULEE et
 *       {@code datePlanifiee} est strictement anterieure a aujourd'hui (TZ
 *       du serveur via {@link Clock} injecte — Africa/Nouakchott en prod).</li>
 *   <li>{@code enCours} : {@code statut == EN_COURS}.</li>
 *   <li>{@code terminee} : {@code statut == TERMINEE}.</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * <p>Les {@code findById} ci-dessous sont tenant-scoped automatiquement
 * (Hibernate {@code @TenantId} sur Chambre et Personnel). Le mapper est
 * appele exclusivement depuis {@code TacheServiceImpl} annote
 * {@code @RequireTenant} (TenantContext garanti pose).</p>
 *
 * <h3>Architecture (abstract class)</h3>
 * <p>Classe abstraite pour permettre {@link Autowired} (impossible avec
 * une interface MapStruct simple). MapStruct genere la methode protegee
 * {@link #rawToDto(Tache)} (mapping brut champ a champ avec les 9 nouveaux
 * champs derives ignores), et la methode publique {@link #toDto(Tache)}
 * reconstruit un nouveau {@link TacheDto} en y injectant les champs
 * derives calcules a partir du repo / des helpers.</p>
 */
@Mapper(componentModel = "spring")
public abstract class TacheMapper {

    @Autowired
    protected ChambreRepository chambreRepository;

    @Autowired
    protected PersonnelRepository personnelRepository;

    @Autowired
    protected Clock clock;

    /**
     * Mapping brut entite -> record (champs persistes uniquement).
     * Les 9 champs derives sont ignores ici et remplis par {@link #toDto(Tache)}.
     */
    @Named("rawToDto")
    @Mapping(target = "numeroChambre", ignore = true)
    @Mapping(target = "nomPersonnel", ignore = true)
    @Mapping(target = "codeStatut", ignore = true)
    @Mapping(target = "libelleStatut", ignore = true)
    @Mapping(target = "libellePriorite", ignore = true)
    @Mapping(target = "dureeMinutes", ignore = true)
    @Mapping(target = "enRetard", ignore = true)
    @Mapping(target = "enCours", ignore = true)
    @Mapping(target = "terminee", ignore = true)
    protected abstract TacheDto rawToDto(Tache entity);

    /**
     * Mapping public enrichi : appelle {@link #rawToDto(Tache)} puis
     * reconstruit un nouveau record avec les 9 champs derives renseignes.
     */
    public TacheDto toDto(Tache entity) {
        if (entity == null) {
            return null;
        }
        TacheDto base = rawToDto(entity);
        StatutTache statut = entity.getStatut();
        return new TacheDto(
                base.tacheId(),
                base.chambreId(),
                base.personnelId(),
                base.statut(),
                base.typeNettoyage(),
                base.priorite(),
                base.datePlanifiee(),
                base.heureDebutPrevue(),
                base.heureFinPrevue(),
                base.heureDebutReelle(),
                base.heureFinReelle(),
                base.commentaires(),
                base.problemesDetectes(),
                base.materielUtilise(),
                base.noteQualite(),
                base.version(),
                base.createdAt(),
                base.updatedAt(),
                // Champs derives :
                resolveNumeroChambre(entity.getChambreId()),
                resolveNomPersonnel(entity.getPersonnelId()),
                statut != null ? statut.name() : null,
                resolveLibelleStatut(statut),
                resolveLibellePriorite(entity.getPriorite()),
                computeDureeMinutes(entity.getHeureDebutReelle(), entity.getHeureFinReelle()),
                isEnRetard(entity),
                statut == StatutTache.EN_COURS,
                statut == StatutTache.TERMINEE);
    }

    @Mapping(target = "tacheId", ignore = true)
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "heureDebutReelle", ignore = true)
    @Mapping(target = "heureFinReelle", ignore = true)
    @Mapping(target = "problemesDetectes", ignore = true)
    @Mapping(target = "materielUtilise", source = "materielNecessaire")
    @Mapping(target = "noteQualite", ignore = true) // rempli au terminer()
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true) // Tour 30 etape 3 : @Version, gere par Hibernate
    public abstract Tache toEntity(TacheCreateDto dto);

    // ─────────────────────────────────────────────────────────────────────
    // Helpers de calcul des champs derives (visibles pour les tests).
    // ─────────────────────────────────────────────────────────────────────

    String resolveNumeroChambre(Long chambreId) {
        if (chambreId == null) {
            return null;
        }
        return chambreRepository.findById(chambreId)
                .map(Chambre::getNumeroChambre)
                .orElse(null);
    }

    String resolveNomPersonnel(Long personnelId) {
        if (personnelId == null) {
            return null;
        }
        return personnelRepository.findById(personnelId)
                .map(Personnel::getNomComplet)
                .orElse(null);
    }

    String resolveLibelleStatut(StatutTache statut) {
        if (statut == null) {
            return null;
        }
        // if/else if au lieu de switch expression : evite la classe synthetique
        // TacheMapper$1 que Surefire ne resout pas dans certains contextes
        // (cf. retour d'experience 35.5.a, NoClassDefFoundError sur $1).
        if (statut == StatutTache.PLANIFIEE) {
            return "Planifiée";
        }
        if (statut == StatutTache.EN_COURS) {
            return "En cours";
        }
        if (statut == StatutTache.TERMINEE) {
            return "Terminée";
        }
        if (statut == StatutTache.ANNULEE) {
            return "Annulée";
        }
        return null;
    }

    String resolveLibellePriorite(Integer priorite) {
        if (priorite == null) {
            return null;
        }
        int p = priorite.intValue();
        if (p == 1) {
            return "Normale";
        }
        if (p == 2) {
            return "Urgente";
        }
        if (p == 3) {
            return "Critique";
        }
        return null;
    }

    Long computeDureeMinutes(Instant debut, Instant fin) {
        if (debut == null || fin == null) {
            return null;
        }
        return Duration.between(debut, fin).toMinutes();
    }

    /**
     * En retard si la tache n'est pas cloturee ({@code TERMINEE}/{@code ANNULEE})
     * ET que {@code datePlanifiee} est strictement anterieure a la date
     * courante (TZ du {@link Clock} injecte). L'evaluation a la granularite
     * de la journee : une tache prevue pour aujourd'hui mais non commencee
     * n'est pas (encore) "en retard" au sens de ce flag — le retard intra-
     * journee est suivi via {@code heureFinPrevue} cote vue front.
     */
    Boolean isEnRetard(Tache entity) {
        if (entity == null || entity.getDatePlanifiee() == null) {
            return false;
        }
        StatutTache statut = entity.getStatut();
        if (statut == StatutTache.TERMINEE || statut == StatutTache.ANNULEE) {
            return false;
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.systemDefault()));
        return entity.getDatePlanifiee().isBefore(today);
    }
}
