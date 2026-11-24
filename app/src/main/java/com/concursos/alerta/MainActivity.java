package com.concursos.alerta;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ---------------------------------------------------------------- paleta
    static final int PRIMARIA   = 0xFF1D4ED8;
    static final int PRIMARIA_E = 0xFF1E40AF;
    static final int FUNDO      = 0xFFF1F5F9;
    static final int CARTAO     = 0xFFFFFFFF;
    static final int TEXTO      = 0xFF0F172A;
    static final int SUAVE      = 0xFF64748B;
    static final int BORDA      = 0xFFE2E8F0;
    static final int VERDE      = 0xFF047857;
    static final int AMBAR      = 0xFFB45309;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private TextView tvSub;
    private FrameLayout conteudo;
    private View telaBuscar, telaAlertas, telaSobre;
    private final TextView[] abaIcone = new TextView[3];
    private final TextView[] abaRotulo = new TextView[3];

    private EditText etCargo, etUrl;
    private Spinner spUf;
    private CheckBox cbSim, cbSemCargo;
    private TextView tvStatus;
    private LinearLayout llRes, llAlertas, llCustom;
    private boolean buscando = false;

    // ---------------------------------------------------------------- helpers
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView tv(String s, int sp, boolean negrito, int cor) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(cor);
        t.setLineSpacing(dp(3), 1f);
        if (negrito) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable forma(int cor, int raio, int corBorda) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(raio));
        if (corBorda != 0) g.setStroke(dp(1), corBorda);
        return g;
    }

    private LinearLayout cartao() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(forma(CARTAO, 16, BORDA));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(14), dp(12), dp(14), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView botao(String texto, int fundo, int corTexto, boolean contorno, View.OnClickListener l) {
        TextView b = tv(texto, 15, true, corTexto);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(forma(fundo, 12, contorno ? PRIMARIA : 0));
        b.setClickable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView selo(String texto, int cor) {
        TextView t = tv(texto, 11, true, cor);
        t.setPadding(dp(8), dp(3), dp(8), dp(3));
        t.setBackground(forma((cor & 0x00FFFFFF) | 0x1A000000, 7, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView titulo(String s) {
        TextView t = tv(s, 13, true, SUAVE);
        t.setAllCaps(true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(18), dp(20), dp(18), dp(2));
        return t;
    }

    private void abrir(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- ciclo
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(FUNDO);

        root.addView(barraSuperior());

        conteudo = new FrameLayout(this);
        root.addView(conteudo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        telaBuscar = montarBuscar();
        telaAlertas = montarAlertas();
        telaSobre = montarSobre();
        conteudo.addView(telaBuscar);
        conteudo.addView(telaAlertas);
        conteudo.addView(telaSobre);

        root.addView(barraAbas());
        setContentView(root);

        Notificador.criarCanal(this);
        pedirPermissao();
        CheckJob.agendar(this);
        renderAlertas();
        renderCustom();
        selecionar(0);
    }

    private View barraSuperior() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setBackgroundColor(PRIMARIA);
        h.setPadding(dp(18), dp(30), dp(18), dp(18));

        ImageView marca = new ImageView(this);
        marca.setImageResource(R.drawable.logo);
        LinearLayout.LayoutParams lpM = new LinearLayout.LayoutParams(dp(42), dp(42));
        lpM.rightMargin = dp(13);
        marca.setLayoutParams(lpM);
        h.addView(marca);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(tv("Concurso Alerta", 21, true, Color.WHITE));
        tvSub = tv("", 13, false, 0xCCFFFFFF);
        txt.addView(tvSub);
        h.addView(txt);
        return h;
    }

    private View barraAbas() {
        LinearLayout barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setBackgroundColor(CARTAO);
        barra.setElevation(dp(8));
        String[] ic = {"🔎", "🔔", "ℹ"};
        String[] nm = {"Buscar", "Alertas", "Sobre"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout aba = new LinearLayout(this);
            aba.setOrientation(LinearLayout.VERTICAL);
            aba.setGravity(Gravity.CENTER);
            aba.setPadding(0, dp(9), 0, dp(10));
            aba.setClickable(true);
            aba.setOnClickListener(v -> selecionar(idx));
            abaIcone[i] = tv(ic[i], 17, false, SUAVE);
            abaIcone[i].setGravity(Gravity.CENTER);
            abaRotulo[i] = tv(nm[i], 11, true, SUAVE);
            abaRotulo[i].setGravity(Gravity.CENTER);
            aba.addView(abaIcone[i]);
            aba.addView(abaRotulo[i]);
            barra.addView(aba, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        return barra;
    }

    private void selecionar(int i) {
        telaBuscar.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        telaAlertas.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        telaSobre.setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        for (int k = 0; k < 3; k++) {
            int cor = (k == i) ? PRIMARIA : SUAVE;
            abaIcone[k].setTextColor(cor);
            abaRotulo[k].setTextColor(cor);
        }
        String[] subs = {
                "Encontre concursos pelo cargo desejado",
                "Notificações de novos editais",
                "Informações do aplicativo"};
        tvSub.setText(subs[i]);
        if (i == 1) renderAlertas();
    }

    private ScrollView rolagem(LinearLayout dentro) {
        ScrollView s = new ScrollView(this);
        dentro.setPadding(0, 0, 0, dp(24));
        s.addView(dentro);
        return s;
    }

    // ---------------------------------------------------------------- aba Buscar
    private View montarBuscar() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c = cartao();
        c.addView(tv("Cargo pretendido", 13, true, TEXTO));
        etCargo = new EditText(this);
        etCargo.setHint("ex.: auxiliar administrativo");
        etCargo.setSingleLine(true);
        etCargo.setTextSize(16);
        etCargo.setTextColor(TEXTO);
        etCargo.setHintTextColor(0xFF94A3B8);
        etCargo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etCargo.setBackground(forma(0xFFF8FAFC, 12, BORDA));
        etCargo.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpE.topMargin = dp(7);
        etCargo.setLayoutParams(lpE);
        c.addView(etCargo);

        TextView lblUf = tv("Abrangência", 13, true, TEXTO);
        lblUf.setPadding(0, dp(16), 0, dp(7));
        c.addView(lblUf);
        LinearLayout caixaUf = new LinearLayout(this);
        caixaUf.setBackground(forma(0xFFF8FAFC, 12, BORDA));
        caixaUf.setPadding(dp(6), dp(2), dp(6), dp(2));
        spUf = new Spinner(this);
        String[] itens = new String[Util.UFS.length + 1];
        itens[0] = "Todo o Brasil";
        for (int i = 0; i < Util.UFS.length; i++) itens[i + 1] = Util.UFS[i] + "  -  " + Util.NOMES[i];
        spUf.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, itens));
        caixaUf.addView(spUf, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        c.addView(caixaUf);

        cbSim = new CheckBox(this);
        cbSim.setText("Incluir cargos similares");
        cbSim.setTextColor(TEXTO);
        cbSim.setChecked(true);
        c.addView(cbSim);

        cbSemCargo = new CheckBox(this);
        cbSemCargo.setText("Exibir também editais sem relação de cargos");
        cbSemCargo.setTextColor(SUAVE);
        cbSemCargo.setTextSize(13);
        c.addView(cbSemCargo);

        c.addView(botao("Buscar editais", PRIMARIA, Color.WHITE, false, v -> buscar()));
        c.addView(botao("Criar alerta para este cargo", 0x00FFFFFF, PRIMARIA, true, v -> cadastrar()));
        col.addView(c);

        tvStatus = tv("", 13, false, SUAVE);
        tvStatus.setPadding(dp(20), dp(16), dp(20), 0);
        col.addView(tvStatus);

        llRes = new LinearLayout(this);
        llRes.setOrientation(LinearLayout.VERTICAL);
        col.addView(llRes);

        return rolagem(col);
    }

    private String ufSelecionada() {
        int p = spUf.getSelectedItemPosition();
        return p <= 0 ? "" : Util.UFS[p - 1];
    }

    private void buscar() {
        if (buscando) return;
        final String cargo = etCargo.getText().toString().trim();
        if (cargo.isEmpty()) {
            Toast.makeText(this, "Informe o cargo pretendido.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String uf = ufSelecionada();
        final boolean sim = cbSim.isChecked();
        final boolean incerto = cbSemCargo.isChecked();
        buscando = true;
        tvStatus.setText("Consultando as fontes\u2026");
        llRes.removeAllViews();
        bg.execute(() -> {
            Fontes.Coleta col = Fontes.coletar(this, false);
            final Fontes.Resultado r = Fontes.buscar(this, col, cargo, uf, sim);
            ui.post(() -> {
                buscando = false;
                mostrar(r, incerto);
            });
        });
    }

    private void mostrar(Fontes.Resultado r, boolean incluirIncertos) {
        llRes.removeAllViews();
        StringBuilder s = new StringBuilder();
        s.append(r.achados.size()).append(r.achados.size() == 1 ? " edital encontrado" : " editais encontrados");
        if (r.fontesComFalha > 0) {
            s.append("   \u00b7   ").append(r.fontesTotal).append(" fontes consultadas, ")
                    .append(r.fontesComFalha).append(" indisponíveis");
        }
        if (r.achados.isEmpty()) s.append(".\nTente um termo mais abrangente, como \"administrativo\".");
        tvStatus.setText(s.toString());

        int n = 0;
        for (Edital e : r.achados) {
            llRes.addView(cartaoEdital(e));
            if (++n >= 60) break;
        }
        if (incluirIncertos && !r.semCargo.isEmpty()) {
            llRes.addView(titulo("Sem relação de cargos (" + r.semCargo.size() + ")"));
            TextView aviso = tv("Estes editais não informam os cargos na listagem. Abra cada um para verificar.",
                    12, false, SUAVE);
            aviso.setPadding(dp(18), 0, dp(18), dp(4));
            llRes.addView(aviso);
            n = 0;
            for (Edital e : r.semCargo) {
                llRes.addView(cartaoEdital(e));
                if (++n >= 40) break;
            }
        } else if (!r.semCargo.isEmpty()) {
            TextView dica = tv("Outros " + r.semCargo.size()
                    + " editais não informam os cargos. Marque a opção acima para exibi-los.",
                    12, false, AMBAR);
            dica.setPadding(dp(20), dp(12), dp(20), 0);
            llRes.addView(dica);
        }
    }

    private View cartaoEdital(final Edital e) {
        LinearLayout c = cartao();

        LinearLayout selos = new LinearLayout(this);
        selos.setOrientation(LinearLayout.HORIZONTAL);
        if (!e.uf.isEmpty()) selos.addView(selo(e.uf.equals("BR") ? "NACIONAL" : e.uf, PRIMARIA));
        if (e.tipo == Edital.SIMILAR) selos.addView(selo("CARGO SIMILAR", AMBAR));
        if (e.tipo == Edital.INCERTO) selos.addView(selo("A VERIFICAR", SUAVE));
        if (selos.getChildCount() > 0) c.addView(selos);

        TextView t = tv(e.titulo, 16, true, TEXTO);
        t.setPadding(0, selos.getChildCount() > 0 ? dp(8) : 0, 0, 0);
        c.addView(t);

        if (!e.info.isEmpty()) {
            TextView i = tv(e.info, 14, true, VERDE);
            i.setPadding(0, dp(6), 0, 0);
            c.addView(i);
        }
        if (!e.cargos.isEmpty()) {
            TextView g = tv(e.cargos, 13, false, TEXTO);
            g.setPadding(0, dp(6), 0, 0);
            c.addView(g);
        }
        StringBuilder meta = new StringBuilder();
        if (!e.nivel.isEmpty()) meta.append(e.nivel);
        if (!e.data.isEmpty()) {
            if (meta.length() > 0) meta.append("   |   ");
            meta.append("até ").append(e.data);
        }
        if (meta.length() > 0) {
            TextView m = tv(meta.toString(), 12, false, SUAVE);
            m.setPadding(0, dp(8), 0, 0);
            c.addView(m);
        }
        TextView f = tv(e.fonte + "   \u00b7   Abrir publicação", 11, true, PRIMARIA);
        f.setPadding(0, dp(10), 0, 0);
        c.addView(f);

        c.setClickable(true);
        c.setOnClickListener(v -> abrir(e.url));
        return c;
    }

    // ---------------------------------------------------------------- aba Alertas
    private View montarAlertas() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c1 = cartao();
        c1.addView(tv("Meus alertas", 17, true, TEXTO));
        c1.addView(tv("O aplicativo consulta as fontes periodicamente e notifica, com som, "
                + "quando houver novo edital para o cargo cadastrado.", 13, false, SUAVE));
        llAlertas = new LinearLayout(this);
        llAlertas.setOrientation(LinearLayout.VERTICAL);
        llAlertas.setPadding(0, dp(12), 0, 0);
        c1.addView(llAlertas);
        col.addView(c1);

        LinearLayout c2 = cartao();
        c2.addView(tv("Verificação", 17, true, TEXTO));
        c2.addView(botao("Testar notificação", 0x00FFFFFF, PRIMARIA, true, v -> {
            if (!Notificador.temPermissao(this)) {
                Toast.makeText(this, "Permita as notificações para este aplicativo.", Toast.LENGTH_LONG).show();
                pedirPermissao();
                return;
            }
            Notificador.teste(this);
        }));
        c2.addView(botao("Verificar agora", 0x00FFFFFF, PRIMARIA, true, v -> verificarAgora()));
        c2.addView(botao("Otimização de bateria", 0x00FFFFFF, PRIMARIA, true, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Abra Configurações, Bateria e libere este aplicativo.",
                        Toast.LENGTH_LONG).show();
            }
        }));
        c2.addView(tv("Alguns aparelhos restringem a execução em segundo plano e podem atrasar "
                + "o aviso. Liberar o aplicativo evita esse atraso.", 12, false, SUAVE));
        col.addView(c2);

        LinearLayout c3 = cartao();
        c3.addView(tv("Portais de órgãos", 17, true, TEXTO));
        c3.addView(tv("Informe o endereço da página de concursos de um órgão para incluí-lo "
                + "no monitoramento.", 13, false, SUAVE));
        etUrl = new EditText(this);
        etUrl.setHint("https://www.prefeitura.exemplo.gov.br/concursos");
        etUrl.setSingleLine(true);
        etUrl.setTextSize(13);
        etUrl.setTextColor(TEXTO);
        etUrl.setHintTextColor(0xFF94A3B8);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        etUrl.setBackground(forma(0xFFF8FAFC, 12, BORDA));
        etUrl.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpU = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpU.topMargin = dp(10);
        etUrl.setLayoutParams(lpU);
        c3.addView(etUrl);
        c3.addView(botao("Adicionar portal", 0x00FFFFFF, PRIMARIA, true, v -> adicionarPortal()));
        llCustom = new LinearLayout(this);
        llCustom.setOrientation(LinearLayout.VERTICAL);
        c3.addView(llCustom);
        col.addView(c3);

        return rolagem(col);
    }

    private void renderAlertas() {
        if (llAlertas == null) return;
        llAlertas.removeAllViews();
        List<Store.Alerta> l = Store.alertas(this);
        if (l.isEmpty()) {
            llAlertas.addView(tv("Nenhum alerta cadastrado. Na aba Buscar, informe um cargo e toque "
                    + "em \"Criar alerta para este cargo\".", 13, false, SUAVE));
            return;
        }
        for (final Store.Alerta a : l) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackground(forma(0xFFF8FAFC, 12, BORDA));
            r.setPadding(dp(12), dp(10), dp(8), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            r.setLayoutParams(lp);

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.addView(tv(a.cargo, 15, true, TEXTO));
            txt.addView(tv((a.uf.isEmpty() ? "Todo o Brasil" : a.uf)
                    + (a.similares ? "   \u00b7   cargos similares" : ""), 12, false, SUAVE));
            r.addView(txt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView x = tv("Remover", 12, true, 0xFFB91C1C);
            x.setPadding(dp(10), dp(8), dp(10), dp(8));
            x.setClickable(true);
            x.setOnClickListener(v -> {
                Store.remover(this, a.id);
                renderAlertas();
            });
            r.addView(x);
            llAlertas.addView(r);
        }
    }

    private void cadastrar() {
        String cargo = etCargo.getText().toString().trim();
        if (cargo.isEmpty()) {
            Toast.makeText(this, "Informe o cargo pretendido.", Toast.LENGTH_SHORT).show();
            return;
        }
        Store.adicionar(this, cargo, ufSelecionada(), cbSim.isChecked());
        renderAlertas();
        CheckJob.agendar(this);
        pedirPermissao();
        Toast.makeText(this, "Alerta criado. Você será notificado quando houver novo edital.",
                Toast.LENGTH_LONG).show();
        bg.execute(() -> {
            try { Verificador.rodar(this); } catch (Exception ignored) { }
        });
    }

    private void verificarAgora() {
        Toast.makeText(this, "Verificando\u2026", Toast.LENGTH_SHORT).show();
        bg.execute(() -> {
            String msg;
            try {
                int n = Verificador.rodar(this);
                msg = n > 0 ? n + (n == 1 ? " novo edital encontrado." : " novos editais encontrados.")
                        : "Nenhum edital novo no momento.";
            } catch (Exception e) {
                msg = "Falha na verificação: " + e.getMessage();
            }
            final String m = msg;
            ui.post(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
        });
    }

    private void adicionarPortal() {
        String u = etUrl.getText().toString().trim();
        if (!u.startsWith("http")) u = "https://" + u;
        if (u.length() < 12 || !u.contains(".")) {
            Toast.makeText(this, "Endereço inválido.", Toast.LENGTH_SHORT).show();
            return;
        }
        Store.adicionarCustom(this, u);
        etUrl.setText("");
        renderCustom();
    }

    private void renderCustom() {
        if (llCustom == null) return;
        llCustom.removeAllViews();
        for (final String u : Store.custom(this)) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(8), 0, dp(8));
            r.addView(tv(u, 12, false, TEXTO), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = tv("Remover", 12, true, 0xFFB91C1C);
            x.setPadding(dp(10), dp(6), dp(4), dp(6));
            x.setClickable(true);
            x.setOnClickListener(v -> {
                Store.removerCustom(this, u);
                renderCustom();
            });
            r.addView(x);
            llCustom.addView(r);
        }
    }

    // ---------------------------------------------------------------- aba Sobre
    private View montarSobre() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        String versao = "1.1";
        try {
            versao = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }

        LinearLayout c1 = cartao();
        c1.addView(tv("Concurso Alerta", 20, true, PRIMARIA_E));
        c1.addView(tv("Versão " + versao, 13, false, SUAVE));
        TextView d = tv("Localiza concursos públicos pelo cargo desejado e notifica, com som, "
                + "a publicação de novos editais.", 14, false, TEXTO);
        d.setPadding(0, dp(10), 0, 0);
        c1.addView(d);
        col.addView(c1);

        LinearLayout c2 = cartao();
        c2.addView(tv("Desenvolvedor", 17, true, TEXTO));
        c2.addView(linha("Willian Gonçalves Ribeiro", "Perfil no LinkedIn",
                () -> abrir("https://www.linkedin.com/in/willianribeiro1090")));
        c2.addView(linha("williangr90@yahoo.com.br", "Enviar e-mail",
                () -> abrir("mailto:williangr90@yahoo.com.br")));
        c2.addView(linha("(31) 98434-0468", "Conversar no WhatsApp",
                () -> abrir("https://wa.me/5531984340468")));
        col.addView(c2);

        LinearLayout c3 = cartao();
        c3.addView(tv("Fontes consultadas", 17, true, TEXTO));
        c3.addView(tv("PCI Concursos, Estratégia Concursos, Gran Cursos, Folha Dirigida e "
                + "Concursos no Brasil; as bancas organizadoras Vunesp, FGV, FCC, IBFC e "
                + "Cesgranrio; o Diário Oficial da União; e os portais de órgãos "
                + "adicionados pelo usuário.", 13, false, SUAVE));
        TextView aviso = tv("O aplicativo reúne links de publicações públicas. A inscrição deve ser "
                + "feita a partir do edital oficial, no site da banca organizadora ou do "
                + "órgão responsável.", 12, false, AMBAR);
        aviso.setPadding(0, dp(10), 0, 0);
        c3.addView(aviso);
        col.addView(c3);

        return rolagem(col);
    }

    private View linha(String principal, String acao, final Runnable r) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(forma(0xFFF8FAFC, 12, BORDA));
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        l.setLayoutParams(lp);
        l.addView(tv(principal, 15, true, TEXTO));
        l.addView(tv(acao, 12, true, PRIMARIA));
        l.setClickable(true);
        l.setOnClickListener(v -> r.run());
        return l;
    }

    // ---------------------------------------------------------------- permissao
    private void pedirPermissao() {
        if (Build.VERSION.SDK_INT >= 33 && !Notificador.temPermissao(this)) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }
}
