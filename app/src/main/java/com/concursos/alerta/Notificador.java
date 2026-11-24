package com.concursos.alerta;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.List;

/**
 * Notificações destacadas, com o som de notificação que o usuário já escolheu no aparelho.
 * São dois canais separados, para que cada um possa ser ajustado de forma independente
 * nas configurações do sistema.
 */
public final class Notificador {
    private Notificador() {}

    public static final String CANAL_NOVOS = "novos_editais";
    public static final String CANAL_PRAZO = "prazos_encerrando";

    public static void criarCanais(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        criar(nm, CANAL_NOVOS, "Novos editais",
                "Notificações de novos editais para os cargos cadastrados");
        criar(nm, CANAL_PRAZO, "Prazos encerrando",
                "Avisos de inscrições prestes a encerrar");
    }

    private static void criar(NotificationManager nm, String id, String nome, String desc) {
        if (nm.getNotificationChannel(id) != null) return;
        NotificationChannel ch = new NotificationChannel(id, nome, NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(desc);
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        ch.enableVibration(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    public static boolean temPermissao(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // ------------------------------------------------------------- novos editais

    public static boolean avisarNovos(Context c, Store.Alerta a, List<Edital> novos) {
        if (!temPermissao(c)) return false;
        criarCanais(c);
        int max = Math.min(5, novos.size());
        for (int i = 0; i < max; i++) {
            Edital e = novos.get(i);
            enviar(c, CANAL_NOVOS, "Novo edital: " + a.cargo, corpo(e), e.url, e.url.hashCode());
        }
        if (novos.size() > max) {
            enviar(c, CANAL_NOVOS, "Novo edital: " + a.cargo,
                    "Mais " + (novos.size() - max) + " editais encontrados.\n"
                            + "Abra o aplicativo para consultar a lista completa.",
                    "https://www.pciconcursos.com.br/concursos/",
                    ("resumo" + a.id).hashCode());
        }
        return true;
    }

    // ------------------------------------------------------------- prazos

    public static boolean avisarPrazos(Context c, List<Edital> l) {
        if (!temPermissao(c) || l.isEmpty()) return false;
        criarCanais(c);
        int max = Math.min(5, l.size());
        for (int i = 0; i < max; i++) {
            Edital e = l.get(i);
            int d = e.diasRestantes();
            String quando = d <= 0 ? "Encerra hoje" : (d == 1 ? "Encerra amanhã" : "Encerra em " + d + " dias");
            enviar(c, CANAL_PRAZO, quando + ": inscrições", corpo(e), e.url, ("p" + e.url).hashCode());
        }
        return true;
    }

    private static String corpo(Edital e) {
        StringBuilder s = new StringBuilder(e.titulo);
        if (!e.uf.isEmpty() && !e.uf.equals("BR")) s.append(" (").append(e.uf).append(")");
        if (!e.info.isEmpty()) s.append("\n").append(e.info);
        if (!e.cargos.isEmpty()) s.append("\n").append(e.cargos);
        if (!e.data.isEmpty()) s.append("\nInscrições até ").append(e.data);
        return s.toString();
    }

    public static void teste(Context c) {
        criarCanais(c);
        enviar(c, CANAL_NOVOS, "Teste de notificação",
                "Se este aviso apareceu na tela e tocou o som, as notificações estão "
                        + "configuradas corretamente.",
                "https://www.pciconcursos.com.br/concursos/", 999001);
    }

    private static void enviar(Context c, String canal, String titulo, String corpo,
                               String url, int id) {
        Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        PendingIntent pi = PendingIntent.getActivity(c, id, it,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, canal)
                : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(corpo.replace('\n', ' '))
                .setStyle(new Notification.BigTextStyle().bigText(corpo))
                .setContentIntent(pi)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, b.build());
    }
}
