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
    chapter_recipes = recipes if recipes is not None else [
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


def test_create_collection_success(client, patch_session):
    patch_session.add_custom_collection.return_value = _make_collection(
        "col-new", "Nouvelle collection", description=None, recipes=[]
    )

    response = client.post("/collections", json={"name": "Nouvelle collection"})

    assert response.status_code == 200
    data = response.json()
    assert data["id"] == "col-new"
    assert data["name"] == "Nouvelle collection"
    patch_session.add_custom_collection.assert_called_once_with("Nouvelle collection")


def test_create_collection_auth_error(client, patch_session):
    patch_session.add_custom_collection.side_effect = CookidooAuthException("Unauthorized")

    response = client.post("/collections", json={"name": "Nouvelle collection"})

    assert response.status_code == 401


def test_create_collection_request_error(client, patch_session):
    patch_session.add_custom_collection.side_effect = CookidooRequestException("Timeout")

    response = client.post("/collections", json={"name": "Nouvelle collection"})

    assert response.status_code == 502


def test_add_recipes_to_collection_success(client, patch_session):
    patch_session.add_recipes_to_custom_collection.return_value = _make_collection("col-1")

    response = client.post("/collections/col-1/recipes", json={"recipe_ids": ["r-1", "r-2"]})

    assert response.status_code == 200
    data = response.json()
    assert data["id"] == "col-1"
    patch_session.add_recipes_to_custom_collection.assert_called_once_with(
        "col-1", ["r-1", "r-2"]
    )


def test_add_recipes_to_collection_auth_error(client, patch_session):
    patch_session.add_recipes_to_custom_collection.side_effect = CookidooAuthException(
        "Unauthorized"
    )

    response = client.post("/collections/col-1/recipes", json={"recipe_ids": ["r-1"]})

    assert response.status_code == 401


def test_add_recipes_to_collection_request_error(client, patch_session):
    patch_session.add_recipes_to_custom_collection.side_effect = CookidooRequestException(
        "Timeout"
    )

    response = client.post("/collections/col-1/recipes", json={"recipe_ids": ["r-1"]})

    assert response.status_code == 502


def test_remove_recipe_from_collection_success(client, patch_session):
    patch_session.remove_recipe_from_custom_collection.return_value = _make_collection(
        "col-1", recipes=[CookidooChapterRecipe(id="r-2", name="Recette 2", total_time=3600)]
    )

    response = client.delete("/collections/col-1/recipes/r-1")

    assert response.status_code == 200
    data = response.json()
    assert data["chapters"][0]["recipes"] == [
        {"id": "r-2", "name": "Recette 2", "total_time": 3600}
    ]
    patch_session.remove_recipe_from_custom_collection.assert_called_once_with("col-1", "r-1")


def test_remove_recipe_from_collection_null_recipes_bug_falls_back_to_refetch(client, patch_session):
    """A TypeError from cookidoo_api's known 'recipes: null' parsing bug when
    removing the last recipe of a chapter is absorbed, and the collection is
    re-fetched to build a consistent response."""
    patch_session.remove_recipe_from_custom_collection.side_effect = TypeError(
        "'NoneType' object is not iterable"
    )
    patch_session.count_custom_collections.return_value = (1, 1)
    patch_session.get_custom_collections.return_value = [
        _make_collection("col-1", recipes=[])
    ]

    response = client.delete("/collections/col-1/recipes/r-1")

    assert response.status_code == 200
    data = response.json()
    assert data["id"] == "col-1"
    assert data["chapters"][0]["recipes"] == []
    patch_session.get_custom_collections.assert_called_once_with(page=0)


def test_remove_recipe_from_collection_unrelated_type_error_propagates(client, patch_session):
    """A TypeError unrelated to the known parsing bug must still surface as an error."""
    patch_session.remove_recipe_from_custom_collection.side_effect = TypeError("unrelated error")

    with pytest.raises(TypeError, match="unrelated error"):
        client.delete("/collections/col-1/recipes/r-1")


def test_remove_recipe_from_collection_auth_error(client, patch_session):
    patch_session.remove_recipe_from_custom_collection.side_effect = CookidooAuthException(
        "Unauthorized"
    )

    response = client.delete("/collections/col-1/recipes/r-1")

    assert response.status_code == 401


def test_remove_recipe_from_collection_request_error(client, patch_session):
    patch_session.remove_recipe_from_custom_collection.side_effect = CookidooRequestException(
        "Timeout"
    )

    response = client.delete("/collections/col-1/recipes/r-1")

    assert response.status_code == 502
