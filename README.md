# Concurso Alerta (Android)

App que busca concursos públicos pelo cargo (palavra-chave), com cargos similares, filtro por estado
ou Todo o Brasil, e avisa por notificação flutuante com som quando surge edital novo.

## Como gerar o APK

**Opção A — GitHub (sem instalar nada):** crie um repositório, envie esta pasta, abra a aba *Actions*
> *Build APK* > *Run workflow*. Ao terminar, baixe o artefato `ConcursoAlerta-apk` (app-debug.apk).

**Opção B — Android Studio:** *File > Open* nesta pasta, aguarde o sync, *Build > Build APK(s)*.
Arquivo em `app/build/outputs/apk/debug/app-debug.apk`.

**Opção C — linha de comando:** com JDK 17 e Android SDK 34: `gradle assembleDebug`.

Instalar no celular: copie o APK, abra e permita "instalar apps desconhecidos".

## Como funciona

- **Busca:** digite o cargo, escolha o estado e toque em *Buscar agora*. Com "cargos similares" marcado,
  "enfermeiro" também traz "técnico de enfermagem" etc. (tabela em `Busca.java`, fácil de ampliar).
- **Cadastro de alerta:** *Cadastrar alerta* guarda cargo + estado no próprio aparelho (sem conta/servidor).
  A 1ª varredura só registra o que já existe; depois, só edital novo gera aviso.
- **Segundo plano:** `JobScheduler` confere as fontes a cada ~30 min e sobrevive a reinício.
  Fabricantes com economia de bateria agressiva podem atrasar; use o botão *Ajustes de bateria*.
- **Notificação:** canal `IMPORTANCE_HIGH` (banner flutuante) com o **som padrão de notificação do
  aparelho** (não define som próprio). No Android 13+ o app pede a permissão de notificações.

## Fontes

PCI Concursos (coletor dedicado), Estratégia, Gran Cursos, Folha Dirigida, Concursos no Brasil,
Jornal dos Concursos, bancas (Vunesp, FGV, FCC, IBFC, Cesgranrio), Diário Oficial da União (busca
da Imprensa Nacional) e portais de órgãos que o usuário adiciona no app.

## Limitações conhecidas (leia antes de publicar)

- Os coletores leem o HTML público dos sites e **não foram testados contra as páginas reais**; se um
  site mudar o layout, aquela fonte para de render (o app avisa "N fontes não responderam"). Ajustes
  ficam em `Fontes.java`.
- Páginas que só carregam com JavaScript (ex.: Cebraspe) não funcionam com esse método.
- Páginas de bancas e de órgãos geralmente listam o nome do concurso, não os cargos; por isso o
  casamento por cargo é mais confiável nos agregadores. Em portais de órgãos, funciona quando o título
  do link cita o cargo.
- Respeite os termos de uso/robots de cada site. O PCI Concursos libera `/concursos/` no robots.txt,
  mas confira os termos antes de distribuir o app publicamente. Para uso amplo, prefira parcerias/APIs.
- Aviso em tempo real "de verdade" (segundos após a publicação) exigiria um servidor + Firebase Cloud
  Messaging; esta versão faz a checagem periódica no próprio celular.
