package com.concursos.alerta;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Armazenamento local (SharedPreferences): alertas cadastrados, itens já vistos, fontes extras. */
public final class Store {
    private Store() {}

    public static class Alerta {
        public String id = "";
        public String cargo = "";
        public String uf = "";          // "" = todo o Brasil
        public boolean similares = true;
        public boolean baseline = false; // true depois da 1ª varredura (que não notifica)

        public String rotulo() {
            return cargo + " — " + (uf.isEmpty() ? "Todo o Brasil" : uf)
                    + (similares ? " (com similares)" : "");
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("concurso_alerta", Context.MODE_PRIVATE);
    }

    public static synchronized List<Alerta> alertas(Context c) {
        List<Alerta> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("alertas", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Alerta a = new Alerta();
                a.id = o.optString("id");
                a.cargo = o.optString("cargo");
                a.uf = o.optString("uf");
                a.similares = o.optBoolean("similares", true);
                a.baseline = o.optBoolean("baseline", false);
                out.add(a);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void salvar(Context c, List<Alerta> l) {
        JSONArray arr = new JSONArray();
        try {
            for (Alerta a : l) {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("cargo", a.cargo);
                o.put("uf", a.uf);
                o.put("similares", a.similares);
                o.put("baseline", a.baseline);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        sp(c).edit().putString("alertas", arr.toString()).apply();
    }

    public static synchronized Alerta adicionar(Context c, String cargo, String uf, boolean similares) {
        List<Alerta> l = alertas(c);
        for (Alerta a : l) {
            if (Util.norm(a.cargo).equals(Util.norm(cargo)) && a.uf.equals(uf)) {
                a.similares = similares;
                salvar(c, l);
                return a;
            }
        }
        Alerta a = new Alerta();
        a.id = UUID.randomUUID().toString().substring(0, 8);
        a.cargo = cargo.trim();
        a.uf = uf;
        a.similares = similares;
        l.add(a);
        salvar(c, l);
        return a;
    }

    public static synchronized void remover(Context c, String id) {
        List<Alerta> l = alertas(c);
        List<Alerta> n = new ArrayList<>();
        for (Alerta a : l) if (!a.id.equals(id)) n.add(a);
        salvar(c, n);
        sp(c).edit().remove("v_" + id).apply();
    }

    public static synchronized void definirBaseline(Context c, String id) {
        List<Alerta> l = alertas(c);
        for (Alerta a : l) if (a.id.equals(id)) a.baseline = true;
        salvar(c, l);
    }

    public static synchronized Set<String> vistos(Context c, String id) {
        return new HashSet<>(sp(c).getStringSet("v_" + id, new HashSet<String>()));
    }

    public static synchronized void marcarVistos(Context c, String id, Collection<String> urls) {
        Set<String> s = vistos(c, id);
        s.addAll(urls);
        if (s.size() > 4000) {
            // evita crescer sem limite: descarta o excedente
            List<String> l = new ArrayList<>(s);
            s = new HashSet<>(l.subList(l.size() - 3000, l.size()));
        }
        sp(c).edit().putStringSet("v_" + id, s).apply();
    }

    // ---- fontes extras (portais de órgãos adicionados pelo usuário) ----

    public static synchronized List<String> custom(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("custom", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static synchronized void adicionarCustom(Context c, String url) {
        List<String> l = custom(c);
        if (!l.contains(url)) l.add(url);
        sp(c).edit().putString("custom", new JSONArray(l).toString()).apply();
    }

    public static synchronized void removerCustom(Context c, String url) {
        List<String> l = custom(c);
        l.remove(url);
        sp(c).edit().putString("custom", new JSONArray(l).toString()).apply();
    }
}
