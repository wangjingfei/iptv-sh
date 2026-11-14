#!/bin/sh
set -eu

root="$(CDPATH= cd "$(dirname "$0")/.." && pwd)"

if rg -n --hidden --glob '!.git/**' --glob '!scripts/check-secrets.sh' \
	-e 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' \
	-e '(password|passwd|token|secret|authenticator)[[:space:]]*[:=][[:space:]]*[^[:space:]"$<{]+' \
	-e '[0-9]{8,}@etv1' \
	"$root"; then
	echo "Potential secret found; review before commit." >&2
	exit 1
fi

echo "OK: no high-confidence credential patterns found"
