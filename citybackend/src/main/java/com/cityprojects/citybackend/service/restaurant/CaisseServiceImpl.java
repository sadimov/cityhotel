package com.cityprojects.citybackend.service.restaurant;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.dto.restaurant.ClotureCaisseDto;
import com.cityprojects.citybackend.entity.finance.ModePaiement;
import com.cityprojects.citybackend.entity.finance.Paiement;
import com.cityprojects.citybackend.entity.finance.StatutPaiement;
import com.cityprojects.citybackend.entity.restaurant.StatutCommande;
import com.cityprojects.citybackend.repository.finance.PaiementRepository;
import com.cityprojects.citybackend.repository.restaurant.CommandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation de {@link CaisseService} (Tour 26.1).
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li>{@code @RequireTenant} au niveau classe : refuse l'appel sans tenant.</li>
 *   <li>{@code @Transactional(readOnly = true)} : lecture seule, aucune
 *       persistance (decision 3=i).</li>
 *   <li>Hibernate filtre auto par {@code hotel_id} via {@code @TenantId}.</li>
 * </ul>
 *
 * <h3>Filtre journalier</h3>
 * <p>{@code Paiement.datePaiement} est un {@code LocalDate} : pas de probleme
 * TZ pour les paiements.</p>
 *
 * <p>{@code Commande.dateCommande} est un {@code Instant} : la fenetre
 * journaliere est calculee dans la timezone serveur ({@code Africa/Nouakchott}
 * cf. {@code application.yml}). On utilise {@link ZoneId#systemDefault()}
 * pour cohenrence avec la JVM (timezone applicative).</p>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class CaisseServiceImpl implements CaisseService {

    private static final Logger logger = LoggerFactory.getLogger(CaisseServiceImpl.class);

    private final PaiementRepository paiementRepository;
    private final CommandeRepository commandeRepository;

    public CaisseServiceImpl(PaiementRepository paiementRepository,
                             CommandeRepository commandeRepository) {
        this.paiementRepository = paiementRepository;
        this.commandeRepository = commandeRepository;
    }

    @Override
    public ClotureCaisseDto statsJournalieres(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("error.cloture.date.required");
        }
        Long hotelId = TenantContext.get();

        // 1) Paiements VALIDES du jour, ventiles par mode.
        List<Paiement> paiements = paiementRepository
                .findByDatePaiementAndStatut(date, StatutPaiement.VALIDE);

        Map<ModePaiement, ClotureCaisseDto.MontantNbPair> totauxParMode =
                new EnumMap<>(ModePaiement.class);
        BigDecimal totalGlobal = BigDecimal.ZERO;

        for (Paiement p : paiements) {
            ModePaiement mode = p.getModePaiement();
            BigDecimal montant = p.getMontantTotal() != null
                    ? p.getMontantTotal() : BigDecimal.ZERO;

            ClotureCaisseDto.MontantNbPair existing = totauxParMode.get(mode);
            if (existing == null) {
                totauxParMode.put(mode, new ClotureCaisseDto.MontantNbPair(montant, 1));
            } else {
                totauxParMode.put(mode, new ClotureCaisseDto.MontantNbPair(
                        existing.montant().add(montant),
                        existing.nombre() + 1));
            }
            totalGlobal = totalGlobal.add(montant);
        }

        int nbTransactionsTotal = paiements.size();

        // 2) Compteurs commandes du jour : fenetre [date 00:00, date+1 00:00) en
        //    timezone serveur. Hibernate filtre auto par tenant.
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfDay = date.atStartOfDay(zone).toInstant();
        Instant startOfNext = date.plusDays(1).atStartOfDay(zone).toInstant();

        long nbCommandesEncaissees = commandeRepository
                .countByDateCommandeBetweenAndFactureIdIsNotNull(startOfDay, startOfNext);
        long nbCommandesAnnulees = commandeRepository
                .countByDateCommandeBetweenAndStatut(startOfDay, startOfNext,
                        StatutCommande.ANNULEE);

        ClotureCaisseDto dto = new ClotureCaisseDto(
                date,
                hotelId,
                totauxParMode,
                totalGlobal,
                nbTransactionsTotal,
                Math.toIntExact(nbCommandesEncaissees),
                Math.toIntExact(nbCommandesAnnulees),
                Instant.now());

        logger.info("Cloture caisse hotel={} date={} : total={} sur {} transactions, "
                        + "{} commandes encaissees, {} annulees",
                hotelId, date, totalGlobal, nbTransactionsTotal,
                nbCommandesEncaissees, nbCommandesAnnulees);

        return dto;
    }
}
