#!/bin/bash
# usage: ycenter.sh "exact text"  -> prints y center of that element
export MSYS_NO_PATHCONV=1
S=192.168.10.8:5555
for i in 1 2 3 4 5 6; do
  out=$(adb -s $S shell 'uiautomator dump /sdcard/ui.xml' 2>&1)
  case "$out" in *"null root node"*) sleep 2;; *) break;; esac
done
adb -s $S shell 'cat /sdcard/ui.xml' | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' | awk '{print int(($2+$4)/2)}'
