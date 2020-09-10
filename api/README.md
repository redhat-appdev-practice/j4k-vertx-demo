# Vert.x Kubernetes Demo

## Overview

This is a relatively simple application to demonstrate some of the features of [Vert.x](https://vertx.io/) which
make it an amazing technology to use on Kubernetes.

## Prerequisites
* NodeJS >= 12
* NPM >= 6.14
* Yarn >= 1.22
* Java >= 1.8
* Maven >= 3.6
* Helm >= 3.0
* Docker Compose >= 3.8 (OPTIONAL)

## Build This Project

1. Clone this repo: `git clone https://github.com/redhat-appdev-practice/j4k-vertx-demo.git`
2. Run the `build_all.sh` script in the root of the cloned repo

## Run Locally Using Docker Compose

```
cd compose
docker-compose up -d

## Scaling Up And Down the number of containers
docker-compose scale api=<num>
```

## Use Helm 3 To Deploy To Kubernetes
```
kubectl create namespace j4kdemo                # For kubernetes Admins
oc new-project j4kdemo                          # For self-provision OpenShift users

cd kube/j4k-2020-vertx-demo
helm install j4kdemo . -set useRoute="true"     ## For OpenShift
helm install j4kdemo . -set useRoute="false"    ## For KiND/MiniKube
```