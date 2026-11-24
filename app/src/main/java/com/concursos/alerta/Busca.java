package com.concursos.alerta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Casamento do cargo digitado (palavra-chave) com títulos, incluindo cargos similares. */
public final class Busca {
    private Busca() {}

    // Grupos de cargos parecidos (já sem acento, minúsculos).
    static final String[][] SINONIMOS = {
            {"professor", "docente", "magisterio", "pedagogo", "educacao basica"},
            {"agente administrativo", "assistente administrativo", "auxiliar administrativo",
                    "tecnico administrativo", "oficial administrativo", "auxiliar de escritorio"},
            {"enfermeiro", "enfermagem", "tecnico de enfermagem", "auxiliar de enfermagem"},
            {"medico", "medicina", "medico clinico", "medico plantonista", "medico veterinario"},
            {"policial", "policia militar", "policia civil", "soldado", "agente de policia",
                    "escrivao", "investigador", "delegado", "policia penal", "agente penitenciario"},
            {"guarda municipal", "guarda civil", "agente de seguranca", "vigilante"},
            {"motorista", "condutor", "operador de maquinas"},
            {"advogado", "procurador", "assessor juridico", "analista juridico", "defensor publico"},
            {"contador", "contabil", "tecnico em contabilidade", "analista contabil"},
            {"engenheiro", "engenharia", "engenheiro civil", "tecnico em edificacoes"},
            {"farmaceutico", "farmacia", "auxiliar de farmacia"},
            {"psicologo", "psicologia", "assistente social"},
            {"analista de sistemas", "analista de ti", "tecnologia da informacao", "desenvolvedor",
                    "programador", "tecnico em informatica", "analista de suporte"},
            {"tecnico judiciario", "analista judiciario", "oficial de justica", "auxiliar judiciario"},
            {"servicos gerais", "auxiliar de servicos gerais", "agente de limpeza", "zelador", "merendeira"},
            {"dentista", "odontologo", "cirurgiao dentista", "odontologia"},
            {"fiscal", "auditor fiscal", "agente fiscal", "fiscal tributario", "auditor"},
            {"bancario", "escriturario", "tecnico bancario"},
            {"bombeiro", "corpo de bombeiros", "bombeiro militar"},
            {"nutricionista", "nutricao"},
            {"fisioterapeuta", "fisioterapia"},
            {"bibliotecario", "biblioteconomo"},
            {"arquiteto", "arquitetura", "urbanista"},
    };

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "de", "da", "do", "das", "dos", "para", "em", "e", "a", "o", "com"));

    public static class Consulta {
        final String direto;
        final List<String> tokens = new ArrayList<>();
        final List<String> similares = new ArrayList<>();

        public Consulta(String cargo, boolean usarSimilares) {
            direto = Util.norm(cargo);
            for (String t : direto.split(" ")) {
                if (t.length() >= 3 && !STOP.contains(t)) tokens.add(t);
            }
            if (usarSimilares) {
                for (String[] g : SINONIMOS) {
                    boolean rel = false;
                    for (String s : g) {
                        if (relacionado(direto, s)) { rel = true; break; }
                    }
                    if (rel) for (String s : g) if (!similares.contains(s)) similares.add(s);
                }
            }
        }

        /** 0 = não casa, 1 = cargo similar, 2 = casa direto. */
        public int pontuar(String texto) {
            String t = " " + Util.norm(texto) + " ";
            if (!direto.isEmpty() && contem(t, direto)) return 2;
            if (!tokens.isEmpty()) {
                boolean todos = true;
                for (String tok : tokens) {
                    if (!contem(t, tok)) { todos = false; break; }
                }
                if (todos) return 2;
            }
            for (String s : similares) if (contem(t, s)) return 1;
            return 0;
        }
    }

    private static boolean relacionado(String q, String s) {
        if (q.isEmpty()) return false;
        if (q.equals(s)) return true;
        return q.length() >= 5 && s.length() >= 4 && (s.contains(q) || q.contains(s));
    }

    /** Casa no início de palavra; tira a vogal final (o/a) para aceitar masculino/feminino/plural. */
    private static boolean contem(String t, String p) {
        if (p.length() <= 4) return t.contains(" " + p + " ");
        String base = p;
        if (p.length() >= 6 && (p.endsWith("o") || p.endsWith("a"))) base = p.substring(0, p.length() - 1);
        return t.contains(" " + base);
    }
}
