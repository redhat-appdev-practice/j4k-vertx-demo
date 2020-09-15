#!/bin/bash

cd ui
yarn install
yarn run build
cd ..

cd api
mvn clean package
cd ..
