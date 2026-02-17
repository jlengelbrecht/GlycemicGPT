"""Story 16.6: Tandem cloud upload service.

Handles authenticating with Tandem via tconnectsync's OIDC PKCE flow,
building the upload payload (matching the official app's JSON schema),
HMAC-SHA1 signing, and POSTing data to the Tandem cloud so the
endocrinologist's portal stays updated.

Protocol reference: _bmad-output/planning-artifacts/tandem-reverse-engineering.md
"""

import base64
import hashlib
import hmac
import json
import uuid
from datetime import UTC, datetime, timedelta

import httpx
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from src.config import settings
from src.core.encryption import decrypt_credential, encrypt_credential
from src.logging_config import get_logger
from src.models.integration import (
    IntegrationCredential,
    IntegrationType,
)
from src.models.pump_hardware_info import PumpHardwareInfo
from src.models.pump_raw_event import PumpRawEvent
from src.models.tandem_upload_state import TandemUploadState

logger = get_logger(__name__)


# HMAC-SHA1 signing key for the TDCToken header.
# This is a public protocol constant embedded in the official t:connect
# mobile app binary (com.tandemdiabetes.tconnect). It is NOT a user secret.
# The key is used as raw UTF-8 bytes (NOT base64-decoded).
_HMAC_KEY = b"1hvigLmZyCUBMQxn37SO7Iwn9EoTB1rBUBQg1CFyxcU="


# Upload limits
_MAX_EVENTS_PER_UPLOAD = 500
_UPLOAD_TIMEOUT_SECONDS = 60

# Endpoint config cache (TTL 24h)
_config_cache: dict[str, tuple[dict, datetime]] = {}
_CONFIG_TTL = timedelta(hours=24)


def sign_tdc_token(json_body_bytes: bytes, hmac_key: bytes | None = None) -> str:
    """Compute HMAC-SHA1 of the JSON body and return base64-encoded signature.

    This produces the value for the TDCToken header:
        TDCToken: token {return_value}
    """
    key = hmac_key or _HMAC_KEY
    signature = hmac.new(key, json_body_bytes, hashlib.sha1).digest()
    return base64.b64encode(signature).decode("ascii")


async def fetch_tandem_config(region: str = "US") -> dict:
    """Fetch dynamic endpoint configuration from Tandem's CDN.

    GET https://assets.tandemdiabetes.com/configuration/mobile-urls/{region}.json

    Caches the result for 24 hours.
    """
    now = datetime.now(UTC)
    cached = _config_cache.get(region)
    if cached and (now - cached[1]) < _CONFIG_TTL:
        return cached[0]

    config_base = settings.tandem_upload_config_base
    url = f"{config_base}/configuration/mobile-urls/{region}.json"
    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        config = resp.json()

    _config_cache[region] = (config, now)
    logger.info("Fetched Tandem endpoint config", region=region)
    return config


async def _authenticate_tandem(
    db: AsyncSession,
    user_id: uuid.UUID,
    state: TandemUploadState,
) -> dict:
    """Get a valid Tandem access token for the user.

    Strategy:
    1. If cached token in upload_state is not expired, use it
    2. If expired but refresh_token exists, try refresh
    3. Otherwise, authenticate fresh using stored Tandem credentials
       via tconnectsync's TandemSourceApi (OIDC PKCE flow)

    Returns a dict with 'access_token', 'pumper_id', and 'region'.
    The pumper_id is persisted in TandemUploadState so it survives
    across cached-token and refresh-token paths.

    Note: tconnectsync does not expose refresh tokens, so the refresh path
    (step 2) is currently unreachable. Kept for future compatibility.
    """
    now = datetime.now(UTC)

    # Helper: pumper_id from state (cached from a previous fresh auth)
    cached_pumper_id = state.tandem_pumper_id or ""

    # Look up region early so it's available for all code paths
    cred_result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == user_id,
            IntegrationCredential.integration_type == IntegrationType.TANDEM,
        )
    )
    credential = cred_result.scalar_one_or_none()
    region = getattr(credential, "region", "US") or "US" if credential else "US"

    # 1. Check cached token
    if (
        state.tandem_access_token
        and state.tandem_token_expires_at
        and state.tandem_token_expires_at > now + timedelta(minutes=5)
    ):
        return {
            "access_token": decrypt_credential(state.tandem_access_token),
            "pumper_id": cached_pumper_id,
            "region": region,
        }

    # 2. Try refresh token (currently unreachable -- see docstring)
    if state.tandem_refresh_token:
        try:
            token_data = await _refresh_tandem_token(
                decrypt_credential(state.tandem_refresh_token),
                region=region,
            )
            _cache_tokens(state, token_data)
            await db.commit()
            logger.info("Refreshed Tandem token", user_id=str(user_id))
            return {
                "access_token": token_data["access_token"],
                "pumper_id": cached_pumper_id,
                "region": region,
            }
        except Exception:
            logger.warning(
                "Tandem token refresh failed, will re-authenticate",
                user_id=str(user_id),
            )

    # 3. Fresh authentication using stored credentials
    if not credential:
        raise RuntimeError("Tandem integration not configured")

    username = decrypt_credential(credential.encrypted_username)
    password = decrypt_credential(credential.encrypted_password)

    token_data = await _authenticate_fresh(username, password, region)
    _cache_tokens(state, token_data)

    # Persist pumper_id so it's available on cached-token paths
    pumper_id = token_data.get("pumper_id", "")
    if pumper_id:
        state.tandem_pumper_id = pumper_id

    await db.commit()
    logger.info("Authenticated with Tandem via OIDC PKCE", user_id=str(user_id))
    return {
        "access_token": token_data["access_token"],
        "pumper_id": pumper_id,
        "region": region,
    }


async def _refresh_tandem_token(refresh_token: str, region: str = "US") -> dict:
    """Refresh the Tandem access token using the refresh_token grant.

    NOTE: Currently unreachable. tconnectsync does not expose refresh tokens,
    so _authenticate_fresh always returns refresh_token=None, and _cache_tokens
    never sets state.tandem_refresh_token. Kept for future use.
    """
    # Region-specific endpoints matching tconnectsync's TandemSourceApi.
    if region == "EU":
        token_endpoint = (
            "https://tdcservices.eu.tandemdiabetes.com/accounts/api/connect/token"
        )
        client_id = "1519e414-eeec-492e-8c5e-97bea4815a10"
    else:
        token_endpoint = (
            "https://tdcservices.tandemdiabetes.com/accounts/api/connect/token"
        )
        client_id = "0oa27ho9tpZE9Arjy4h7"

    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.post(
            token_endpoint,
            data={
                "grant_type": "refresh_token",
                "client_id": client_id,
                "refresh_token": refresh_token,
            },
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        resp.raise_for_status()
        return resp.json()


async def _authenticate_fresh(username: str, password: str, region: str) -> dict:
    """Authenticate with Tandem using stored credentials.

    Uses tconnectsync's TandemSourceApi which handles the OIDC PKCE flow
    (login + authorization code exchange via tdcservices.tandemdiabetes.com).

    Extracts accessToken (camelCase), accessTokenExpiresAt, and pumperId
    from the API instance after successful login.
    """
    import asyncio

    from tconnectsync.api.tandemsource import TandemSourceApi

    def _login():
        api = TandemSourceApi(email=username, password=password, region=region)

        # tconnectsync stores the token as self.accessToken (camelCase).
        # See: tconnectsync/api/tandemsource.py line 204
        access_token = getattr(api, "accessToken", None)
        if not access_token:
            raise RuntimeError(
                "Could not extract Tandem access token from TandemSourceApi. "
                "Expected attribute 'accessToken' not found."
            )

        # Compute real expires_in from accessTokenExpiresAt (arrow datetime)
        expires_in = 3600
        token_expiry = getattr(api, "accessTokenExpiresAt", None)
        if token_expiry is not None:
            try:
                import arrow as arrow_lib

                diff = (arrow_lib.get(token_expiry) - arrow_lib.get()).total_seconds()
                if diff > 0:
                    expires_in = int(diff)
            except Exception:
                logger.warning(
                    "Failed to parse accessTokenExpiresAt, using default 3600s"
                )

        # Extract pumperId for deviceAssignmentId in upload payloads
        pumper_id = getattr(api, "pumperId", None) or ""

        return {
            "access_token": access_token,
            "expires_in": expires_in,
            "refresh_token": None,  # tconnectsync does not expose refresh tokens
            "pumper_id": str(pumper_id),
        }

    return await asyncio.to_thread(_login)


def _cache_tokens(state: TandemUploadState, token_data: dict) -> None:
    """Cache the OAuth tokens in the upload state (encrypted)."""
    state.tandem_access_token = encrypt_credential(token_data["access_token"])
    expires_in = token_data.get("expires_in", 3600)
    state.tandem_token_expires_at = datetime.now(UTC) + timedelta(seconds=expires_in)

    refresh_token = token_data.get("refresh_token")
    if refresh_token:
        state.tandem_refresh_token = encrypt_credential(refresh_token)


async def get_last_event_uploaded(
    access_token: str,
    config: dict,
    serial_number: int,
    model_number: int,
) -> int:
    """Query Tandem's getLastEventUploaded endpoint for incremental sync.

    Returns the maxPumpEventIndex (events with index > this should be uploaded).
    """
    url = config.get("getLastEventUploadedUrl")
    if not url:
        logger.warning("No getLastEventUploadedUrl in config, starting from 0")
        return 0

    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.get(
            url,
            params={
                "serialNumber": str(serial_number),
                "modelNumber": str(model_number),
            },
            headers={"Authorization": f"Bearer {access_token}"},
        )
        if resp.status_code == 404:
            return 0
        resp.raise_for_status()
        data = resp.json()
        return data.get("maxPumpEventIndex", 0)


def build_upload_payload(
    pump_info: PumpHardwareInfo,
    raw_events: list[PumpRawEvent],
    settings_b64: str | None = None,
    device_assignment_id: str = "",
) -> dict:
    """Build the Tandem upload payload JSON matching the official app schema.

    Schema: UploadPayload > Package > Device > Data (misc, settings, events)
    """
    # Build misc object
    misc = {
        "platform": "GlycemicGPT Mobile [Android]",
        "pumpFeatures": pump_info.pump_features or {},
        "pumpAPIVersion": "",
        "appVersion": "2.9.1 (3368rb)",
        "uploaderClient": "mobile_tconnect",
        "pumpDateTime": datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%S"),
        "clientDateTimeWithOffset": datetime.now(UTC).strftime(
            "%Y-%m-%dT%H:%M:%S+00:00"
        ),
        "deviceAssignmentId": device_assignment_id,
    }

    # Build events list (each is the raw base64 bytes from the pump)
    events_list = [ev.raw_bytes_b64 for ev in raw_events] if raw_events else None

    # Build data object
    data = {"misc": misc}
    if settings_b64:
        data["settings"] = settings_b64
    if events_list:
        data["events"] = events_list

    # Build device object
    device = {
        "modelNum": pump_info.model_number,
        "serialNum": pump_info.serial_number,
        "partNum": pump_info.part_number,
        "pumpRev": pump_info.pump_rev,
        "armSwVer": pump_info.arm_sw_ver,
        "mspSwVer": pump_info.msp_sw_ver,
        "configABits": pump_info.config_a_bits,
        "configBBits": pump_info.config_b_bits,
        "pcbaSN": pump_info.pcba_sn,
        "pcbaRev": pump_info.pcba_rev,
        "data": data,
    }

    return {
        "client": "mHealth",
        "package": {"device": device},
    }


async def _post_upload(
    access_token: str,
    config: dict,
    payload: dict,
) -> dict:
    """POST the upload payload to Tandem's cloud with HMAC-SHA1 signing."""
    url = config.get("postUploadUrl")
    if not url:
        raise RuntimeError("No postUploadUrl in Tandem endpoint config")

    json_bytes = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    tdc_signature = sign_tdc_token(json_bytes)

    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}",
        "TDCToken": f"token {tdc_signature}",
    }

    async with httpx.AsyncClient(timeout=_UPLOAD_TIMEOUT_SECONDS) as client:
        resp = await client.post(url, content=json_bytes, headers=headers)
        resp.raise_for_status()
        return resp.json() if resp.content else {}


async def upload_to_tandem(
    db: AsyncSession,
    user_id: uuid.UUID,
) -> dict:
    """Main upload orchestrator.

    1. Get/create upload state
    2. Authenticate with Tandem
    3. Get pump hardware info
    4. Query getLastEventUploaded for incremental sync
    5. Fetch un-uploaded raw events from DB
    6. Build upload payload
    7. Sign and POST to Tandem cloud
    8. Mark events as uploaded, update state
    """
    now = datetime.now(UTC)

    # Get or create upload state
    state_result = await db.execute(
        select(TandemUploadState).where(TandemUploadState.user_id == user_id)
    )
    state = state_result.scalar_one_or_none()
    if not state:
        state = TandemUploadState(user_id=user_id, enabled=False)
        db.add(state)
        await db.flush()

    # Get pump hardware info
    hw_result = await db.execute(
        select(PumpHardwareInfo).where(PumpHardwareInfo.user_id == user_id)
    )
    pump_info = hw_result.scalar_one_or_none()
    if not pump_info:
        msg = "No pump hardware info available. Pair your pump first."
        state.last_upload_status = "error"
        state.last_error = msg
        await db.commit()
        return {"message": msg, "events_uploaded": 0, "status": "error"}

    # Authenticate
    try:
        auth_result = await _authenticate_tandem(db, user_id, state)
    except Exception as e:
        msg = f"Tandem authentication failed: {e!s}"
        state.last_upload_status = "error"
        state.last_error = msg
        await db.commit()
        logger.error("Tandem upload auth failed", user_id=str(user_id), error=str(e))
        return {"message": msg, "events_uploaded": 0, "status": "error"}

    access_token = auth_result["access_token"]
    pumper_id = auth_result.get("pumper_id", "")
    region = auth_result.get("region", "US")

    # Fetch endpoint config
    try:
        config = await fetch_tandem_config(region)
    except Exception as e:
        msg = f"Failed to fetch Tandem config: {e!s}"
        state.last_upload_status = "error"
        state.last_error = msg
        await db.commit()
        return {"message": msg, "events_uploaded": 0, "status": "error"}

    # Query last uploaded event index for incremental sync
    try:
        last_index = await get_last_event_uploaded(
            access_token, config, pump_info.serial_number, pump_info.model_number
        )
    except Exception:
        # Fall back to our local tracking
        last_index = state.max_event_index_uploaded
        logger.warning(
            "getLastEventUploaded failed, using local state",
            user_id=str(user_id),
            local_max=last_index,
        )

    # Use the higher of remote vs local to avoid re-uploading
    effective_min = max(last_index, state.max_event_index_uploaded)

    # Fetch un-uploaded raw events
    events_result = await db.execute(
        select(PumpRawEvent)
        .where(
            PumpRawEvent.user_id == user_id,
            PumpRawEvent.uploaded_to_tandem.is_(False),
            PumpRawEvent.sequence_number > effective_min,
        )
        .order_by(PumpRawEvent.sequence_number.asc())
        .limit(_MAX_EVENTS_PER_UPLOAD)
    )
    raw_events = list(events_result.scalars().all())

    if not raw_events:
        state.last_upload_at = now
        state.last_upload_status = "success"
        state.last_error = None
        await db.commit()
        return {
            "message": "No new events to upload",
            "events_uploaded": 0,
            "status": "success",
        }

    # Build and upload payload
    payload = build_upload_payload(
        pump_info, raw_events, device_assignment_id=pumper_id
    )

    try:
        await _post_upload(access_token, config, payload)
    except httpx.HTTPStatusError as e:
        msg = f"Tandem upload HTTP error: {e.response.status_code}"
        state.last_upload_status = "error"
        state.last_error = msg
        await db.commit()
        logger.error(
            "Tandem upload failed",
            user_id=str(user_id),
            status=e.response.status_code,
        )
        return {"message": msg, "events_uploaded": 0, "status": "error"}
    except Exception as e:
        msg = f"Tandem upload failed: {e!s}"
        state.last_upload_status = "error"
        state.last_error = msg
        await db.commit()
        logger.error("Tandem upload failed", user_id=str(user_id), error=str(e))
        return {"message": msg, "events_uploaded": 0, "status": "error"}

    # Mark events as uploaded
    event_ids = [ev.id for ev in raw_events]
    max_seq = max(ev.sequence_number for ev in raw_events)

    await db.execute(
        update(PumpRawEvent)
        .where(PumpRawEvent.id.in_(event_ids))
        .values(uploaded_to_tandem=True, uploaded_at=now)
    )

    # Update state
    state.last_upload_at = now
    state.last_upload_status = "success"
    state.last_error = None
    state.max_event_index_uploaded = max(state.max_event_index_uploaded, max_seq)
    await db.commit()

    logger.info(
        "Tandem upload complete",
        user_id=str(user_id),
        events_uploaded=len(raw_events),
        max_sequence=max_seq,
    )

    return {
        "message": f"Uploaded {len(raw_events)} events to Tandem cloud",
        "events_uploaded": len(raw_events),
        "status": "success",
    }
