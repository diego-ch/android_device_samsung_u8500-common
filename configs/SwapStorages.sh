#!/system/bin/sh

MDIR=/system
PROP=/system/build.prop
P1="persist.sys.vold.switchexternal"

if [ $# -lt 1 ]; then
  exit
fi

MNT=$( cat /proc/mounts | grep " $MDIR " | cut -d ' ' -f 4 | tr ',' ' ' )
if [ ! -z "$MNT" ]; then
  REMOUNT=0
  for OPT in $MNT; do
    if [ "$OPT" == "rw" ]; then
      REMOUNT=0
      break;
    fi
  if [ "$OPT" == "ro" ]; then
    REMOUNT=1
    break;
  fi
  done
else
  exit
fi

if [ "$REMOUNT" == "1" ]; then
  mount -o remount,rw $MDIR
fi

V=$(grep "^$P1=" $PROP)
if [ "$V" == "" ]; then
  echo "" >> $PROP
  echo "$P1=$1" >> $PROP
else
  sed -i "s/^$P1=.*$/$P1=$1/g" $PROP
fi

if [ "$REMOUNT" == "1" ]; then
  mount -o remount,ro $MDIR
fi
