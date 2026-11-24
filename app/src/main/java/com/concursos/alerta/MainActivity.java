package com.concursos.alerta;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ---------------------------------------------------------------- tema
    private boolean noite;
    private int ACENTO, BOTAO, BARRA, FUNDO, CARTAO, TEXTO, SUAVE, BORDA, CAMPO,
            VERDE, AMBAR, VERMELHO;

    /** true quando o aplicativo deve usar o tema escuro, respeitando a preferência do usuário. */
    private boolean escuroAtivo() {
        int m;
        try { m = Store.tema(this); } catch (Exception e) { m = 0; }
        if (m == 1) return false;
        if (m == 2) return true;
        try {
            return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        } catch (Exception e) {
            return false;
        }
    }

    private void aplicarTema() {
        noite = escuroAtivo();
        if (noite) {
            ACENTO = 0xFF7DA7FF; BOTAO = 0xFF2563EB; BARRA = 0xFF11204A;
            FUNDO = 0xFF0B1220; CARTAO = 0xFF162039; TEXTO = 0xFFE2E8F0;
            SUAVE = 0xFF94A3B8; BORDA = 0xFF24324F; CAMPO = 0xFF101A2E;
            VERDE = 0xFF34D399; AMBAR = 0xFFFBBF24; VERMELHO = 0xFFF87171;
        } else {
            ACENTO = 0xFF1D4ED8; BOTAO = 0xFF1D4ED8; BARRA = 0xFF1D4ED8;
            FUNDO = 0xFFF1F5F9; CARTAO = 0xFFFFFFFF; TEXTO = 0xFF0F172A;
            SUAVE = 0xFF64748B; BORDA = 0xFFE2E8F0; CAMPO = 0xFFF8FAFC;
            VERDE = 0xFF047857; AMBAR = 0xFFB45309; VERMELHO = 0xFFB91C1C;
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private TextView tvSub;
    private View telaBuscar, telaSalvos, telaAlertas, telaAjustes, telaSobre;
    private final TextView[] abaIcone = new TextView[5];
    private final TextView[] abaRotulo = new TextView[5];

    private EditText etCargo, etUrl;
    private Spinner spUf, spOrdem, spNivel, spSalario;
    private CheckBox cbSim, cbSemCargo;
    private TextView tvStatus, tvOffline;
    private LinearLayout llRes, llFiltros, llAlertas, llCustom, llSalvos;
    private boolean buscando = false;
    private TextView btBuscar;
    private Fontes.Resultado ultimo = null;

    // ---------------------------------------------------------------- utilidades
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

    /** Vibração curta ao tocar, como a de envio de mensagem. Respeita o ajuste do usuário. */
    private void toque(View v) {
        try {
            if (!Store.vibrar(this)) return;
        } catch (Exception e) {
            return;
        }
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 29) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                } else if (Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createOneShot(16, 90));
                } else {
                    vib.vibrate(16);
                }
                return;
            }
        } catch (Exception ignored) { }
        try {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception ignored) { }
    }

    /** Envolve o fundo num efeito de onda, para o toque ficar visível. */
    private Drawable comOnda(Drawable base, int cor) {
        try {
            return new RippleDrawable(ColorStateList.valueOf(cor), base, null);
        } catch (Exception e) {
            return base;
        }
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

    private TextView botao(String texto, int fundo, int corTexto, boolean contorno,
                           View.OnClickListener l) {
        TextView b = tv(texto, 15, true, corTexto);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(comOnda(forma(fundo, 12, contorno ? ACENTO : 0),
                contorno ? ((ACENTO & 0x00FFFFFF) | 0x44000000) : 0x55FFFFFF));
        b.setClickable(true);
        b.setOnClickListener(v -> {
            toque(v);
            l.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView selo(String texto, int cor) {
        TextView t = tv(texto, 11, true, cor);
        t.setPadding(dp(8), dp(3), dp(8), dp(3));
        t.setBackground(forma((cor & 0x00FFFFFF) | (noite ? 0x33000000 : 0x1A000000), 7, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        lp.topMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView rotulo(String s) {
        TextView t = tv(s, 12, true, SUAVE);
        t.setPadding(0, dp(12), 0, dp(5));
        return t;
    }

    private Spinner seletor(String[] itens, final Runnable aoMudar) {
        LinearLayout caixa = new LinearLayout(this);
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, itens));
        sp.setBackground(forma(CAMPO, 12, BORDA));
        sp.setPadding(dp(10), dp(6), dp(10), dp(6));
        if (aoMudar != null) {
            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                boolean primeira = true;
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    if (primeira) { primeira = false; return; }
                    aoMudar.run();
                }
                @Override public void onNothingSelected(AdapterView<?> p) { }
            });
        }
        caixa.addView(sp);
        return sp;
    }

    private void abrir(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void compartilhar(Edital e) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, e.titulo);
            i.putExtra(Intent.EXTRA_TEXT, e.paraCompartilhar());
            startActivity(Intent.createChooser(i, "Compartilhar edital"));
        } catch (Exception ex) {
            Toast.makeText(this, "Não foi possível compartilhar.", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- ciclo
    @Override
    protected void onCreate(Bundle b) {
        setTheme(escuroAtivo() ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        aplicarTema();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(FUNDO);
        root.addView(barraSuperior());

        FrameLayout conteudo = new FrameLayout(this);
        root.addView(conteudo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        telaBuscar = montarBuscar();
        telaSalvos = montarSalvos();
        telaAlertas = montarAlertas();
        telaAjustes = montarAjustes();
        telaSobre = montarSobre();
        conteudo.addView(telaBuscar);
        conteudo.addView(telaSalvos);
        conteudo.addView(telaAlertas);
        conteudo.addView(telaAjustes);
        conteudo.addView(telaSobre);

        root.addView(barraAbas());
        setContentView(root);
        try { getWindow().setStatusBarColor(BARRA); } catch (Exception ignored) { }

        Notificador.criarCanais(this);
        pedirPermissao();
        CheckJob.agendar(this);
        renderAlertas();
        renderCustom();
        renderSalvos();
        selecionar(0);
    }

    private View barraSuperior() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setBackgroundColor(BARRA);
        h.setPadding(dp(18), dp(30), dp(18), dp(18));

        ImageView marca = new ImageView(this);
        marca.setImageResource(R.drawable.logo);
        marca.setContentDescription("Concurso Alerta");
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
        String[] ic = {"🔎", "🔖", "🔔", "⚙", "ℹ"};
        String[] nm = {"Buscar", "Salvos", "Alertas", "Ajustes", "Sobre"};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout aba = new LinearLayout(this);
            aba.setOrientation(LinearLayout.VERTICAL);
            aba.setGravity(Gravity.CENTER);
            aba.setPadding(0, dp(9), 0, dp(10));
            aba.setClickable(true);
            aba.setBackground(comOnda(forma(0x00FFFFFF, 0, 0),
                    (ACENTO & 0x00FFFFFF) | 0x33000000));
            aba.setOnClickListener(v -> {
                toque(v);
                selecionar(idx);
            });
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
        View[] telas = {telaBuscar, telaSalvos, telaAlertas, telaAjustes, telaSobre};
        for (int k = 0; k < telas.length; k++) {
            telas[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            int cor = (k == i) ? ACENTO : SUAVE;
            abaIcone[k].setTextColor(cor);
            abaRotulo[k].setTextColor(cor);
        }
        String[] subs = {
                "Encontre concursos pelo cargo desejado",
                "Editais guardados para acompanhar",
                "Notificações de novos editais",
                "Preferências do aplicativo",
                "Informações do aplicativo"};
        tvSub.setText(subs[i]);
        if (i == 1) renderSalvos();
        if (i == 2) renderAlertas();
    }

    private ScrollView rolagem(LinearLayout dentro) {
        ScrollView s = new ScrollView(this);
        dentro.setPadding(0, 0, 0, dp(26));
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
        etCargo.setHintTextColor(SUAVE);
        etCargo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etCargo.setBackground(forma(CAMPO, 12, BORDA));
        etCargo.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpE.topMargin = dp(7);
        etCargo.setLayoutParams(lpE);
        c.addView(etCargo);

        c.addView(rotulo("ABRANGÊNCIA"));
        String[] ufs = new String[Util.UFS.length + 1];
        ufs[0] = "Todo o Brasil";
        for (int i = 0; i < Util.UFS.length; i++) ufs[i + 1] = Util.UFS[i] + "  -  " + Util.NOMES[i];
        spUf = seletor(ufs, null);
        c.addView(spUf);

        cbSim = new CheckBox(this);
        cbSim.setText("Incluir cargos similares");
        cbSim.setTextColor(TEXTO);
        cbSim.setChecked(true);
        c.addView(cbSim);

        cbSemCargo = new CheckBox(this);
        cbSemCargo.setText("Exibir também editais sem relação de cargos");
        cbSemCargo.setTextColor(SUAVE);
        cbSemCargo.setTextSize(13);
        cbSemCargo.setOnCheckedChangeListener((bt, v) -> { if (ultimo != null) renderResultados(); });
        c.addView(cbSemCargo);

        btBuscar = botao("Buscar editais", BOTAO, Color.WHITE, false, v -> buscar());
        c.addView(btBuscar);
        c.addView(botao("Criar alerta para este cargo", 0x00FFFFFF, ACENTO, true, v -> cadastrar()));
        col.addView(c);

        llFiltros = cartao();
        llFiltros.setVisibility(View.GONE);
        llFiltros.addView(tv("Refinar resultados", 15, true, TEXTO));
        llFiltros.addView(rotulo("ORDENAR POR"));
        spOrdem = seletor(new String[]{"Mais relevantes", "Prazo mais próximo",
                "Maior salário", "Mais vagas"}, this::renderResultados);
        llFiltros.addView(spOrdem);
        llFiltros.addView(rotulo("ESCOLARIDADE"));
        spNivel = seletor(new String[]{"Qualquer escolaridade", "Fundamental", "Médio",
                "Técnico", "Superior"}, this::renderResultados);
        llFiltros.addView(spNivel);
        llFiltros.addView(rotulo("SALÁRIO MÍNIMO"));
        spSalario = seletor(new String[]{"Qualquer salário", "A partir de R$ 2.000",
                "A partir de R$ 3.000", "A partir de R$ 5.000", "A partir de R$ 8.000"},
                this::renderResultados);
        llFiltros.addView(spSalario);
        col.addView(llFiltros);

        tvOffline = tv("", 12, true, AMBAR);
        tvOffline.setPadding(dp(20), dp(14), dp(20), 0);
        tvOffline.setVisibility(View.GONE);
        col.addView(tvOffline);

        tvStatus = tv("", 13, false, SUAVE);
        tvStatus.setPadding(dp(20), dp(14), dp(20), 0);
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
        buscando = true;
        btBuscar.setText("Buscando…");
        btBuscar.setAlpha(0.65f);
        tvStatus.setText("Consultando as fontes…");
        tvOffline.setVisibility(View.GONE);
        llRes.removeAllViews();
        bg.execute(() -> {
            Fontes.Coleta col = Fontes.coletar(this, false);
            final Fontes.Resultado r = Fontes.buscar(this, col, cargo, uf, sim);
            ui.post(() -> {
                buscando = false;
                btBuscar.setText("Buscar editais");
                btBuscar.setAlpha(1f);
                ultimo = r;
                llFiltros.setVisibility(View.VISIBLE);
                renderResultados();
            });
        });
    }

    // ---------------------------------------------------------- filtros e ordem
    private int nivelSelecionado() {
        switch (spNivel.getSelectedItemPosition()) {
            case 1: return Edital.FUNDAMENTAL;
            case 2: return Edital.MEDIO;
            case 3: return Edital.TECNICO;
            case 4: return Edital.SUPERIOR;
            default: return 0;
        }
    }

    private double salarioMinimo() {
        switch (spSalario.getSelectedItemPosition()) {
            case 1: return 2000;
            case 2: return 3000;
            case 3: return 5000;
            case 4: return 8000;
            default: return 0;
        }
    }

    private List<Edital> refinar(List<Edital> base) {
        int nivel = nivelSelecionado();
        double sal = salarioMinimo();
        List<Edital> out = new ArrayList<>();
        for (Edital e : base) {
            if (nivel != 0 && (e.niveis & nivel) == 0) continue;
            if (sal > 0 && e.salario < sal) continue;
            out.add(e);
        }
        final int ordem = spOrdem.getSelectedItemPosition();
        if (ordem == 1) {
            Collections.sort(out, new Comparator<Edital>() {
                public int compare(Edital a, Edital b) {
                    long pa = a.prazo == 0 ? Long.MAX_VALUE : a.prazo;
                    long pb = b.prazo == 0 ? Long.MAX_VALUE : b.prazo;
                    return Long.compare(pa, pb);
                }
            });
        } else if (ordem == 2) {
            Collections.sort(out, (a, b) -> Double.compare(b.salario, a.salario));
        } else if (ordem == 3) {
            Collections.sort(out, (a, b) -> Integer.compare(b.vagas, a.vagas));
        }
        return out;
    }

    private void renderResultados() {
        if (ultimo == null) return;
        llRes.removeAllViews();
        List<Edital> achados = refinar(ultimo.achados);

        if (ultimo.offline) {
            tvOffline.setText("Sem conexão no momento. Exibindo a última consulta, de "
                    + Cache.idade(ultimo.quando) + ".");
            tvOffline.setVisibility(View.VISIBLE);
        } else {
            tvOffline.setVisibility(View.GONE);
        }

        StringBuilder s = new StringBuilder();
        s.append(achados.size()).append(achados.size() == 1 ? " edital" : " editais");
        if (achados.size() != ultimo.achados.size()) {
            s.append(" de ").append(ultimo.achados.size()).append(" (filtrados)");
        }
        if (!ultimo.offline && ultimo.fontesComFalha > 0) {
            s.append("   ·   ").append(ultimo.fontesTotal).append(" fontes consultadas, ")
                    .append(ultimo.fontesComFalha).append(" indisponíveis");
        }
        if (achados.isEmpty()) {
            s.append(ultimo.achados.isEmpty()
                    ? ".\nTente um termo mais abrangente, como \"administrativo\"."
                    : ".\nNenhum edital atende aos filtros escolhidos.");
        }
        tvStatus.setText(s.toString());

        int n = 0;
        for (Edital e : achados) {
            llRes.addView(cartaoEdital(e, false));
            if (++n >= 60) break;
        }

        List<Edital> incertos = refinar(ultimo.semCargo);
        if (cbSemCargo.isChecked() && !incertos.isEmpty()) {
            TextView t = tv("Sem relação de cargos (" + incertos.size() + ")", 13, true, SUAVE);
            t.setAllCaps(true);
            t.setLetterSpacing(0.06f);
            t.setPadding(dp(18), dp(22), dp(18), dp(2));
            llRes.addView(t);
            TextView aviso = tv("Estes editais não informam os cargos na listagem. "
                    + "Abra cada um para verificar.", 12, false, SUAVE);
            aviso.setPadding(dp(18), 0, dp(18), dp(4));
            llRes.addView(aviso);
            n = 0;
            for (Edital e : incertos) {
                llRes.addView(cartaoEdital(e, false));
                if (++n >= 40) break;
            }
        } else if (!incertos.isEmpty()) {
            TextView dica = tv("Outros " + incertos.size() + " editais não informam os cargos. "
                    + "Marque a opção acima para exibi-los.", 12, false, AMBAR);
            dica.setPadding(dp(20), dp(14), dp(20), 0);
            llRes.addView(dica);
        }
    }

    // ---------------------------------------------------------- cartão de edital
    private View cartaoEdital(final Edital e, final boolean naAbaSalvos) {
        final boolean lido = Store.estaLido(this, e.url);
        LinearLayout c = cartao();

        LinearLayout selos = new LinearLayout(this);
        selos.setOrientation(LinearLayout.HORIZONTAL);
        if (!e.uf.isEmpty()) selos.addView(selo(e.uf.equals("BR") ? "NACIONAL" : e.uf, ACENTO));
        int d = e.diasRestantes();
        if (d != Integer.MAX_VALUE && d <= 7) {
            selos.addView(selo(d <= 0 ? "ENCERRA HOJE"
                    : (d == 1 ? "ENCERRA AMANHÃ" : "FALTAM " + d + " DIAS"), VERMELHO));
        }
        if (e.tipo == Edital.SIMILAR) selos.addView(selo("CARGO SIMILAR", AMBAR));
        if (e.tipo == Edital.INCERTO) selos.addView(selo("A VERIFICAR", SUAVE));
        if (lido) selos.addView(selo("LIDO", SUAVE));
        if (selos.getChildCount() > 0) c.addView(selos);

        TextView t = tv(e.titulo, 16, !lido, lido ? SUAVE : TEXTO);
        t.setPadding(0, selos.getChildCount() > 0 ? dp(8) : 0, 0, 0);
        c.addView(t);

        if (!e.info.isEmpty()) {
            TextView i = tv(e.info, 14, true, VERDE);
            i.setPadding(0, dp(6), 0, 0);
            c.addView(i);
        }
        if (!e.cargos.isEmpty()) {
            TextView g = tv(e.cargos, 13, false, lido ? SUAVE : TEXTO);
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

        TextView f = tv(e.fonte + "   ·   Abrir publicação", 11, true, ACENTO);
        f.setPadding(0, dp(10), 0, 0);
        f.setClickable(true);
        f.setOnClickListener(v -> {
            Store.marcarLido(this, e.url);
            abrir(e.url);
        });
        c.addView(f);

        LinearLayout acoes = new LinearLayout(this);
        acoes.setOrientation(LinearLayout.HORIZONTAL);
        acoes.setPadding(0, dp(10), 0, 0);
        final boolean salvo = Store.estaSalvo(this, e.url);
        TextView bSalvar = tv(naAbaSalvos ? "Remover" : (salvo ? "Salvo" : "Salvar"), 13, true,
                naAbaSalvos ? VERMELHO : (salvo ? VERDE : ACENTO));
        bSalvar.setPadding(0, dp(6), dp(22), dp(6));
        bSalvar.setClickable(true);
        bSalvar.setOnClickListener(v -> {
            if (naAbaSalvos || salvo) {
                Store.removerSalvo(this, e.url);
                Toast.makeText(this, "Removido dos salvos.", Toast.LENGTH_SHORT).show();
            } else {
                Store.salvar(this, e);
                Toast.makeText(this, "Salvo. Você será avisado quando o prazo estiver no fim.",
                        Toast.LENGTH_LONG).show();
            }
            renderSalvos();
            if (!naAbaSalvos && ultimo != null) renderResultados();
        });
        acoes.addView(bSalvar);

        TextView bShare = tv("Compartilhar", 13, true, ACENTO);
        bShare.setPadding(0, dp(6), dp(10), dp(6));
        bShare.setClickable(true);
        bShare.setOnClickListener(v -> compartilhar(e));
        acoes.addView(bShare);
        c.addView(acoes);

        c.setBackground(comOnda(forma(CARTAO, 16, BORDA),
                (ACENTO & 0x00FFFFFF) | 0x22000000));
        c.setClickable(true);
        c.setOnClickListener(v -> {
            toque(v);
            Store.marcarLido(this, e.url);
            abrir(e.url);
        });
        return c;
    }

    // ---------------------------------------------------------------- aba Salvos
    private View montarSalvos() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        llSalvos = new LinearLayout(this);
        llSalvos.setOrientation(LinearLayout.VERTICAL);
        col.addView(llSalvos);
        return rolagem(col);
    }

    private void renderSalvos() {
        if (llSalvos == null) return;
        llSalvos.removeAllViews();
        List<Edital> l = Store.salvos(this);
        if (l.isEmpty()) {
            LinearLayout c = cartao();
            c.addView(tv("Nenhum edital salvo", 17, true, TEXTO));
            c.addView(tv("Ao encontrar um edital de interesse, toque em \"Salvar\". "
                    + "Ele fica guardado aqui, disponível mesmo sem internet, e o aplicativo "
                    + "avisa quando o prazo de inscrição estiver no fim.", 13, false, SUAVE));
            llSalvos.addView(c);
            return;
        }
        TextView t = tv(l.size() + (l.size() == 1 ? " edital salvo" : " editais salvos"),
                13, false, SUAVE);
        t.setPadding(dp(20), dp(16), dp(20), 0);
        llSalvos.addView(t);
        for (Edital e : l) llSalvos.addView(cartaoEdital(e, true));
    }

    // ---------------------------------------------------------------- aba Alertas
    private View montarAlertas() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c1 = cartao();
        c1.addView(tv("Meus alertas", 17, true, TEXTO));
        c1.addView(tv("O aplicativo consulta as fontes periodicamente e notifica, com som, "
                + "quando houver novo edital para o cargo cadastrado. Também avisa quando "
                + "o prazo de um edital salvo estiver a até "
                + Verificador.DIAS_AVISO_PRAZO + " dias do fim.", 13, false, SUAVE));
        llAlertas = new LinearLayout(this);
        llAlertas.setOrientation(LinearLayout.VERTICAL);
        llAlertas.setPadding(0, dp(12), 0, 0);
        c1.addView(llAlertas);
        col.addView(c1);

        return rolagem(col);
    }

    /** Linha de escolha única, com marca de selecionado. */
    private View opcao(String texto, boolean marcado, final Runnable r) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        int fundo = marcado ? ((ACENTO & 0x00FFFFFF) | (noite ? 0x33000000 : 0x14000000)) : CAMPO;
        l.setBackground(comOnda(forma(fundo, 12, marcado ? ACENTO : BORDA),
                (ACENTO & 0x00FFFFFF) | 0x33000000));
        l.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(9);
        l.setLayoutParams(lp);
        l.addView(tv(texto, 15, marcado, marcado ? ACENTO : TEXTO),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (marcado) l.addView(tv("✓", 16, true, ACENTO));
        l.setClickable(true);
        l.setOnClickListener(v -> {
            toque(v);
            r.run();
        });
        return l;
    }

    // ---------------------------------------------------------------- aba Ajustes
    private View montarAjustes() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c0 = cartao();
        c0.addView(tv("Aparência", 17, true, TEXTO));
        c0.addView(tv("Escolha como o aplicativo deve ser exibido. A troca é aplicada "
                + "imediatamente.", 13, false, SUAVE));
        String[] temas = {"Seguir o sistema", "Sempre claro", "Sempre escuro"};
        int atual = Store.tema(this);
        for (int i = 0; i < temas.length; i++) {
            final int modo = i;
            c0.addView(opcao(temas[i], atual == modo, () -> {
                if (Store.tema(this) == modo) return;
                Store.definirTema(this, modo);
                recreate();
            }));
        }
        col.addView(c0);

        LinearLayout cv = cartao();
        cv.addView(tv("Resposta ao toque", 17, true, TEXTO));
        CheckBox cbVib = new CheckBox(this);
        cbVib.setText("Vibrar ao tocar nos botões");
        cbVib.setTextColor(TEXTO);
        cbVib.setChecked(Store.vibrar(this));
        cbVib.setOnCheckedChangeListener((bt, v) -> {
            Store.definirVibrar(this, v);
            if (v) toque(bt);
        });
        cv.addView(cbVib);
        cv.addView(tv("Uma vibração curta confirma que o toque foi registrado.",
                12, false, SUAVE));
        col.addView(cv);

        LinearLayout c2 = cartao();
        c2.addView(tv("Verificação", 17, true, TEXTO));
        c2.addView(botao("Testar notificação", 0x00FFFFFF, ACENTO, true, v -> {
            if (!Notificador.temPermissao(this)) {
                Toast.makeText(this, "Permita as notificações para este aplicativo.",
                        Toast.LENGTH_LONG).show();
                pedirPermissao();
                return;
            }
            Notificador.teste(this);
        }));
        c2.addView(botao("Verificar agora", 0x00FFFFFF, ACENTO, true, v -> verificarAgora()));
        c2.addView(botao("Otimização de bateria", 0x00FFFFFF, ACENTO, true, v -> {
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
        etUrl.setHintTextColor(SUAVE);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        etUrl.setBackground(forma(CAMPO, 12, BORDA));
        etUrl.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpU = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpU.topMargin = dp(10);
        etUrl.setLayoutParams(lpU);
        c3.addView(etUrl);
        c3.addView(botao("Adicionar portal", 0x00FFFFFF, ACENTO, true, v -> adicionarPortal()));
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
            llAlertas.addView(tv("Nenhum alerta cadastrado. Na aba Buscar, informe um cargo e "
                    + "toque em \"Criar alerta para este cargo\".", 13, false, SUAVE));
            return;
        }
        for (final Store.Alerta a : l) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackground(forma(CAMPO, 12, BORDA));
            r.setPadding(dp(12), dp(10), dp(8), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            r.setLayoutParams(lp);

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.addView(tv(a.cargo, 15, true, TEXTO));
            txt.addView(tv((a.uf.isEmpty() ? "Todo o Brasil" : a.uf)
                    + (a.similares ? "   ·   cargos similares" : ""), 12, false, SUAVE));
            r.addView(txt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView x = tv("Remover", 12, true, VERMELHO);
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
        Toast.makeText(this, "Verificando…", Toast.LENGTH_SHORT).show();
        bg.execute(() -> {
            String msg;
            try {
                Verificador.Saldo s = Verificador.rodar(this);
                if (s.total() == 0) {
                    msg = "Nenhuma novidade no momento.";
                } else {
                    StringBuilder b = new StringBuilder();
                    if (s.novos > 0) b.append(s.novos).append(s.novos == 1
                            ? " novo edital" : " novos editais");
                    if (s.prazos > 0) {
                        if (b.length() > 0) b.append(" e ");
                        b.append(s.prazos).append(s.prazos == 1
                                ? " prazo encerrando" : " prazos encerrando");
                    }
                    msg = b.append(".").toString();
                }
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
            TextView x = tv("Remover", 12, true, VERMELHO);
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

        String versao = "1.3";
        try {
            versao = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }

        LinearLayout c1 = cartao();
        c1.addView(tv("Concurso Alerta", 20, true, ACENTO));
        c1.addView(tv("Versão " + versao, 13, false, SUAVE));
        TextView d = tv("Localiza concursos públicos pelo cargo desejado e notifica, com som, "
                + "a publicação de novos editais e a proximidade do fim das inscrições.",
                14, false, TEXTO);
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
        TextView aviso = tv("O aplicativo reúne links de publicações públicas. A inscrição deve "
                + "ser feita a partir do edital oficial, no site da banca organizadora ou do "
                + "órgão responsável.", 12, false, AMBAR);
        aviso.setPadding(0, dp(10), 0, 0);
        c3.addView(aviso);
        col.addView(c3);

        return rolagem(col);
    }

    private View linha(String principal, String acao, final Runnable r) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(forma(CAMPO, 12, BORDA));
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        l.setLayoutParams(lp);
        l.addView(tv(principal, 15, true, TEXTO));
        l.addView(tv(acao, 12, true, ACENTO));
        l.setBackground(comOnda(forma(CAMPO, 12, BORDA), (ACENTO & 0x00FFFFFF) | 0x33000000));
        l.setClickable(true);
        l.setOnClickListener(v -> {
            toque(v);
            r.run();
        });
        return l;
    }

    // ---------------------------------------------------------------- permissão
    private void pedirPermissao() {
        if (Build.VERSION.SDK_INT >= 33 && !Notificador.temPermissao(this)) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }
}
