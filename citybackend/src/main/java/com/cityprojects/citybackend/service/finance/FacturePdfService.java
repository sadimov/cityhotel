package com.cityprojects.citybackend.service.finance;

/**
 * Generation PDF des factures clients (Bloc B6, 2026-05-06).
 *
 * <p>Le PDF produit est conforme aux mentions legales mauritaniennes :
 * identite hotel, identite client, lignes detaillees, totaux HT/TVA/TTC,
 * mentions de paiement parametrables, NIF hotel si renseigne, filigrane
 * diagonal pour les statuts BROUILLON et ANNULEE.</p>
 *
 * <p>Le tenant est extrait de {@code TenantContext} (annoation
 * {@link com.cityprojects.citybackend.common.tenant.RequireTenant} portee
 * par l'impl). Un appel cross-tenant aboutit a une {@link
 * com.cityprojects.citybackend.exception.ResourceNotFoundException}
 * (filtre Hibernate {@code @TenantId} sur {@code Facture}).</p>
 */
public interface FacturePdfService {

    /**
     * Genere le PDF d'une facture pour le tenant courant.
     *
     * <p>Le PDF mentionne explicitement le statut (couleur de pastille et
     * filigrane si BROUILLON ou ANNULEE).</p>
     *
     * @param factureId identifiant de la facture (tenant courant)
     * @return contenu PDF (jamais null, length &gt; 0)
     * @throws com.cityprojects.citybackend.exception.ResourceNotFoundException
     *         si la facture n'existe pas pour le tenant courant
     * @throws com.cityprojects.citybackend.exception.BusinessException
     *         {@code error.facture.pdf.failed} en cas d'echec OpenPDF
     */
    byte[] generate(Long factureId);
}
