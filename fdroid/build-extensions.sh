#!/usr/bin/env bash
# Builds MPV and the Media3 FFmpeg and AV1 decoders (damontecres/wholphin-extensions) from source into app/libs/,
# for F-Droid's build server. See fdroid/README.md.
#
#   build-extensions.sh prepare        # recipe prebuild: lays the srclibs out where the upstream scripts expect them
#   build-extensions.sh build <abi>    # recipe build: armeabi-v7a, arm64-v8a or x86_64
#
# Paths come from the environment, filled in by the recipe from its srclibs:
#   NDK                  NDK 29 ($$NDK$$)
#   WHOLPHIN_EXTENSIONS  wholphin-extensions at the tag in gradle/libs.versions.toml
#   MEDIA                androidx/media at the tag wholphin-extensions pins
#   FFMPEG DAV1D CPU_FEATURES MBEDTLS FREETYPE FRIBIDI HARFBUZZ UNIBREAK LIBASS LIBPLACEBO MPV
#
# The decoder list, the API level and every dependency version come from wholphin-extensions' own scripts; `prepare`
# fails when a srclib is at another version, so bumping wholphin-extensions shows what the recipe needs.
set -euo pipefail

PEKE_APP="$(cd "$(dirname "${BASH_SOURCE[0]}")/../app" && pwd)"
LUA_URL="https://www.lua.org/ftp/lua-5.2.4.tar.gz"
LUA_SHA256="b9e2e4aad6789b3b63a056d442f7b39f0ecfca3ae0f1fc0ae4e9614401b69f4b"

EXT="${WHOLPHIN_EXTENSIONS:?}"
MEDIA="${MEDIA:?}"
NATIVE="$EXT/wholphin-mpv/src/native"
DEPS="$EXT/wholphin-mpv/build/libmpv/deps"
FFMPEG_MODULE="$MEDIA/libraries/decoder_ffmpeg/src/main"
AV1_MODULE="$MEDIA/libraries/decoder_av1/src/main"

stamp() { printf '\n==> [%(%T)T] %s\n' -1 "$*"; }

# A variable assigned in one of wholphin-extensions' scripts, e.g. FFMPEG_BRANCH="n9.0"
upstream_var() { sed -n "s/^$2=\"\{0,1\}\([^\"]*\)\"\{0,1\}$/\1/p" "$1" | head -n1; }

# The ref get_dependencies.sh clones for a repository URL fragment, e.g. "mpv-player/mpv" -> v0.41.0
upstream_clone_ref() { sed -n "s|^clone \"[^\"]*$1[^\"]*\" \"\([^\"]*\)\".*|\1|p" "$NATIVE/get_dependencies.sh"; }

# The version in a tarball URL get_dependencies.sh downloads, e.g. "fribidi" -> 1.0.16
upstream_tarball_version() {
	sed -n "s|.*/releases/download/[^ ]*/$1-\([0-9][0-9.]*[0-9]\)\.tar.*|\1|p" "$NATIVE/get_dependencies.sh" | head -n1
}

# Fails unless the srclib checkout is exactly at one of the given tags
expect_tag() {
	local name="$1" dir="$2"
	shift 2
	local tags
	tags="$(git -C "$dir" tag --points-at HEAD)"
	for want in "$@"; do
		if grep -qxF "$want" <<<"$tags"; then
			return 0
		fi
	done
	echo "srclib $name is at '$(git -C "$dir" describe --tags --always)', wholphin-extensions wants $*" >&2
	exit 1
}

check_versions() {
	local decoders="$EXT/wholphin-media3/build_decoders.sh"
	local media_version unibreak_version
	media_version="$(sed -n 's/^androidx-media3 = "\(.*\)"/\1/p' "$EXT/gradle/libs.versions.toml")"
	unibreak_version="$(upstream_var "$NATIVE/get_dependencies.sh" version_unibreak)"

	expect_tag media "$MEDIA" "$media_version"
	expect_tag FFmpeg "$FFMPEG" "$(upstream_var "$decoders" FFMPEG_BRANCH)"
	expect_tag FFmpeg "$FFMPEG" "$(upstream_clone_ref FFmpeg/FFmpeg)"
	expect_tag dav1d "$DAV1D" "$(upstream_var "$decoders" DAV1D_BRANCH)"
	expect_tag dav1d "$DAV1D" "$(upstream_clone_ref videolan/dav1d)"
	expect_tag freetype2 "$FREETYPE" "$(upstream_clone_ref freetype/freetype)"
	expect_tag libass "$LIBASS" "$(upstream_clone_ref libass/libass)"
	expect_tag libplacebo "$LIBPLACEBO" "$(upstream_clone_ref haasn/libplacebo)"
	expect_tag mpv "$MPV" "$(upstream_clone_ref mpv-player/mpv)"
	expect_tag mbedtls "$MBEDTLS" "mbedtls-$(upstream_tarball_version mbedtls)" "v$(upstream_tarball_version mbedtls)"
	expect_tag fribidi "$FRIBIDI" "v$(upstream_tarball_version fribidi)"
	expect_tag harfbuzz "$HARFBUZZ" "$(upstream_tarball_version harfbuzz)"
	expect_tag libunibreak "$UNIBREAK" "libunibreak_${unibreak_version//./_}"
	# cpu_features is cloned at its default branch upstream, so there is no version to compare
	if ! grep -qF "$LUA_URL" "$NATIVE/get_dependencies.sh"; then
		echo "get_dependencies.sh no longer downloads $LUA_URL" >&2
		exit 1
	fi
}

prepare() {
	stamp "check srclib versions against wholphin-extensions"
	check_versions

	stamp "media3: FFmpeg, dav1d and cpu_features next to the decoder JNI code"
	# Same patch as wholphin-media3/build_decoders.sh: FFmpeg 8 dropped postproc
	sed -i '/--disable-postproc/d' "$FFMPEG_MODULE/jni/build_ffmpeg.sh"
	ln -sfn "$FFMPEG" "$FFMPEG_MODULE/jni/ffmpeg"
	ln -sfn "$DAV1D" "$AV1_MODULE/jni/dav1d"
	ln -sfn "$CPU_FEATURES" "$AV1_MODULE/jni/cpu_features"

	stamp "libmpv: dependencies where get_dependencies.sh would put them"
	# Copies, not links: the media3 build configures FFmpeg and dav1d inside their source trees
	mkdir -p "$DEPS"
	local dep src
	for dep in dav1d:DAV1D ffmpeg:FFMPEG freetype2:FREETYPE libass:LIBASS libplacebo:LIBPLACEBO mpv:MPV \
		mbedtls:MBEDTLS fribidi:FRIBIDI harfbuzz:HARFBUZZ unibreak:UNIBREAK; do
		src="${dep#*:}"
		rm -rf "${DEPS:?}/${dep%%:*}"
		cp -a "${!src:?}" "$DEPS/${dep%%:*}"
	done
	# libunibreak's release tarball ships a generated configure script; the git tag does not
	(cd "$DEPS/unibreak" && ./bootstrap)

	# Lua has no git repository; the tarball is checked against a fixed hash
	rm -rf "$DEPS/lua"
	mkdir -p "$DEPS/lua"
	curl -fsSL -o "$DEPS/lua.tar.gz" "$LUA_URL"
	echo "$LUA_SHA256  $DEPS/lua.tar.gz" | sha256sum -c -
	tar -xz --strip-components=1 -C "$DEPS/lua" -f "$DEPS/lua.tar.gz"
	rm "$DEPS/lua.tar.gz"

	# buildscripts look for the NDK under sdk/android-ndk-$v_ndk (include/path.sh)
	local v_ndk
	v_ndk="$(upstream_var "$NATIVE/include/depinfo.sh" v_ndk)"
	mkdir -p "$NATIVE/sdk"
	ln -sfn "${NDK:?}" "$NATIVE/sdk/android-ndk-$v_ndk"

	# media3 sets no ndkVersion, so AGP would ask for its own default NDK; build the JNI code with the recipe's NDK, which
	# also builds FFmpeg, dav1d and libmpv
	local module ndk_version
	ndk_version="$(sed -n 's/^Pkg.Revision *= *//p' "$NDK/source.properties")"
	for module in decoder_ffmpeg decoder_av1; do
		printf 'android.ndkVersion = "%s"\nandroid.ndkPath = "%s"\n' "$ndk_version" "$NDK" \
			>>"$MEDIA/libraries/$module/build.gradle.kts"
	done

	# Peke's GitHub Packages repository serves the prebuilt AARs this script replaces, and fdroid scanner rejects it
	local peke_settings="$PEKE_APP/../settings.gradle.kts"
	perl -0pi -e 's/\n *\@Suppress\("ktlint:standard:property-naming"\)\n *val WholphinExtensionsUsername.*?\n {8}\}\n/\n/s' \
		"$peke_settings"
	if grep -q 'maven.pkg.github.com' "$peke_settings"; then
		echo "could not remove the GitHub Packages repository from $peke_settings" >&2
		exit 1
	fi

	# The foojay plugin downloads JDKs; the build server's JDK is used instead
	local settings
	for settings in "$EXT"/settings.gradle* "$MEDIA"/settings.gradle*; do
		sed -i '/foojay/d' "$settings"
	done
}

build() {
	local abi="$1" mpv_arch
	case "$abi" in
	armeabi-v7a) mpv_arch=armv7l ;;
	arm64-v8a) mpv_arch=arm64 ;;
	x86_64) mpv_arch=x86_64 ;;
	*)
		echo "unknown ABI $abi" >&2
		exit 1
		;;
	esac
	local decoders="$EXT/wholphin-media3/build_decoders.sh"
	local api decoder_list
	api="$(upstream_var "$decoders" ANDROID_ABI)"
	decoder_list="$(sed -n 's/^ENABLED_DECODERS=(\(.*\))$/\1/p' "$decoders")"
	local host_platform
	host_platform="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"

	stamp "media3 FFmpeg decoder"
	(cd "$FFMPEG_MODULE/jni" && read -ra list <<<"$decoder_list" &&
		./build_ffmpeg.sh "$FFMPEG_MODULE" "$NDK" "$host_platform" "$api" "${list[@]}")

	stamp "media3 AV1 decoder"
	(cd "$AV1_MODULE/jni" && ./build_dav1d.sh "$AV1_MODULE" "$NDK" "$host_platform")

	stamp "media3 decoder AARs"
	(cd "$MEDIA" && ./gradlew --no-daemon :lib-decoder-ffmpeg:assembleRelease :lib-decoder-av1:assembleRelease)

	stamp "libmpv for $mpv_arch"
	(cd "$NATIVE" && ./buildall.sh --arch "$mpv_arch" mpv)

	stamp "wholphin-mpv AAR for $abi"
	# Android.mk needs a libmpv prefix for every ABI in abiFilters, and this build only has one
	sed -i "/abiFilters += \"/{/\"$abi\"/!d}" "$EXT/wholphin-mpv/build.gradle.kts"
	(cd "$EXT" && ./gradlew --no-daemon :wholphin-mpv:assembleRelease)

	mkdir -p "$PEKE_APP/libs"
	cp "$MEDIA/libraries/decoder_ffmpeg/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar" \
		"$MEDIA/libraries/decoder_av1/buildout/outputs/aar/lib-decoder-av1-release.aar" \
		"$EXT/wholphin-mpv/build/outputs/aar/wholphin-mpv-release.aar" \
		"$PEKE_APP/libs/"
	stamp "done: $(ls "$PEKE_APP/libs")"
}

case "${1:-}" in
prepare) prepare ;;
build) build "${2:?usage: $0 build <abi>}" ;;
*)
	echo "usage: $0 prepare | build <abi>" >&2
	exit 1
	;;
esac
