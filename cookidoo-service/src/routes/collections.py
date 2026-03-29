"""Collections route: returns all custom Cookidoo collections."""

import logging

from cookidoo_api import CookidooAuthException, CookidooRequestException
from fastapi import APIRouter, HTTPException, status

from ..cookidoo_session import cookidoo_session
from ..models import CollectionResponse, ChapterResponse, ChapterRecipeResponse

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get(
    "/collections",
    response_model=list[CollectionResponse],
    summary="Get all custom collections",
    description=(
        "Fetches all custom Cookidoo collections across all pages "
        "and returns them in a single response."
    ),
)
async def get_collections() -> list[CollectionResponse]:
    try:
        client = await cookidoo_session.get_client()
        _, total_pages = await client.count_custom_collections()

        all_collections: list[CollectionResponse] = []
        for page in range(total_pages):
            page_collections = await client.get_custom_collections(page=page)
            for col in page_collections:
                all_collections.append(
                    CollectionResponse(
                        id=col.id,
                        name=col.name,
                        description=col.description,
                        chapters=[
                            ChapterResponse(
                                name=ch.name,
                                recipes=[
                                    ChapterRecipeResponse(
                                        id=r.id,
                                        name=r.name,
                                        total_time=r.total_time,
                                    )
                                    for r in ch.recipes
                                ],
                            )
                            for ch in col.chapters
                        ],
                    )
                )

        return all_collections

    except CookidooAuthException as exc:
        logger.error("Authentication failure fetching collections: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Cookidoo authentication failed",
        ) from exc
    except CookidooRequestException as exc:
        logger.error("Request failure fetching collections: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Cookidoo API request failed",
        ) from exc
