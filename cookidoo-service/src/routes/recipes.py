"""Recipes route: returns full recipe details from Cookidoo."""

import logging

from cookidoo_api import CookidooAuthException, CookidooRequestException
from fastapi import APIRouter, HTTPException, status

from ..cookidoo_session import cookidoo_session
from ..models import (
    CategoryResponse,
    IngredientResponse,
    NutritionGroupResponse,
    NutritionResponse,
    RecipeDetailsResponse,
    RecipeNutritionResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get(
    "/recipes/{recipe_id}",
    response_model=RecipeDetailsResponse,
    summary="Get recipe details",
    description="Fetches complete recipe details from Cookidoo by recipe ID.",
)
async def get_recipe(recipe_id: str) -> RecipeDetailsResponse:
    try:
        client = await cookidoo_session.get_client()
        details = await client.get_recipe_details(recipe_id)

        return RecipeDetailsResponse(
            id=details.id,
            name=details.name,
            thumbnail=details.thumbnail,
            image=details.image,
            url=details.url,
            difficulty=details.difficulty,
            serving_size=details.serving_size,
            active_time=details.active_time,
            total_time=details.total_time,
            notes=details.notes,
            utensils=details.utensils,
            ingredients=[
                IngredientResponse(
                    id=ing.id,
                    name=ing.name,
                    description=ing.description,
                )
                for ing in details.ingredients
            ],
            categories=[
                CategoryResponse(
                    id=cat.id,
                    name=cat.name,
                    notes=cat.notes,
                )
                for cat in details.categories
            ],
            nutrition_groups=[
                NutritionGroupResponse(
                    name=ng.name,
                    recipe_nutritions=[
                        RecipeNutritionResponse(
                            quantity=rn.quantity,
                            unit_notation=rn.unit_notation,
                            nutritions=[
                                NutritionResponse(
                                    number=n.number,
                                    type=n.type,
                                    unit_type=n.unittype,
                                )
                                for n in rn.nutritions
                            ],
                        )
                        for rn in ng.recipe_nutritions
                    ],
                )
                for ng in details.nutrition_groups
            ],
        )

    except CookidooAuthException as exc:
        logger.error("Authentication failure fetching recipe %s: %s", recipe_id, exc)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Cookidoo authentication failed",
        ) from exc
    except CookidooRequestException as exc:
        logger.error("Request failure fetching recipe %s: %s", recipe_id, exc)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Cookidoo API request failed",
        ) from exc
