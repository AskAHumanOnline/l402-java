#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:-}
if [[ -z "$VERSION" ]]; then
  echo "Usage: ./release.sh <version>"
  echo "Example: ./release.sh 0.2.0"
  exit 1
fi

TAG="v${VERSION}"

# Ensure we're on main and up to date
git checkout main
git pull origin main

# Bump all pom.xml files atomically
mvn versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false -q

# Commit
git add pom.xml l402-java-core/pom.xml l402-java-spring-boot-starter/pom.xml l402-java-examples/pom.xml
git commit -m "chore: bump version to ${TAG}"

# Tag and push
git tag "${TAG}"
git push origin main
git push origin "${TAG}"

echo "Released ${TAG} — CI is publishing to Maven Central"
echo "https://github.com/AskAHumanOnline/l402-java/actions"
