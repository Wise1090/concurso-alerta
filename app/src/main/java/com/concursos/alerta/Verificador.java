package com.concursos.alerta;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Varredura dos alertas cadastrados: acha editais novos e notifica. */
public final class Verificador {
    private Verificador() {}

    /**
     * @return quantidade de editais novos notificados.
     * A 1ª varredura de cada alerta só registra o que já existe (não notifica), para não
     * disparar dezenas de avisos de uma vez ao cadastrar.
     */
    public static synchronized int rodar(Context ctx) throws Exception {
        List<Store.Alerta> alertas = Store.alertas(ctx);
        if (alertas.isEmpty()) return 0;

        Fontes.Coleta col = Fontes.coletar(ctx, true);
        if (col.total > 0 && col.falhas.size() >= col.total) {
            throw new IOException("Nenhuma fonte respondeu (sem internet?)");
        }

        int notificados = 0;
        for (Store.Alerta a : alertas) {
            List<Edital> achados = Fontes.buscar(ctx, col, a.cargo, a.uf, a.similares);
            Set<String> vistos = Store.vistos(ctx, a.id);

            if (!a.baseline) {
                Store.marcarVistos(ctx, a.id, urls(achados));
                Store.definirBaseline(ctx, a.id);
                continue;
            }

            List<Edital> novos = new ArrayList<>();
            for (Edital e : achados) if (!vistos.contains(e.url)) novos.add(e);
            if (novos.isEmpty()) continue;

            if (Notificador.avisar(ctx, a, novos)) {
                Store.marcarVistos(ctx, a.id, urls(novos));
                notificados += novos.size();
            }
        }
        return notificados;
    }

    private static List<String> urls(List<Edital> l) {
        List<String> u = new ArrayList<>();
        for (Edital e : l) u.add(e.url);
        return u;
    }
}
