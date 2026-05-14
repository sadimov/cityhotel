package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.entity.finance.JournalComptable;
import com.cityprojects.citybackend.entity.finance.TypeJournal;
import com.cityprojects.citybackend.exception.BusinessException;
import com.cityprojects.citybackend.repository.finance.JournalComptableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Initialise les 6 journaux comptables standards pour un hotel donne.
 *
 * <h2>Pourquoi un bean separe</h2>
 * <p>{@link JournalComptableServiceImpl} porte {@code @RequireTenant} au
 * niveau classe : l'aspect AOP refuserait un appel sans tenant courant. Or
 * l'initialisation des journaux se fait pendant la creation d'un hotel,
 * dans le contexte du service admin {@code HotelAdminServiceImpl} qui tourne
 * en mode ROOT (cross-tenant, SUPERADMIN).</p>
 *
 * <p>Ce bean dedie n'est <b>pas</b> annote {@code @RequireTenant}. Il prend
 * lui-meme la main sur le {@link TenantScope} en se positionnant sur
 * l'hotel cible avant d'invoquer le repository tenant-aware.</p>
 *
 * <h2>Idempotence</h2>
 * <p>Si des journaux portant ces codes existent deja pour le tenant cible, ils
 * ne sont pas reecrits : seuls les codes manquants sont crees. Permet le
 * rejeu sans risque.</p>
 *
 * <h2>Transaction</h2>
 * <p>{@link Propagation#REQUIRES_NEW} : ouvre sa propre transaction pour que
 * les inserts soient committes independamment de la transaction de creation
 * de l'hotel. Si le seed plante, l'hotel reste cree (et le seed pourra etre
 * rejoue manuellement) ; si le seed reussit mais que la creation d'hotel
 * rollback, les journaux orphelins ne nuisent pas (l'isolation
 * {@code @TenantId} bloque tout acces depuis un autre hotel).</p>
 */
@Component
public class JournalComptableInitializer {

    private static final Logger logger = LoggerFactory.getLogger(JournalComptableInitializer.class);

    /**
     * Catalogue des journaux par defaut (ordre stable : VTE, ACH, BAN, CAI, OD, AVO).
     * LinkedHashMap pour garantir l'ordre d'insertion - utile pour les tests
     * deterministes et le debug.
     */
    private static final Map<String, JournalDef> DEFAULT_JOURNAUX;

    static {
        Map<String, JournalDef> map = new LinkedHashMap<>();
        map.put("VTE", new JournalDef("VTE", "Ventes", TypeJournal.VENTE));
        map.put("ACH", new JournalDef("ACH", "Achats", TypeJournal.ACHAT));
        map.put("BAN", new JournalDef("BAN", "Banque", TypeJournal.TRESORERIE));
        map.put("CAI", new JournalDef("CAI", "Caisse", TypeJournal.TRESORERIE));
        map.put("OD", new JournalDef("OD", "Operations Diverses", TypeJournal.OPERATION_DIVERSE));
        map.put("AVO", new JournalDef("AVO", "Avoirs", TypeJournal.AVOIR));
        DEFAULT_JOURNAUX = map;
    }

    private final JournalComptableRepository repository;

    public JournalComptableInitializer(JournalComptableRepository repository) {
        this.repository = repository;
    }

    /**
     * Cree les 6 journaux par defaut pour l'hotel cible (codes manquants
     * uniquement - idempotent).
     *
     * @param hotelId identifiant strictement positif de l'hotel cible.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedDefault(Long hotelId) {
        if (hotelId == null || hotelId <= 0L) {
            throw new BusinessException("error.journal.hotelIdRequired");
        }
        TenantScope.runAs(hotelId, () -> {
            int created = 0;
            for (JournalDef def : DEFAULT_JOURNAUX.values()) {
                if (repository.findByCode(def.code).isPresent()) {
                    continue;
                }
                JournalComptable j = new JournalComptable();
                j.setCode(def.code);
                j.setLibelle(def.libelle);
                j.setType(def.type);
                j.setActif(Boolean.TRUE);
                // PAS de setHotelId : Hibernate via @TenantId.
                repository.save(j);
                created++;
            }
            logger.info("Journaux comptables par defaut crees pour hotel {} : {} entrees",
                    hotelId, created);
        });
    }

    /** Definition immuable d'un journal seede par defaut. */
    private record JournalDef(String code, String libelle, TypeJournal type) {}
}
