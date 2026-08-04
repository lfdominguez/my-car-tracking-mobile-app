{ pkgs, lib, config, inputs, ... }:

let
  # AGP/Gradle in this repo are not ready for JDK 25+ (pkgs.jetbrains.jdk).
  # Pin a stable LTS used for Android builds.
  jdk = pkgs.jdk21;
in
{

  android = {
      enable = true;
      platforms.version = [ "36" ];
      systemImageTypes = [];
      abis = [ "arm64-v8a" "x86_64" ];
      cmake.version = [ "3.22.1" ];
      cmdLineTools.version = "11.0";
      tools.version = "26.1.1";
      platformTools.version = "36.0.1";
      buildTools.version = [ "35.0.0" ];
      emulator = {
        enable = false;
      };
      sources.enable = false;
      systemImages.enable = false;
      ndk.enable = true;
      googleAPIs.enable = true;
      googleTVAddOns.enable = false;
      extras = [ "extras;google;gcm" ];
      extraLicenses = [
        "android-sdk-preview-license"
        "android-googletv-license"
        "android-sdk-arm-dbt-license"
        "google-gdk-license"
        "intel-android-extra-license"
        "intel-android-sysimage-license"
        "mips-android-sysimage-license"
      ];
      android-studio = {
        enable = false;
      };
    };

  packages = [
    jdk
  ];

  # Set up Java environment
  languages.java = {
    enable = true;
    jdk.package = jdk;
    gradle.enable = true;
  };

  languages.kotlin.enable = true;

  env = {
    # Prefer the pinned JDK for Gradle even if another java appears earlier on PATH.
    # Do not override GRADLE_OPTS here — the android module sets aapt2FromMavenOverride.
    JAVA_HOME = "${jdk.home}";
  };

  enterShell = ''
    export JAVA_HOME="${jdk.home}"
    export PATH="$JAVA_HOME/bin:$PATH"
    # Keep android GRADLE_OPTS and force this JDK for the Gradle daemon.
    export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME -Dorg.gradle.java.installations.auto-download=false ''${GRADLE_OPTS:-}"
    echo "Java version:"
    java -version
    echo ""
    echo "JAVA_HOME: $JAVA_HOME"
    echo "ANDROID_HOME: $ANDROID_HOME"
  '';


}
