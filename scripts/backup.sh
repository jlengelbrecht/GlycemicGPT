#!/bin/bash
# Story 1.5: Database Backup Script
# Performs PostgreSQL backup with configurable retention policy
#
# Environment Variables:
#   BACKUP_PATH       - Directory to store backups (default: /backups)
#   RETENTION_DAYS    - Number of days to keep backups (default: 7)
#   PGHOST            - PostgreSQL host (default: glycemicgpt-db)
#   PGPORT            - PostgreSQL port (default: 5432)
#   PGUSER            - PostgreSQL user (default: glycemicgpt)
#   PGPASSWORD        - PostgreSQL password (required)
#   PGDATABASE        - PostgreSQL database (default: glycemicgpt)

set -euo pipefail

# Configuration with defaults
BACKUP_PATH="${BACKUP_PATH:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
PGHOST="${PGHOST:-glycemicgpt-db}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-glycemicgpt}"
PGDATABASE="${PGDATABASE:-glycemicgpt}"

# Timestamp for backup filename
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_PATH}/glycemicgpt_${TIMESTAMP}.sql.gz"

# Logging function with JSON output
log() {
    local level="$1"
    local message="$2"
    local timestamp
    timestamp=$(date -Iseconds)
    echo "{\"timestamp\":\"${timestamp}\",\"level\":\"${level}\",\"service\":\"glycemicgpt-backup\",\"message\":\"${message}\"}"
}

# Error handling
error_exit() {
    log "ERROR" "$1"
    exit 1
}

# Check required environment variable
if [ -z "${PGPASSWORD:-}" ]; then
    error_exit "PGPASSWORD environment variable is required"
fi

# Create backup directory if it doesn't exist
mkdir -p "${BACKUP_PATH}" || error_exit "Failed to create backup directory: ${BACKUP_PATH}"

log "INFO" "Starting database backup"
log "INFO" "Backup path: ${BACKUP_PATH}"
log "INFO" "Retention: ${RETENTION_DAYS} days"

# Perform backup
log "INFO" "Running pg_dump for database: ${PGDATABASE}"
pg_dump \
    -h "${PGHOST}" \
    -p "${PGPORT}" \
    -U "${PGUSER}" \
    -d "${PGDATABASE}" \
    --format=plain \
    --no-owner \
    --no-privileges \
    2>/dev/null | gzip > "${BACKUP_FILE}" || error_exit "pg_dump failed"

# Verify backup was created
if [ ! -f "${BACKUP_FILE}" ]; then
    error_exit "Backup file was not created: ${BACKUP_FILE}"
fi

BACKUP_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
log "INFO" "Backup completed: ${BACKUP_FILE} (${BACKUP_SIZE})"

# Clean up old backups based on retention policy
log "INFO" "Cleaning up backups older than ${RETENTION_DAYS} days"
DELETED_COUNT=0

# Find and delete old backups
while IFS= read -r old_backup; do
    if [ -n "${old_backup}" ]; then
        rm -f "${old_backup}"
        log "INFO" "Deleted old backup: ${old_backup}"
        ((DELETED_COUNT++)) || true
    fi
done < <(find "${BACKUP_PATH}" -name "glycemicgpt_*.sql.gz" -type f -mtime "+${RETENTION_DAYS}" 2>/dev/null)

log "INFO" "Cleanup complete. Deleted ${DELETED_COUNT} old backup(s)"

# List current backups
BACKUP_COUNT=$(find "${BACKUP_PATH}" -name "glycemicgpt_*.sql.gz" -type f | wc -l)
log "INFO" "Current backup count: ${BACKUP_COUNT}"

log "INFO" "Backup process completed successfully"
