package com.concursos.alerta;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Um edital encontrado em alguma fonte, com os campos já convertidos para comparação. */
public class Edital {
    public static final int DIRETO = 2;    // o cargo buscado aparece na relação de cargos
    public static final int SIMILAR = 1;   // cargo equivalente
    public static final int INCERTO = 0;   // edital sem relação de cargos

    public static final int FUNDAMENTAL = 1;
    public static final int MEDIO = 2;
    public static final int TECNICO = 4;
    public static final int SUPERIOR = 8;

    public String titulo = "";
    public String orgao = "";
    public String uf = "";
    public String cargos = "";
    public String info = "";
    public String nivel = "";
    public String data = "";
    public String url = "";
    public String fonte = "";
    public int tipo = DIRETO;

    // derivados
    public int vagas = 0;        // 0 = não informado
    public double salario = 0;   // 0 = não informado
    public long prazo = 0;       // 0 = prazo desconhecido
    public int niveis = 0;       // combinação de FUNDAMENTAL/MEDIO/TECNICO/SUPERIOR

    private static final Pattern P_VAGAS = Pattern.compile("(\\d[\\d.]*)\\s*vaga");
    private static final Pattern P_SALARIO = Pattern.compile("R\\$\\s*([\\d.]+,\\d{2})");
    private static final Pattern P_DATA = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");

    /** Preenche vagas, salário, prazo e escolaridade a partir dos textos lidos da fonte. */
    public void calcular() {
        niveis = 0;
        String n = Util.norm(nivel + " " + cargos);
        if (n.contains("fundamental")) niveis |= FUNDAMENTAL;
        if (n.contains("medio")) niveis |= MEDIO;
        if (n.contains("tecnico")) niveis |= TECNICO;
        if (n.contains("superior")) niveis |= SUPERIOR;

        vagas = 0;
        Matcher mv = P_VAGAS.matcher(info.toLowerCase(Locale.ROOT));
        if (mv.find()) {
            try {
                long v = Long.parseLong(mv.group(1).replace(".", ""));
                vagas = (v > 0 && v < 2_000_000L) ? (int) v : 0;
            } catch (Exception ignored) { }
        }

        salario = 0;
        Matcher ms = P_SALARIO.matcher(info);
        double maior = 0;
        while (ms.find()) {
            try {
                double d = Double.parseDouble(ms.group(1).replace(".", "").replace(",", "."));
                if (d > maior) maior = d;
            } catch (Exception ignored) { }
        }
        salario = maior;

        prazo = 0;
        Matcher md = P_DATA.matcher(data);
        String ultima = null;
        while (md.find()) ultima = md.group(1);
        if (ultima != null) {
            try {
                Date d = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).parse(ultima);
                if (d != null) prazo = d.getTime();
            } catch (Exception ignored) { }
        }
    }

    /** Dias que faltam para o encerramento; Integer.MAX_VALUE quando não há prazo conhecido. */
    public int diasRestantes() {
        if (prazo == 0) return Integer.MAX_VALUE;
        long ms = prazo + 86_400_000L - System.currentTimeMillis();
        return (int) Math.floor(ms / 86_400_000.0);
    }

    public boolean semRelacaoDeCargos() {
        String c = Util.norm(cargos);
        return c.isEmpty() || c.contains("varios cargos") || c.contains("diversos cargos");
    }

    public Edital copia() {
        Edital e = new Edital();
        e.titulo = titulo; e.orgao = orgao; e.uf = uf; e.cargos = cargos; e.info = info;
        e.nivel = nivel; e.data = data; e.url = url; e.fonte = fonte; e.tipo = tipo;
        e.vagas = vagas; e.salario = salario; e.prazo = prazo; e.niveis = niveis;
        return e;
    }

    public String textoBusca() {
        return titulo + " " + orgao + " " + cargos;
    }

    /** Texto usado ao compartilhar. */
    public String paraCompartilhar() {
        StringBuilder s = new StringBuilder();
        s.append(titulo);
        if (!uf.isEmpty() && !uf.equals("BR")) s.append(" (").append(uf).append(")");
        if (!info.isEmpty()) s.append("\n").append(info);
        if (!cargos.isEmpty()) s.append("\nCargos: ").append(cargos);
        if (!data.isEmpty()) s.append("\nInscrições até ").append(data);
        s.append("\n").append(url);
        s.append("\n\nEnviado pelo Concurso Alerta");
        return s.toString();
    }

    public JSONObject json() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("t", titulo); o.put("o", orgao); o.put("u", uf); o.put("c", cargos);
        o.put("i", info); o.put("n", nivel); o.put("d", data); o.put("l", url);
        o.put("f", fonte); o.put("p", tipo); o.put("v", vagas); o.put("s", salario);
        o.put("z", prazo); o.put("x", niveis);
        return o;
    }

    public static Edital de(JSONObject o) {
        Edital e = new Edital();
        e.titulo = o.optString("t"); e.orgao = o.optString("o"); e.uf = o.optString("u");
        e.cargos = o.optString("c"); e.info = o.optString("i"); e.nivel = o.optString("n");
        e.data = o.optString("d"); e.url = o.optString("l"); e.fonte = o.optString("f");
        e.tipo = o.optInt("p", DIRETO); e.vagas = o.optInt("v"); e.salario = o.optDouble("s", 0);
        e.prazo = o.optLong("z"); e.niveis = o.optInt("x");
        return e;
    }
}
