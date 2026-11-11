# Build environment for SharpCalc: JDK 17 + Android SDK 34 + Gradle 8.9.
# Nothing is installed on the host; everything lives in this image.
FROM eclipse-temurin:17-jdk-jammy
RUN apt-get update && apt-get install -y --no-install-recommends unzip curl zip git && rm -rf /var/lib/apt/lists/*
ENV ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp \
 && curl -fsSL -o ct.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
 && unzip -q ct.zip && mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest && rm ct.zip \
 && yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null \
 && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /dev/null \
 && chmod -R a+rX $ANDROID_HOME
RUN cd /opt && curl -fsSL -o g.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip && unzip -q g.zip && rm g.zip
ENV PATH=$PATH:/opt/gradle-8.9/bin:/opt/android-sdk/platform-tools
ENV GRADLE_USER_HOME=/cache/gradle
WORKDIR /work
