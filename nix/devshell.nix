{ pkgs, perSystem }:
let
  # Matches compileSdk in gradle/libs.versions.toml and AGP's default build-tools.
  androidSdk =
    (pkgs.androidenv.composeAndroidPackages {
      platformVersions = [ "37.0" ];
      buildToolsVersions = [
        "36.0.0"
        "37.0.0"
      ];
      includeEmulator = false;
      includeSystemImages = false;
      includeSources = false;
    }).androidsdk;

  sdkRoot = "${androidSdk}/libexec/android-sdk";

  traceProcessor = pkgs.stdenv.mkDerivation {
    pname = "trace-processor-shell";
    version = "57.2";
    src = pkgs.fetchurl {
      url = "https://commondatastorage.googleapis.com/perfetto-luci-artifacts/v57.2/linux-amd64/trace_processor_shell";
      sha256 = "55ba613fc6d4f71df81eee2dbfc293020063655c241b3e314bff75345b802684";
    };
    dontUnpack = true;
    nativeBuildInputs = [ pkgs.autoPatchelfHook ];
    buildInputs = [ pkgs.stdenv.cc.cc.lib ];
    installPhase = "install -Dm755 $src $out/bin/trace_processor_shell";
  };

  jadxHeadlessMcp = pkgs.fetchurl {
    url = "https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar";
    sha256 = "6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75";
  };
in
perSystem.devshell.mkShell {
  packages = with pkgs; [
    perSystem.self.formatter # treefmt, the same formatter `nix fmt` runs

    # Android build: JDK for Gradle/AGP, SDK with platform-tools (adb) and build-tools (apksigner)
    jdk21
    androidSdk

    # Tools the repo's agent skills call (.claude/skills). The SDK already provides `android`, apkanalyzer,
    # r8, retrace and lint, so the skills' curl installers aren't needed.
    ktlint # Kotlin style, the same version as the pre-commit hook
    detekt # Kotlin static analysis
    perfetto # record traces on a device (android-profiler)
    traceProcessor # query Perfetto traces with PerfettoSQL (android-profiler)
    # helper scripts (android-profiler), R8 report decoding (r8-analyzer) and the logo generator (design/tools)
    (python3.withPackages (p: [
      p.protobuf
      p.fonttools
    ]))
    librsvg # rsvg-convert: renders the social preview and checks SVGs (design/tools)
    jq # spec-kit scripts parse JSON safely with it
    bundletool # inspect and size app bundles and APK splits
    scrcpy # mirror and control a TV or device over adb
    pre-commit # the hooks in .pre-commit-config.yaml

    # APK reverse engineering (REA MCP providers)
    apktool
    jadx
    procps # REA requires a procps-compatible `ps` for process ownership checks
  ];

  env = [
    {
      name = "NIX_PATH";
      value = "nixpkgs=${toString pkgs.path}";
    }
    {
      name = "JAVA_HOME";
      value = pkgs.jdk21.home;
    }
    {
      name = "ANDROID_HOME";
      value = sdkRoot;
    }
    {
      name = "ANDROID_SDK_ROOT";
      value = sdkRoot;
    }
    {
      # apksigner, aapt2, zipalign
      name = "PATH";
      prefix = "${sdkRoot}/build-tools/37.0.0";
    }
    {
      # The aapt2 Gradle downloads from Maven is dynamically linked and fails on NixOS.
      name = "GRADLE_OPTS";
      value = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/37.0.0/aapt2";
    }
    {
      name = "REA_JADX_MCP_JAR";
      value = "${jadxHeadlessMcp}";
    }
  ];
}
