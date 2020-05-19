#!/usr/bin/env bash

git clone https://github.com/EnMasseProject/enmasse.git
pushd enmasse
git checkout 0.31.1
mvn clean install -N -DskipTests
popd
pushd enmasse/api-model
mvn clean install -DskipTests
popd
