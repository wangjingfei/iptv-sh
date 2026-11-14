#!/bin/sh
set -eu

root="$(CDPATH= cd "$(dirname "$0")/.." && pwd)"

if rg -n --hidden --glob '!.git/**' --glob '!scripts/check-secrets.sh' \
	-e 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' \
	-e '(password|passwd|token|secret|authenticator)[[:space:]]*[:=][[:space:]]*[^[:space:]"$<{]+' \
	-e '[0-9]{8,}@etv1' \
	"$root"; then
	echo "发现可能的敏感信息，请在提交前检查。" >&2
	exit 1
fi

echo "通过：未发现高可信度的凭据特征"
