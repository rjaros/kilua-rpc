#!/usr/bin/env bash
#
# Reports any Kilua RPC module that was published without a variant it should have.
#
# Run after `./gradlew publishToMavenLocal -PSNAPSHOT=true`. The check exists because a KMP module
# publishes one variant per declared target, and a target added to the build that the module cannot
# actually produce an artifact for is invisible until a consumer tries to resolve it - at which point
# the consumer fails with "No matching variant", pointing nowhere near here.
#
# It reports and never fails. A missing variant is a bug worth seeing, but it is a bug in library
# metadata rather than a broken build, and the target set it expects depends on the host that ran
# the publication, so a wrong expectation would otherwise turn into a red build for no reason.
#
# Only this project's own modules are considered. The local repository is shared with other dev.kilua
# artifacts, so every module is selected by name from settings.gradle.kts rather than by globbing.
#
# Usage: assert-variants.sh [--apple] [repository-dir]

set -uo pipefail

APPLE=0
if [[ "${1:-}" == "--apple" ]]; then
    APPLE=1
    shift
fi
REPO="${1:-$HOME/.m2/repository}"

if [[ ! -f settings.gradle.kts ]]; then
    echo "::warning::assert-variants.sh must be run from the repository root"
    exit 0
fi

# Variants every module publishes, whatever the host: the JVM and Android targets are declared
# everywhere, and so are the native ones that cross-compile from any host.
EXPECTED=("jvm" "js" "wasmJs" "android" "linuxX64" "linuxArm64" "mingwX64")

# Declared only where Xcode is, so only a publication run from a macOS host can be expected to have
# them. macosX64 is the deprecated Intel build and is deliberately not registered; the other four,
# iosX64 the Intel simulator among them, are.
if [[ "$APPLE" -eq 1 ]]; then
    EXPECTED+=("macosArm64" "iosArm64" "iosX64" "iosSimulatorArm64")
fi

# kilua-rpc-metro is a JVM-only helper module with neither an Android nor a Kotlin/Native target,
# so it is checked against the smaller set rather than being made to match the other eighteen.
JVM_ONLY_MODULES=("kilua-rpc-metro")

module_names="$(grep -oE 'include\(":modules:[a-z0-9-]+"' settings.gradle.kts | sed 's/.*://; s/"//')"

checked=0
problems=0

for module in $module_names; do
    module_dir="$REPO/dev/kilua/$module"

    # Anything not published on this host is not a finding: a Linux run simply has no Apple
    # artifacts, and a module could legitimately not have been reached by the publication task.
    if [[ ! -d "$module_dir" ]]; then
        echo "::warning::$module was not published at all - is it in the publication graph?"
        problems=$((problems + 1))
        continue
    fi

    # A local repository accumulates versions, and `find` would pick whichever the filesystem lists
    # first - which on a developer machine is usually an old one that genuinely predates a target. CI
    # starts from an empty repository, so this only matters locally, but picking wrong here would
    # report the same missing variants forever.
    #
    # `sort -V` is GNU only, and this script also runs in the macOS job, so it falls back to a plain
    # sort there. The publication puts one version in the repository, so the fallback cannot pick
    # wrong on the host where it is used.
    versions="$(find "$module_dir" -maxdepth 1 -mindepth 1 -type d | sed 's|.*/||')"
    if sort -V </dev/null >/dev/null 2>&1; then
        version="$(printf '%s\n' "$versions" | sort -V | tail -1)"
    else
        version="$(printf '%s\n' "$versions" | sort | tail -1)"
    fi
    module_file="$module_dir/$version/$module-$version.module"
    if [[ -z "$version" || ! -f "$module_file" ]]; then
        echo "::warning::$module has no Gradle module metadata, so its variants cannot be read"
        problems=$((problems + 1))
        continue
    fi

    variants="$(grep -o '"name": "[a-zA-Z0-9]*ApiElements[^"]*"' "$module_file" \
        | sed 's/"name": "//; s/ApiElements.*//' \
        | sort -u)"

    expected=("${EXPECTED[@]}")
    for jvm_only in "${JVM_ONLY_MODULES[@]}"; do
        if [[ "$module" == "$jvm_only" ]]; then
            expected=("jvm")
        fi
    done

    missing=()
    for variant in "${expected[@]}"; do
        grep -qx "$variant" <<< "$variants" || missing+=("$variant")
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        echo "::warning::$module does not publish: ${missing[*]} (found: $(tr '\n' ' ' <<< "$variants"))"
        problems=$((problems + 1))
    fi
    checked=$((checked + 1))
done

echo "Checked $checked module(s); $problems with missing or absent variants."
exit 0