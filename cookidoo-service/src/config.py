from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="COOKIDOO_", case_sensitive=False)

    email: str
    password: str
    country_code: str = "ch"
    language: str = "de-CH"


settings = Settings()
