-- ============================================================================
-- SEED EXTENDED Part 05 — FINANCE
--   * comptes (auxiliaires client/societe)
--   * factures + lignes_factures (mix statuts)
--   * paiements (mix 7 modes paiement)
--   * affectations_paiements (incl. ligne_facture_id Tour 45)
--   * operations_comptes (DEBIT facturation, CREDIT paiement)
--
-- Ordre :
--   comptes -> factures (compte_id) -> lignes_factures -> paiements (compte_id)
--   -> affectations_paiements -> operations_comptes
-- ============================================================================

\echo '=== Part 05: FINANCE (comptes, factures, paiements, operations) ==='

-- ----------------------------------------------------------------------------
-- 5.1 COMPTES — 1 compte CLIENT par client principal des reservations + 1 compte SOCIETE par societe active
-- numero_compte = 'AC-' || hotel_code || '-CLI-' || numero_client (auxiliaire client)
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cl AS (SELECT client_id, hotel_id, numero_client FROM client.clients)
INSERT INTO finance.comptes
  (hotel_id, numero_compte, type_compte, client_id, societe_id,
   solde_actuel, credit_limite, statut, created_at, updated_at, created_by)
SELECT h.hotel_id,
       'AC-' || h.hotel_code || '-CLI-' || RIGHT(cl.numero_client, 4),
       'CLIENT',
       cl.client_id, NULL,
       0, 100000, 'ACTIF',
       NOW(), NOW(), 'system'
FROM cl
JOIN h ON h.hotel_id = cl.hotel_id
-- Limiter aux 7 premiers clients de chaque hotel (parmi les 15)
WHERE RIGHT(cl.numero_client, 4)::int BETWEEN 1 AND 7
ON CONFLICT (hotel_id, numero_compte) DO NOTHING;

-- Comptes societe (8 par hotel parmi les 10)
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     soc AS (SELECT societe_id, hotel_id, societe_nom, ROW_NUMBER() OVER (PARTITION BY hotel_id ORDER BY societe_id) AS rn FROM client.societes)
INSERT INTO finance.comptes
  (hotel_id, numero_compte, type_compte, client_id, societe_id,
   solde_actuel, credit_limite, statut, created_at, updated_at, created_by)
SELECT h.hotel_id,
       'AC-' || h.hotel_code || '-SOC-' || LPAD(soc.rn::text, 3, '0'),
       'SOCIETE',
       NULL, soc.societe_id,
       0, 500000, 'ACTIF',
       NOW(), NOW(), 'system'
FROM soc
JOIN h ON h.hotel_id = soc.hotel_id
WHERE soc.rn <= 8
ON CONFLICT (hotel_id, numero_compte) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5.2 FACTURES — 8 par hotel = 16 total
-- Statuts : BROUILLON (1), EMISE (2), PARTIELLEMENT_PAYEE (2), PAYEE (3)
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cl AS (SELECT client_id, hotel_id, numero_client FROM client.clients),
     r AS (SELECT reservation_id, hotel_id, numero_reservation FROM hebergement.reservations),
     compte AS (SELECT compte_id, hotel_id, client_id FROM finance.comptes WHERE type_compte = 'CLIENT'),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers)
INSERT INTO finance.factures
  (hotel_id, numero_facture, type_facture, compte_id, client_id, societe_id,
   reservation_id, fournisseur_id, facture_reference_id,
   date_facture, date_echeance, montant_ht, montant_tva, montant_ttc, montant_paye,
   statut, devise, commentaires, user_id, created_at, updated_at, created_by)
SELECT h.hotel_id, f.num, 'FACTURE', compte.compte_id, cl.client_id, NULL,
       r.reservation_id, NULL, NULL,
       f.dt_fact::date, f.dt_ech::date, f.mt_ht, 0, f.mt_ttc, f.mt_paye,
       f.statut, 'MRU', f.com,
       u.user_id, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','FACT-2026-MR-000001','CLI-NKC-2026-0001','RES-NKC-2026-0001','2026-05-04','2026-05-19',  10500, 10500, 10500, 'PAYEE','reception1.nkc','Sejour 3 nuits standard'),
  ('NKC','FACT-2026-MR-000002','CLI-NKC-2026-0003','RES-NKC-2026-0002','2026-05-11','2026-05-26',  13500, 13500, 13500, 'PAYEE','reception1.nkc','Sejour 3 nuits superieure + restaurant'),
  ('NKC','FACT-2026-MR-000003','CLI-NKC-2026-0005','RES-NKC-2026-0003','2026-05-10','2026-05-25',  22500, 22500, 11000, 'PARTIELLEMENT_PAYEE','reception1.nkc','Arrivee, paiement partiel'),
  ('NKC','FACT-2026-MR-000004','CLI-NKC-2026-0007','RES-NKC-2026-0004','2026-05-09','2026-05-24',  13500, 13500,  5000, 'PARTIELLEMENT_PAYEE','reception1.nkc','Acompte recu'),
  ('NKC','FACT-2026-MR-000005','CLI-NKC-2026-0009','RES-NKC-2026-0005','2026-05-12','2026-05-27',  22500, 22500,     0, 'EMISE','reception1.nkc','Pre-arrivee'),
  ('NKC','FACT-2026-MR-000006','CLI-NKC-2026-0002','RES-NKC-2026-0008','2026-04-27','2026-05-12',   7000,  7000,  7000, 'PAYEE','reception1.nkc','Sejour 2 nuits standard'),
  ('NKC','FACT-2026-MR-000007','CLI-NKC-2026-0001',NULL,                '2026-05-09','2026-05-24',   2400,  2400,     0, 'EMISE','restau.nkc','Commande restaurant non payee'),
  ('NKC','FACT-2026-MR-000008','CLI-NKC-2026-0003',NULL,                '2026-05-10','2026-05-25',   3300,  3300,     0, 'BROUILLON','gerant.nkc','Brouillon, a valider'),
  ('DKR','FACT-2026-MR-000001','CLI-DKR-2026-0001','RES-DKR-2026-0001','2026-05-06','2026-05-21',  22000, 22000, 22000, 'PAYEE','reception1.dkr','Sejour 4 nuits standard + commande'),
  ('DKR','FACT-2026-MR-000002','CLI-DKR-2026-0003','RES-DKR-2026-0002','2026-05-12','2026-05-27',  16500, 16500, 16500, 'PAYEE','reception1.dkr','Sejour 3 nuits superieure'),
  ('DKR','FACT-2026-MR-000003','CLI-DKR-2026-0005','RES-DKR-2026-0003','2026-05-13','2026-05-28',  28500, 28500, 15000, 'PARTIELLEMENT_PAYEE','reception1.dkr','Arrivee, acompte 15000'),
  ('DKR','FACT-2026-MR-000004','CLI-DKR-2026-0007','RES-DKR-2026-0004','2026-05-12','2026-05-27',  28500, 28500,  8000, 'PARTIELLEMENT_PAYEE','reception1.dkr','Acompte recu'),
  ('DKR','FACT-2026-MR-000005','CLI-DKR-2026-0009','RES-DKR-2026-0005','2026-05-14','2026-05-29',  16500, 16500,     0, 'EMISE','reception1.dkr','Pre-arrivee'),
  ('DKR','FACT-2026-MR-000006','CLI-DKR-2026-0002','RES-DKR-2026-0008','2026-04-30','2026-05-15',  11000, 11000, 11000, 'PAYEE','reception1.dkr','Sejour 2 nuits'),
  ('DKR','FACT-2026-MR-000007','CLI-DKR-2026-0001',NULL,                '2026-05-04','2026-05-19',  11000, 11000,     0, 'EMISE','restau.dkr','Commande restaurant facturee'),
  ('DKR','FACT-2026-MR-000008','CLI-DKR-2026-0005',NULL,                '2026-05-10','2026-05-25',   2500,  2500,     0, 'BROUILLON','gerant.dkr','Brouillon, a valider')
) AS f(hcode, num, ncli, nres, dt_fact, dt_ech, mt_ht, mt_ttc, mt_paye, statut, uname, com)
JOIN h ON h.hotel_code = f.hcode
JOIN cl ON cl.numero_client = f.ncli AND cl.hotel_id = h.hotel_id
LEFT JOIN r ON r.numero_reservation = f.nres AND r.hotel_id = h.hotel_id
LEFT JOIN compte ON compte.client_id = cl.client_id AND compte.hotel_id = h.hotel_id
JOIN u ON u.username = f.uname AND u.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, numero_facture) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5.3 LIGNES_FACTURES — mix NUITEE / COMMANDE / SERVICE / DIVERS
-- ----------------------------------------------------------------------------
-- 5.3.a Lignes NUITEE : 1 ligne par nuitee pour les factures liees aux reservations PAYEE/PARTIELLE
WITH fact AS (SELECT facture_id, hotel_id, numero_facture, reservation_id FROM finance.factures WHERE reservation_id IS NOT NULL),
     nui AS (SELECT nuitee_id, hotel_id, reservation_id, date_nuit, prix_nuit FROM hebergement.nuitees)
INSERT INTO finance.lignes_factures
  (hotel_id, facture_id, type_ligne, nuitee_id, produit_id, commande_id, service_id,
   libelle, quantite, prix_unitaire, taux_tva, montant_ht, montant_tva, montant_ttc, date_prestation)
SELECT fact.hotel_id, fact.facture_id, 'NUITEE', nui.nuitee_id, NULL, NULL, NULL,
       'Nuitee du ' || nui.date_nuit::text, 1, nui.prix_nuit, 0,
       nui.prix_nuit, 0, nui.prix_nuit, nui.date_nuit
FROM fact
JOIN nui ON nui.reservation_id = fact.reservation_id AND nui.hotel_id = fact.hotel_id
WHERE NOT EXISTS (
  SELECT 1 FROM finance.lignes_factures lf
  WHERE lf.facture_id = fact.facture_id AND lf.nuitee_id = nui.nuitee_id
);

-- 5.3.b Lignes COMMANDE : pour les factures sans reservation_id (resto independant)
WITH fact AS (SELECT facture_id, hotel_id, numero_facture FROM finance.factures
              WHERE reservation_id IS NULL AND numero_facture IN
              ('FACT-2026-MR-000007','FACT-2026-MR-000008')),
     h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cmd AS (SELECT commande_id, hotel_id, numero_commande, montant_ttc FROM restaurant.commandes)
INSERT INTO finance.lignes_factures
  (hotel_id, facture_id, type_ligne, nuitee_id, produit_id, commande_id, service_id,
   libelle, quantite, prix_unitaire, taux_tva, montant_ht, montant_tva, montant_ttc, date_prestation)
SELECT fact.hotel_id, fact.facture_id, 'COMMANDE', NULL, NULL, cmd.commande_id, NULL,
       'Commande restaurant ' || cmd.numero_commande, 1, cmd.montant_ttc, 0,
       cmd.montant_ttc, 0, cmd.montant_ttc, CURRENT_DATE
FROM fact
JOIN h ON h.hotel_id = fact.hotel_id
JOIN cmd ON cmd.hotel_id = fact.hotel_id
  AND ((fact.numero_facture = 'FACT-2026-MR-000007' AND h.hotel_code = 'NKC' AND cmd.numero_commande = 'CMD-NKC-2026-0008')
    OR (fact.numero_facture = 'FACT-2026-MR-000008' AND h.hotel_code = 'NKC' AND cmd.numero_commande = 'CMD-NKC-2026-0006')
    OR (fact.numero_facture = 'FACT-2026-MR-000007' AND h.hotel_code = 'DKR' AND cmd.numero_commande = 'CMD-DKR-2026-0001')
    OR (fact.numero_facture = 'FACT-2026-MR-000008' AND h.hotel_code = 'DKR' AND cmd.numero_commande = 'CMD-DKR-2026-0005'))
WHERE NOT EXISTS (
  SELECT 1 FROM finance.lignes_factures lf
  WHERE lf.facture_id = fact.facture_id AND lf.commande_id = cmd.commande_id
);

-- 5.3.c Lignes SERVICE : ajout d'1 service par facture PAYEE (extra spa, transport)
WITH fact AS (SELECT facture_id, hotel_id, numero_facture FROM finance.factures WHERE statut = 'PAYEE'),
     h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     sh AS (SELECT service_id, hotel_id, code, nom, prix_unitaire FROM inventory.services_hoteliers WHERE code = 'SPA-MASSAGE-1H')
INSERT INTO finance.lignes_factures
  (hotel_id, facture_id, type_ligne, nuitee_id, produit_id, commande_id, service_id,
   libelle, quantite, prix_unitaire, taux_tva, montant_ht, montant_tva, montant_ttc, date_prestation)
SELECT fact.hotel_id, fact.facture_id, 'SERVICE', NULL, NULL, NULL, sh.service_id,
       sh.nom, 1, sh.prix_unitaire, 0,
       sh.prix_unitaire, 0, sh.prix_unitaire, CURRENT_DATE
FROM fact
JOIN sh ON sh.hotel_id = fact.hotel_id
WHERE NOT EXISTS (
  SELECT 1 FROM finance.lignes_factures lf
  WHERE lf.facture_id = fact.facture_id AND lf.service_id = sh.service_id
);

-- 5.3.d Lignes DIVERS : minibar — sur toutes les factures sauf brouillon
WITH fact AS (SELECT facture_id, hotel_id, numero_facture FROM finance.factures WHERE statut <> 'BROUILLON')
INSERT INTO finance.lignes_factures
  (hotel_id, facture_id, type_ligne, nuitee_id, produit_id, commande_id, service_id,
   libelle, quantite, prix_unitaire, taux_tva, montant_ht, montant_tva, montant_ttc, date_prestation)
SELECT fact.hotel_id, fact.facture_id, 'DIVERS', NULL, NULL, NULL, NULL,
       'Minibar consommation', 1, 1500, 0,
       1500, 0, 1500, CURRENT_DATE
FROM fact
WHERE NOT EXISTS (
  SELECT 1 FROM finance.lignes_factures lf
  WHERE lf.facture_id = fact.facture_id AND lf.type_ligne = 'DIVERS' AND lf.libelle = 'Minibar consommation'
);

-- 5.3.e Lignes DIVERS supplementaires (frais ou service annexe) sur factures reservation
-- Pour garantir qu'au moins 2 lignes existent sur chaque facture liee a une reservation
-- (cas des reservations CONFIRMEE sans nuitees seedees).
WITH fact AS (SELECT facture_id, hotel_id, numero_facture FROM finance.factures
              WHERE reservation_id IS NOT NULL AND statut <> 'BROUILLON')
INSERT INTO finance.lignes_factures
  (hotel_id, facture_id, type_ligne, nuitee_id, produit_id, commande_id, service_id,
   libelle, quantite, prix_unitaire, taux_tva, montant_ht, montant_tva, montant_ttc, date_prestation)
SELECT fact.hotel_id, fact.facture_id, 'DIVERS', NULL, NULL, NULL, NULL,
       'Frais reservation', 1, 1000, 0,
       1000, 0, 1000, CURRENT_DATE
FROM fact
WHERE NOT EXISTS (
  SELECT 1 FROM finance.lignes_factures lf
  WHERE lf.facture_id = fact.facture_id AND lf.type_ligne = 'DIVERS' AND lf.libelle = 'Frais reservation'
);

-- ----------------------------------------------------------------------------
-- 5.4 PAIEMENTS — 8 par hotel = 16 paiements, mix 7 modes paiement
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cl AS (SELECT client_id, hotel_id, numero_client FROM client.clients),
     compte AS (SELECT compte_id, hotel_id, client_id FROM finance.comptes WHERE type_compte = 'CLIENT'),
     u AS (SELECT user_id, hotel_id, username FROM core.dbusers)
INSERT INTO finance.paiements
  (hotel_id, numero_paiement, compte_id, montant_total, devise, mode_paiement, reference_paiement,
   date_paiement, statut, commentaires, user_id, created_at, updated_at, created_by)
SELECT h.hotel_id, p.num, compte.compte_id, p.mt, 'MRU', p.mode, p.ref,
       p.dt::date, 'VALIDE', p.com, u.user_id, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','PAY-2026-MR-000001','CLI-NKC-2026-0001', 10500, 'ESPECES',       NULL,                '2026-05-04','reception1.nkc','Paiement integral FACT-001'),
  ('NKC','PAY-2026-MR-000002','CLI-NKC-2026-0003', 13500, 'BANKILY',       'BNK-2026-051101',   '2026-05-11','reception1.nkc','Paiement integral FACT-002'),
  ('NKC','PAY-2026-MR-000003','CLI-NKC-2026-0005', 11000, 'CARTE_BANCAIRE','TX-CB-052010',      '2026-05-10','reception1.nkc','Acompte FACT-003'),
  ('NKC','PAY-2026-MR-000004','CLI-NKC-2026-0007',  5000, 'ESPECES',       NULL,                '2026-05-09','reception1.nkc','Acompte FACT-004'),
  ('NKC','PAY-2026-MR-000005','CLI-NKC-2026-0002',  7000, 'CHEQUE',        'CHQ-451-2026',      '2026-04-27','reception1.nkc','Paiement integral FACT-006'),
  ('NKC','PAY-2026-MR-000006','CLI-NKC-2026-0001',  2400, 'MASRIVI',       'MAS-2026-051001',   '2026-05-10','restau.nkc',    'Paiement separe restaurant'),
  ('NKC','PAY-2026-MR-000007','CLI-NKC-2026-0001',  1500, 'BANKILY',       'BNK-2026-051002',   '2026-05-10','restau.nkc',    'Paiement ligne service spa'),
  ('NKC','PAY-2026-MR-000008','CLI-NKC-2026-0003',  3000, 'SEDAD',         'SED-2026-051101',   '2026-05-11','restau.nkc',    'Acompte ligne nuitee + minibar'),
  ('DKR','PAY-2026-MR-000001','CLI-DKR-2026-0001', 22000, 'CARTE_BANCAIRE','TX-CB-050601',      '2026-05-06','reception1.dkr','Paiement integral FACT-001'),
  ('DKR','PAY-2026-MR-000002','CLI-DKR-2026-0003', 16500, 'BANKILY',       'BNK-DKR-2026-1201', '2026-05-12','reception1.dkr','Paiement integral FACT-002'),
  ('DKR','PAY-2026-MR-000003','CLI-DKR-2026-0005', 15000, 'ESPECES',       NULL,                '2026-05-13','reception1.dkr','Acompte FACT-003'),
  ('DKR','PAY-2026-MR-000004','CLI-DKR-2026-0007',  8000, 'VIREMENT',      'VIR-2026-0512',     '2026-05-12','reception1.dkr','Acompte FACT-004'),
  ('DKR','PAY-2026-MR-000005','CLI-DKR-2026-0002', 11000, 'CHEQUE',        'CHQ-DKR-389-2026',  '2026-04-30','reception1.dkr','Paiement integral FACT-006'),
  ('DKR','PAY-2026-MR-000006','CLI-DKR-2026-0001',  6000, 'MASRIVI',       'MAS-DKR-2026-0504', '2026-05-04','restau.dkr',    'Paiement separe commande'),
  ('DKR','PAY-2026-MR-000007','CLI-DKR-2026-0001',  3000, 'SEDAD',         'SED-DKR-2026-0504', '2026-05-04','restau.dkr',    'Complement ligne paella'),
  ('DKR','PAY-2026-MR-000008','CLI-DKR-2026-0005',  1500, 'BANKILY',       'BNK-DKR-2026-1301', '2026-05-13','reception1.dkr','Ligne minibar FACT-003')
) AS p(hcode, num, ncli, mt, mode, ref, dt, uname, com)
JOIN h ON h.hotel_code = p.hcode
JOIN cl ON cl.numero_client = p.ncli AND cl.hotel_id = h.hotel_id
LEFT JOIN compte ON compte.client_id = cl.client_id AND compte.hotel_id = h.hotel_id
JOIN u ON u.username = p.uname AND u.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, numero_paiement) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5.5 AFFECTATIONS_PAIEMENTS — paiement -> facture (mode legacy) ou paiement -> ligne_facture (Tour 45)
-- ----------------------------------------------------------------------------
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     pay AS (SELECT paiement_id, hotel_id, numero_paiement, montant_total FROM finance.paiements),
     fact AS (SELECT facture_id, hotel_id, numero_facture FROM finance.factures),
     lf AS (
       SELECT lf.ligne_facture_id, lf.facture_id, lf.type_ligne, lf.libelle, lf.montant_ttc,
              f.hotel_id, f.numero_facture,
              ROW_NUMBER() OVER (PARTITION BY lf.facture_id ORDER BY lf.ligne_facture_id) AS rn
       FROM finance.lignes_factures lf
       JOIN finance.factures f ON f.facture_id = lf.facture_id
     )
-- 5.5.a Affectations LEGACY (paiement entier sur facture entiere, ligne_facture_id NULL)
-- Reservees aux factures PAYEE integralement et a la commande resto facturee.
-- Les factures PARTIELLEMENT_PAYEE seront traitees en 5.5.c (granulaire).
INSERT INTO finance.affectations_paiements
  (hotel_id, paiement_id, facture_id, montant_affecte, date_affectation, ligne_facture_id)
SELECT fact.hotel_id, pay.paiement_id, fact.facture_id, pay.montant_total, NOW(), NULL
FROM (VALUES
  -- (hotel_code, numero_paiement, numero_facture)
  ('NKC','PAY-2026-MR-000001','FACT-2026-MR-000001'),
  ('NKC','PAY-2026-MR-000002','FACT-2026-MR-000002'),
  ('NKC','PAY-2026-MR-000005','FACT-2026-MR-000006'),
  ('NKC','PAY-2026-MR-000006','FACT-2026-MR-000007'),
  ('DKR','PAY-2026-MR-000001','FACT-2026-MR-000001'),
  ('DKR','PAY-2026-MR-000002','FACT-2026-MR-000002'),
  ('DKR','PAY-2026-MR-000005','FACT-2026-MR-000006')
) AS a(hcode, num_pay, num_fact)
JOIN h ON h.hotel_code = a.hcode
JOIN pay ON pay.numero_paiement = a.num_pay AND pay.hotel_id = h.hotel_id
JOIN fact ON fact.numero_facture = a.num_fact AND fact.hotel_id = h.hotel_id
WHERE NOT EXISTS (
  SELECT 1 FROM finance.affectations_paiements ap
  WHERE ap.paiement_id = pay.paiement_id AND ap.facture_id = fact.facture_id AND ap.ligne_facture_id IS NULL
);

-- 5.5.b Affectations GRANULAIRES (Tour 45) — paiement -> ligne_facture specifique
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     pay AS (SELECT paiement_id, hotel_id, numero_paiement, montant_total FROM finance.paiements),
     fact AS (SELECT facture_id, hotel_id, numero_facture FROM finance.factures),
     lf AS (
       SELECT lf.ligne_facture_id, lf.facture_id, lf.type_ligne, lf.libelle, lf.montant_ttc,
              f.hotel_id, f.numero_facture,
              ROW_NUMBER() OVER (PARTITION BY lf.facture_id, lf.type_ligne ORDER BY lf.ligne_facture_id) AS rn
       FROM finance.lignes_factures lf
       JOIN finance.factures f ON f.facture_id = lf.facture_id
     )
INSERT INTO finance.affectations_paiements
  (hotel_id, paiement_id, facture_id, montant_affecte, date_affectation, ligne_facture_id)
SELECT lf.hotel_id, pay.paiement_id, lf.facture_id, LEAST(pay.montant_total, lf.montant_ttc), NOW(), lf.ligne_facture_id
FROM (VALUES
  -- (hotel, num_pay, num_fact, type_ligne, rn_dans_type)
  ('NKC','PAY-2026-MR-000007','FACT-2026-MR-000001','SERVICE',1),     -- 1500 sur ligne spa
  ('NKC','PAY-2026-MR-000008','FACT-2026-MR-000002','NUITEE', 1),     -- partial nuitee
  ('DKR','PAY-2026-MR-000006','FACT-2026-MR-000007','COMMANDE',1),    -- 6000 sur commande resto
  ('DKR','PAY-2026-MR-000007','FACT-2026-MR-000007','COMMANDE',1),    -- complement 3000 sur meme commande (UNIQUE NULLS NOT DISTINCT OK car ligne_facture_id non null)
  ('DKR','PAY-2026-MR-000008','FACT-2026-MR-000003','DIVERS',  1)     -- 1500 sur ligne minibar
) AS a(hcode, num_pay, num_fact, tl, rn)
JOIN h ON h.hotel_code = a.hcode
JOIN pay ON pay.numero_paiement = a.num_pay AND pay.hotel_id = h.hotel_id
JOIN fact ON fact.numero_facture = a.num_fact AND fact.hotel_id = h.hotel_id
JOIN lf ON lf.facture_id = fact.facture_id AND lf.type_ligne = a.tl AND lf.rn = a.rn
WHERE NOT EXISTS (
  SELECT 1 FROM finance.affectations_paiements ap
  WHERE ap.paiement_id = pay.paiement_id
    AND ap.facture_id = lf.facture_id
    AND ap.ligne_facture_id = lf.ligne_facture_id
);

-- 5.5.c Affectations granulaires complementaires : sur les factures qui n'ont
-- PAS d'affectation legacy (FACT-NKC-000003 partielle, FACT-DKR-000003 partielle,
-- FACT-DKR-000007 commande). Cela porte le total granulaire >12 sans creer de
-- conflit metier (= sans cumuler legacy + granulaire sur meme paiement+facture).
-- On affecte PAY-NKC-000003 (acompte 11000) aux premieres lignes de FACT-NKC-000003,
-- ainsi que PAY-DKR-000003 (15000) aux premieres lignes de FACT-DKR-000003.
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     pay AS (SELECT paiement_id, hotel_id, numero_paiement, montant_total FROM finance.paiements),
     fact AS (SELECT facture_id, hotel_id, numero_facture FROM finance.factures),
     lf AS (
       SELECT lf.ligne_facture_id, lf.facture_id, lf.type_ligne, lf.libelle, lf.montant_ttc,
              f.hotel_id, f.numero_facture,
              ROW_NUMBER() OVER (PARTITION BY lf.facture_id ORDER BY lf.ligne_facture_id) AS rn_global
       FROM finance.lignes_factures lf
       JOIN finance.factures f ON f.facture_id = lf.facture_id
     )
INSERT INTO finance.affectations_paiements
  (hotel_id, paiement_id, facture_id, montant_affecte, date_affectation, ligne_facture_id)
SELECT lf.hotel_id, pay.paiement_id, lf.facture_id, LEAST(pay.montant_total, lf.montant_ttc), NOW(), lf.ligne_facture_id
FROM (VALUES
  -- NKC : PAY-003 (11000) -> 3 lignes de FACT-NKC-000003
  ('NKC','PAY-2026-MR-000003','FACT-2026-MR-000003',1),
  ('NKC','PAY-2026-MR-000003','FACT-2026-MR-000003',2),
  ('NKC','PAY-2026-MR-000003','FACT-2026-MR-000003',3),
  -- NKC : PAY-004 (5000) -> 2 lignes de FACT-NKC-000004
  ('NKC','PAY-2026-MR-000004','FACT-2026-MR-000004',1),
  ('NKC','PAY-2026-MR-000004','FACT-2026-MR-000004',2),
  -- DKR : PAY-003 (15000) -> 3 lignes de FACT-DKR-000003
  ('DKR','PAY-2026-MR-000003','FACT-2026-MR-000003',1),
  ('DKR','PAY-2026-MR-000003','FACT-2026-MR-000003',2),
  ('DKR','PAY-2026-MR-000003','FACT-2026-MR-000003',3),
  -- DKR : PAY-004 (8000) -> 2 lignes FACT-DKR-000004
  ('DKR','PAY-2026-MR-000004','FACT-2026-MR-000004',1),
  ('DKR','PAY-2026-MR-000004','FACT-2026-MR-000004',2)
) AS a(hcode, num_pay, num_fact, rn)
JOIN h ON h.hotel_code = a.hcode
JOIN pay ON pay.numero_paiement = a.num_pay AND pay.hotel_id = h.hotel_id
JOIN fact ON fact.numero_facture = a.num_fact AND fact.hotel_id = h.hotel_id
JOIN lf ON lf.facture_id = fact.facture_id AND lf.rn_global = a.rn
WHERE NOT EXISTS (
  SELECT 1 FROM finance.affectations_paiements ap
  WHERE ap.paiement_id = pay.paiement_id
    AND ap.facture_id = lf.facture_id
    AND ap.ligne_facture_id = lf.ligne_facture_id
);

-- ----------------------------------------------------------------------------
-- 5.6 OPERATIONS_COMPTES — journal audit (DEBIT facturation + CREDIT paiement)
-- ----------------------------------------------------------------------------
-- DEBIT a la facturation (chaque facture EMISE/PARTIELLEMENT_PAYEE/PAYEE)
WITH compte AS (SELECT compte_id, hotel_id, client_id FROM finance.comptes WHERE type_compte = 'CLIENT'),
     fact AS (
       SELECT f.facture_id, f.hotel_id, f.numero_facture, f.client_id, f.montant_ttc, f.user_id, f.date_facture, f.compte_id
       FROM finance.factures f
       WHERE f.statut IN ('EMISE','PARTIELLEMENT_PAYEE','PAYEE')
     )
INSERT INTO finance.operations_comptes
  (hotel_id, compte_id, type_operation, montant, libelle, facture_id, paiement_id,
   solde_avant, solde_apres, date_operation, user_id)
SELECT fact.hotel_id, fact.compte_id, 'DEBIT', fact.montant_ttc,
       'Facturation ' || fact.numero_facture,
       fact.facture_id, NULL,
       0, fact.montant_ttc,
       (fact.date_facture::timestamp AT TIME ZONE 'UTC'), fact.user_id
FROM fact
WHERE fact.compte_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM finance.operations_comptes oc
    WHERE oc.compte_id = fact.compte_id AND oc.facture_id = fact.facture_id AND oc.type_operation = 'DEBIT'
  );

-- CREDIT au paiement
WITH pay AS (
  SELECT p.paiement_id, p.hotel_id, p.numero_paiement, p.montant_total, p.compte_id, p.user_id, p.date_paiement
  FROM finance.paiements p
  WHERE p.compte_id IS NOT NULL
)
INSERT INTO finance.operations_comptes
  (hotel_id, compte_id, type_operation, montant, libelle, facture_id, paiement_id,
   solde_avant, solde_apres, date_operation, user_id)
SELECT pay.hotel_id, pay.compte_id, 'CREDIT', pay.montant_total,
       'Encaissement ' || pay.numero_paiement,
       NULL, pay.paiement_id,
       pay.montant_total, 0,
       (pay.date_paiement::timestamp AT TIME ZONE 'UTC'), pay.user_id
FROM pay
WHERE NOT EXISTS (
  SELECT 1 FROM finance.operations_comptes oc
  WHERE oc.compte_id = pay.compte_id AND oc.paiement_id = pay.paiement_id AND oc.type_operation = 'CREDIT'
);

-- ----------------------------------------------------------------------------
-- 5.7a Recalibrage Facture.montant_ttc = SUM(LigneFacture.montant_ttc) (apres ajout lignes).
-- Indispensable pour respecter CHECK (montant_paye <= montant_ttc + 0.01).
-- ----------------------------------------------------------------------------
-- Étape pré-recalibrage : ramener montant_paye à 0 sur toutes les factures
-- pour éviter les CHECK violations transitoires lors du recalcul montant_ttc.
UPDATE finance.factures SET montant_paye = 0;

UPDATE finance.factures f
SET montant_ht = COALESCE((SELECT SUM(lf.montant_ht) FROM finance.lignes_factures lf WHERE lf.facture_id = f.facture_id), f.montant_ht),
    montant_tva = COALESCE((SELECT SUM(lf.montant_tva) FROM finance.lignes_factures lf WHERE lf.facture_id = f.facture_id), f.montant_tva),
    montant_ttc = COALESCE((SELECT SUM(lf.montant_ttc) FROM finance.lignes_factures lf WHERE lf.facture_id = f.facture_id), f.montant_ttc),
    updated_at = NOW(),
    updated_by = 'system'
WHERE EXISTS (SELECT 1 FROM finance.lignes_factures lf WHERE lf.facture_id = f.facture_id);

-- 5.7b Recalibrage Facture.montant_paye = SUM(AffectationPaiement.montant_affecte)
-- Capé à montant_ttc pour respecter CHECK (montant_paye <= montant_ttc + 0.01)
UPDATE finance.factures f
SET montant_paye = LEAST(
      COALESCE((
        SELECT SUM(ap.montant_affecte)
        FROM finance.affectations_paiements ap
        WHERE ap.facture_id = f.facture_id
      ), 0),
      f.montant_ttc
    ),
    updated_at = NOW(),
    updated_by = 'system';

-- 5.7c Recalibrage Facture.statut selon coherence montant
UPDATE finance.factures f
SET statut = CASE
  WHEN f.statut = 'BROUILLON' THEN 'BROUILLON'
  WHEN f.montant_paye = 0                                       THEN 'EMISE'
  WHEN f.montant_paye >= f.montant_ttc - 0.01                   THEN 'PAYEE'
  ELSE                                                          'PARTIELLEMENT_PAYEE'
END,
    updated_at = NOW(),
    updated_by = 'system'
WHERE f.statut <> 'BROUILLON';

-- ----------------------------------------------------------------------------
-- 5.7d Recalibrage soldes des comptes : solde_actuel = SUM(DEBIT) - SUM(CREDIT)
-- (auxiliaire client : DEBIT = doit, CREDIT = paye)
-- ----------------------------------------------------------------------------
UPDATE finance.comptes c
SET solde_actuel = COALESCE((
  SELECT SUM(CASE WHEN oc.type_operation = 'DEBIT' THEN oc.montant ELSE -oc.montant END)
  FROM finance.operations_comptes oc
  WHERE oc.compte_id = c.compte_id
), 0),
    updated_at = NOW(),
    updated_by = 'system'
WHERE EXISTS (SELECT 1 FROM finance.operations_comptes oc WHERE oc.compte_id = c.compte_id);

\echo '=== FINANCE extras seeded ==='
SELECT 'comptes'                AS t, COUNT(*) FROM finance.comptes UNION ALL
SELECT 'factures',                  COUNT(*) FROM finance.factures UNION ALL
SELECT 'factures_payees',           COUNT(*) FROM finance.factures WHERE statut = 'PAYEE' UNION ALL
SELECT 'factures_partielles',       COUNT(*) FROM finance.factures WHERE statut = 'PARTIELLEMENT_PAYEE' UNION ALL
SELECT 'factures_emises',           COUNT(*) FROM finance.factures WHERE statut = 'EMISE' UNION ALL
SELECT 'factures_brouillon',        COUNT(*) FROM finance.factures WHERE statut = 'BROUILLON' UNION ALL
SELECT 'lignes_factures',           COUNT(*) FROM finance.lignes_factures UNION ALL
SELECT 'paiements',                 COUNT(*) FROM finance.paiements UNION ALL
SELECT 'affectations_paiements',    COUNT(*) FROM finance.affectations_paiements UNION ALL
SELECT 'affectations_granulaires',  COUNT(*) FROM finance.affectations_paiements WHERE ligne_facture_id IS NOT NULL UNION ALL
SELECT 'operations_comptes',        COUNT(*) FROM finance.operations_comptes;
