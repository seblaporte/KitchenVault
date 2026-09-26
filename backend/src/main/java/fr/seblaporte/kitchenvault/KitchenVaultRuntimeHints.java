package fr.seblaporte.kitchenvault;

import fr.seblaporte.kitchenvault.config.AiProperties;
import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class KitchenVaultRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Changelogs Liquibase — les fichiers YAML ne sont pas détectés automatiquement
        hints.resources().registerPattern("db/changelog/*");
        hints.resources().registerPattern("db/changelog/**/*");

        // Proxy JDK pour le client HTTP déclaratif (@HttpExchange)
        hints.proxies().registerJdkProxy(CookidooServiceClient.class);

        // Hibernate Validator (JPATraversableResolver) lit les champs contraints par réflexion
        // brute pour vérifier leur "reachability", en dehors du binding @ConfigurationProperties
        // que Spring enregistre déjà automatiquement pour la compilation native.
        hints.reflection().registerType(AiProperties.WeeklyPlanProperties.class, MemberCategory.DECLARED_FIELDS);
    }
}
