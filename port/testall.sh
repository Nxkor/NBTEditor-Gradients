#!/bin/bash
# Runs the in-game test of every version, one game after the other, and says PASS or FAIL for each.
# From the project root: port/testall.sh [version...]   (no argument: the twelve versions)
# JDK21 and JDK25 are the two JDKs; the real mouse stays out of the window of the game.
JDK21=${JDK21:-C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot}
JDK25=${JDK25:-C:/Users/imnot/.jdks/jdk-25.0.4.1+1}
for v in ${@:-1.21.11 1.21.10 26.1.2 26.2 26.3 1.17.1 1.18.2 1.19.4 1.20.1 1.20.6 1.21.1 1.21.4}; do
	case $v in
	26.*) export JAVA_HOME=$JDK25;;
	*) export JAVA_HOME=$JDK21;;
	esac
	case $v in
	1.21.11) ./gradlew runClientGameTest --console=plain > build/gametest.log 2>&1 && echo "PASS $v" || echo "FAIL $v";;
	1.21.10|26.*) (cd versions/$v && ./gradlew runClientGameTest --console=plain > gametest.log 2>&1) && echo "PASS $v" || echo "FAIL $v";;
	*) bash port/devcheck.sh $v | tail -1;;
	esac
done
