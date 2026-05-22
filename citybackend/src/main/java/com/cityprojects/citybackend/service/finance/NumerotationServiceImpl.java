package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.finance.NumerotationSequence;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.finance.NumerotationSequenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Implementation par defaut de {@link NumerotationService}.
 * <p>
 * Strategie d'incrementation :
 * <ol>
 *   <li>Charger le compteur (type, exercice, discriminant) avec
 *       {@code SELECT ... FOR UPDATE}.</li>
 *   <li>S'il n'existe pas, creer une nouvelle ligne initialisee a 0.
 *       En cas d'insertion concurrente, le verrou pessimiste serialise
 *       les transactions : la 2e voit la ligne creee par la 1ere et la
 *       met a jour normalement.</li>
 *   <li>Incrementer {@code last_value} et flush.</li>
 *   <li>Resoudre le {@code codePays} de l'hotel et formater.</li>
 * </ol>
 *
 * <h3>Annotations cles</h3>
 * <ul>
 *   <li>{@link RequireTenant} : refuse l'appel si TenantContext est vide
 *       (garde AOP, message {@code "error.tenant.missing"}).</li>
 *   <li>{@link Transactional} (write) : indispensable, sans transaction le
 *       lock pessimiste est immediatement libere et l'incrementation
 *       perd sa garantie d'unicite.</li>
 * </ul>
 *
 * <h3>Format produit</h3>
 * <ul>
 *   <li>Sans discriminant : {@code TYPE-{exercice}-{codePays}-{6 chiffres}},
 *       ex. {@code FACT-2026-MR-000123}.</li>
 *   <li>Avec discriminant (JRN obligatoire) :
 *       {@code TYPE-{discriminant}-{exercice}-{codePays}-{6 chiffres}},
 *       ex. {@code JRN-VTE-2026-MR-000007}.</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * <p>Une invocation = un SELECT FOR UPDATE + un UPDATE + un SELECT hotel
 * (~3 round-trips DB). Acceptable pour la cible city (&lt; 100 numeros/s).
 * Pas de cache : un cache transactionnel decorrele la valeur retournee de
 * la valeur reellement persistee, ce qui defait l'invariant "pas de trou".</p>
 */
@Service
@RequireTenant
@Transactional
public class NumerotationServiceImpl implements NumerotationService {

    private static final Logger logger = LoggerFactory.getLogger(NumerotationServiceImpl.class);

    private final NumerotationSequenceRepository sequenceRepository;
    private final HotelRepository hotelRepository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public NumerotationServiceImpl(NumerotationSequenceRepository sequenceRepository,
                                   HotelRepository hotelRepository,
                                   Clock clock) {
        this.sequenceRepository = sequenceRepository;
        this.hotelRepository = hotelRepository;
        this.clock = clock;
    }

    @Override
    public String next(TypeNumerotation type) {
        return next(type, null);
    }

    @Override
    public String next(TypeNumerotation type, String discriminant) {
        if (type == null) {
            throw new IllegalArgumentException("error.numerotation.type.null");
        }
        // JRN exige un discriminant non vide (le code journal). Toute autre
        // famille tolere un discriminant null (qui devient chaine vide en BDD).
        String effectiveDiscriminant = (discriminant == null) ? "" : discriminant.trim();
        if (type == TypeNumerotation.JRN && effectiveDiscriminant.isEmpty()) {
            throw new IllegalArgumentException("error.numerotation.discriminant.required");
        }

        // TenantContext.get() leve si absent — double garde avec @RequireTenant
        // pour echouer plus tot et donner un message coherent.
        // hotelId est immuable une fois lu via TenantContext.get() :
        // NE JAMAIS le surcharger ni le re-lire depuis un DTO/payload HTTP.
        // Toute autre source (parametre HTTP, body, header) constitue une
        // violation du contrat multi-tenant.
        Long hotelId = TenantContext.get();
        Integer exercice = LocalDate.now(clock).getYear();

        Optional<NumerotationSequence> existing =
                sequenceRepository.findByTypeExerciceAndDiscriminantForUpdate(
                        type, exercice, effectiveDiscriminant);

        NumerotationSequence sequence;
        if (existing.isPresent()) {
            sequence = existing.get();
        } else {
            // Cas migration BD : une séquence n'existe pas encore pour
            // (hotel, type, exercice) mais des numéros peuvent déjà avoir
            // été émis (reprise de données, restauration partielle…).
            // On recale `last_value` sur le MAX existant pour garantir
            // l'unicité métier — sinon collision UNIQUE au prochain INSERT.
            // Cf. consigne user 2026-05-22 : « numérotations successives
            // pour chaque hôtel à part, pas de sauts, pas d'interférence ».
            sequence = new NumerotationSequence(hotelId, type, exercice, effectiveDiscriminant);
            long recalibratedFloor = findMaxExistingValueForRecalibration(
                    type, exercice, effectiveDiscriminant, hotelId);
            if (recalibratedFloor > 0L) {
                logger.info("Recalibrage seq {} hotel={} exercice={} disc={} : MAX existant = {}",
                        type, hotelId, exercice, effectiveDiscriminant, recalibratedFloor);
                sequence.setLastValue(recalibratedFloor);
            }
        }

        long nextValue = sequence.getLastValue() + 1L;
        sequence.setLastValue(nextValue);
        // saveAndFlush garantit qu'en cas d'erreur posterieure dans la transaction
        // appelante (insertion d'une facture par exemple), le compteur soit coherent
        // avec le rollback global. Le save() classique suffirait, mais flush rend
        // explicite l'INSERT/UPDATE et facilite le debug si une contrainte casse.
        NumerotationSequence persisted = sequenceRepository.saveAndFlush(sequence);

        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new IllegalStateException("error.hotel.notFound"));

        // Format avec ou sans discriminant
        if (effectiveDiscriminant.isEmpty()) {
            return String.format("%s-%d-%s-%06d",
                    type.name(),
                    exercice,
                    hotel.getCodePays(),
                    persisted.getLastValue());
        }
        return String.format("%s-%s-%d-%s-%06d",
                type.name(),
                effectiveDiscriminant,
                exercice,
                hotel.getCodePays(),
                persisted.getLastValue());
    }

    /**
     * Cherche dans la table cible le MAX du compteur déjà émis pour ce
     * (type, exercice, discriminant, hotelId). Retourne 0 si aucune
     * occurrence (cas nominal nouvel hôtel) ou si le type ne mappe pas
     * vers une table connue.
     *
     * <p>Le pattern de numéro est {@code TYPE-{disc-}EXERCICE-CODEPAYS-NNNNNN}.
     * On extrait les 6 derniers chiffres via {@code SUBSTRING + CAST}
     * portable Postgres/H2.</p>
     *
     * <p>Hibernate {@code @TenantId} ajoute automatiquement
     * {@code WHERE hotel_id = ?} aux requêtes JPA — le scope par hôtel est
     * garanti sans paramètre explicite.</p>
     */
    private long findMaxExistingValueForRecalibration(TypeNumerotation type,
                                                       Integer exercice,
                                                       String discriminant,
                                                       Long hotelId) {
        try {
            String jpql = switch (type) {
                case FACT, AVOIR -> "SELECT MAX(CAST(SUBSTRING(f.numeroFacture, "
                        + "LENGTH(f.numeroFacture) - 5, 6) AS integer)) "
                        + "FROM Facture f WHERE f.numeroFacture LIKE :pattern";
                case PAY -> "SELECT MAX(CAST(SUBSTRING(p.numeroPaiement, "
                        + "LENGTH(p.numeroPaiement) - 5, 6) AS integer)) "
                        + "FROM Paiement p WHERE p.numeroPaiement LIKE :pattern";
                case RES -> "SELECT MAX(CAST(SUBSTRING(r.numeroReservation, "
                        + "LENGTH(r.numeroReservation) - 5, 6) AS integer)) "
                        + "FROM Reservation r WHERE r.numeroReservation LIKE :pattern";
                case BC -> "SELECT MAX(CAST(SUBSTRING(b.numeroBc, "
                        + "LENGTH(b.numeroBc) - 5, 6) AS integer)) "
                        + "FROM BonCommande b WHERE b.numeroBc LIKE :pattern";
                case BS -> "SELECT MAX(CAST(SUBSTRING(b.numeroBs, "
                        + "LENGTH(b.numeroBs) - 5, 6) AS integer)) "
                        + "FROM BonSortie b WHERE b.numeroBs LIKE :pattern";
                case CLI -> "SELECT MAX(CAST(SUBSTRING(c.numeroClient, "
                        + "LENGTH(c.numeroClient) - 5, 6) AS integer)) "
                        + "FROM Client c WHERE c.numeroClient LIKE :pattern";
                case COMM -> "SELECT MAX(CAST(SUBSTRING(c.numeroCommande, "
                        + "LENGTH(c.numeroCommande) - 5, 6) AS integer)) "
                        + "FROM Commande c WHERE c.numeroCommande LIKE :pattern";
                // JRN (écritures comptables) : recalcul complexe par journal
                // (discriminant). Pas géré ici — repart à 0 en migration.
                // PROD : codes libres user-saisis, pas de pattern strict.
                default -> null;
            };
            if (jpql == null) {
                return 0L;
            }
            String prefix = discriminant.isEmpty()
                    ? String.format("%s-%d-", type.name(), exercice)
                    : String.format("%s-%s-%d-", type.name(), discriminant, exercice);
            Object result = entityManager.createQuery(jpql)
                    .setParameter("pattern", prefix + "%")
                    .getSingleResult();
            if (result == null) {
                return 0L;
            }
            return ((Number) result).longValue();
        } catch (Exception e) {
            // Fail-safe : tout échec de recalibrage retombe à 0. Mieux vaut
            // démarrer à 1 et collisionner visiblement (l'INSERT lèvera
            // une erreur) que masquer un problème de schéma.
            logger.warn("Recalibrage seq {} hotel={} exercice={} echoue : {}",
                    type, hotelId, exercice, e.getMessage());
            return 0L;
        }
    }
}
