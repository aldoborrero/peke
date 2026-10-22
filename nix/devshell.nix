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

  # Headless JADX engine used by the REA MCP server for APK analysis.
  # Hash pinned by REA (src/android/JadxRelease.ts).
  jadxHeadlessMcp = pkgs.fetchurl {
    url = "https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar";
    sha256 = "6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75";
  };
in
perSystem.devshell.mkShell {
  packages = with pkgs; [
    nixfmt-tree # same formatter `nix fmt` uses, for the nix/ files

    # Android build: JDK for Gradle/AGP, SDK with platform-tools (adb) and build-tools (apksigner)
    jdk21
    androidSdk

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
