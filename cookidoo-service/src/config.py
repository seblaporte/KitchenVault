from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="COOKIDOO_", case_sensitive=False)

    email: str
    password: str
    country_code: str = "fr"
    language: str = "fr-FR"


settings = Settings()
