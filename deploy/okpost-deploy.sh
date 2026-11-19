#!/bin/bash
# Installé sur le VPS dans /usr/local/bin/okpost-deploy (voir installer-deploiement.sh).
# Appelé par GitHub Actions via : sudo /usr/local/bin/okpost-deploy
# Active le jar déposé dans /opt/okpost/releases/okpost.jar, attend que l'API réponde, sinon revient à la version précédente.
set -euo pipefail

NOUVEAU=/opt/okpost/releases/okpost.jar
ACTUEL=/opt/okpost/okpost.jar

# Port lu dans /etc/okpost.env, en tolérant guillemets, espaces et fins de ligne Windows
PORT=$(sed -n 's/^[[:space:]]*PORT=//p' /etc/okpost.env 2>/dev/null | head -1 | tr -d "\"' \r" || true)
PORT=${PORT:-8083}
URL="http://127.0.0.1:$PORT/actuator/health"

[ -s "$NOUVEAU" ] || { echo "Jar introuvable : $NOUVEAU"; exit 1; }
[ "$(head -c 2 "$NOUVEAU")" = "PK" ] || { echo "Ce fichier n'est pas un jar"; exit 1; }

# Garde la version en place pour pouvoir revenir en arrière
[ -f "$ACTUEL" ] && cp -p "$ACTUEL" "$ACTUEL.prev"
install -o okpost -g okpost -m 640 "$NOUVEAU" "$ACTUEL"
systemctl restart okpost
echo "Service redémarré, attente de la réponse de $URL"

# Le démarrage prend 20 à 40 secondes : on attend jusqu'à 2 minutes.
REPONSE=""
for i in $(seq 1 40); do
  REPONSE=$(curl -s -m 5 "$URL" 2>&1 || true)
  case "$REPONSE" in
    *'"status":"UP"'*)
      echo "OkPost est démarré (version déployée)."
      rm -f "$NOUVEAU"
      exit 0
      ;;
  esac
  sleep 3
done

echo "L'API n'a pas répondu « UP » en 2 minutes : retour à la version précédente."
echo "Adresse testée : $URL"
echo "Dernière réponse : ${REPONSE:-aucune}"
echo "Ports en écoute (java) :"
ss -ltnp 2>/dev/null | grep java || echo "  aucun"
journalctl -u okpost -n 40 --no-pager || true
if [ -f "$ACTUEL.prev" ]; then
  install -o okpost -g okpost -m 640 "$ACTUEL.prev" "$ACTUEL"
  systemctl restart okpost
  echo "Version précédente remise en service."
  echo "(Si la nouvelle version avait appliqué une migration de base, la précédente peut refuser de démarrer : restaurer la sauvegarde.)"
fi
exit 1
