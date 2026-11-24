package com.concursos.alerta;

/** Um edital/concurso encontrado em alguma fonte. */
public class Edital {
    public String titulo = "";
    public String orgao = "";
    public String uf = "";        // "" = desconhecido, "BR" = nacional/federal
    public String cargos = "";
    public String info = "";      // vagas / salário
    public String nivel = "";     // escolaridade
    public String data = "";
    public String url = "";
    public String fonte = "";
    public boolean similar = false;

    public Edital copia() {
        Edital e = new Edital();
        e.titulo = titulo; e.orgao = orgao; e.uf = uf; e.cargos = cargos; e.info = info;
        e.nivel = nivel; e.data = data; e.url = url; e.fonte = fonte; e.similar = similar;
        return e;
    }

    public String textoBusca() {
        return titulo + " " + orgao + " " + cargos;
    }
}
