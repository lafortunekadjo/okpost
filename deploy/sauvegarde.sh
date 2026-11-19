#!/bin/sh
# Export quotidien de la base « okpost », conservé 14 jours.
# Crontab (root) : 30 2 * * * /opt/okpost/deploy/sauvegarde.sh
# Sans Docker (installation actuelle) : pg_dump sur le PostgreSQL du serveur.
# Avec Docker (plus tard) : détecte le conteneur « db » du docker-compose et l'utilise.
set -e
umask 077
DOSSIER=/opt/okpost/sauvegardes
COMPOSE=/opt/okpost/deploy/docker-compose.yml
FICHIER="$DOSSIER/okpost-$(date +%F).sql.gz"
mkdir -p "$DOSSIER"

if command -v docker >/dev/null 2>&1 && [ -f "$COMPOSE" ] \
   && docker compose -f "$COMPOSE" ps --status running --services 2>/dev/null | grep -qx db; then
  docker compose -f "$COMPOSE" exec -T db pg_dump -U okpost okpost | gzip > "$FICHIER"
else
  runuser -u postgres -- pg_dump okpost | gzip > "$FICHIER"
fi

# Une sauvegarde vide ou minuscule veut dire que pg_dump a échoué : on la supprime et on le signale
if [ "$(wc -c < "$FICHIER")" -lt 1024 ]; then
  rm -f "$FICHIER"
  echo "Sauvegarde invalide (fichier vide) : vérifier pg_dump et la base okpost" >&2
  exit 1
fi

find "$DOSSIER" -name 'okpost-*.sql.gz' -mtime +14 -delete
