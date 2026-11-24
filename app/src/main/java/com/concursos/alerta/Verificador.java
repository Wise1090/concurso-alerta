package com.concursos.alerta;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Varredura em segundo plano: novos editais para os alertas e prazos prestes a encerrar. */
public final class Verificador {
    private Verificador() {}

    /** Avisa quando faltarem este número de dias ou menos para o fim das inscrições. */
    public static final int DIAS_AVISO_PRAZO = 3;

    public static class Saldo {
        public int novos;
        public int prazos;
        public int total() { return novos + prazos; }
    }

    public static synchronized Saldo rodar(Context ctx) throws Exception {
        Saldo s = new Saldo();

        // 1) prazos acompanhados: não dependem de rede
        List<Edital> prazos = Store.prazosParaAvisar(ctx, DIAS_AVISO_PRAZO);
        if (!prazos.isEmpty() && Notificador.avisarPrazos(ctx, prazos)) {
            List<String> urls = new ArrayList<>();
            for (Edital e : prazos) urls.add(e.url);
            Store.marcarPrazoAvisado(ctx, urls);
            s.prazos = prazos.size();
        }

        // 2) novos editais para cada alerta
        List<Store.Alerta> alertas = Store.alertas(ctx);
        if (alertas.isEmpty()) return s;

        Fontes.Coleta col = Fontes.coletar(ctx, true);
        if (col.total > 0 && col.falhas.size() >= col.total && !col.offline) {
            throw new IOException("Nenhuma fonte respondeu. Verifique a conexão.");
        }
        if (col.offline) return s; // sem dados novos: não há o que comparar

        for (Store.Alerta a : alertas) {
            List<Edital> achados = Fontes.buscar(ctx, col, a.cargo, a.uf, a.similares).achados;
            Set<String> vistos = Store.vistos(ctx, a.id);

            if (!a.baseline) {
                Store.marcarVistos(ctx, a.id, urls(achados));
                Store.definirBaseline(ctx, a.id);
                continue;
            }

            List<Edital> novos = new ArrayList<>();
            for (Edital e : achados) if (!vistos.contains(e.url)) novos.add(e);
            if (novos.isEmpty()) continue;

            if (Notificador.avisarNovos(ctx, a, novos)) {
                for (Edital e : novos) Store.acompanhar(ctx, e);
                Store.marcarVistos(ctx, a.id, urls(novos));
                s.novos += novos.size();
            }
        }
        return s;
    }

    private static List<String> urls(List<Edital> l) {
        List<String> u = new ArrayList<>();
        for (Edital e : l) u.add(e.url);
        return u;
    }
}
