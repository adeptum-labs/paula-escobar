#!/usr/bin/env bash
#
# Paula Escobar is a terminal music player for demoscene and chip music.
# Copyright © 2026 Adam Waldenberg, Adeptum AB, Org.nr 559494-1824.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.
#
# This program is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
# or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
# more details.
#
# You should have received a copy of the GNU General Public License along
# with this program. If not, see <https://www.gnu.org/licenses/>.
#
# Website: https://www.adeptum.se
# Contact: info@adeptum.se
#
# Cuts a release: builds Paula at a fixed version, keeps the runnable jar in
# releases/ under that version, records it in a commit and a tag, and opens
# the next snapshot.
#
# Run ./create-release.sh --help for what it takes.
#
# Nothing is pushed. The tag and the commits stay local until you send them,
# and it is the tag reaching GitHub that builds the native executables.

set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly RELEASES="$ROOT/releases"
readonly NATIVE="$ROOT/target/paula"
readonly TOOLCHAINS="$HOME/.m2/toolchains.xml"

die() { printf '%s\n' "$*" >&2; exit 1; }
step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

usage() {
	cat <<'USAGE'
Cuts a release: builds Paula at a fixed version, keeps the runnable jar in
releases/ under that version, records it in a commit and an annotated tag,
and reopens the next revision as a snapshot. Nothing is pushed.

  ./create-release.sh [options]

The version released is the one the pom is already working towards, with
the snapshot suffix dropped; it is shown and confirmed before anything is
built. What is opened afterwards is up to --bump.

  --bump=revrevision the third number, and the default: 0.1.0 opens
                     0.1.1-SNAPSHOT.
  --bump=revision    the second, zeroing the third: 0.1.0 opens
                     0.2.0-SNAPSHOT.
  --bump=version     the first, zeroing the rest: 0.1.0 opens
                     1.0.0-SNAPSHOT.

  --skip-tests       build only. Nothing is verified — for a release you
                     have already tested, or one that is not going out.
  --help, -h         this.
USAGE
}

tests="all"
bump="revrevision"
for argument in "$@"; do
	case "$argument" in
		--help|-h) usage; exit 0 ;;
		--skip-tests) tests="none" ;;
		--bump=version|--bump=revision|--bump=revrevision) bump="${argument#--bump=}" ;;
		--bump=*) die "Bump one of version, revision or revrevision — got '${argument#--bump=}'." ;;
		*) usage >&2; die "
Takes no version: the pom already says which one comes next.
Unexpected argument '$argument'." ;;
	esac
done

is_version() { [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; }

# The next snapshot: the named level one higher, everything under it back to
# zero, so a release opens a version rather than an odd corner of one.
bumped() {
	local major="${1%%.*}" rest="${1#*.}"
	local minor="${rest%%.*}" patch="${rest#*.}"
	case "$2" in
		version) printf '%s.0.0' "$(( major + 1 ))" ;;
		revision) printf '%s.%s.0' "$major" "$(( minor + 1 ))" ;;
		*) printf '%s.%s.%s' "$major" "$minor" "$(( patch + 1 ))" ;;
	esac
}

cd "$ROOT"

# --- what the release must be able to assume ---------------------------------

git rev-parse --git-dir >/dev/null 2>&1 || die "Not a git repository."

# The whole build runs on the GraalVM the toolchain names, native image and
# all, so a missing one fails at the first phase rather than an hour in.
grep -q graalvm "$TOOLCHAINS" 2>/dev/null \
	|| die "No GraalVM toolchain in $TOOLCHAINS.
The build needs one registered with the vendor 'graalvm'; see the readme."

dirty="$(git status --porcelain)"
[ -z "$dirty" ] || die "Working tree is not clean; commit or stash first:
$dirty"

# Every release opens the next one as a snapshot, so the pom already names
# what comes next; dropping the suffix is the version being cut.
pom_version="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null | tail -1)"
version="${pom_version%-SNAPSHOT}"
is_version "$version" \
	|| die "The pom reads '$pom_version', which names no version to release.
Set it to a major.minor.patch snapshot first."
next="$(bumped "$version" "$bump")-SNAPSHOT"

tag="v$version"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null \
	&& die "Tag $tag already exists — that release has been cut."

jar="$ROOT/target/paula-$version-all.jar"
artifact="$RELEASES/paula-escobar-$version.jar"
[ -e "$artifact" ] && die "$artifact already exists."

printf 'Release %s, then open %s?' "$version" "$next"
if [ -t 0 ]; then
	printf ' [Y/n] '
	read -r answer
	case "$answer" in
		""|y|Y|yes) ;;
		*) die "Nothing released." ;;
	esac
else
	printf '\n'
fi

# --- build it at the version it will be released as --------------------------

step "Setting the version to $version"
mvn -q versions:set -DnewVersion="$version"

# Everything is put back if the build refuses, so a failed release leaves the
# working copy exactly as it was found.
restore_snapshot() {
	git checkout -- pom.xml 2>/dev/null || true
}
trap restore_snapshot ERR

case "$tests" in
	all)
		step "Building and testing"
		mvn clean verify
		;;
	none)
		step "Building, nothing verified"
		mvn clean package -DskipTests
		;;
esac

trap - ERR

[ -f "$jar" ] || die "The build produced no $jar."
[ -x "$NATIVE" ] || die "The build produced no $NATIVE."

# What the executable answers is what the release claims, so a version that
# never reached the build is caught before it is committed.
built="$("$NATIVE" --version)"
[ "$built" = "paula $version" ] \
	|| die "The executable calls itself '$built' rather than 'paula $version'."

# --- keep it, record it ------------------------------------------------------

step "Keeping the runnable jar"
mkdir -p "$RELEASES"
cp "$jar" "$artifact"
printf '%s — %s\n' "$(basename "$artifact")" "$(du -h "$artifact" | cut -f1)"

step "Recording the release"
git add pom.xml "$artifact"
git commit -q -m "Release $version

Paula Escobar at version $version. The jar beside this commit runs on any
machine with a Java runtime; the executables for Linux, macOS and Windows
are built from the tag and hang off the release on GitHub."

git tag -a "$tag" -m "Paula Escobar $version"

step "Opening the next snapshot"
mvn -q versions:set -DnewVersion="$next"
git add pom.xml
git commit -q -m "Start $next"

printf '\n\033[1mReleased %s\033[0m\n' "$version"
printf '  jar    %s\n' "${artifact#$ROOT/}"
printf '  tag    %s\n' "$tag"
printf '  next   %s\n' "$next"
printf '\nNothing was pushed. When you are ready:\n'
printf '  git push && git push origin %s\n' "$tag"
printf '\nThe tag is what builds the executables and drafts the release.\n'
