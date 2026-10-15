package dev.ronaldofojr.monitor.service;

import dev.ronaldofojr.monitor.service.Exceptions.BusinessRuleException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Aceita so http e https com host. Sem isso o monitor poderia ser usado para ler file:// ou
 * outros esquemas. (Bloquear enderecos internos, como o metadata da nuvem, e uma melhoria
 * listada no README.)
 */
public final class UrlValidator {

    private UrlValidator() {
    }

    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessRuleException("A URL e obrigatoria");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new BusinessRuleException("URL invalida: " + e.getReason());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BusinessRuleException("A URL deve comecar com http:// ou https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessRuleException("A URL precisa ter um host");
        }
    }
}
