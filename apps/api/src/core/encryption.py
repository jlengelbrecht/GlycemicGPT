"""Story 3.1: Credential encryption utilities.

Provides symmetric encryption for storing third-party API credentials securely.
Uses Fernet (AES-128-CBC with HMAC) from the cryptography library.
"""

import base64
import hashlib

from cryptography.fernet import Fernet, InvalidToken

from src.config import settings


def _get_encryption_key() -> bytes:
    """Derive a Fernet-compatible key from the application secret.

    Fernet requires a 32-byte URL-safe base64-encoded key.
    We derive this from the application's secret_key using SHA-256.

    Returns:
        A 32-byte base64-encoded key suitable for Fernet
    """
    # Use SHA-256 to derive a consistent 32-byte key from the secret
    key_bytes = hashlib.sha256(settings.secret_key.encode()).digest()
    # Fernet needs URL-safe base64 encoding
    return base64.urlsafe_b64encode(key_bytes)


def encrypt_credential(plaintext: str) -> str:
    """Encrypt a credential string.

    Args:
        plaintext: The credential value to encrypt (e.g., password)

    Returns:
        The encrypted value as a base64-encoded string
    """
    fernet = Fernet(_get_encryption_key())
    encrypted = fernet.encrypt(plaintext.encode("utf-8"))
    return encrypted.decode("utf-8")


def decrypt_credential(encrypted: str) -> str:
    """Decrypt an encrypted credential string.

    Args:
        encrypted: The encrypted credential as a base64-encoded string

    Returns:
        The decrypted plaintext value

    Raises:
        ValueError: If decryption fails (invalid key or corrupted data)
    """
    try:
        fernet = Fernet(_get_encryption_key())
        decrypted = fernet.decrypt(encrypted.encode("utf-8"))
        return decrypted.decode("utf-8")
    except InvalidToken as e:
        raise ValueError(
            "Failed to decrypt credential - invalid key or corrupted data"
        ) from e
