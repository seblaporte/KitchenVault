"""Tests for the /collections endpoint."""

from unittest.mock import AsyncMock

import pytest
from cookidoo_api import CookidooAuthException, CookidooRequestException
from cookidoo_api.types import (
    CookidooChapter,
    CookidooChapterRecipe,
    CookidooCollection,
)


def _make_collection(
    collection_id: str = "col-1",
    name: str = "Ma Collection",
    description: str | None = "Description",
    recipes: list | None = None,
) -> CookidooCollection:
    chapter_recipes = recipes or [
        CookidooChapterRecipe(id="r-1", name="Recette 1", total_time=1800),
        CookidooChapterRecipe(id="r-2", name="Recette 2", total_time=3600),
    ]
    return CookidooCollection(
        id=collection_id,
        name=name,
        description=description,
        chapters=[CookidooChapter(name="Chapitre 1", recipes=chapter_recipes)],
    )


def test_get_collections_success(client, patch_session):
    patch_session.count_custom_collections.return_value = (2, 1)
    patch_session.get_custom_collections.return_value = [
        _make_collection("col-1", "Collection A"),
        _make_collection("col-2", "Collection B", description=None, recipes=[]),
    ]

    response = client.get("/collections")

    assert response.status_code == 200
    data = response.json()
    assert len(data) == 2

    assert data[0]["id"] == "col-1"
    assert data[0]["name"] == "Collection A"
    assert len(data[0]["chapters"]) == 1
    assert len(data[0]["chapters"][0]["recipes"]) == 2

    assert data[1]["id"] == "col-2"
    assert data[1]["description"] is None
    assert data[1]["chapters"][0]["recipes"] == []


def test_get_collections_paginated(client, patch_session):
    patch_session.count_custom_collections.return_value = (2, 2)
    patch_session.get_custom_collections.side_effect = [
        [_make_collection("col-1")],
        [_make_collection("col-2")],
    ]

    response = client.get("/collections")

    assert response.status_code == 200
    assert len(response.json()) == 2
    assert patch_session.get_custom_collections.call_count == 2
    patch_session.get_custom_collections.assert_any_call(page=0)
    patch_session.get_custom_collections.assert_any_call(page=1)


def test_get_collections_empty(client, patch_session):
    patch_session.count_custom_collections.return_value = (0, 0)

    response = client.get("/collections")

    assert response.status_code == 200
    assert response.json() == []
    patch_session.get_custom_collections.assert_not_called()


def test_get_collections_auth_error(client, patch_session):
    patch_session.count_custom_collections.side_effect = CookidooAuthException("Unauthorized")

    response = client.get("/collections")

    assert response.status_code == 401


def test_get_collections_request_error(client, patch_session):
    patch_session.count_custom_collections.side_effect = CookidooRequestException("Timeout")

    response = client.get("/collections")

    assert response.status_code == 502
