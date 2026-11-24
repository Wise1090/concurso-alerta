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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coleta de editais. Cada fonte roda isolada: se um site mudar o HTML, so aquela fonte para.
 * O PCI Concursos tem leitor proprio porque e a unica fonte que publica a lista de cargos
 * na propria pagina de listagem.
 */
public final class Fontes {
    private Fontes() {}

    static class Fonte {
        final String nome, url, tipo; // pci | agregador | banca | orgao
        Fonte(String nome, String url, String tipo) { this.nome = nome; this.url = url; this.tipo = tipo; }
    }

    public static class Coleta {
        public final List<Edital> itens = new ArrayList<>();
        public final List<String> falhas = new ArrayList<>();
        public int total;
    }

    static List<Fonte> padrao() {
        List<Fonte> l = new ArrayList<>();
        l.add(new Fonte("PCI Concursos", "https://www.pciconcursos.com.br/concursos/", "pci"));
        l.add(new Fonte("Estrategia Concursos", "https://concursos.estrategia.com/portal/concursos-abertos/", "agregador"));
        l.add(new Fonte("Gran Cursos", "https://blog.grancursosonline.com.br/concursos-abertos/", "agregador"));
        l.add(new Fonte("Folha Dirigida", "https://folhadirigida.com.br/concursos/", "agregador"));
        l.add(new Fonte("Concursos no Brasil", "https://concursosnobrasil.com.br/concursos/", "agregador"));
        l.add(new Fonte("Vunesp", "https://www.vunesp.com.br/", "banca"));
        l.add(new Fonte("FGV", "https://conhecimento.fgv.br/concursos", "banca"));
        l.add(new Fonte("FCC", "https://www.concursosfcc.com.br/", "banca"));
        l.add(new Fonte("IBFC", "https://www.ibfc.org.br/", "banca"));
        l.add(new Fonte("Cesgranrio", "https://www.cesgranrio.org.br/concursos/", "banca"));
        return l;
    }

    private static long cacheT = 0;
    private static Coleta cache = null;

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
            if (r.isEmpty()) r = parseLinks(html, f.url, f.nome, false);
        } else {
            r = parseLinks(html, f.url, f.nome, f.tipo.equals("banca") || f.tipo.equals("orgao"));
        }
        if (r.isEmpty()) throw new IOException("nada reconhecido em " + f.nome);
        return r;
    }

    // ------------------------------------------------------------------ PCI Concursos
    //
    // Estrutura real da listagem (conferida na pagina):
    //   <div class="ca">
    //     <a href="/noticias/...">ORGAO</a>
    //     <div class="cb"><img></div>
    //     <div class="cc">SP</div>                        -> UF ("" quando nacional)
    //     <div class="cd">N vagas ate R$ X<br><span>Cargos<br><span>Escolaridade</span></span></div>
    //     <div class="ce"><span>dd/mm/aaaa</span></div>
    //   </div>

    private static final Pattern BLOCO = Pattern.compile("(?i)<div[^>]*class=\"ca\"[^>]*>");
    private static final Pattern ANCORA_PCI =
            Pattern.compile("(?is)<a\\s[^>]*href\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern DATA = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");

    static List<Edital> parsePci(String html, String base, String fonte) {
        List<Edital> out = new ArrayList<>();
        Matcher mb = BLOCO.matcher(html);
        List<Integer> inicios = new ArrayList<>();
        while (mb.find()) inicios.add(mb.end());
        for (int i = 0; i < inicios.size(); i++) {
            int fim = (i + 1 < inicios.size()) ? inicios.get(i + 1) : html.length();
            String b = html.substring(inicios.get(i), Math.min(fim, inicios.get(i) + 4000));
            Edital e = bloco(b, base, fonte);
            if (e != null) out.add(e);
        }
        return out;
    }

    private static Edital bloco(String b, String base, String fonte) {
        Matcher ma = ANCORA_PCI.matcher(b);
        if (!ma.find()) return null;
        String url = Util.abs(base, ma.group(1));
        String orgao = Util.stripTags(ma.group(2));
        if (url == null || orgao.length() < 3) return null;

        Edital e = new Edital();
        e.url = url;
        e.orgao = orgao;
        e.titulo = orgao;
        e.fonte = fonte;

        String uf = interno(b, "cc");
        if (Util.UF_SET.contains(uf)) e.uf = uf;

        List<String> cd = linhasInternas(b, "cd", "ce");
        if (cd.size() > 0) e.info = cd.get(0);
        if (cd.size() > 1) e.cargos = cd.get(1);
        if (cd.size() > 2) e.nivel = cd.get(2);

        List<String> ce = linhasInternas(b, "ce", null);
        if (!ce.isEmpty()) e.data = ce.get(0);

        if (e.info.isEmpty() && e.cargos.isEmpty()) return null;
        if (encerrado(e.data)) return null;
        return e;
    }

    /** Texto simples de <div class="X">...</div> dentro do bloco. */
    private static String interno(String b, String cls) {
        Matcher m = Pattern.compile("(?is)class=\"" + cls + "\"[^>]*>(.*?)</div>").matcher(b);
        return m.find() ? Util.stripTags(m.group(1)) : "";
    }

    /** Linhas do trecho que vai de class="de" ate class="ate" (os &lt;br&gt; viram quebras). */
    private static List<String> linhasInternas(String b, String de, String ate) {
        int i = b.indexOf("class=\"" + de + "\"");
        if (i < 0) return new ArrayList<>();
        int abre = b.indexOf('>', i);
        if (abre < 0) return new ArrayList<>();
        int f = (ate == null) ? b.length() : b.indexOf("class=\"" + ate + "\"", abre);
        if (f < 0) f = b.length();
        return Util.htmlToLines(b.substring(abre + 1, f));
    }

    /** true se o ultimo prazo da linha ja passou. */
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

    // ------------------------------------------------------- Agregadores / bancas / orgaos

    private static final Pattern ANCORA =
            Pattern.compile("(?is)<a\\s[^>]*?href\\s*=\\s*[\"']([^\"'#]+)[\"'][^>]*>(.*?)</a>");
    private static final Pattern PALAVRAS = Pattern.compile(
            "concurso|edital|processo seletivo|selecao|vagas|inscricoes|prefeitura|camara|tribunal|"
                    + "policia|hospital|universidade|instituto|secretaria|governo|ministerio|conselho|"
                    + "bombeiro|defensoria|procuradoria");

    static List<Edital> parseLinks(String html, String base, String fonte, boolean aceitaTudo) {
        List<Edital> out = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
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

    // ------------------------------------------------------------------ DOU

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
            e.fonte = "Diario Oficial da Uniao";
            out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------ filtro

    public static class Resultado {
        public final List<Edital> achados = new ArrayList<>();
        public final List<Edital> semCargo = new ArrayList<>(); // "Varios Cargos": pode conter o cargo
        public int fontesComFalha, fontesTotal;
    }

    public static Resultado buscar(Context ctx, Coleta col, String cargo, String uf, boolean similares) {
        Busca.Consulta q = new Busca.Consulta(cargo, similares);
        Resultado r = new Resultado();
        List<Edital> diretos = new ArrayList<>(), parecidos = new ArrayList<>();

        for (Edital e : col.itens) {
            if (!ufOk(e, uf)) continue;
            int p = q.pontuar(e.textoBusca());
            if (p == 2) { Edital c = e.copia(); c.tipo = Edital.DIRETO; diretos.add(c); }
            else if (p == 1) { Edital c = e.copia(); c.tipo = Edital.SIMILAR; parecidos.add(c); }
            else if (e.semListaDeCargos() && !e.info.isEmpty()) {
                Edital c = e.copia(); c.tipo = Edital.INCERTO; r.semCargo.add(c);
            }
        }
        r.achados.addAll(diretos);
        r.achados.addAll(parecidos);

        try {
            for (Edital e : dou(cargo)) {
                boolean ja = false;
                for (Edital x : r.achados) if (x.url.equals(e.url)) { ja = true; break; }
                if (!ja) r.achados.add(e);
            }
        } catch (Exception ignored) {
            col.falhas.add("Diario Oficial da Uniao");
        }
        r.fontesComFalha = col.falhas.size();
        r.fontesTotal = col.total + 1;
        return r;
    }

    private static boolean ufOk(Edital e, String uf) {
        if (uf == null || uf.isEmpty()) return true;
        return e.uf.equals(uf) || e.uf.equals("BR") || e.uf.isEmpty();
    }
}
