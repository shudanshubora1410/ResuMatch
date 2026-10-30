#!/bin/sh
# =====================================================================
# ResuMatch container entrypoint
#
# Builds a JVM truststore that trusts Aiven's CA certificate, then starts
# the application.
#
# Why: Aiven requires TLS for MySQL connections and issues the server
# certificate from a per-project CA that is NOT in the JVM's default
# cacerts. Without trusting it, the connection fails with:
#   "self-signed certificate in certificate chain" / PKIX path building failed
#
# The truststore is built at container start (takes ~1 second) and written to
# a temp path, so no build artefact has to be committed.
# =====================================================================
set -e

CA_FILE="${AIVEN_CA_FILE:-/app/aiven-project-ca.pem}"
TRUSTSTORE="${AIVEN_TRUSTSTORE:-/tmp/aiven-truststore.jks}"
TRUSTSTORE_PASS="${AIVEN_TRUSTSTORE_PASS:-changeit}"

if [ -f "$CA_FILE" ]; then
  echo "[entrypoint] Building truststore from $CA_FILE"
  rm -f "$TRUSTSTORE"
  keytool -importcert -noprompt \
    -alias aiven-project-ca \
    -file "$CA_FILE" \
    -keystore "$TRUSTSTORE" \
    -storepass "$TRUSTSTORE_PASS" >/dev/null 2>&1 \
    && echo "[entrypoint] Truststore ready at $TRUSTSTORE" \
    || echo "[entrypoint] WARNING: could not build truststore, TLS may fail"
else
  echo "[entrypoint] NOTE: $CA_FILE not found. If the MySQL host requires TLS,"
  echo "[entrypoint] mount the CA certificate via Render Secret Files and set"
  echo "[entrypoint] AIVEN_CA_FILE to its path."
fi

# Only add the truststore to the JDBC URL if it actually exists.
if [ -f "$TRUSTSTORE" ]; then
  export DB_URL="${DB_URL:-jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=true&requireSSL=true&verifyServerCertificate=true&trustCertificateKeyStoreUrl=file://${TRUSTSTORE}&trustCertificateKeyStorePassword=${TRUSTSTORE_PASS}&trustCertificateKeyStoreType=JKS&rewriteBatchedStatements=true&serverTimezone=UTC&allowPublicKeyRetrieval=true}"
  echo "[entrypoint] Using TLS-trusting JDBC URL"
fi

exec java -jar app.jar \
  --server.port="${PORT:-8080}" \
  --server.address=0.0.0.0
