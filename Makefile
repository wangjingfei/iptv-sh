.PHONY: check live-check

check:
	python3 scripts/validate.py
	sh -n router/apply-iptv.sh router/rollback.sh router/verify.sh scripts/check-secrets.sh
	./scripts/check-secrets.sh

live-check:
	curl -fsS http://192.168.31.200:8090/healthz
	curl -fsS http://192.168.31.200:8091/healthz
	curl -fsS http://192.168.31.200:8091/status.json
