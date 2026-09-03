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
# Registers the GraalVM a runner just installed as the toolchain the build
# asks for. The build names the vendor rather than reading JAVA_HOME, so a
# runner has to say where its GraalVM is in the same file a developer keeps
# it in — which also keeps the build here and the build at home the same one.

set -euo pipefail

readonly HOME_M2="$HOME/.m2"

[ -n "${JAVA_HOME:-}" ] || { echo "JAVA_HOME is unset; set up GraalVM first." >&2; exit 1; }

mkdir -p "$HOME_M2"
cat > "$HOME_M2/toolchains.xml" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>21</version>
      <vendor>graalvm</vendor>
    </provides>
    <configuration>
      <jdkHome>$JAVA_HOME</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
XML

cat "$HOME_M2/toolchains.xml"
