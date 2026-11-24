package com.concursos.alerta;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Guarda em disco a última coleta, para o aplicativo funcionar sem internet. */
public final class Cache {
    private Cache() {}

    private static final String ARQ = "coleta.json";

    public static class Guardado {
        public final List<Edital> itens;
        public final long quando;
        Guardado(List<Edital> i, long q) { itens = i; quando = q; }
    }

    public static synchronized void gravar(Context c, List<Edital> itens) {
        if (itens == null || itens.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            int n = 0;
            for (Edital e : itens) {
                arr.put(e.json());
                if (++n >= 1500) break;
            }
            JSONObject raiz = new JSONObject();
            raiz.put("quando", System.currentTimeMillis());
            raiz.put("itens", arr);
            File f = new File(c.getFilesDir(), ARQ);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(raiz.toString().getBytes("UTF-8"));
            }
        } catch (Exception ignored) { }
    }

    public static synchronized Guardado ler(Context c) {
        try {
            File f = new File(c.getFilesDir(), ARQ);
            if (!f.exists()) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream in = new java.io.FileInputStream(f)) {
                byte[] b = new byte[16384];
                int k;
                while ((k = in.read(b)) > 0) bos.write(b, 0, k);
            }
            JSONObject raiz = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            JSONArray arr = raiz.getJSONArray("itens");
            List<Edital> l = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) l.add(Edital.de(arr.getJSONObject(i)));
            return new Guardado(l, raiz.optLong("quando"));
        } catch (Exception e) {
            return null;
        }
    }

    /** "há 12 min", "há 3 h", "há 2 dias". */
    public static String idade(long quando) {
        long min = Math.max(0, (System.currentTimeMillis() - quando) / 60000L);
        if (min < 1) return "agora";
        if (min < 60) return "há " + min + " min";
        long h = min / 60;
        if (h < 24) return "há " + h + (h == 1 ? " hora" : " horas");
        long d = h / 24;
        return "há " + d + (d == 1 ? " dia" : " dias");
    }
}
