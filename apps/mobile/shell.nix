{ pkgs ? import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  }
}:

let
  androidComposition = pkgs.androidenv.composeAndroidPackages {
    # Build tools matching build.gradle.kts compileSdk / targetSdk
    # AGP 8.7.3 requires build-tools 34 in addition to 35
    platformVersions = [ "34" "35" ];
    buildToolsVersions = [ "34.0.0" "35.0.0" ];

    # Emulator + system image for local UI testing
    includeEmulator = true;
    includeSystemImages = true;
    systemImageTypes = [ "google_apis" ];
    abiVersions = [ "x86_64" ];

    # Extras
    includeNDK = false;
    includeSources = false;
    includeExtras = [];
  };

  androidSdk = androidComposition.androidsdk;
in
pkgs.mkShell {
  buildInputs = [
    androidSdk
    pkgs.jdk17
    pkgs.gradle
  ];

  ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
  ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
  JAVA_HOME = "${pkgs.jdk17.home}";

  # Gradle needs writable directories
  GRADLE_USER_HOME = toString ./.gradle-home;

  shellHook = ''
    echo ""
    echo "Android development shell ready"
    echo "  ANDROID_HOME=$ANDROID_HOME"
    echo "  JAVA_HOME=$JAVA_HOME"
    echo "  SDK platforms: 35"
    echo "  Build tools: 35.0.0"
    echo ""
    echo "Available commands:"
    echo "  ./gradlew assembleDebug   - Build debug APK"
    echo "  ./gradlew test            - Run unit tests"
    echo "  ./gradlew lint            - Run lint checks"
    echo "  emulator -list-avds       - List AVDs"
    echo ""
  '';
}
