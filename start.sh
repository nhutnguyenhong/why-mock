#!/usr/bin/env bash
mvn install

#docker login container-registry.oracle.com
docker build -t whymock:latest .
#docker network create whymock_default
docker-compose -f docker-compose.local.yml down
docker-compose -f docker-compose.local.yml up

