#!/bin/bash
# Installé sur le VPS dans /usr/local/bin/okpost-deploy (voir installer-deploiement.sh).
# Appelé par GitHub Actions via : sudo /usr/local/bin/okpost-deploy
# Active le jar déposé dans /opt/okpost/releases/okpost.jar, attend que l'API réponde, sinon revient à la version précédente.
set -euo pipefail

NOUVEAU=/opt/okpost/releases/okpost.jar
ACTUEL=/opt/okpost/okpost.jar
PORT=$(grep -E '^PORT=' /etc/okpost.env 2>/dev/null | head -1 | cut -d= -f2 || true)
PORT=${PORT:-8083}

[ -s "$NOUVEAU" ] || { echo "Jar introuvable : $NOUVEAU"; exit 1; }
[ "$(head -c 2 "$NOUVEAU")" = "PK" ] || { echo "Ce fichier n'est pas un jar"; exit 1; }

# Garde la version en place pour pouvoir revenir en arrière
[ -f "$ACTUEL" ] && cp -p "$ACTUEL" "$ACTUEL.prev"
install -o okpost -g okpost -m 640 "$NOUVEAU" "$ACTUEL"
systemctl restart okpost

for i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    echo "OkPost est démarré (version déployée)."
    rm -f "$NOUVEAU"
    exit 0
  fi
  sleep 3
done

echo "L'API n'a pas démarré en 90 s : retour à la version précédente."
journalctl -u okpost -n 40 --no-pager || true
if [ -f "$ACTUEL.prev" ]; then
  install -o okpost -g okpost -m 640 "$ACTUEL.prev" "$ACTUEL"
  systemctl restart okpost
  echo "Version précédente remise en service."
  echo "(Si la nouvelle version avait appliqué une migration de base, la précédente peut refuser de démarrer : restaurer la sauvegarde.)"
fi
exit 1
