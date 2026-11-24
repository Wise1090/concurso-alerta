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

/** Armazenamento local: alertas, editais salvos, editais lidos, prazos acompanhados e portais. */
public final class Store {
    private Store() {}

    public static class Alerta {
        public String id = "";
        public String cargo = "";
        public String uf = "";          // "" = todo o Brasil
        public boolean similares = true;
        public boolean baseline = false; // true após a primeira varredura
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("concurso_alerta", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ preferências

    /** 0 = seguir o sistema, 1 = sempre claro, 2 = sempre escuro. */
    public static int tema(Context c) {
        try { return sp(c).getInt("tema", 0); } catch (Exception e) { return 0; }
    }

    public static void definirTema(Context c, int v) {
        sp(c).edit().putInt("tema", v).apply();
    }

    public static boolean vibrar(Context c) {
        try { return sp(c).getBoolean("vibrar", true); } catch (Exception e) { return true; }
    }

    public static void definirVibrar(Context c, boolean v) {
        sp(c).edit().putBoolean("vibrar", v).apply();
    }

    // ------------------------------------------------------------------ alertas

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
        } catch (Exception ignored) { }
        return out;
    }

    private static void gravarAlertas(Context c, List<Alerta> l) {
        JSONArray arr = new JSONArray();
        try {
            for (Alerta a : l) {
                JSONObject o = new JSONObject();
                o.put("id", a.id); o.put("cargo", a.cargo); o.put("uf", a.uf);
                o.put("similares", a.similares); o.put("baseline", a.baseline);
                arr.put(o);
            }
        } catch (Exception ignored) { }
        sp(c).edit().putString("alertas", arr.toString()).apply();
    }

    public static synchronized Alerta adicionar(Context c, String cargo, String uf, boolean similares) {
        List<Alerta> l = alertas(c);
        for (Alerta a : l) {
            if (Util.norm(a.cargo).equals(Util.norm(cargo)) && a.uf.equals(uf)) {
                a.similares = similares;
                gravarAlertas(c, l);
                return a;
            }
        }
        Alerta a = new Alerta();
        a.id = UUID.randomUUID().toString().substring(0, 8);
        a.cargo = cargo.trim();
        a.uf = uf;
        a.similares = similares;
        l.add(a);
        gravarAlertas(c, l);
        return a;
    }

    public static synchronized void remover(Context c, String id) {
        List<Alerta> n = new ArrayList<>();
        for (Alerta a : alertas(c)) if (!a.id.equals(id)) n.add(a);
        gravarAlertas(c, n);
        sp(c).edit().remove("v_" + id).apply();
    }

    public static synchronized void definirBaseline(Context c, String id) {
        List<Alerta> l = alertas(c);
        for (Alerta a : l) if (a.id.equals(id)) a.baseline = true;
        gravarAlertas(c, l);
    }

    public static synchronized Set<String> vistos(Context c, String id) {
        return new HashSet<>(sp(c).getStringSet("v_" + id, new HashSet<String>()));
    }

    public static synchronized void marcarVistos(Context c, String id, Collection<String> urls) {
        Set<String> s = vistos(c, id);
        s.addAll(urls);
        if (s.size() > 4000) {
            List<String> l = new ArrayList<>(s);
            s = new HashSet<>(l.subList(l.size() - 3000, l.size()));
        }
        sp(c).edit().putStringSet("v_" + id, s).apply();
    }

    // ------------------------------------------------------------------ salvos

    private static List<Edital> lista(Context c, String chave) {
        List<Edital> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(chave, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(Edital.de(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    private static void gravarLista(Context c, String chave, List<Edital> l) {
        JSONArray arr = new JSONArray();
        try {
            for (Edital e : l) arr.put(e.json());
        } catch (Exception ignored) { }
        sp(c).edit().putString(chave, arr.toString()).apply();
    }

    public static synchronized List<Edital> salvos(Context c) {
        return lista(c, "salvos");
    }

    public static synchronized boolean estaSalvo(Context c, String url) {
        for (Edital e : salvos(c)) if (e.url.equals(url)) return true;
        return false;
    }

    public static synchronized void salvar(Context c, Edital e) {
        List<Edital> l = salvos(c);
        for (Edital x : l) if (x.url.equals(e.url)) return;
        l.add(0, e.copia());
        while (l.size() > 300) l.remove(l.size() - 1);
        gravarLista(c, "salvos", l);
        acompanhar(c, e);
    }

    public static synchronized void removerSalvo(Context c, String url) {
        List<Edital> n = new ArrayList<>();
        for (Edital e : salvos(c)) if (!e.url.equals(url)) n.add(e);
        gravarLista(c, "salvos", n);
    }

    // ------------------------------------------------------------------ lidos

    public static synchronized boolean estaLido(Context c, String url) {
        return sp(c).getStringSet("lidos", new HashSet<String>()).contains(url);
    }

    public static synchronized void marcarLido(Context c, String url) {
        Set<String> s = new HashSet<>(sp(c).getStringSet("lidos", new HashSet<String>()));
        s.add(url);
        if (s.size() > 3000) {
            List<String> l = new ArrayList<>(s);
            s = new HashSet<>(l.subList(l.size() - 2000, l.size()));
        }
        sp(c).edit().putStringSet("lidos", s).apply();
    }

    // ------------------------------------------- prazos acompanhados

    /** Passa a acompanhar o prazo deste edital, para avisar quando a inscrição estiver no fim. */
    public static synchronized void acompanhar(Context c, Edital e) {
        if (e.prazo == 0) return;
        List<Edital> l = lista(c, "acomp");
        for (Edital x : l) if (x.url.equals(e.url)) return;
        l.add(e.copia());
        // descarta o que já venceu
        List<Edital> vivos = new ArrayList<>();
        for (Edital x : l) if (x.diasRestantes() >= 0) vivos.add(x);
        while (vivos.size() > 400) vivos.remove(0);
        gravarLista(c, "acomp", vivos);
    }

    /** Editais acompanhados que encerram em até `dias` e ainda não foram avisados. */
    public static synchronized List<Edital> prazosParaAvisar(Context c, int dias) {
        Set<String> ja = new HashSet<>(sp(c).getStringSet("prazo_avisado", new HashSet<String>()));
        List<Edital> out = new ArrayList<>();
        for (Edital e : lista(c, "acomp")) {
            int d = e.diasRestantes();
            if (d >= 0 && d <= dias && !ja.contains(e.url)) out.add(e);
        }
        return out;
    }

    public static synchronized void marcarPrazoAvisado(Context c, Collection<String> urls) {
        Set<String> s = new HashSet<>(sp(c).getStringSet("prazo_avisado", new HashSet<String>()));
        s.addAll(urls);
        if (s.size() > 2000) {
            List<String> l = new ArrayList<>(s);
            s = new HashSet<>(l.subList(l.size() - 1500, l.size()));
        }
        sp(c).edit().putStringSet("prazo_avisado", s).apply();
    }

    // ------------------------------------------------------------------ portais

    public static synchronized List<String> custom(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("custom", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) { }
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
