Ce que vous voulez maintenant ?

  3 options :

  - A — Tour 43 propre : intégration Dolibarr en partant de zéro avec brief verrouillé (pas d'agent qui dérive). Périmètre :
    a. Entité HotelDolibarrConfig (table core.hotel_dolibarr_config : baseUrl, apiKey chiffré Jasypt, entityId)
    b. DolibarrFeignClient (Feign + Resilience4j retry/circuit-breaker)
    c. DTOs Dolibarr (DolibarrThirdparty, DolibarrInvoice, DolibarrPayment, etc.)
    d. ParametrageComptable (mapping TypeLigneFacture → 70x, ModePaiement → 51x/53x)
    e. DolibarrSyncService (push Facture/Paiement vers Dolibarr, idempotent via ref_ext)
    f. Listeners @TransactionalEventListener sur FactureEmiseEvent + PaiementEnregistreEvent → push async Dolibarr
    g. Champ dolibarrInvoiceId / dolibarrThirdpartyId sur entités city (changeset Liquibase)
    h. Status sync (PENDING / SYNCED / FAILED) avec retry scheduler
    i. Tests Mockito (DolibarrSyncTests) + WireMock IT (DolibarrSyncIT)

  Charge estimée : 6-10 heures (un gros tour, sans hallucination). À découper en 2-3 sous-tours.
  - B — Récupérer le code Tour 35 halluciné depuis les commits orphelins (probablement encore accessibles via git reflog ou git fsck --lost-found) et audit complet avant
  intégration. Le code est probablement utilisable à 60-70% mais avec hallucinations à nettoyer. Risque qualité.
  - C — Garder l'état actuel (v1.0.0 sans Dolibarr) et planifier Vague 3 pour plus tard.

  Reco

  A si vous voulez vraiment Dolibarr opérationnel et propre. C si la comptabilité auxiliaire client suffit pour votre phase actuelle (démo, pilote, premier hôtel sans
  contraintes légales SYSCOHADA fortes).

  ⏵ A, B ou C ?