#!/bin/bash
PROJECT_DIR=$(cd $(dirname $0) ; pwd)

if [ ! -d $PROJECT_DIR/out ] ; then
  echo "Please run build.sh at frist!!!"
  exit 0
fi

if [[ -z "$1" ]] ; then
  echo "Please add x86 or arm parameters!!!" 
  exit 0
fi

APKS=`ls $PROJECT_DIR/out/ArdroneDemo*.apk`

for apk in $APKS ; do
  if [[ $apk == *x86* && $1 == "x86" ]] ; then
    adb install -r $apk
  fi
  if [[ $apk == *arm* && $1 == "arm" ]] ; then
    adb install -r $apk
  fi
done
