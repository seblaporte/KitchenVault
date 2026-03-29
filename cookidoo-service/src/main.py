"""FastAPI application entry point for cookidoo-service."""

import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from .cookidoo_session import cookidoo_session
from .routes import collections, recipes

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    logger.info("Starting cookidoo-service — performing initial authentication")
    try:
        await cookidoo_session.get_client()
        logger.info("Initial authentication succeeded")
    except Exception as exc:
        logger.warning("Initial authentication failed: %s — will retry on first request", exc)
    yield
    logger.info("Shutting down cookidoo-service")
    await cookidoo_session.close()


app = FastAPI(
    title="cookidoo-service",
    description="FastAPI wrapper around cookidoo-api for my-cookidoo",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(collections.router)
app.include_router(recipes.router)


@app.get("/health", include_in_schema=False)
async def health() -> JSONResponse:
    return JSONResponse({"status": "ok"})
