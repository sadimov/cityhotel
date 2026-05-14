package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.entity.finance.CompteMapping;
import com.cityprojects.citybackend.entity.finance.TypeEvenementComptable;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.CompteMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Initialise les mappings comptables par défaut pour un hôtel donné.
 *
 * <h2>Pourquoi un bean séparé</h2>
 * <p>{@link CompteMappingServiceImpl} porte {@code @RequireTenant} au niveau
 * classe : l'aspect AOP refuserait un appel sans tenant courant. Or
 * l'initialisation des mappings se fait pendant la création d'un hôtel,
 * dans le contexte du service admin {@code HotelAdminServiceImpl} qui tourne
 * en mode ROOT (cross-tenant, SUPERADMIN).</p>
 *
 * <p>Ce bean dédié n'est <b>pas</b> annoté {@code @RequireTenant}. Il prend
 * lui-même la main sur le {@link TenantScope} en se positionnant sur
 * l'hôtel cible avant d'invoquer le repository tenant-aware.</p>
 *
 * <h2>Idempotence</h2>
 * <p>Si des mappings existent déjà pour le tenant cible, la méthode ne les
 * écrase pas : seuls les types non encore mappés se voient injecter leur
 * code par défaut. Permet le rejeu sans risque.</p>
 *
 * <h2>Transaction</h2>
 * <p>{@link Propagation#REQUIRES_NEW} : ouvre sa propre transaction pour
 * que les inserts soient committés indépendamment de la transaction de
 * création de l'hôtel. Si le seed plante, l'hôtel reste créé (et le seed
 * pourra être rejoué) ; si le seed réussit mais que la création d'hôtel
 * rollback, les mappings orphelins ne nuisent pas (l'isolation
 * {@code @TenantId} bloque tout accès depuis un autre hôtel).</p>
 */
@Component
public class CompteMappingInitializer {

    private static final Logger logger = LoggerFactory.getLogger(CompteMappingInitializer.class);

    private final CompteMappingRepository mappingRepository;

    public CompteMappingInitializer(CompteMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    /**
     * Crée en base un mapping par défaut pour chaque {@link TypeEvenementComptable}
     * non encore présent pour l'hôtel cible.
     *
     * @param hotelId identifiant strictement positif de l'hôtel cible.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initDefaultsForHotel(Long hotelId) {
        if (hotelId == null || hotelId <= 0L) {
            throw new BusinessException("error.mapping.hotelIdRequired");
        }
        TenantScope.runAs(hotelId, () -> {
            // Index des mappings deja existants pour ne pas ecraser une config
            // personnalisee deja posee (rejeu idempotent).
            Map<TypeEvenementComptable, CompteMapping> existing = new EnumMap<>(TypeEvenementComptable.class);
            for (CompteMapping cm : mappingRepository.findAllByOrderByTypeEvenementAsc()) {
                existing.put(cm.getTypeEvenement(), cm);
            }
            int created = 0;
            for (TypeEvenementComptable type : TypeEvenementComptable.values()) {
                if (existing.containsKey(type)) {
                    continue;
                }
                CompteMapping cm = new CompteMapping();
                cm.setTypeEvenement(type);
                cm.setCompteCode(type.defaultCompteCode());
                cm.setActif(Boolean.TRUE);
                // PAS de setHotelId : Hibernate via @TenantId.
                mappingRepository.save(cm);
                created++;
            }
            logger.info("Mappings comptables par defaut crees pour hotel {} : {} entrees",
                    hotelId, created);
        });
    }
}
