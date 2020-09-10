#!/bin/bash

cd ui
yarn install
yarn run build
cd ..

cd api
mvn clean package
mvn clean compile package -Pkubernetes
cd ..
