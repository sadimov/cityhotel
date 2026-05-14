package com.cityprojects.citybackend.service.finance;

import com.cityprojects.citybackend.common.tenant.RequireTenant;
import com.cityprojects.citybackend.common.tenant.TenantContext;
import com.cityprojects.citybackend.common.tenant.TenantScope;
import com.cityprojects.citybackend.entity.client.Client;
import com.cityprojects.citybackend.entity.client.Societe;
import com.cityprojects.citybackend.entity.core.Hotel;
import com.cityprojects.citybackend.entity.finance.Facture;
import com.cityprojects.citybackend.entity.finance.LigneFacture;
import com.cityprojects.citybackend.exception.ResourceNotFoundException;
import com.cityprojects.citybackend.repository.client.ClientRepository;
import com.cityprojects.citybackend.repository.client.SocieteRepository;
import com.cityprojects.citybackend.repository.core.HotelRepository;
import com.cityprojects.citybackend.repository.finance.FactureRepository;
import com.cityprojects.citybackend.repository.finance.LigneFactureRepository;
import com.cityprojects.citybackend.service.finance.pdf.FacturePdfHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation OpenPDF de la generation PDF facture (Bloc B6).
 *
 * <p>Tenant-safety :</p>
 * <ul>
 *   <li>{@link RequireTenant} au niveau classe : le service refuse tout appel
 *       sans {@code TenantContext}.</li>
 *   <li>Le {@code FactureRepository.findById} est filtre par Hibernate via
 *       {@code @TenantId} - cross-tenant retourne empty -&gt;
 *       {@link ResourceNotFoundException} et donc 404 cote HTTP.</li>
 *   <li>Le {@code Hotel} est resolu en mode ROOT (table {@code core.hotels}
 *       non tenant-scoped) via {@link TenantScope#runAsRoot} pour ne pas
 *       casser le contrat {@code @TenantId} lorsqu'une session reutilise
 *       les memes resolvers.</li>
 * </ul>
 */
@Service
@RequireTenant
@Transactional(readOnly = true)
public class FacturePdfServiceImpl implements FacturePdfService {

    private static final Logger log = LoggerFactory.getLogger(FacturePdfServiceImpl.class);

    private final FactureRepository factureRepository;
    private final LigneFactureRepository ligneRepository;
    private final HotelRepository hotelRepository;
    private final ClientRepository clientRepository;
    private final SocieteRepository societeRepository;

    public FacturePdfServiceImpl(FactureRepository factureRepository,
                                  LigneFactureRepository ligneRepository,
                                  HotelRepository hotelRepository,
                                  ClientRepository clientRepository,
                                  SocieteRepository societeRepository) {
        this.factureRepository = factureRepository;
        this.ligneRepository = ligneRepository;
        this.hotelRepository = hotelRepository;
        this.clientRepository = clientRepository;
        this.societeRepository = societeRepository;
    }

    @Override
    public byte[] generate(Long factureId) {
        if (factureId == null) {
            throw new IllegalArgumentException("factureId must not be null");
        }
        Long tenantId = TenantContext.get(); // protege par @RequireTenant aussi

        // Filtre @TenantId Hibernate : si la facture appartient a un autre
        // tenant, findById retourne empty -> 404.
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new ResourceNotFoundException("error.facture.notFound"));

        List<LigneFacture> lignes =
                ligneRepository.findByFactureIdOrderByLigneFactureIdAsc(factureId);

        // Hotel est table CORE non tenant-scoped : resolution explicite en ROOT.
        Hotel hotel = TenantScope.runAsRoot(() ->
                hotelRepository.findById(tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("error.hotel.notFound")));

        Client client = null;
        if (facture.getClientId() != null) {
            client = clientRepository.findById(facture.getClientId()).orElse(null);
        }
        Societe societe = null;
        if (facture.getSocieteId() != null) {
            societe = societeRepository.findById(facture.getSocieteId()).orElse(null);
        }

        log.info("Generation PDF facture id={} numero={} statut={} tenant={}",
                facture.getFactureId(), facture.getNumeroFacture(),
                facture.getStatut(), tenantId);
        return FacturePdfHelper.generate(hotel, facture, lignes, client, societe);
    }
}
