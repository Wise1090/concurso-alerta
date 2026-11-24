package com.concursos.alerta;

/** Um edital/concurso encontrado em alguma fonte. */
public class Edital {
    public static final int DIRETO = 2;    // o cargo digitado aparece na lista de cargos
    public static final int SIMILAR = 1;   // cargo equivalente
    public static final int INCERTO = 0;   // edital que nao detalha os cargos ("Varios Cargos")

    public String titulo = "";
    public String orgao = "";
    public String uf = "";        // "" = desconhecido, "BR" = nacional/federal
    public String cargos = "";
    public String info = "";      // vagas / salario
    public String nivel = "";     // escolaridade
    public String data = "";
    public String url = "";
    public String fonte = "";
    public int tipo = DIRETO;

    public boolean semListaDeCargos() {
        String c = Util.norm(cargos);
        return c.isEmpty() || c.contains("varios cargos") || c.contains("diversos cargos");
    }

    public Edital copia() {
        Edital e = new Edital();
        e.titulo = titulo; e.orgao = orgao; e.uf = uf; e.cargos = cargos; e.info = info;
        e.nivel = nivel; e.data = data; e.url = url; e.fonte = fonte; e.tipo = tipo;
        return e;
    }

    public String textoBusca() {
        return titulo + " " + orgao + " " + cargos;
    }
}
