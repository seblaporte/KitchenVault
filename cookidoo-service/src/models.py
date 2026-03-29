"""Pydantic response models for the FastAPI service."""

from pydantic import BaseModel


class ChapterRecipeResponse(BaseModel):
    id: str
    name: str
    total_time: int


class ChapterResponse(BaseModel):
    name: str
    recipes: list[ChapterRecipeResponse]


class CollectionResponse(BaseModel):
    id: str
    name: str
    description: str | None
    chapters: list[ChapterResponse]


class IngredientResponse(BaseModel):
    id: str
    name: str
    description: str


class CategoryResponse(BaseModel):
    id: str
    name: str
    notes: str


class NutritionResponse(BaseModel):
    number: float
    type: str
    unit_type: str


class RecipeNutritionResponse(BaseModel):
    nutritions: list[NutritionResponse]
    quantity: int
    unit_notation: str


class NutritionGroupResponse(BaseModel):
    name: str
    recipe_nutritions: list[RecipeNutritionResponse]


class RecipeDetailsResponse(BaseModel):
    id: str
    name: str
    thumbnail: str | None
    image: str | None
    url: str
    difficulty: str
    serving_size: int
    active_time: int
    total_time: int
    notes: list[str]
    utensils: list[str]
    ingredients: list[IngredientResponse]
    categories: list[CategoryResponse]
    nutrition_groups: list[NutritionGroupResponse]
