package fr.seblaporte.kitchenvault.cookidoo;

import fr.seblaporte.kitchenvault.cookidoo.model.AddRecipesToCalendarRequest;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooCollection;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooRecipeDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import org.springframework.http.ResponseEntity;

import java.util.List;

@HttpExchange
public interface CookidooServiceClient {

    @GetExchange("/collections")
    List<CookidooCollection> getCollections();

    @GetExchange("/recipes/{id}")
    CookidooRecipeDetails getRecipeById(@PathVariable String id);

    @PostExchange("/calendar/{date}/recipes")
    ResponseEntity<Void> addRecipesToCalendar(@PathVariable String date, @RequestBody AddRecipesToCalendarRequest request);
}
