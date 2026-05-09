#!/usr/bin/env bash
# postgres-restore.sh — Restore d'un backup pg_dump.
# A LANCER MANUELLEMENT (jamais automatique).
#
# Usage :
#   bash postgres-restore.sh <fichier.sql.gz[.gpg]> [target_db]
#
# Variables d'env attendues : PGHOST, PGPORT, PGUSER, PGPASSWORD

set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <backup.sql.gz[.gpg]> [target_db]"
    exit 1
fi

SRC="$1"
TARGET_DB="${2:-cityprojectdb_restore}"

if [[ ! -f "${SRC}" ]]; then
    echo "ERREUR : fichier introuvable : ${SRC}"
    exit 2
fi

echo "==============================================================="
echo "  RESTAURATION PostgreSQL"
echo "  Source : ${SRC}"
echo "  Cible  : ${PGUSER}@${PGHOST}:${PGPORT} / ${TARGET_DB}"
echo "==============================================================="
read -r -p "Confirmer ? (taper 'OUI' en majuscules) : " CONFIRM
[[ "${CONFIRM}" == "OUI" ]] || { echo "Annulé."; exit 3; }

# 1) Déchiffrement GPG si nécessaire
TMP_PLAIN=""
if [[ "${SRC}" == *.gpg ]]; then
    TMP_PLAIN=$(mktemp /tmp/restore.XXXXXX.sql.gz)
    echo "[+] Déchiffrement GPG..."
    gpg --decrypt --output "${TMP_PLAIN}" "${SRC}"
    SRC="${TMP_PLAIN}"
fi

# 2) Création de la base cible si inexistante
echo "[+] Vérification de la base cible..."
EXISTS=$(psql -tAc "SELECT 1 FROM pg_database WHERE datname='${TARGET_DB}'" | tr -d '[:space:]')
if [[ "${EXISTS}" == "1" ]]; then
    echo "    ⚠ La base ${TARGET_DB} existe déjà."
    read -r -p "    La supprimer (DROP DATABASE) ? (taper 'OUI') : " DROP
    if [[ "${DROP}" == "OUI" ]]; then
        psql -c "DROP DATABASE \"${TARGET_DB}\";"
    else
        echo "Annulé."
        [[ -n "${TMP_PLAIN}" ]] && rm -f "${TMP_PLAIN}"
        exit 4
    fi
fi
psql -c "CREATE DATABASE \"${TARGET_DB}\" OWNER \"${PGUSER}\";"

# 3) Restore
echo "[+] Restore en cours (peut prendre plusieurs minutes)..."
if [[ "${SRC}" == *.gz ]]; then
    gunzip -c "${SRC}" | psql -d "${TARGET_DB}"
else
    psql -d "${TARGET_DB}" < "${SRC}"
fi

# 4) Sanity checks rapides
echo "[+] Vérifications post-restore :"
psql -d "${TARGET_DB}" -c "SELECT current_database(), now();"
psql -d "${TARGET_DB}" -c "\dn" | head -20
psql -d "${TARGET_DB}" -c "SELECT schemaname, COUNT(*) AS tables FROM pg_tables WHERE schemaname IN ('core','clients','hebergement','inventory','finance','restaurant','menage','reporting') GROUP BY schemaname;"

# 5) Cleanup
[[ -n "${TMP_PLAIN}" ]] && rm -f "${TMP_PLAIN}"

echo "==============================================================="
echo "  ✓ Restauration terminée dans la base : ${TARGET_DB}"
echo "  Pensez à valider l'intégrité fonctionnelle avant tout switch."
echo "==============================================================="
