#!/bin/sh

set -e

mvn clean install

# setup PostgreSQL
db_svc=postgresql-db
cf services | grep $db_svc || cf create-service elephantsql turtle $db_svc

# deploy
cf push

cf delete-orphaned-routes
