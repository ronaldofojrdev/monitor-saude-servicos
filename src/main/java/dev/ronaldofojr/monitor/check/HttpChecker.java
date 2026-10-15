package dev.ronaldofojr.monitor.check;

/** Faz uma checagem HTTP. Interface para os testes poderem trocar a implementacao. */
public interface HttpChecker {

    /** Nunca lanca excecao de rede: qualquer problema vira um CheckOutcome com success = false. */
    CheckOutcome check(CheckSpec spec);
}
