-- ============================================================================
-- SEED Part 5 — MENAGE.taches + RESTAURANT.commandes
-- ============================================================================

-- ====================================================
-- MENAGE.TACHES (15 par hotel = 30)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     ch AS (SELECT chambre_id, hotel_id, numero_chambre FROM hebergement.chambres),
     pers AS (SELECT personnel_id, hotel_id, numero_employe FROM menage.personnel)
INSERT INTO menage.taches (hotel_id, chambre_id, personnel_id, type_nettoyage, statut, priorite, date_planifiee, heure_debut_prevue, heure_fin_prevue, version, created_at, updated_at, created_by)
SELECT h.hotel_id, ch.chambre_id, pers.personnel_id, t.type, t.statut, t.prio, t.date::date, t.hd::time, t.hf::time, 0, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','101','MEN-NKC-001','QUOTIDIEN','PLANIFIEE',2,'2026-05-09','08:00','08:45'),
  ('NKC','102','MEN-NKC-001','QUOTIDIEN','PLANIFIEE',2,'2026-05-09','08:45','09:30'),
  ('NKC','103','MEN-NKC-002','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','09:00','09:45'),
  ('NKC','104','MEN-NKC-002','GRAND_MENAGE','PLANIFIEE',1,'2026-05-10','10:00','11:30'),
  ('NKC','105','MEN-NKC-003','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','11:30','12:15'),
  ('NKC','201','MEN-NKC-001','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','13:00','13:45'),
  ('NKC','202','MEN-NKC-002','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','13:45','14:30'),
  ('NKC','203','MEN-NKC-003','QUOTIDIEN','PLANIFIEE',3,'2026-05-10','14:30','15:15'),
  ('NKC','204','MEN-NKC-004','MAINTENANCE','PLANIFIEE',1,'2026-05-10','08:00','12:00'),
  ('NKC','301','MEN-NKC-001','GRAND_MENAGE','PLANIFIEE',1,'2026-05-08','08:00','10:00'),
  ('NKC','302','MEN-NKC-002','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','15:30','16:15'),
  ('NKC','401','MEN-NKC-003','GRAND_MENAGE','PLANIFIEE',1,'2026-05-11','08:00','10:00'),
  ('NKC','402','MEN-NKC-002','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','10:00','11:00'),
  ('NKC','403','MEN-NKC-003','QUOTIDIEN','PLANIFIEE',3,'2026-05-11','09:00','09:45'),
  ('NKC','101','MEN-NKC-005','GRAND_MENAGE','PLANIFIEE',2,'2026-05-15','08:00','10:00'),
  ('DKR','101','MEN-DKR-001','QUOTIDIEN','PLANIFIEE',2,'2026-05-09','08:00','08:45'),
  ('DKR','102','MEN-DKR-001','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','09:00','09:45'),
  ('DKR','103','MEN-DKR-002','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','09:45','10:30'),
  ('DKR','104','MEN-DKR-002','GRAND_MENAGE','PLANIFIEE',1,'2026-05-11','08:00','10:00'),
  ('DKR','105','MEN-DKR-003','QUOTIDIEN','PLANIFIEE',3,'2026-05-10','11:00','11:45'),
  ('DKR','201','MEN-DKR-001','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','13:00','13:45'),
  ('DKR','202','MEN-DKR-002','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','13:45','14:30'),
  ('DKR','203','MEN-DKR-003','QUOTIDIEN','PLANIFIEE',3,'2026-05-10','14:30','15:15'),
  ('DKR','204','MEN-DKR-001','GRAND_MENAGE','PLANIFIEE',1,'2026-05-12','08:00','10:00'),
  ('DKR','205','MEN-DKR-004','MAINTENANCE','PLANIFIEE',1,'2026-05-10','08:00','12:00'),
  ('DKR','301','MEN-DKR-002','QUOTIDIEN','PLANIFIEE',2,'2026-05-10','15:30','16:15'),
  ('DKR','302','MEN-DKR-003','GRAND_MENAGE','PLANIFIEE',1,'2026-05-08','08:00','10:00'),
  ('DKR','BG01','MEN-DKR-001','QUOTIDIEN','PLANIFIEE',2,'2026-05-11','09:00','09:45'),
  ('DKR','BG02','MEN-DKR-002','GRAND_MENAGE','PLANIFIEE',1,'2026-05-12','08:00','10:00'),
  ('DKR','BG03','MEN-DKR-005','QUOTIDIEN','PLANIFIEE',3,'2026-05-11','10:00','10:45')
) AS t(hcode, ch_num, p_num, type, statut, prio, date, hd, hf)
JOIN h ON h.hotel_code = t.hcode
JOIN ch ON ch.numero_chambre = t.ch_num AND ch.hotel_id = h.hotel_id
JOIN pers ON pers.numero_employe = t.p_num AND pers.hotel_id = h.hotel_id;

-- ====================================================
-- RESTAURANT.COMMANDES (10 par hotel = 20)
-- ====================================================
WITH h AS (SELECT hotel_id, hotel_code FROM core.hotels),
     cl AS (SELECT client_id, hotel_id, numero_client FROM client.clients),
     r AS (SELECT reservation_id, hotel_id, numero_reservation FROM hebergement.reservations)
INSERT INTO restaurant.commandes (hotel_id, numero_commande, client_id, reservation_id, numero_table, mode_reglement, statut_commande, montant_ht, montant_ttc, montant_paye, devise, date_commande, created_at, updated_at, created_by)
SELECT h.hotel_id, c.num, cl.client_id, r.reservation_id, c.tbl, c.mode, c.statut, c.mont, c.mont, c.paye, 'MRU', c.dt::timestamptz, NOW(), NOW(), 'system'
FROM (VALUES
  ('NKC','CMD-NKC-2026-0001','CLI-NKC-2026-0001',NULL,'T-01','COMPTANT','SERVIE',6300,6300,'2026-05-09 12:30:00'),
  ('NKC','CMD-NKC-2026-0002','CLI-NKC-2026-0003','RES-NKC-2026-0002','T-03','REPORTE_CHAMBRE','SERVIE',5800,5800,'2026-05-09 13:00:00'),
  ('NKC','CMD-NKC-2026-0003',NULL,NULL,'T-05','COMPTANT','SERVIE',2100,2100,'2026-05-09 13:30:00'),
  ('NKC','CMD-NKC-2026-0004','CLI-NKC-2026-0005','RES-NKC-2026-0003','T-02','REPORTE_CHAMBRE','EN_PREPARATION',7300,0,'2026-05-10 12:30:00'),
  ('NKC','CMD-NKC-2026-0005',NULL,NULL,'T-04','COMPTANT','VALIDEE',4200,0,'2026-05-10 13:00:00'),
  ('NKC','CMD-NKC-2026-0006','CLI-NKC-2026-0007',NULL,'T-06','COMPTANT','PRETE',3300,0,'2026-05-10 13:15:00'),
  ('NKC','CMD-NKC-2026-0007','CLI-NKC-2026-0009',NULL,'T-01','COMPTANT','BROUILLON',1800,0,'2026-05-10 13:45:00'),
  ('NKC','CMD-NKC-2026-0008',NULL,NULL,'T-08','COMPTANT','SERVIE',2400,2400,'2026-05-09 19:30:00'),
  ('NKC','CMD-NKC-2026-0009',NULL,NULL,'T-07','COMPTANT','ANNULEE',900,0,'2026-05-09 14:00:00'),
  ('NKC','CMD-NKC-2026-0010',NULL,NULL,'T-02','COMPTANT','SERVIE',5500,5500,'2026-05-09 20:00:00'),
  ('DKR','CMD-DKR-2026-0001','CLI-DKR-2026-0001','RES-DKR-2026-0001','T-01','REPORTE_CHAMBRE','SERVIE',11000,11000,'2026-05-04 13:00:00'),
  ('DKR','CMD-DKR-2026-0002',NULL,NULL,'T-02','COMPTANT','SERVIE',8500,8500,'2026-05-09 13:30:00'),
  ('DKR','CMD-DKR-2026-0003','CLI-DKR-2026-0003','RES-DKR-2026-0002','T-03','REPORTE_CHAMBRE','EN_PREPARATION',13800,0,'2026-05-10 12:30:00'),
  ('DKR','CMD-DKR-2026-0004','CLI-DKR-2026-0005','RES-DKR-2026-0003','T-04','REPORTE_CHAMBRE','SERVIE',9000,9000,'2026-05-09 13:00:00'),
  ('DKR','CMD-DKR-2026-0005',NULL,NULL,'T-05','COMPTANT','VALIDEE',2500,0,'2026-05-10 12:45:00'),
  ('DKR','CMD-DKR-2026-0006',NULL,NULL,'T-06','COMPTANT','PRETE',7200,0,'2026-05-10 13:30:00'),
  ('DKR','CMD-DKR-2026-0007',NULL,NULL,'T-01','COMPTANT','BROUILLON',2200,0,'2026-05-10 14:00:00'),
  ('DKR','CMD-DKR-2026-0008',NULL,NULL,'T-08','COMPTANT','SERVIE',12000,12000,'2026-05-09 20:00:00'),
  ('DKR','CMD-DKR-2026-0009',NULL,NULL,'T-07','COMPTANT','ANNULEE',1500,0,'2026-05-09 14:30:00'),
  ('DKR','CMD-DKR-2026-0010',NULL,NULL,'T-02','COMPTANT','SERVIE',6800,6800,'2026-05-09 19:30:00')
) AS c(hcode, num, ncli, nres, tbl, mode, statut, mont, paye, dt)
JOIN h ON h.hotel_code = c.hcode
LEFT JOIN cl ON cl.numero_client = c.ncli AND cl.hotel_id = h.hotel_id
LEFT JOIN r ON r.numero_reservation = c.nres AND r.hotel_id = h.hotel_id
ON CONFLICT (hotel_id, numero_commande) DO NOTHING;

\echo '=== Part 5 OK ==='
SELECT 'taches' AS t, COUNT(*) FROM menage.taches UNION ALL
SELECT 'commandes',  COUNT(*) FROM restaurant.commandes;
