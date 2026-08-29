"""Collections route: reads and writes custom Cookidoo collections."""

import logging

from cookidoo_api import Cookidoo, CookidooAuthException, CookidooCollection, CookidooRequestException
from fastapi import APIRouter, HTTPException, status

from ..cookidoo_session import cookidoo_session
from ..models import (
    AddRecipesToCollectionRequest,
    ChapterRecipeResponse,
    ChapterResponse,
    CollectionResponse,
    CreateCollectionRequest,
)

logger = logging.getLogger(__name__)

router = APIRouter()


def _to_response(col: CookidooCollection) -> CollectionResponse:
    return CollectionResponse(
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


async def _find_collection(client: Cookidoo, collection_id: str) -> CollectionResponse:
    """Re-fetch a collection by id across pages.

    Used as a fallback when a write call's response cannot be parsed
    (see the known cookidoo_api==0.17.2 bug documented below), since the
    write already succeeded server-side in that case.
    """
    _, total_pages = await client.count_custom_collections()
    for page in range(total_pages):
        for col in await client.get_custom_collections(page=page):
            if col.id == collection_id:
                return _to_response(col)
    raise HTTPException(
        status_code=status.HTTP_404_NOT_FOUND,
        detail="Collection not found after write",
    )


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
                all_collections.append(_to_response(col))

        return all_collections

    except CookidooAuthException as exc:
        cookidoo_session.invalidate()
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


@router.post(
    "/collections",
    response_model=CollectionResponse,
    summary="Create a custom collection",
    description="Creates a new custom Cookidoo collection with the given name.",
)
async def create_collection(body: CreateCollectionRequest) -> CollectionResponse:
    try:
        client = await cookidoo_session.get_client()
        result = await client.add_custom_collection(body.name)
        return _to_response(result)

    except CookidooAuthException as exc:
        cookidoo_session.invalidate()
        logger.error("Authentication failure creating collection: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Cookidoo authentication failed",
        ) from exc
    except CookidooRequestException as exc:
        logger.error("Request failure creating collection: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Cookidoo API request failed",
        ) from exc


@router.post(
    "/collections/{collection_id}/recipes",
    response_model=CollectionResponse,
    summary="Add recipes to a custom collection",
    description="Adds one or more recipes to an existing custom Cookidoo collection.",
)
async def add_recipes_to_collection(
    collection_id: str, body: AddRecipesToCollectionRequest
) -> CollectionResponse:
    try:
        client = await cookidoo_session.get_client()
        result = await client.add_recipes_to_custom_collection(collection_id, body.recipe_ids)
        return _to_response(result)

    except CookidooAuthException as exc:
        cookidoo_session.invalidate()
        logger.error(
            "Authentication failure adding recipes to collection %s: %s", collection_id, exc
        )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Cookidoo authentication failed",
        ) from exc
    except CookidooRequestException as exc:
        logger.error("Request failure adding recipes to collection %s: %s", collection_id, exc)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Cookidoo API request failed",
        ) from exc


@router.delete(
    "/collections/{collection_id}/recipes/{recipe_id}",
    response_model=CollectionResponse,
    summary="Remove a recipe from a custom collection",
    description="Removes a single recipe from an existing custom Cookidoo collection.",
)
async def remove_recipe_from_collection(collection_id: str, recipe_id: str) -> CollectionResponse:
    try:
        client = await cookidoo_session.get_client()
        try:
            result = await client.remove_recipe_from_custom_collection(collection_id, recipe_id)
            return _to_response(result)
        except TypeError as exc:
            # Bug connu de cookidoo_api==0.17.2 : en retirant la dernière recette
            # d'un chapitre, Cookidoo renvoie "recipes": null au lieu de [] dans la
            # réponse de confirmation, ce que cookidoo_collection_from_json ne gère
            # pas. Le retrait a déjà réussi (l'erreur survient après
            # raise_for_status(), en parsant la réponse) : on recharge la collection
            # pour construire une réponse cohérente plutôt que de remonter une 500.
            if "NoneType" not in str(exc):
                raise
            return await _find_collection(client, collection_id)

    except CookidooAuthException as exc:
        cookidoo_session.invalidate()
        logger.error(
            "Authentication failure removing recipe %s from collection %s: %s",
            recipe_id,
            collection_id,
            exc,
        )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Cookidoo authentication failed",
        ) from exc
    except CookidooRequestException as exc:
        logger.error(
            "Request failure removing recipe %s from collection %s: %s",
            recipe_id,
            collection_id,
            exc,
        )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Cookidoo API request failed",
        ) from exc
