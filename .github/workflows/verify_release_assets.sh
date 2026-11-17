#!/usr/bin/env bash
set -euo pipefail

android_name="wynime-$TAG_VERSION-arm64-v8a.apk"
windows_name="wynime-$TAG_VERSION-windows-x86_64.zip"
releases="repos/$GITHUB_REPOSITORY/releases"

assets=$(gh api "$releases/$RELEASE_ID/assets?per_page=100")
jq -e --arg apk "$android_name" --arg zip "$windows_name" '
  (map(.name) | sort) == ([$apk, $zip] | sort) and all(.state == "uploaded" and .size > 0)
' <<< "$assets" > /dev/null
android_id=$(jq -er --arg name "$android_name" '.[] | select(.name == $name).id' <<< "$assets")
windows_id=$(jq -er --arg name "$windows_name" '.[] | select(.name == $name).id' <<< "$assets")

previous_id=$(gh api --paginate --slurp "$releases?per_page=100" | jq -er --arg version "$TAG_VERSION" '
  def version_parts: ltrimstr("v") | split("-")[0] | split(".") | map(tonumber);
  ($version | version_parts) as $current |
  [.[][] | select((.draft or .prerelease) | not) |
    select(.tag_name | test("^v[0-9]+\\.[0-9]+\\.[0-9]+$")) |
    (.tag_name | version_parts) as $parts | select($parts < $current) |
    ("wynime-" + (.tag_name | ltrimstr("v")) + "-arm64-v8a.apk") as $name |
    .assets[] | select(.name == $name and .state == "uploaded") |
    {id, version: $parts}] | max_by(.version).id // empty
')

download_asset() {
  curl --fail --silent --show-error --location --retry 3 \
    -H 'Accept: application/octet-stream' -H "Authorization: Bearer $GH_TOKEN" \
    "https://api.github.com/$releases/assets/$1" --output "$2"
}

apk="$RUNNER_TEMP/$android_name"
zip="$RUNNER_TEMP/$windows_name"
previous_apk="$RUNNER_TEMP/previous-wynime.apk"
download_asset "$android_id" "$apk"
download_asset "$windows_id" "$zip"
download_asset "$previous_id" "$previous_apk"

sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
aapt2="$sdk_root/build-tools/36.0.0/aapt2"
apk_signers() {
  "$apksigner" verify --verbose --print-certs "$1" |
    sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: //p' | sort
}

signers=$(apk_signers "$apk")
previous_signers=$(apk_signers "$previous_apk")
if [[ -z "$signers" || "$signers" != "$previous_signers" ]]; then
  echo 'The APK must use the same signing certificate as the previous official release.' >&2
  exit 1
fi

badging=$("$aapt2" dump badging "$apk")
previous_badging=$("$aapt2" dump badging "$previous_apk")
version_code_pattern="s/^package: .*versionCode='([0-9]+)'.*/\\1/p"
version_code=$(sed -nE "$version_code_pattern" <<< "$badging")
previous_version_code=$(sed -nE "$version_code_pattern" <<< "$previous_badging")
grep -Fq "package: name='com.wynime.app'" <<< "$badging"
grep -Fq "versionName='$TAG_VERSION'" <<< "$badging"
[[ -n "$version_code" && -n "$previous_version_code" ]]
(( version_code > previous_version_code ))
[[ $(sed -n 's/^native-code: //p' <<< "$badging") == "'arm64-v8a'" ]]

unzip -t "$zip" >/dev/null
unzip -Z1 "$zip" | grep -Fx 'Wynime/Wynime.exe' >/dev/null
