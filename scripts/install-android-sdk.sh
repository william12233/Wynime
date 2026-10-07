#!/usr/bin/env bash
 
                                                   
                                                       
 

set -euo pipefail

                                                                      
                                   
apt -qq update
apt -qq install -y --install-recommends android-sdk unzip curl

                                                                      
             
export ANDROID_HOME=/usr/lib/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"

echo "sdk.dir=$ANDROID_HOME" > local.properties

                                                                      
                                                           
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd /tmp
curl -sSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdline-tools.zip
rm -rf cmdline-tools
unzip -q cmdline-tools.zip
mv cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
rm cmdline-tools.zip

                                                                      
                                                          
                                                                             
                                                                        
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || [[ $? -eq 141 ]]

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" \
      "platforms;android-35" \
      "build-tools;35.0.0" \
      "platform-tools"             || [[ $? -eq 141 ]]

echo "✅ Android SDK API 35 installed."
