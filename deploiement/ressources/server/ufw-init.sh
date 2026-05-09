#!/usr/bin/env bash
# ufw-init.sh — Initialise le pare-feu UFW pour city-prod.
# A exécuter une seule fois après installation Ubuntu, AVANT d'enable.
# Usage : sudo bash ufw-init.sh

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "ERREUR : doit être exécuté en root (sudo bash $0)" >&2
    exit 1
fi

echo "[1/8] Réinitialisation des règles UFW..."
ufw --force reset

echo "[2/8] Politiques par défaut : deny incoming, allow outgoing..."
ufw default deny incoming
ufw default allow outgoing
ufw default deny routed

echo "[3/8] SSH custom (port 2222) — limit pour ralentir bruteforce..."
ufw limit 2222/tcp comment 'SSH custom port (rate-limited)'

echo "[4/8] HTTP / HTTPS publics (Traefik / cert-manager ACME)..."
ufw allow 80/tcp  comment 'HTTP - Traefik + ACME HTTP-01'
ufw allow 443/tcp comment 'HTTPS - Traefik'

echo "[5/8] K3s API local seulement (loopback uniquement)..."
# Bloque 6443 sur l'extérieur ; loopback est implicite (UFW n'affecte pas lo).
# Si besoin d'accès kubectl distant, créer un tunnel SSH ou un VPN.

echo "[6/8] Plage flannel (réseau interne K3s)..."
ufw allow from 10.42.0.0/16 to any comment 'K3s flannel pod network'
ufw allow from 10.43.0.0/16 to any comment 'K3s service network'

echo "[7/8] Logs UFW : medium..."
ufw logging medium

echo "[8/8] Activation..."
ufw --force enable

ufw status verbose
echo
echo "✓ UFW initialisé. Vérifier que vous pouvez encore vous connecter en SSH"
echo "  AVANT de fermer cette session."
