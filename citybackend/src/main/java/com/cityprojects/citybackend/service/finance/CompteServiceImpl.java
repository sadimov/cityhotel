package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.entity.finance.Compte;
import com.cityprojects.citybackend.entity.finance.StatutCompte;
import com.cityprojects.citybackend.entity.finance.TypeCompte;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.finance.CompteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Implementation de {@link CompteService}.
 *
 * <p>Gere le grand-livre auxiliaire client : recherche / creation idempotente
 * (par {@code clientId} ou {@code societeId}) des comptes auxiliaires
 * referencees par les factures et les paiements. Le solde {@link Compte#getSoldeActuel()}
 * est mis a jour de facon synchrone par {@link OperationCompteService}.</p>
 *
 * @see CompteService
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class CompteServiceImpl implements CompteService {

    private static final Logger logger = LoggerFactory.getLogger(CompteServiceImpl.class);

    private final CompteRepository compteRepository;

    public CompteServiceImpl(CompteRepository compteRepository) {
        this.compteRepository = compteRepository;
    }

    @Override
    @Transactional
    public Compte findOrCreateForClient(Long clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("clientId requis");
        }
        Optional<Compte> existing = compteRepository.findByClientId(clientId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Compte compte = new Compte();
        compte.setNumeroCompte("CPT-CLI-" + clientId);
        compte.setTypeCompte(TypeCompte.CLIENT);
        compte.setClientId(clientId);
        compte.setSoldeActuel(BigDecimal.ZERO);
        compte.setCreditLimite(BigDecimal.ZERO);
        compte.setStatut(StatutCompte.ACTIF);
        // Hibernate populate hotel_id via @TenantId resolver.
        Compte saved = compteRepository.save(compte);
        logger.info("Compte auxiliaire cree (CLIENT) : id={}, numero={}, clientId={}",
                saved.getCompteId(), saved.getNumeroCompte(), clientId);
        return saved;
    }

    @Override
    @Transactional
    public Compte findOrCreateForSociete(Long societeId) {
        if (societeId == null) {
            throw new IllegalArgumentException("societeId requis");
        }
        Optional<Compte> existing = compteRepository.findBySocieteId(societeId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Compte compte = new Compte();
        compte.setNumeroCompte("CPT-SOC-" + societeId);
        compte.setTypeCompte(TypeCompte.SOCIETE);
        compte.setSocieteId(societeId);
        compte.setSoldeActuel(BigDecimal.ZERO);
        compte.setCreditLimite(BigDecimal.ZERO);
        compte.setStatut(StatutCompte.ACTIF);
        Compte saved = compteRepository.save(compte);
        logger.info("Compte auxiliaire cree (SOCIETE) : id={}, numero={}, societeId={}",
                saved.getCompteId(), saved.getNumeroCompte(), societeId);
        return saved;
    }

    @Override
    public Compte findById(Long compteId) {
        return compteRepository.findById(compteId)
                .orElseThrow(() -> new ResourceNotFoundException("error.compte.notFound"));
    }

    @Override
    public Optional<Compte> findByClientId(Long clientId) {
        if (clientId == null) {
            return Optional.empty();
        }
        return compteRepository.findByClientId(clientId);
    }

    @Override
    public Optional<Compte> findBySocieteId(Long societeId) {
        if (societeId == null) {
            return Optional.empty();
        }
        return compteRepository.findBySocieteId(societeId);
    }
}
