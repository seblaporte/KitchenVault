"""Tests for the /recipes/{id} endpoint."""

from cookidoo_api import CookidooAuthException, CookidooRequestException
from cookidoo_api.types import (
    CookidooCategory,
    CookidooIngredient,
    CookidooNutrition,
    CookidooNutritionGroup,
    CookidooRecipeNutrition,
    CookidooShoppingRecipeDetails,
)


def _make_recipe_details(recipe_id: str = "recipe-1") -> CookidooShoppingRecipeDetails:
    return CookidooShoppingRecipeDetails(
        id=recipe_id,
        name="Tarte aux pommes",
        ingredients=[
            CookidooIngredient(id="ing-1", name="Pommes", description="3 pièces"),
            CookidooIngredient(id="ing-2", name="Farine", description="200 g"),
        ],
        thumbnail="https://example.com/thumb.jpg",
        image="https://example.com/image.jpg",
        url="https://cookidoo.ch/recipes/recipe/fr-CH/r12345",
        difficulty="easy",
        notes=["Conseil : utiliser des pommes acides"],
        categories=[CookidooCategory(id="cat-1", name="Desserts", notes="")],
        collections=[],
        utensils=["Fouet"],
        serving_size=4,
        active_time=900,
        total_time=3600,
        nutrition_groups=[
            CookidooNutritionGroup(
                name="Par portion",
                recipe_nutritions=[
                    CookidooRecipeNutrition(
                        nutritions=[
                            CookidooNutrition(number=250.0, type="kcal", unittype="kcal"),
                            CookidooNutrition(number=10.0, type="fat", unittype="g"),
                        ],
                        quantity=1,
                        unit_notation="portion",
                    )
                ],
            )
        ],
    )


def test_get_recipe_success(client, patch_session):
    patch_session.get_recipe_details.return_value = _make_recipe_details("recipe-1")

    response = client.get("/recipes/recipe-1")

    assert response.status_code == 200
    data = response.json()
    assert data["id"] == "recipe-1"
    assert data["name"] == "Tarte aux pommes"
    assert data["difficulty"] == "easy"
    assert data["serving_size"] == 4
    assert data["active_time"] == 900
    assert data["total_time"] == 3600
    assert len(data["ingredients"]) == 2
    assert data["ingredients"][0]["name"] == "Pommes"
    assert len(data["categories"]) == 1
    assert data["categories"][0]["name"] == "Desserts"
    assert len(data["nutrition_groups"]) == 1
    assert data["nutrition_groups"][0]["recipe_nutritions"][0]["nutritions"][0]["type"] == "kcal"


def test_get_recipe_notes_and_utensils(client, patch_session):
    patch_session.get_recipe_details.return_value = _make_recipe_details()

    response = client.get("/recipes/recipe-1")

    data = response.json()
    assert data["notes"] == ["Conseil : utiliser des pommes acides"]
    assert data["utensils"] == ["Fouet"]


def test_get_recipe_null_images(client, patch_session):
    recipe = _make_recipe_details()
    recipe.thumbnail = None
    recipe.image = None
    patch_session.get_recipe_details.return_value = recipe

    response = client.get("/recipes/recipe-1")

    data = response.json()
    assert data["thumbnail"] is None
    assert data["image"] is None


def test_get_recipe_auth_error(client, patch_session):
    patch_session.get_recipe_details.side_effect = CookidooAuthException("Unauthorized")

    response = client.get("/recipes/recipe-1")

    assert response.status_code == 401


def test_get_recipe_request_error(client, patch_session):
    patch_session.get_recipe_details.side_effect = CookidooRequestException("Timeout")

    response = client.get("/recipes/recipe-1")

    assert response.status_code == 502


def test_get_recipe_calls_correct_id(client, patch_session):
    patch_session.get_recipe_details.return_value = _make_recipe_details("my-special-id")

    client.get("/recipes/my-special-id")

    patch_session.get_recipe_details.assert_called_once_with("my-special-id")
