.PHONY: check live-check live-check-quick

check:
	python3 scripts/validate.py
	sh -n router/apply-iptv.sh router/rollback.sh router/verify.sh scripts/check-secrets.sh
	./scripts/check-secrets.sh

live-check:
	python3 scripts/live_check.py

live-check-quick:
	python3 scripts/live_check.py --skip-all-sources
