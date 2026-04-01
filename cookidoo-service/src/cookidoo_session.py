"""Manages the Cookidoo authenticated session lifecycle."""

import asyncio
import logging
from datetime import datetime, timedelta, UTC

import aiohttp
from cookidoo_api import (
    Cookidoo,
    CookidooAuthException,
    CookidooConfig,
    CookidooLocalizationConfig,
    CookidooRequestException,
)

from .config import settings

logger = logging.getLogger(__name__)

_TOKEN_REFRESH_MARGIN_SECONDS = 60


class CookidooSession:
    """Singleton-like session holder. Manages auth token lifecycle."""

    def __init__(self) -> None:
        self._http_session: aiohttp.ClientSession | None = None
        self._cookidoo: Cookidoo | None = None
        self._token_expires_at: datetime | None = None
        self._lock = asyncio.Lock()

    async def _ensure_session(self) -> Cookidoo:
        """Return an authenticated Cookidoo client, refreshing if needed."""
        async with self._lock:
            if self._http_session is None or self._http_session.closed:
                self._http_session = aiohttp.ClientSession()
                self._cookidoo = Cookidoo(
                    self._http_session,
                    cfg=CookidooConfig(
                        localization=CookidooLocalizationConfig(
                            country_code=settings.country_code,
                            language=settings.language,
                            url=f"https://cookidoo.{settings.country_code}/foundation/{settings.language}",
                        ),
                        email=settings.email,
                        password=settings.password,
                    ),
                )
                self._token_expires_at = None

            assert self._cookidoo is not None

            if self._needs_refresh():
                await self._authenticate()

            return self._cookidoo

    def _needs_refresh(self) -> bool:
        if self._token_expires_at is None:
            return True
        margin = timedelta(seconds=_TOKEN_REFRESH_MARGIN_SECONDS)
        return datetime.now(UTC) >= self._token_expires_at - margin

    async def _authenticate(self) -> None:
        assert self._cookidoo is not None
        try:
            if self._token_expires_at is None:
                logger.info("Logging into Cookidoo")
                auth = await self._cookidoo.login()
            else:
                logger.info("Refreshing Cookidoo token")
                auth = await self._cookidoo.refresh_token()
            self._token_expires_at = datetime.now(UTC) + timedelta(seconds=auth.expires_in)
            logger.info("Cookidoo authentication successful, token valid for %ds", auth.expires_in)
        except CookidooAuthException as exc:
            logger.error("Cookidoo authentication failed: %s", exc)
            raise

    async def get_client(self) -> Cookidoo:
        return await self._ensure_session()

    async def close(self) -> None:
        if self._http_session and not self._http_session.closed:
            await self._http_session.close()


# Application-level singleton
cookidoo_session = CookidooSession()
