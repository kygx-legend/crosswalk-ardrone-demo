#!/bin/bash

# directory containing this script
PROJECT_DIR=$(cd $(dirname $0) ; pwd)

EXTENSION_SRC=$PROJECT_DIR/xwalk-ardrone-demo-extension-src
APP_SRC=$PROJECT_DIR/xwalk-ardrone-demo-app

# get crosswalk for android
if [ ! -f $EXTENSION_SRC/lib/crosswalk.zip ] ; then
  echo
  echo "********* DOWNLOADING CROSSWALK..."
  echo
  wget https://download.01.org/crosswalk/releases/crosswalk/android/canary/9.38.208.0/crosswalk-9.38.208.0.zip
  mv crosswalk-9.38.208.0.zip $EXTENSION_SRC/lib/crosswalk.zip
  cd $EXTENSION_SRC/lib
  unzip crosswalk.zip
  cd -
fi

# build the extension
echo
echo "********* BUILDING EXTENSION..."
echo
cd $EXTENSION_SRC
ant

# location of Crosswalk Android (downloaded during extension build)
XWALK_DIR=$EXTENSION_SRC/lib/`ls $EXTENSION_SRC/lib/ | grep 'crosswalk-'`
echo $XWALK_DIR

# build the apks
echo
echo "********* BUILDING ANDROID APK FILES..."
cd $XWALK_DIR
python make_apk.py --package=org.xwalk.extension.ardrone_demo --fullscreen --enable-remote-debugging --manifest=$APP_SRC/manifest.json --extensions=$EXTENSION_SRC/xwalk-ardrone-demo-extension/

# back to where we started
cd $PROJECT_DIR

# show the location of the output apk files
echo
echo "********* APK FILES GENERATED:"
APKS=`ls $XWALK_DIR/ArdroneDemo*.apk`
mkdir out
for apk in $APKS ; do
  echo $apk
  mv $apk ./out
done
echo
