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
 * Notificações "flutuantes" (heads-up) com o som padrão de notificação do celular.
 * Não definimos som próprio: o canal usa o toque de notificação que o usuário já escolheu no
 * aparelho (e ele pode trocar depois em Configurações > Apps > Concurso Alerta > Notificações).
 */
public final class Notificador {
    private Notificador() {}

    public static final String CANAL = "novos_editais";

    public static void criarCanal(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CANAL) != null) return;
        NotificationChannel ch = new NotificationChannel(CANAL, "Novos editais",
                NotificationManager.IMPORTANCE_HIGH); // HIGH = aparece como banner flutuante + som
        ch.setDescription("Avisos de novos editais para os cargos que você cadastrou");
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

    /** Envia até 5 notificações individuais e uma de resumo. Retorna false se não há permissão. */
    public static boolean avisar(Context c, Store.Alerta a, List<Edital> novos) {
        if (!temPermissao(c)) return false;
        criarCanal(c);
        int max = Math.min(5, novos.size());
        for (int i = 0; i < max; i++) enviar(c, a, novos.get(i));
        if (novos.size() > max) {
            Edital resumo = new Edital();
            resumo.titulo = "+" + (novos.size() - max) + " outros editais para \"" + a.cargo + "\"";
            resumo.url = "https://www.pciconcursos.com.br/concursos/";
            resumo.info = "Abra o app para ver todos";
            enviar(c, a, resumo);
        }
        return true;
    }

    public static void teste(Context c) {
        Edital e = new Edital();
        e.titulo = "Teste: novo edital para o seu cargo";
        e.info = "Se você viu o banner e ouviu o som, está tudo certo.";
        e.url = "https://www.pciconcursos.com.br/concursos/";
        Store.Alerta a = new Store.Alerta();
        a.cargo = "teste";
        enviar(c, a, e);
    }

    @SuppressWarnings("deprecation")
    private static void enviar(Context c, Store.Alerta a, Edital e) {
        Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(e.url));
        PendingIntent pi = PendingIntent.getActivity(c, e.url.hashCode(), it,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String titulo = "Novo edital: " + a.cargo;
        StringBuilder corpo = new StringBuilder(e.titulo);
        if (!e.uf.isEmpty() && !e.uf.equals("BR")) corpo.append(" (").append(e.uf).append(")");
        if (!e.info.isEmpty()) corpo.append("\n").append(e.info);
        if (!e.data.isEmpty()) corpo.append("\n").append(e.data);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CANAL)
                : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(e.titulo)
                .setStyle(new Notification.BigTextStyle().bigText(corpo.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(e.url.hashCode(), b.build());
    }
}
