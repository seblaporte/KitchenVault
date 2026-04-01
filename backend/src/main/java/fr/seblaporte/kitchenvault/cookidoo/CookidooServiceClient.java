package fr.seblaporte.kitchenvault.cookidoo;

import fr.seblaporte.kitchenvault.cookidoo.model.CookidooCollection;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooRecipeDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange
public interface CookidooServiceClient {

    @GetExchange("/collections")
    List<CookidooCollection> getCollections();

    @GetExchange("/recipes/{id}")
    CookidooRecipeDetails getRecipeById(@PathVariable String id);
}
