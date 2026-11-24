package com.concursos.alerta;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coleta de editais. Os coletores são "melhor esforço": cada site pode mudar o HTML a qualquer
 * momento, então cada fonte roda isolada e uma falha não derruba as demais.
 */
public final class Fontes {
    private Fontes() {}

    static class Fonte {
        final String nome, url, tipo; // tipo: "pci" | "agregador" | "banca" | "orgao"
        Fonte(String nome, String url, String tipo) { this.nome = nome; this.url = url; this.tipo = tipo; }
    }

    public static class Coleta {
        public final List<Edital> itens = new ArrayList<>();
        public final List<String> falhas = new ArrayList<>();
        public int total;
    }

    static List<Fonte> padrao() {
        List<Fonte> l = new ArrayList<>();
        // Plataformas de estudo / agregadores com área de editais gratuita
        l.add(new Fonte("PCI Concursos", "https://www.pciconcursos.com.br/concursos/", "pci"));
        l.add(new Fonte("Estratégia Concursos", "https://concursos.estrategia.com/portal/concursos-abertos/", "agregador"));
        l.add(new Fonte("Gran Cursos", "https://blog.grancursosonline.com.br/concursos-abertos/", "agregador"));
        l.add(new Fonte("Folha Dirigida", "https://folhadirigida.com.br/concursos/", "agregador"));
        l.add(new Fonte("Concursos no Brasil", "https://concursosnobrasil.com.br/concursos/", "agregador"));
        l.add(new Fonte("Jornal dos Concursos", "https://jornaldosconcursos.com.br/", "agregador"));
        // Bancas organizadoras (páginas que renderizam só com JavaScript, como a do Cebraspe, não funcionam)
        l.add(new Fonte("Vunesp", "https://www.vunesp.com.br/", "banca"));
        l.add(new Fonte("FGV Conhecimento", "https://conhecimento.fgv.br/concursos", "banca"));
        l.add(new Fonte("FCC", "https://www.concursosfcc.com.br/", "banca"));
        l.add(new Fonte("IBFC", "https://www.ibfc.org.br/", "banca"));
        l.add(new Fonte("Cesgranrio", "https://www.cesgranrio.org.br/concursos/", "banca"));
        return l;
    }

    private static long cacheT = 0;
    private static Coleta cache = null;

    /** Coleta as páginas de listagem (todas as fontes em paralelo). Resultado fica em cache por 5 min. */
    public static synchronized Coleta coletar(Context ctx, boolean forcar) {
        if (!forcar && cache != null && System.currentTimeMillis() - cacheT < 5 * 60_000L) return cache;

        List<Fonte> fontes = padrao();
        for (String u : Store.custom(ctx)) {
            String host;
            try { host = new URI(u).getHost(); } catch (Exception e) { host = u; }
            fontes.add(new Fonte(host == null ? u : host, u, "orgao"));
        }

        Coleta col = new Coleta();
        col.total = fontes.size();
        ExecutorService ex = Executors.newFixedThreadPool(6);
        List<Future<List<Edital>>> fs = new ArrayList<>();
        for (final Fonte f : fontes) fs.add(ex.submit(() -> coletarFonte(f)));
        Map<String, Edital> porUrl = new LinkedHashMap<>();
        for (int i = 0; i < fs.size(); i++) {
            try {
                for (Edital e : fs.get(i).get(45, TimeUnit.SECONDS)) {
                    if (!porUrl.containsKey(e.url)) porUrl.put(e.url, e);
                }
            } catch (Exception e) {
                col.falhas.add(fontes.get(i).nome);
            }
        }
        ex.shutdownNow();
        col.itens.addAll(porUrl.values());
        cache = col;
        cacheT = System.currentTimeMillis();
        return col;
    }

    private static List<Edital> coletarFonte(Fonte f) throws IOException {
        String html = Util.get(f.url);
        List<Edital> r;
        if (f.tipo.equals("pci")) {
            r = parsePci(html, f.url, f.nome);
            if (r.isEmpty()) r = parseLinks(html, f.url, f.nome, false); // plano B se o layout mudou
        } else {
            r = parseLinks(html, f.url, f.nome, f.tipo.equals("banca") || f.tipo.equals("orgao"));
        }
        if (r.isEmpty()) throw new IOException("nada reconhecido em " + f.nome);
        return r;
    }

    // ------------------------------------------------------------------ PCI Concursos

    private static final Pattern LINK_LINHA = Pattern.compile("\u0001([^\u0002]*)\u0002(.*)");
    private static final Pattern DATA = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");

    static List<Edital> parsePci(String html, String base, String fonte) {
        List<Edital> out = new ArrayList<>();
        Edital cur = null;
        for (String line : Util.htmlToLines(html)) {
            if (line.contains("/apostilas/")) continue;
            Matcher m = LINK_LINHA.matcher(line);
            if (m.find() && m.group(1).contains("/noticias/")) {
                String nome = Util.clean(m.group(2));
                String url = Util.abs(base, m.group(1));
                if (nome.length() >= 4 && url != null) {
                    guardarPci(out, cur);
                    cur = new Edital();
                    cur.orgao = nome;
                    cur.titulo = nome;
                    cur.url = url;
                    cur.fonte = fonte;
                    continue;
                }
            }
            if (cur == null) continue;
            String t = Util.clean(line);
            if (t.isEmpty()) continue;
            if (cur.uf.isEmpty() && Util.UF_SET.contains(t)) { cur.uf = t; continue; }
            if (cur.info.isEmpty() && t.matches("(?i).*(vagas?|cadastro de reserva).*")) { cur.info = t; continue; }
            if (t.matches(".*\\d{2}/\\d{2}/\\d{4}.*") || t.matches("(?i)(suspenso|prorrogado.*|reaberto.*)")) {
                if (cur.data.isEmpty()) cur.data = t;
                continue;
            }
            if (t.matches("(?i)(n[aã]o informada|(fundamental|m[eé]dio|superior|t[eé]cnico|alfabetizado)([ /,-]+.*)?)")) {
                cur.nivel = t;
                continue;
            }
            if (cur.cargos.isEmpty() && t.length() < 500) cur.cargos = t;
        }
        guardarPci(out, cur);
        return out;
    }

    private static void guardarPci(List<Edital> out, Edital e) {
        if (e == null) return;
        if (e.uf.isEmpty() && e.info.isEmpty()) return; // link de menu/notícia qualquer
        if (encerrado(e.data)) return;
        out.add(e);
    }

    /** true se a última data da linha (prazo final) já passou. */
    private static boolean encerrado(String data) {
        if (data == null || data.isEmpty()) return false;
        Matcher m = DATA.matcher(data);
        String ult = null;
        while (m.find()) ult = m.group();
        if (ult == null) return false;
        try {
            Date d = new SimpleDateFormat("dd/MM/yyyy").parse(ult);
            return d != null && d.getTime() + 86_400_000L < System.currentTimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------- Agregadores / bancas / órgãos

    private static final Pattern ANCORA =
            Pattern.compile("(?is)<a\\s[^>]*?href\\s*=\\s*[\"']([^\"'#]+)[\"'][^>]*>(.*?)</a>");
    private static final Pattern PALAVRAS = Pattern.compile(
            "concurso|edital|processo seletivo|selecao|vagas|inscricoes|prefeitura|camara|tribunal|"
                    + "policia|hospital|universidade|instituto|secretaria|governo|ministerio|conselho|"
                    + "bombeiro|defensoria|procuradoria");

    /** Extrai links (âncoras) cujo texto parece um concurso. */
    static List<Edital> parseLinks(String html, String base, String fonte, boolean aceitaTudo) {
        List<Edital> out = new ArrayList<>();
        java.util.Set<String> vistos = new java.util.HashSet<>();
        Matcher m = ANCORA.matcher(html);
        while (m.find()) {
            String href = m.group(1).trim();
            if (href.startsWith("javascript") || href.startsWith("mailto") || href.startsWith("tel")) continue;
            String texto = Util.stripTags(m.group(2));
            if (texto.length() < 18 || texto.length() > 240) continue;
            String url = Util.abs(base, href);
            if (url == null || !vistos.add(url)) continue;
            if (!aceitaTudo && !PALAVRAS.matcher(Util.norm(texto)).find()) continue;
            Edital e = new Edital();
            e.titulo = texto;
            e.url = url;
            e.fonte = fonte;
            e.uf = Util.detectUf(texto);
            out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------ DOU (busca)

    /** Busca no Diário Oficial da União (Imprensa Nacional). Melhor esforço. */
    public static List<Edital> dou(String cargo) throws Exception {
        String q = "\"edital\" \"concurso\" " + cargo;
        String url = "https://www.in.gov.br/consulta/-/buscar/dou?q=" + URLEncoder.encode(q, "UTF-8")
                + "&s=todos&exactDate=all&sortType=0&delta=20";
        String html = Util.get(url);
        Matcher m = Pattern.compile("(?is)<script[^>]*BuscaDouPortlet_params[^>]*>(.*?)</script>").matcher(html);
        if (!m.find()) throw new IOException("DOU: formato inesperado");
        JSONArray arr = new JSONObject(m.group(1).trim()).getJSONArray("jsonArray");
        List<Edital> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String urlTitle = o.optString("urlTitle");
            if (urlTitle.isEmpty()) continue;
            Edital e = new Edital();
            e.titulo = Util.stripTags(o.optString("title"));
            e.orgao = o.optString("hierarchyStr");
            e.data = o.optString("pubDate");
            e.info = o.optString("artType");
            e.url = "https://www.in.gov.br/web/dou/-/" + urlTitle;
            e.uf = "BR";
            e.fonte = "Diário Oficial da União";
            out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------ filtro

    public static List<Edital> filtrar(List<Edital> todos, Busca.Consulta q, String uf) {
        List<Edital> out = new ArrayList<>();
        for (Edital e : todos) {
            int p = q.pontuar(e.textoBusca());
            if (p == 0 || !ufOk(e, uf)) continue;
            Edital c = e.copia();
            c.similar = (p == 1);
            out.add(c);
        }
        // diretos antes dos similares
        List<Edital> ord = new ArrayList<>();
        for (Edital e : out) if (!e.similar) ord.add(e);
        for (Edital e : out) if (e.similar) ord.add(e);
        return ord;
    }

    private static boolean ufOk(Edital e, String uf) {
        if (uf == null || uf.isEmpty()) return true;
        return e.uf.equals(uf) || e.uf.equals("BR");
    }

    /** Busca completa: listagens + DOU. Usada pela tela e pelo verificador em segundo plano. */
    public static List<Edital> buscar(Context ctx, Coleta col, String cargo, String uf, boolean similares) {
        List<Edital> r = filtrar(col.itens, new Busca.Consulta(cargo, similares), uf);
        try {
            for (Edital e : dou(cargo)) {
                boolean ja = false;
                for (Edital x : r) if (x.url.equals(e.url)) { ja = true; break; }
                if (!ja) r.add(e);
            }
        } catch (Exception ignored) {
            col.falhas.add("Diário Oficial da União");
        }
        return r;
    }
}
