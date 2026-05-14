package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.finance.NumerotationSequence;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.finance.NumerotationSequenceRepository;
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

    private final NumerotationSequenceRepository sequenceRepository;
    private final HotelRepository hotelRepository;
    private final Clock clock;

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

        NumerotationSequence sequence = existing.orElseGet(
                () -> new NumerotationSequence(hotelId, type, exercice, effectiveDiscriminant));

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
}
