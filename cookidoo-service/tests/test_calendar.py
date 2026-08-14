"""Tests for the POST /calendar/{day}/recipes endpoint."""

import pytest
from cookidoo_api import CookidooAuthException, CookidooRequestException
from cookidoo_api.types import CookidooCalendarDay, CookidooCalendarDayRecipe


def _make_calendar_day(
    day_iso: str = "2024-01-15",
    recipes: list[CookidooCalendarDayRecipe] | None = None,
) -> CookidooCalendarDay:
    return CookidooCalendarDay(
        id=day_iso,
        title=f"Day {day_iso}",
        recipes=recipes or [],
    )


def _make_recipe(recipe_id: str = "recipe-1", name: str = "Tarte aux pommes") -> CookidooCalendarDayRecipe:
    return CookidooCalendarDayRecipe(
        id=recipe_id,
        name=name,
        total_time=3600,
        thumbnail="https://example.com/thumb.jpg",
        image="https://example.com/image.jpg",
        url=f"https://cookidoo.ch/recipes/{recipe_id}",
    )


def test_get_calendar_week(client, patch_session):
    """GET /calendar/week/{day} returns the week's planned recipes."""
    day1 = _make_calendar_day("2024-01-15", [_make_recipe("recipe-1")])
    day2 = _make_calendar_day("2024-01-16", [_make_recipe("recipe-2"), _make_recipe("recipe-3")])
    patch_session.get_recipes_in_calendar_week.return_value = [day1, day2]

    response = client.get("/calendar/week/2024-01-15")

    assert response.status_code == 200
    data = response.json()
    assert len(data) == 2
    assert data[0]["id"] == "2024-01-15"
    assert len(data[0]["recipes"]) == 1
    assert data[1]["id"] == "2024-01-16"
    assert len(data[1]["recipes"]) == 2
    patch_session.get_recipes_in_calendar_week.assert_called_once()


def test_get_calendar_week_empty(client, patch_session):
    """GET /calendar/week/{day} returns an empty list when nothing is planned."""
    patch_session.get_recipes_in_calendar_week.return_value = []

    response = client.get("/calendar/week/2024-01-15")

    assert response.status_code == 200
    assert response.json() == []


def test_get_calendar_week_auth_error(client, patch_session):
    """CookidooAuthException is mapped to HTTP 401."""
    patch_session.get_recipes_in_calendar_week.side_effect = CookidooAuthException("Unauthorized")

    response = client.get("/calendar/week/2024-01-15")

    assert response.status_code == 401


def test_get_calendar_week_request_error(client, patch_session):
    """CookidooRequestException is mapped to HTTP 502."""
    patch_session.get_recipes_in_calendar_week.side_effect = CookidooRequestException("Timeout")

    response = client.get("/calendar/week/2024-01-15")

    assert response.status_code == 502


def test_add_recipes_no_replace(client, patch_session):
    """POST with replace=false calls add_recipes_to_calendar directly and returns 200."""
    result_day = _make_calendar_day("2024-01-15", [_make_recipe("recipe-1")])
    patch_session.add_recipes_to_calendar.return_value = result_day

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["recipe-1"], "replace": False},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["id"] == "2024-01-15"
    assert data["title"] == "Day 2024-01-15"
    assert len(data["recipes"]) == 1
    assert data["recipes"][0]["id"] == "recipe-1"
    assert data["recipes"][0]["name"] == "Tarte aux pommes"
    assert data["recipes"][0]["total_time"] == 3600
    assert data["recipes"][0]["url"] == "https://cookidoo.ch/recipes/recipe-1"

    patch_session.add_recipes_to_calendar.assert_called_once()
    patch_session.get_recipes_in_calendar_week.assert_not_called()
    patch_session.remove_recipe_from_calendar.assert_not_called()


def test_add_recipes_default_no_replace(client, patch_session):
    """POST without replace field defaults to replace=false."""
    result_day = _make_calendar_day("2024-01-15", [_make_recipe("recipe-2")])
    patch_session.add_recipes_to_calendar.return_value = result_day

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["recipe-2"]},
    )

    assert response.status_code == 200
    patch_session.get_recipes_in_calendar_week.assert_not_called()
    patch_session.remove_recipe_from_calendar.assert_not_called()


def test_add_recipes_with_replace(client, patch_session):
    """POST with replace=true removes existing recipes then adds new ones."""
    existing_recipe_1 = _make_recipe("old-recipe-1", "Old Recipe 1")
    existing_recipe_2 = _make_recipe("old-recipe-2", "Old Recipe 2")
    existing_day = _make_calendar_day("2024-01-15", [existing_recipe_1, existing_recipe_2])

    result_day = _make_calendar_day("2024-01-15", [_make_recipe("new-recipe-1")])
    patch_session.get_recipes_in_calendar_week.return_value = [existing_day]
    patch_session.remove_recipe_from_calendar.return_value = _make_calendar_day("2024-01-15", [])
    patch_session.add_recipes_to_calendar.return_value = result_day

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["new-recipe-1"], "replace": True},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["recipes"][0]["id"] == "new-recipe-1"

    patch_session.get_recipes_in_calendar_week.assert_called_once()
    assert patch_session.remove_recipe_from_calendar.call_count == 2
    patch_session.add_recipes_to_calendar.assert_called_once()


def test_add_recipes_replace_day_not_in_calendar(client, patch_session):
    """POST with replace=true and day absent from calendar still adds recipes without error."""
    # Calendar week contains a different day, not the requested one
    other_day = _make_calendar_day("2024-01-14", [_make_recipe("other-recipe")])
    result_day = _make_calendar_day("2024-01-15", [_make_recipe("new-recipe-1")])

    patch_session.get_recipes_in_calendar_week.return_value = [other_day]
    patch_session.add_recipes_to_calendar.return_value = result_day

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["new-recipe-1"], "replace": True},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["recipes"][0]["id"] == "new-recipe-1"

    patch_session.get_recipes_in_calendar_week.assert_called_once()
    patch_session.remove_recipe_from_calendar.assert_not_called()
    patch_session.add_recipes_to_calendar.assert_called_once()


def test_add_recipes_replace_empty_calendar(client, patch_session):
    """POST with replace=true and empty calendar week still adds recipes without error."""
    result_day = _make_calendar_day("2024-01-15", [_make_recipe("recipe-1")])

    patch_session.get_recipes_in_calendar_week.return_value = []
    patch_session.add_recipes_to_calendar.return_value = result_day

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["recipe-1"], "replace": True},
    )

    assert response.status_code == 200
    patch_session.remove_recipe_from_calendar.assert_not_called()
    patch_session.add_recipes_to_calendar.assert_called_once()


def test_add_recipes_replace_removes_last_recipe_from_day(client, patch_session):
    """A TypeError from cookidoo_api's known 'recipes: null' parsing bug when
    removing the last recipe of a day is absorbed, and the sync still succeeds."""
    existing_recipe = _make_recipe("old-recipe-1", "Old Recipe 1")
    existing_day = _make_calendar_day("2024-01-15", [existing_recipe])
    result_day = _make_calendar_day("2024-01-15", [_make_recipe("new-recipe-1")])

    patch_session.get_recipes_in_calendar_week.return_value = [existing_day]
    patch_session.remove_recipe_from_calendar.side_effect = TypeError(
        "'NoneType' object is not subscriptable"
    )
    patch_session.add_recipes_to_calendar.return_value = result_day

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["new-recipe-1"], "replace": True},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["recipes"][0]["id"] == "new-recipe-1"
    patch_session.add_recipes_to_calendar.assert_called_once()


def test_add_recipes_replace_empty_recipe_ids_skips_add_call(client, patch_session):
    """replace=true with an empty recipe_ids list clears the day without
    calling add_recipes_to_calendar (which would hit the same parsing bug)."""
    existing_recipe = _make_recipe("old-recipe-1", "Old Recipe 1")
    existing_day = _make_calendar_day("2024-01-15", [existing_recipe])

    patch_session.get_recipes_in_calendar_week.return_value = [existing_day]
    patch_session.remove_recipe_from_calendar.side_effect = TypeError(
        "'NoneType' object is not subscriptable"
    )

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": [], "replace": True},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["id"] == "2024-01-15"
    assert data["recipes"] == []
    patch_session.remove_recipe_from_calendar.assert_called_once()
    patch_session.add_recipes_to_calendar.assert_not_called()


def test_add_recipes_replace_unrelated_type_error_propagates(client, patch_session):
    """A TypeError unrelated to the known parsing bug must still surface as an error."""
    existing_recipe = _make_recipe("old-recipe-1", "Old Recipe 1")
    existing_day = _make_calendar_day("2024-01-15", [existing_recipe])

    patch_session.get_recipes_in_calendar_week.return_value = [existing_day]
    patch_session.remove_recipe_from_calendar.side_effect = TypeError("unrelated error")

    with pytest.raises(TypeError, match="unrelated error"):
        client.post(
            "/calendar/2024-01-15/recipes",
            json={"recipe_ids": ["new-recipe-1"], "replace": True},
        )


def test_add_recipes_auth_error(client, patch_session):
    """CookidooAuthException is mapped to HTTP 401."""
    patch_session.add_recipes_to_calendar.side_effect = CookidooAuthException("Unauthorized")

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["recipe-1"]},
    )

    assert response.status_code == 401


def test_add_recipes_request_error(client, patch_session):
    """CookidooRequestException is mapped to HTTP 502."""
    patch_session.add_recipes_to_calendar.side_effect = CookidooRequestException("Timeout")

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["recipe-1"]},
    )

    assert response.status_code == 502


def test_add_recipes_auth_error_on_get_week(client, patch_session):
    """CookidooAuthException during get_recipes_in_calendar_week is mapped to HTTP 401."""
    patch_session.get_recipes_in_calendar_week.side_effect = CookidooAuthException("Unauthorized")

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["recipe-1"], "replace": True},
    )

    assert response.status_code == 401


def test_add_recipes_null_thumbnail_and_image(client, patch_session):
    """Recipes with null thumbnail and image are correctly serialized."""
    recipe = _make_recipe("recipe-1")
    recipe.thumbnail = None
    recipe.image = None
    result_day = _make_calendar_day("2024-01-15", [recipe])
    patch_session.add_recipes_to_calendar.return_value = result_day

    response = client.post(
        "/calendar/2024-01-15/recipes",
        json={"recipe_ids": ["recipe-1"]},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["recipes"][0]["thumbnail"] is None
    assert data["recipes"][0]["image"] is None
