.PHONY: check live-check live-check-quick android-test android-check android-apk android-signing-check android-device-check

check:
	python3 scripts/validate.py
	sh -n router/apply-iptv.sh router/rollback.sh router/verify.sh scripts/check-secrets.sh scripts/verify-android-signing.sh scripts/verify-android-device.sh
	./scripts/check-secrets.sh

live-check:
	python3 scripts/live_check.py

live-check-quick:
	python3 scripts/live_check.py --skip-all-sources

android-test:
	cd android-tv && ./gradlew testDebugUnitTest

android-check:
	cd android-tv && ./gradlew --no-parallel testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug assembleRelease
	cd android-tv && ./gradlew --no-parallel lintDebug

android-apk:
	cd android-tv && ./gradlew --no-parallel testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug assembleRelease
	cd android-tv && ./gradlew --no-parallel lintDebug

android-signing-check:
	./scripts/verify-android-signing.sh

android-device-check:
	./scripts/verify-android-device.sh
