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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText etCargo, etUrl;
    private Spinner spUf;
    private CheckBox cbSim;
    private TextView tvStatus;
    private LinearLayout llRes, llAlertas, llCustom;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView texto(String s, int sp, boolean negrito, int cor) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(cor);
        if (negrito) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button botao(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        sv.addView(root);
        setContentView(sv);

        root.addView(texto("Concurso Alerta", 26, true, Color.rgb(20, 60, 120)));
        root.addView(texto("Busque concursos pelo cargo e receba aviso quando sair edital novo.", 14, false, Color.DKGRAY));

        etCargo = new EditText(this);
        etCargo.setHint("Cargo pretendido (ex.: professor, enfermeiro)");
        etCargo.setSingleLine(true);
        etCargo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(etCargo);

        spUf = new Spinner(this);
        String[] itens = new String[Util.UFS.length + 1];
        itens[0] = "Todo o Brasil";
        for (int i = 0; i < Util.UFS.length; i++) itens[i + 1] = Util.UFS[i] + " — " + Util.NOMES[i];
        spUf.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, itens));
        root.addView(spUf);

        cbSim = new CheckBox(this);
        cbSim.setText("Incluir cargos similares");
        cbSim.setChecked(true);
        root.addView(cbSim);

        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        Button bBuscar = botao("Buscar agora", v -> buscar());
        Button bAlerta = botao("Cadastrar alerta", v -> cadastrar());
        linha.addView(bBuscar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        linha.addView(bAlerta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(linha);

        tvStatus = texto("", 13, false, Color.GRAY);
        root.addView(tvStatus);
        llRes = new LinearLayout(this);
        llRes.setOrientation(LinearLayout.VERTICAL);
        root.addView(llRes);

        root.addView(secao("Meus alertas"));
        llAlertas = new LinearLayout(this);
        llAlertas.setOrientation(LinearLayout.VERTICAL);
        root.addView(llAlertas);
        root.addView(texto("O app confere as fontes a cada ~30 min e avisa só o que for novo.", 12, false, Color.GRAY));

        LinearLayout linha2 = new LinearLayout(this);
        linha2.setOrientation(LinearLayout.HORIZONTAL);
        linha2.addView(botao("Testar notificação", v -> {
            if (!Notificador.temPermissao(this)) {
                Toast.makeText(this, "Permita as notificações para este app.", Toast.LENGTH_LONG).show();
                pedirPermissao();
                return;
            }
            Notificador.teste(this);
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        linha2.addView(botao("Verificar agora", v -> verificarAgora()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(linha2);
        root.addView(botao("Ajustes de bateria (para o aviso não atrasar)", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Abra Configurações > Bateria e libere este app.", Toast.LENGTH_LONG).show();
            }
        }));

        root.addView(secao("Portais de órgãos (fontes extras)"));
        root.addView(texto("Cole o endereço da página de concursos/editais de um órgão para o app monitorar também.", 12, false, Color.GRAY));
        etUrl = new EditText(this);
        etUrl.setHint("https://www.prefeitura.exemplo.gov.br/concursos");
        etUrl.setSingleLine(true);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(etUrl);
        root.addView(botao("Adicionar portal", v -> adicionarPortal()));
        llCustom = new LinearLayout(this);
        llCustom.setOrientation(LinearLayout.VERTICAL);
        root.addView(llCustom);

        Notificador.criarCanal(this);
        pedirPermissao();
        CheckJob.agendar(this);
        renderAlertas();
        renderCustom();
    }

    private View secao(String s) {
        TextView t = texto(s, 18, true, Color.rgb(20, 60, 120));
        t.setPadding(0, dp(24), 0, dp(4));
        return t;
    }

    private void pedirPermissao() {
        if (Build.VERSION.SDK_INT >= 33 && !Notificador.temPermissao(this)) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private String ufSelecionada() {
        int p = spUf.getSelectedItemPosition();
        return p <= 0 ? "" : Util.UFS[p - 1];
    }

    // -------------------------------------------------------------------- busca

    private void buscar() {
        final String cargo = etCargo.getText().toString().trim();
        if (cargo.isEmpty()) {
            Toast.makeText(this, "Digite o cargo pretendido.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String uf = ufSelecionada();
        final boolean sim = cbSim.isChecked();
        tvStatus.setText("Buscando nas fontes…");
        llRes.removeAllViews();
        bg.execute(() -> {
            Fontes.Coleta col = Fontes.coletar(this, false);
            List<Edital> r = Fontes.buscar(this, col, cargo, uf, sim);
            final int falhas = col.falhas.size();
            ui.post(() -> mostrar(r, falhas, col.total + 1));
        });
    }

    private void mostrar(List<Edital> lista, int falhas, int total) {
        llRes.removeAllViews();
        String s = lista.size() + " resultado(s).";
        if (falhas > 0) s += " " + falhas + " de " + total + " fontes não responderam.";
        if (lista.isEmpty()) s += " Tente outra palavra-chave ou cadastre um alerta.";
        tvStatus.setText(s);
        int n = 0;
        for (Edital e : lista) {
            llRes.addView(cartao(e));
            if (++n >= 100) break;
        }
    }

    private View cartao(final Edital e) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setStroke(dp(1), Color.LTGRAY);
        g.setCornerRadius(dp(8));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        c.setLayoutParams(lp);

        c.addView(texto(e.titulo + (e.similar ? "  (cargo similar)" : ""), 15, true, Color.BLACK));
        StringBuilder meta = new StringBuilder();
        if (!e.uf.isEmpty()) meta.append(e.uf.equals("BR") ? "Nacional" : e.uf).append(" · ");
        if (!e.info.isEmpty()) meta.append(e.info).append(" · ");
        if (!e.nivel.isEmpty()) meta.append(e.nivel).append(" · ");
        if (!e.data.isEmpty()) meta.append(e.data).append(" · ");
        if (meta.length() > 0) c.addView(texto(meta.substring(0, meta.length() - 3), 12, false, Color.DKGRAY));
        if (!e.cargos.isEmpty()) c.addView(texto("Cargos: " + e.cargos, 12, false, Color.DKGRAY));
        c.addView(texto("Fonte: " + e.fonte + "  ›  toque para abrir", 11, false, Color.rgb(20, 60, 120)));
        c.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(e.url)));
            } catch (Exception ex) {
                Toast.makeText(this, "Não consegui abrir o link.", Toast.LENGTH_SHORT).show();
            }
        });
        return c;
    }

    // ------------------------------------------------------------------- alertas

    private void cadastrar() {
        String cargo = etCargo.getText().toString().trim();
        if (cargo.isEmpty()) {
            Toast.makeText(this, "Digite o cargo pretendido.", Toast.LENGTH_SHORT).show();
            return;
        }
        Store.adicionar(this, cargo, ufSelecionada(), cbSim.isChecked());
        renderAlertas();
        CheckJob.agendar(this);
        pedirPermissao();
        Toast.makeText(this, "Alerta cadastrado! Você será avisado quando sair edital novo.", Toast.LENGTH_LONG).show();
        // 1ª varredura: registra o que já existe, sem notificar
        bg.execute(() -> {
            try { Verificador.rodar(this); } catch (Exception ignored) { }
        });
    }

    private void renderAlertas() {
        llAlertas.removeAllViews();
        List<Store.Alerta> l = Store.alertas(this);
        if (l.isEmpty()) {
            llAlertas.addView(texto("Nenhum alerta cadastrado.", 13, false, Color.GRAY));
            return;
        }
        for (final Store.Alerta a : l) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            TextView t = texto(a.rotulo(), 14, false, Color.BLACK);
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            r.addView(botao("Remover", v -> {
                Store.remover(this, a.id);
                renderAlertas();
            }));
            llAlertas.addView(r);
        }
    }

    private void verificarAgora() {
        Toast.makeText(this, "Verificando…", Toast.LENGTH_SHORT).show();
        bg.execute(() -> {
            String msg;
            try {
                int n = Verificador.rodar(this);
                msg = n > 0 ? n + " edital(is) novo(s) encontrado(s)!" : "Nenhum edital novo por enquanto.";
            } catch (Exception e) {
                msg = "Falha na verificação: " + e.getMessage();
            }
            final String m = msg;
            ui.post(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
        });
    }

    // ------------------------------------------------------------ portais extras

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
        llCustom.removeAllViews();
        for (final String u : Store.custom(this)) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.addView(texto(u, 12, false, Color.BLACK),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            r.addView(botao("Remover", v -> {
                Store.removerCustom(this, u);
                renderCustom();
            }));
            llCustom.addView(r);
        }
    }
}
