#!/bin/bash
set -e

# -----------------------------------------------------------------------------
# Heroku Postgres publishes its credentials as DATABASE_URL, in libpq form:
#
#   postgres://user:password@host:5432/database
#
# Spring Boot needs a JDBC URL and separate credentials, and the Java buildpack
# that would normally provide JDBC_DATABASE_URL is not involved in a container
# deploy. So translate it here, at startup, rather than pinning credentials into
# config vars that go stale whenever Heroku rotates them.
#
# When JDBC_URL is already set - the docker-compose path - this is skipped.
# -----------------------------------------------------------------------------

# The user and password inside a connection URL are percent-encoded, so a
# password containing '@' arrives as '%40'. Passing that straight through would
# authenticate with the wrong string.
urldecode() {
    local encoded=${1//+/ }
    printf '%b' "${encoded//%/\\x}"
}

if [ -n "$DATABASE_URL" ] && [ -z "$JDBC_URL" ]; then
    without_scheme=${DATABASE_URL#*://}
    credentials=${without_scheme%%@*}
    host_and_db=${without_scheme#*@}

    DB_USER=$(urldecode "${credentials%%:*}")
    DB_PASSWORD=$(urldecode "${credentials#*:}")
    # Heroku Postgres refuses non-TLS connections.
    JDBC_URL="jdbc:postgresql://${host_and_db}?sslmode=require"

    export DB_USER DB_PASSWORD JDBC_URL
fi

# Heroku assigns the port at runtime; application-prod.properties already reads
# ${PORT} with 8084 as the local fallback.

exec java -XX:MaxRAMPercentage=75 -jar /app/app.jar "$@"
