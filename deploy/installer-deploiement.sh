#!/bin/bash
# À lancer UNE SEULE FOIS sur le VPS, en root, depuis le dossier du dépôt :
#   cd /opt/okpost/deploy && sudo bash installer-deploiement.sh "ssh-ed25519 AAAA... github-deploy"
# Crée l'utilisateur « deploy » (sans mot de passe, accès par clé), qui ne peut que déposer un jar et lancer okpost-deploy.
set -euo pipefail

CLE_PUBLIQUE="${1:-}"
[ "$(id -u)" -eq 0 ] || { echo "Lance ce script en root (sudo)."; exit 1; }
[ -n "$CLE_PUBLIQUE" ] || { echo "Usage : sudo bash $0 \"<clé publique ssh>\""; exit 1; }
case "$CLE_PUBLIQUE" in
  ssh-ed25519\ *|ssh-rsa\ *) ;;
  *) echo "Ce n'est pas une clé publique SSH (elle doit commencer par ssh-ed25519 ou ssh-rsa)."; exit 1 ;;
esac

DOSSIER="$(cd "$(dirname "$0")" && pwd)"

id deploy >/dev/null 2>&1 || useradd -m -s /bin/bash deploy
install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
echo "no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty $CLE_PUBLIQUE" > /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

install -d -o deploy -g deploy -m 755 /opt/okpost/releases
install -m 755 "$DOSSIER/okpost-deploy.sh" /usr/local/bin/okpost-deploy

# « deploy » ne peut exécuter en root que ce script, sans argument
echo 'deploy ALL=(root) NOPASSWD: /usr/local/bin/okpost-deploy' > /etc/sudoers.d/okpost-deploy
chmod 440 /etc/sudoers.d/okpost-deploy
visudo -cf /etc/sudoers.d/okpost-deploy >/dev/null || { rm -f /etc/sudoers.d/okpost-deploy; echo "Règle sudo invalide, annulée."; exit 1; }

echo "Installation terminée. Utilisateur : deploy. Dépôt des jars : /opt/okpost/releases."
