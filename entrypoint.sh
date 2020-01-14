#!/usr/bin/env bash

java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar "/app.jar"
