"""Pytest configuration and shared fixtures."""

import pytest
from fastapi.testclient import TestClient
from unittest.mock import AsyncMock, MagicMock, patch

from src.main import app
from src.cookidoo_session import cookidoo_session


@pytest.fixture
def client():
    """FastAPI test client with a mocked Cookidoo session."""
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def mock_cookidoo():
    """Returns a MagicMock simulating the Cookidoo API client."""
    mock = MagicMock()
    mock.count_custom_collections = AsyncMock()
    mock.get_custom_collections = AsyncMock()
    mock.get_recipe_details = AsyncMock()
    return mock


@pytest.fixture(autouse=True)
def patch_session(mock_cookidoo):
    """Patches cookidoo_session.get_client() for all tests."""
    with patch.object(cookidoo_session, "get_client", new=AsyncMock(return_value=mock_cookidoo)):
        yield mock_cookidoo
