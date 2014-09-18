# Introduction

An Ardrone Demo based on Crosswalk External Extension.

*   Use Android API 19.
*   Use [Crosswalk For Android Canary 9.38.208.0](https://download.01.org/crosswalk/releases/crosswalk/android/canary/9.38.208.0/).

# Pre-requisites

What you need first:

*   You'll need to set up your host for Crosswalk Android development
*   You'll need an Android device (real or emulated) to deploy to

See https://crosswalk-project.org/#documentation/getting_started
for details, particularly the host and target setup instructions.

# Building

Set Env Variable:

    $ export ANDROID_SDK_HOME=<PATH TO YOUR SDK HOME>

Run build script:

    $ chmod +x build.sh
    $ ./build.sh

# Install Apks

    $ chmod +x install.sh
    $ ./install.sh x86
    $ ./install.sh arm

Make sure you use the right apk file for your architecture (x86 or ARM).
