#!/usr/bin/env bash
#
# Builds the LibreOffice engine for Android (64-bit ARM phones) and installs it
# into libreoffice-kit/, so 360Reader can show Word, Excel and PowerPoint files
# on your phone with their real layout.
#
# Run this ONCE on Linux, or on Windows inside WSL2 (Ubuntu 24.04):
#
#     bash tools/build-android-engine.sh
#
# Needs: ~40 GB free disk, 16 GB RAM recommended, and 1–3 hours the first time.
# Re-running is incremental and much faster.
#
# Optional settings (environment variables):
#   LO_WORK      where to build            (default: ~/lo-android — keep it on the Linux disk, not /mnt/c)
#   LO_BRANCH    LibreOffice branch        (default: the one in tools/engine.conf)
#   NDK_VERSION  Android NDK version       (default: 27.2.12479018; LibreOffice accepts 27–29)
#   JOBS         parallel build jobs       (default: number of CPU cores)

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KIT_DIR="$PROJECT_DIR/libreoffice-kit/src/main"
WORK="${LO_WORK:-$HOME/lo-android}"
SDK="$WORK/android-sdk"
CORE="$WORK/core"
# Same LibreOffice branch and NDK as the cloud build, unless overridden.
[ -f "$PROJECT_DIR/tools/engine.conf" ] && . "$PROJECT_DIR/tools/engine.conf"
NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
# One compile job per ~2 GB of RAM keeps the link steps from running out of memory.
MEM_GB=$(awk '/MemTotal/ {print int($2/1024/1024)}' /proc/meminfo)
JOBS="${JOBS:-$(( MEM_GB / 2 < $(nproc) ? (MEM_GB / 2 > 0 ? MEM_GB / 2 : 1) : $(nproc) ))}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

say() { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31mError: %s\033[0m\n' "$*" >&2; exit 1; }

case "$PROJECT_DIR" in
  /mnt/*) echo "Note: the project is on the Windows drive. That's fine; only the engine build runs in $WORK." ;;
esac
mkdir -p "$WORK"
FREE_GB=$(df --output=avail -BG "$WORK" | tail -1 | tr -dc 0-9)
echo "Free disk: ${FREE_GB} GB, RAM: ${MEM_GB} GB, parallel jobs: $JOBS"
[ "$FREE_GB" -ge 45 ] || die "Need about 45 GB free in $WORK (have ${FREE_GB} GB)."

# ---------------------------------------------------------------------------
say "1/5  Installing build tools (you may be asked for your password)"
sudo apt-get update -y
sudo apt-get install -y \
  git build-essential zip unzip wget curl ccache \
  autoconf automake libtool pkg-config gperf flex bison nasm \
  python3 python3-dev python3-setuptools gettext xsltproc libxml2-utils \
  openjdk-21-jdk ant libkrb5-dev libnss3-dev libfontconfig1-dev

# ---------------------------------------------------------------------------
say "2/5  Android SDK and NDK $NDK_VERSION"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  wget -q --show-progress -O "$WORK/$CMDLINE_TOOLS_ZIP" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/cmdline-tools"
  unzip -q "$WORK/$CMDLINE_TOOLS_ZIP" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$SDK" --install \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0" "ndk;$NDK_VERSION"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"

# ---------------------------------------------------------------------------
say "3/5  Getting LibreOffice source"
if [ ! -d "$CORE/.git" ]; then
  BRANCH="${LO_BRANCH:-}"
  if [ -z "$BRANCH" ]; then
    # Newest stable release branch, e.g. libreoffice-25-8.
    BRANCH="$(git ls-remote --heads https://git.libreoffice.org/core 'libreoffice-[0-9]*-[0-9]*' 2>/dev/null \
      | sed 's|.*refs/heads/||' | grep -E '^libreoffice-[0-9]+-[0-9]+$' | sort -V | tail -n 2 | head -n 1 || true)"
  fi
  if [ -n "$BRANCH" ]; then
    echo "Using branch $BRANCH"
    git clone --depth 1 --branch "$BRANCH" https://git.libreoffice.org/core "$CORE" \
      || git clone --depth 1 --branch "$BRANCH" https://github.com/LibreOffice/core.git "$CORE"
  else
    git clone --depth 1 https://git.libreoffice.org/core "$CORE" \
      || git clone --depth 1 https://github.com/LibreOffice/core.git "$CORE"
  fi
fi

# ---------------------------------------------------------------------------
say "4/5  Building LibreOffice for Android (this is the long part)"
cd "$CORE"
cat > autogen.input <<EOF
--with-distro=LibreOfficeAndroidAarch64
--with-android-sdk=$SDK
--with-android-ndk=$SDK/ndk/$NDK_VERSION
--with-parallelism=$JOBS
--enable-ccache
EOF
if [ ! -f config_host.mk ]; then
  ./autogen.sh
fi
make

APK="$(find "$CORE" -path '*outputs/apk*' -name '*.apk' -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -n 1 | cut -d' ' -f2- || true)"
if [ -z "$APK" ]; then
  echo "No APK yet; building the LibreOffice viewer app with Gradle…"
  (cd "$CORE/android" && ./gradlew assembleStrippedUIRelease) || (cd "$CORE/android" && ./gradlew assembleStrippedUIDebug)
  APK="$(find "$CORE" -path '*outputs/apk*' -name '*.apk' -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -n 1 | cut -d' ' -f2- || true)"
fi
[ -n "$APK" ] || die "Couldn't find the built LibreOffice APK under $CORE. Check the make output above."
echo "Engine package: $APK"

# ---------------------------------------------------------------------------
say "5/5  Installing the engine into 360Reader"
STAGE="$WORK/stage"
rm -rf "$STAGE" && mkdir -p "$STAGE"
unzip -q "$APK" 'lib/arm64-v8a/*' 'assets/*' -d "$STAGE"
[ -f "$STAGE/lib/arm64-v8a/liblo-native-code.so" ] || die "liblo-native-code.so is missing from the APK."

rm -rf "$KIT_DIR/jniLibs/arm64-v8a"
mkdir -p "$KIT_DIR/jniLibs" "$KIT_DIR/assets"
cp -r "$STAGE/lib/arm64-v8a" "$KIT_DIR/jniLibs/"
find "$KIT_DIR/assets" -mindepth 1 ! -name '.gitkeep' -exec rm -rf {} + 2>/dev/null || true
cp -r "$STAGE/assets/." "$KIT_DIR/assets/"

# Keep the Java bindings exactly in step with the native library just built.
cp "$CORE"/android/Bootstrap/src/org/libreoffice/kit/*.java "$KIT_DIR/java/org/libreoffice/kit/"

# One file for GitHub: the cloud app build downloads it from the "office-engine" release.
PKG="$WORK/package"
rm -rf "$PKG" && mkdir -p "$PKG/java/org/libreoffice/kit" "$PKG/jniLibs"
cp -r "$STAGE/lib/arm64-v8a" "$PKG/jniLibs/"
cp -r "$STAGE/assets" "$PKG/assets"
cp "$CORE"/android/Bootstrap/src/org/libreoffice/kit/*.java "$PKG/java/org/libreoffice/kit/"
git -C "$CORE" log -1 --format='LibreOffice %H %cd' > "$PKG/ENGINE_VERSION.txt"
TARBALL="$WORK/lo-engine-arm64.tar.gz"
tar -C "$PKG" -czf "$TARBALL" .

# On Windows (WSL), also drop it into your Downloads folder.
if command -v cmd.exe > /dev/null 2>&1; then
  WIN_PROFILE="$(cmd.exe /c 'echo %USERPROFILE%' 2>/dev/null | tr -d '\r')"
  if [ -n "$WIN_PROFILE" ]; then
    DOWNLOADS="$(wslpath "$WIN_PROFILE")/Downloads"
    cp "$TARBALL" "$DOWNLOADS/" && TARBALL="$DOWNLOADS/lo-engine-arm64.tar.gz (in your Windows Downloads folder)"
  fi
fi

SIZE="$(du -sh "$KIT_DIR/jniLibs/arm64-v8a" | cut -f1)"
say "Done. Engine built ($SIZE of native code)."
echo "Upload this file to the GitHub release named office-engine:"
echo "  $TARBALL"
