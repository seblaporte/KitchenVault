"""Calendar route: adds or replaces recipes on a Cookidoo calendar day."""

import logging
from datetime import date

from cookidoo_api import CookidooAuthException, CookidooRequestException
from fastapi import APIRouter, HTTPException, status

from ..cookidoo_session import cookidoo_session
from ..models import AddRecipesToCalendarRequest, CalendarDayRecipeResponse, CalendarDayResponse

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post(
    "/calendar/{day}/recipes",
    response_model=CalendarDayResponse,
    summary="Add recipes to a calendar day",
    description=(
        "Adds recipes to a Cookidoo calendar day. "
        "If replace=true, existing recipes for that day are removed first."
    ),
)
async def add_recipes_to_calendar(
    day: date, body: AddRecipesToCalendarRequest
) -> CalendarDayResponse:
    try:
        client = await cookidoo_session.get_client()

        if body.replace:
            calendar_week = await client.get_recipes_in_calendar_week(day)
            calendar_day = next(
                (d for d in calendar_week if d.id == day.isoformat()), None
            )
            if calendar_day is not None:
                for recipe in calendar_day.recipes:
                    await client.remove_recipe_from_calendar(day, recipe.id)

        result = await client.add_recipes_to_calendar(day, body.recipe_ids)

        return CalendarDayResponse(
            id=result.id,
            title=result.title,
            recipes=[
                CalendarDayRecipeResponse(
                    id=r.id,
                    name=r.name,
                    total_time=r.total_time,
                    thumbnail=r.thumbnail,
                    image=r.image,
                    url=r.url,
                )
                for r in result.recipes
            ],
        )

    except CookidooAuthException as exc:
        logger.error("Authentication failure adding recipes to calendar day %s: %s", day, exc)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Cookidoo authentication failed",
        ) from exc
    except CookidooRequestException as exc:
        logger.error("Request failure adding recipes to calendar day %s: %s", day, exc)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Cookidoo API request failed",
        ) from exc
