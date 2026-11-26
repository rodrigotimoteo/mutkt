#!/usr/bin/env bash
# Checks what publishToMavenLocal wrote: every artifact is signed, every POM has the metadata Central requires,
# and the modules with a user-facing API ship Dokka HTML in their javadoc jar.
# Signatures are verified against the keyring in GNUPGHOME.
# Usage: tools/release/verify-publication.sh <maven-repository> <version>
set -euo pipefail

repo=${1:?usage: verify-publication.sh <maven-repository> <version>}
version=${2:?usage: verify-publication.sh <maven-repository> <version>}
group_dir="$repo/io/github/rodrigotimoteo"
marker_id=io.github.rodrigotimoteo.mutkt.gradle.plugin
modules=(mutkt-annotations mutkt-core mutkt-asm mutkt-junit mutkt-report mutkt-gradle)
documented=(mutkt-annotations mutkt-gradle)

files=()
for module in "${modules[@]}"; do
  for suffix in .pom .module .jar -sources.jar -javadoc.jar; do
    files+=("$group_dir/$module/$version/$module-$version$suffix")
  done
done
marker_pom="$group_dir/mutkt/$marker_id/$version/$marker_id-$version.pom"
files+=("$marker_pom")

for f in "${files[@]}"; do
  [[ -f "$f" ]] || { echo "missing: $f" >&2; exit 1; }
  [[ -f "$f.asc" ]] || { echo "unsigned: $f" >&2; exit 1; }
  gpg --batch --quiet --verify "$f.asc" "$f" 2>/dev/null || { echo "bad signature: $f" >&2; exit 1; }
done

poms=("$marker_pom")
for module in "${modules[@]}"; do poms+=("$group_dir/$module/$version/$module-$version.pom"); done
docs=()
for module in "${documented[@]}"; do docs+=("$group_dir/$module/$version/$module-$version-javadoc.jar"); done
python3 -I - "$group_dir/mutkt-gradle/$version/mutkt-gradle-$version.jar" "${docs[@]}" -- "${poms[@]}" <<'PY'
import sys, zipfile
import xml.etree.ElementTree as ET

ns = {"m": "http://maven.apache.org/POM/4.0.0"}
required = ["m:name", "m:description", "m:url", "m:licenses/m:license/m:name", "m:scm/m:url",
            "m:scm/m:connection", "m:developers/m:developer/m:id"]
split = sys.argv.index("--")
docs, poms = sys.argv[2:split], sys.argv[split + 1:]
failures = []
for pom in poms:
    root = ET.parse(pom).getroot()
    for path in required:
        node = root.find(path, ns)
        if node is None or not (node.text or "").strip():
            failures.append(f"{pom}: missing {path.replace('m:', '')}")
descriptor = "META-INF/gradle-plugins/io.github.rodrigotimoteo.mutkt.properties"
plugin_jar = zipfile.ZipFile(sys.argv[1])
if descriptor not in plugin_jar.namelist():
    failures.append(f"{sys.argv[1]}: missing {descriptor}")
elif "compatibility.feature.configuration-cache=DECLARED_SUPPORTED" not in plugin_jar.read(descriptor).decode().splitlines():
    failures.append(f"{sys.argv[1]}: {descriptor} does not declare configuration cache support")
for jar in docs:
    if "index.html" not in zipfile.ZipFile(jar).namelist():
        failures.append(f"{jar}: no Dokka index.html")
if failures:
    sys.exit("\n".join(failures))
PY
echo "publication verified: ${#files[@]} signed files, ${#poms[@]} POMs"
