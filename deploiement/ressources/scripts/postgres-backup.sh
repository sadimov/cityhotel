#!/usr/bin/env bash
# postgres-backup.sh — sauvegarde compressée quotidienne de cityprojectdb.
# Lancé via cronjob k8s ou container backup compose.
#
# Variables d'env attendues (transmises par k8s/Compose) :
#   PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE
#   RETENTION_DAYS  (défaut 30)
#   BACKUP_DIR      (défaut /backup)
#   BACKUP_GPG_RECIPIENT  (optionnel : empreinte GPG pour chiffrement)

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backup}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
TS=$(date +%Y%m%d_%H%M%S)
DAY=$(date +%Y-%m-%d)
HOST=$(hostname -s)
FILE="${BACKUP_DIR}/${PGDATABASE}_${DAY}_${HOST}.sql.gz"
LOG="${BACKUP_DIR}/.backup.log"

mkdir -p "${BACKUP_DIR}"

echo "[$(date -Iseconds)] === BACKUP DEBUT === db=${PGDATABASE} host=${PGHOST}" | tee -a "${LOG}"

# 1) pg_dump compressé
if pg_dump --format=plain --no-owner --no-privileges \
           --quote-all-identifiers --verbose \
   | gzip -9 > "${FILE}.tmp"; then
    mv "${FILE}.tmp" "${FILE}"
    SIZE=$(du -h "${FILE}" | cut -f1)
    echo "[$(date -Iseconds)]     ✓ dump OK : ${FILE} (${SIZE})" | tee -a "${LOG}"
else
    echo "[$(date -Iseconds)]     ✗ ECHEC pg_dump" | tee -a "${LOG}"
    rm -f "${FILE}.tmp"
    exit 2
fi

# 2) Chiffrement GPG (optionnel)
if [[ -n "${BACKUP_GPG_RECIPIENT:-}" ]]; then
    if gpg --batch --yes --trust-model always \
           --recipient "${BACKUP_GPG_RECIPIENT}" \
           --output "${FILE}.gpg" --encrypt "${FILE}"; then
        rm -f "${FILE}"
        FILE="${FILE}.gpg"
        echo "[$(date -Iseconds)]     ✓ chiffré GPG : ${FILE}" | tee -a "${LOG}"
    else
        echo "[$(date -Iseconds)]     ⚠ ECHEC chiffrement GPG (backup gardé en clair)" | tee -a "${LOG}"
    fi
fi

# 3) Vérification rapide d'intégrité (gzip -t)
if [[ "${FILE}" == *.gz ]]; then
    gzip -t "${FILE}" || { echo "    ✗ gzip corrompu" | tee -a "${LOG}"; exit 3; }
fi

# 4) Rotation : suppression des backups > RETENTION_DAYS
DELETED=$(find "${BACKUP_DIR}" -maxdepth 1 -type f \( -name "${PGDATABASE}_*.sql.gz" -o -name "${PGDATABASE}_*.sql.gz.gpg" \) -mtime "+${RETENTION_DAYS}" -print -delete | wc -l)
echo "[$(date -Iseconds)]     ✓ rotation : ${DELETED} fichier(s) supprimé(s) (> ${RETENTION_DAYS} j)" | tee -a "${LOG}"

# 5) Inventaire actuel
COUNT=$(find "${BACKUP_DIR}" -maxdepth 1 -type f \( -name "${PGDATABASE}_*.sql.gz" -o -name "${PGDATABASE}_*.sql.gz.gpg" \) | wc -l)
TOTAL=$(du -sh "${BACKUP_DIR}" | cut -f1)
echo "[$(date -Iseconds)]     ✓ inventaire : ${COUNT} backups, total ${TOTAL}" | tee -a "${LOG}"

echo "[$(date -Iseconds)] === BACKUP FIN ===" | tee -a "${LOG}"
exit 0
