package com.cityprojects.citybackend.service.finance;

/**
 * Service interne (Tour 45) exposant le recalcul atomique d'une facture pour
 * les services cross-module (ex. {@code NuiteeServiceImpl} qui modifie le
 * prix d'une nuitee et doit recalculer la facture parente).
 *
 * <p>Separe de {@link FactureService} pour eviter d'exposer cette operation
 * de bas niveau via le REST API. Reste sous {@code @RequireTenant}.</p>
 */
public interface FactureRecalcInternalService {

    /**
     * Recalcule les montants HT / TVA / TTC d'une facture comme la somme de
     * ses lignes, puis ajuste eventuellement le statut (BROUILLON / EMISE /
     * PARTIELLEMENT_PAYEE / PAYEE) en fonction de {@code montantPaye}.
     *
     * <p><b>Atomique</b> : doit s'executer dans la transaction de l'appelant
     * (annotation {@code @Transactional} attendue sur la methode parente).</p>
     */
    void recalcMontantsFacture(Long factureId);
}
