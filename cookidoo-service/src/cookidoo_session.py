"""Manages the Cookidoo authenticated session lifecycle."""

import asyncio
import logging
from pathlib import Path

import aiohttp
from cookidoo_api import (
    Cookidoo,
    CookidooAuthException,
    CookidooConfig,
    CookidooLocalizationConfig,
)

from .config import settings

logger = logging.getLogger(__name__)


class CookidooSession:
    """Singleton-like session holder. Manages auth via session cookies."""

    def __init__(self) -> None:
        self._http_session: aiohttp.ClientSession | None = None
        self._cookidoo: Cookidoo | None = None
        self._authenticated = False
        self._lock = asyncio.Lock()

    async def _ensure_session(self) -> Cookidoo:
        """Return an authenticated Cookidoo client."""
        async with self._lock:
            if self._http_session is None or self._http_session.closed:
                await self._create_session()
            elif not self._authenticated:
                await self._login_and_save()
            assert self._cookidoo is not None
            return self._cookidoo

    async def _create_session(self) -> None:
        self._http_session = aiohttp.ClientSession(
            cookie_jar=aiohttp.CookieJar(unsafe=True)
        )
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
        self._authenticated = False

        cookies_path = Path(settings.cookies_file_path)
        if cookies_path.exists():
            try:
                self._cookidoo.load_cookies(str(cookies_path))
                self._authenticated = True
                logger.info("Restored Cookidoo session from %s", cookies_path)
            except Exception as exc:
                logger.warning(
                    "Failed to load cookies from %s: %s — performing fresh login",
                    cookies_path,
                    exc,
                )

        if not self._authenticated:
            await self._login_and_save()

    async def _login_and_save(self) -> None:
        assert self._cookidoo is not None
        logger.info("Logging into Cookidoo")
        try:
            await self._cookidoo.login()
            self._authenticated = True
            cookies_path = Path(settings.cookies_file_path)
            cookies_path.parent.mkdir(parents=True, exist_ok=True)
            self._cookidoo.save_cookies(str(cookies_path))
            logger.info("Saved Cookidoo cookies to %s", cookies_path)
        except CookidooAuthException as exc:
            logger.error("Cookidoo login failed: %s", exc)
            raise

    def invalidate(self) -> None:
        """Mark session as unauthenticated — next get_client() will re-login."""
        self._authenticated = False
        logger.info("Cookidoo session invalidated — will re-login on next request")

    async def get_client(self) -> Cookidoo:
        return await self._ensure_session()

    async def close(self) -> None:
        if self._http_session and not self._http_session.closed:
            await self._http_session.close()


# Application-level singleton
cookidoo_session = CookidooSession()
