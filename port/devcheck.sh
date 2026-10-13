#!/bin/bash
# Runs the in-game check of one of the versions of NBT Editor 2.0.3 (1.17.1 to 1.21.4): port/devcheck.sh <version>
# The game starts from nothing each time: no world (the check makes one), and options that open no first-launch screen.
# The interface is at scale 1 (854 wide), where the gradient editor does not cover the fields.
# One game at a time, and the real mouse out of its window.
set -e
v=$1
run=versions/$v/build/run/devCheck
mkdir -p $run
rm -rf $run/saves $run/screenshots
cat > $run/options.txt <<EOF
onboardAccessibility:false
pauseOnLostFocus:false
tutorialStep:none
skipMultiplayerWarning:true
narrator:0
guiScale:1
soundCategory_master:0.0
EOF
cd versions/$v
./gradlew runDevCheck --console=plain > devcheck.log 2>&1 || true
grep 'dev check' devcheck.log | tail -40
grep -q 'dev check done' devcheck.log && ! grep -q 'dev check failed' devcheck.log && echo "PASS $v" || { echo "FAIL $v"; exit 1; }
