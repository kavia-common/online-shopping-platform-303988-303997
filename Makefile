.PHONY: check

# Convenience target for environments where invoking Gradle directly differs.
# This still requires the repository to be mounted and the working directory to be the repo root.
check:
	./gradlew check
