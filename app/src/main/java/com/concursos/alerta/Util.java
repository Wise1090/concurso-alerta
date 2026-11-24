package com.concursos.alerta;

import android.text.Html;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Util {
    private Util() {}

    public static final String[] UFS = {
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"};
    public static final String[] NOMES = {
            "Acre", "Alagoas", "Amapá", "Amazonas", "Bahia", "Ceará", "Distrito Federal",
            "Espírito Santo", "Goiás", "Maranhão", "Mato Grosso", "Mato Grosso do Sul",
            "Minas Gerais", "Pará", "Paraíba", "Paraná", "Pernambuco", "Piauí", "Rio de Janeiro",
            "Rio Grande do Norte", "Rio Grande do Sul", "Rondônia", "Roraima", "Santa Catarina",
            "São Paulo", "Sergipe", "Tocantins"};
    static final Set<String> UF_SET = new HashSet<>(Arrays.asList(UFS));

    private static final Integer[] ORDEM;
    static {
        ORDEM = new Integer[NOMES.length];
        for (int i = 0; i < ORDEM.length; i++) ORDEM[i] = i;
        Arrays.sort(ORDEM, (a, b) -> NOMES[b].length() - NOMES[a].length());
    }

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/121.0.0.0 Safari/537.36";

    /** Minúsculas, sem acentos, só letras/números e espaços simples. */
    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static String get(String u) throws IOException {
        if (u.startsWith("http://")) u = "https://" + u.substring(7);
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.5");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            String cs = "UTF-8";
            String ct = c.getContentType();
            if (ct != null) {
                Matcher m = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(ct);
                if (m.find()) cs = m.group(1);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0 && bos.size() < 6_000_000) bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), cs);
        } finally {
            c.disconnect();
        }
    }

    public static String unescape(String s) {
        if (s == null || s.isEmpty()) return "";
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
    }

    public static String stripTags(String s) {
        return unescape(s.replaceAll("(?s)<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
    }

    /** Remove os marcadores de link (\u0001url\u0002) deixados por htmlToLines. */
    public static String clean(String s) {
        return s.replaceAll("\u0001[^\u0002]*\u0002", "").replaceAll("\\s+", " ").trim();
    }

    /** Converte HTML em linhas de texto; links viram \u0001href\u0002texto. */
    public static List<String> htmlToLines(String html) {
        String h = html.replaceAll("(?is)<(script|style|noscript)[^>]*>.*?</\\1>", " ");
        h = h.replaceAll("(?is)<a\\s[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", "\u0001$1\u0002");
        h = h.replaceAll("(?i)</?(div|p|br|li|ul|ol|tr|td|th|h[1-6]|section|article|table|header|footer)(\\s[^>]*)?/?>", "\n");
        h = h.replaceAll("(?s)<[^>]+>", "");
        List<String> out = new ArrayList<>();
        for (String raw : h.split("\n")) {
            String l = unescape(raw).replaceAll("[\\s\u00a0]+", " ").trim();
            if (!l.isEmpty()) out.add(l);
        }
        return out;
    }

    public static String abs(String base, String href) {
        try {
            String r = new URI(base).resolve(href.trim().replace(" ", "%20")).toString();
            return r.startsWith("http") ? r : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static final Pattern UF_PAT = Pattern.compile("[/\\-–(]\\s?([A-Z]{2})\\b");

    /** Tenta descobrir a UF a partir de um texto ("Prefeitura de X/BA", "Governo da Bahia"...). */
    public static String detectUf(String texto) {
        if (texto == null) return "";
        Matcher m = UF_PAT.matcher(texto);
        while (m.find()) {
            if (UF_SET.contains(m.group(1))) return m.group(1);
        }
        String n = " " + norm(texto) + " ";
        for (int idx : ORDEM) {
            if (UFS[idx].equals("PA")) continue; // "Pará" colide com a preposição "para"
            if (n.contains(" " + norm(NOMES[idx]) + " ")) return UFS[idx];
        }
        if (n.contains(" estado do para ") || n.contains(" governo do para ")) return "PA";
        return "";
    }
}
