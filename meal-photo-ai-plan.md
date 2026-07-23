# Plano de implementacao: leitura de refeicoes e tabelas nutricionais por IA

## Objetivo

Adicionar ao FoodYou dois fluxos opcionais baseados no Gemini:

1. estimar os totais nutricionais de uma refeicao fotografada, permitir revisao e salvar o resultado como uma entrada manual do diario;
2. ler uma tabela nutricional fotografada e preencher os campos de macronutrientes do formulario de criacao de produto.

O plano adapta o comportamento descrito em `meal-photo-ai.md` para a arquitetura atual do FoodYou: Kotlin Multiplatform, Compose, Material 3, Koin, Ktor, DataStore e Room.

## Decisoes fechadas

- Entregar os dois fluxos: refeicao e tabela nutricional.
- Usar Gemini com chave fornecida pelo usuario (BYOK).
- Armazenar a chave localmente com `MasterCrypto` e Android Keystore.
- Fazer a requisicao diretamente do dispositivo ao Gemini; nao criar backend.
- Nao implementar OCR local nesta entrega.
- Implementar aquisicao de imagem inicialmente no Android.
- Nao persistir fotos, respostas brutas, componentes, alertas ou confianca.
- Reutilizar `ManualDiaryEntry` para a refeicao estimada.
- Apenas preencher o formulario de produto ao ler um rotulo; o usuario continua responsavel por revisar e salvar.
- Nao alterar o schema Room nesta entrega.
- Preservar o Material 3, a navegacao e os padroes visuais atuais.

## Fora do escopo

- Backend, contas, sincronizacao ou armazenamento remoto pelo FoodYou.
- OCR/Tesseract/ML Kit e funcionamento offline da leitura de rotulos.
- Persistencia da foto ou de metadados da analise.
- Criacao automatica de receitas ou identificacao de ingredientes do catalogo.
- Registro de tokens, custo ou resposta bruta do provedor.
- Suporte funcional a iOS, enquanto as abstracoes de plataforma existentes continuarem somente com implementacao Android.
- Retry automatico de chamadas ao Gemini.

## Arquitetura proposta

### Dominio

Criar contratos independentes do Gemini para impedir que DTOs do provedor vazem para a interface:

- `AiCredentialsRepository`
- `MealPhotoEstimator`
- `NutritionLabelExtractor`
- `MealPhotoEstimate`
- `NutritionLabelExtraction`
- `NutritionLabelBasis`
- `AiImage`
- hierarquia de erros de analise

Os contratos ficam em `commonMain`. A implementacao Gemini usa Ktor e `kotlinx.serialization`, seguindo a separacao dominio/infraestrutura existente.

### Infraestrutura

Criar um cliente Ktor dedicado ao Gemini com:

- endpoint e modelo centralizados;
- JSON sem `ignoreUnknownKeys` para as respostas estruturadas da IA;
- timeout de requisicao, conexao e socket;
- propagacao de `CancellationException`;
- mapeamento de status e erros para tipos de dominio;
- chave enviada sem ser incluida em logs;
- imagem enviada como `inlineData` em Base64;
- `responseMimeType = application/json` e JSON Schema do resultado.

Usar inicialmente `gemini-3.1-flash-lite`, mas confirmar na documentacao oficial, durante a implementacao, que o identificador esta disponivel na API utilizada. Manter o nome do modelo em uma unica constante para permitir substituicao sem alterar os fluxos.

### Injecao de dependencias

Registrar repositorio de credenciais, cliente Gemini, extratores e ViewModels nos modulos Koin correspondentes. Nao criar um novo modulo Gradle; o projeto deliberadamente minimiza modulos.

## Fase 1: contratos, validacao e erros

### Implementacao

1. Criar os modelos de dominio para imagem e resultados.
2. Criar interfaces para credenciais e os dois extratores.
3. Criar erros tipados:
   - chave ausente;
   - chave recusada;
   - cota ou rate limit;
   - solicitacao recusada;
   - timeout ou rede;
   - imagem invalida;
   - resposta invalida;
   - provedor indisponivel.
4. Implementar validadores explicitos depois da desserializacao.

### Contrato da refeicao

Campos:

- `label`: texto nao vazio, maximo de 100 caracteres;
- `calories`: finito, entre 0 e 99.999;
- `protein`: finito, entre 0 e 99.999;
- `carbs`: finito, entre 0 e 99.999;
- `fat`: finito, entre 0 e 99.999;
- `estimatedWeightGrams`: positivo e finito ou `null`;
- `components`: no maximo 12 textos limitados;
- `confidence`: `low`, `medium` ou `high` no contrato interno;
- `warnings`: no maximo 8 textos limitados.

Os textos apresentados ao usuario devem ser localizados, mesmo que o enum interno use identificadores em ingles.

### Contrato do rotulo

Campos:

- `basis`: `per_100g`, `per_serving` ou `unknown`;
- `servingGrams`: positivo e finito ou `null`;
- `protein`, `carbs`, `fat` e `energy`: finitos, nao negativos ou `null`;
- `warnings`: no maximo 6 textos limitados.

### Testes

- resposta valida de cada contrato;
- JSON malformado;
- campo ausente ou adicional;
- `NaN`, infinito, negativo e limite excedido;
- listas e textos acima dos limites;
- enum desconhecido.

### Criterio de conclusao

Os contratos podem ser testados sem Android, Compose, rede real ou chave Gemini.

## Fase 2: credencial Gemini criptografada

### Implementacao

1. Criar `AiCredentialsRepository` em dominio.
2. Implementar o repositorio com DataStore e `MasterCrypto`, seguindo `OpenFoodFactsCredentialsRepositoryImpl.kt`.
3. Armazenar:
   - chave criptografada como bytes;
   - quatro ultimos caracteres para exibicao;
   - data da ultima chamada bem-sucedida, se o custo de estado adicional continuar justificavel na implementacao.
4. Expor operacoes para salvar, carregar, remover e observar se existe configuracao.
5. Validar comprimento entre 20 e 500 caracteres antes de criptografar.
6. Tratar envelope criptografado invalido sem derrubar o aplicativo; a credencial deve ser considerada indisponivel e poder ser removida/substituida.

### Interface

Adicionar “Recursos de IA” a `SettingsScreen.kt`, usando `SettingsListItem` e uma nova rota tipada em `FoodYouAppNavHost.kt`.

A tela deve conter:

- explicacao de que a chave pertence ao usuario;
- link para obtencao da chave;
- campo de senha com opcao de revelar durante a digitacao;
- estado configurado mostrando somente o sufixo;
- acoes de salvar, substituir e remover;
- aviso de que imagens escolhidas serao enviadas ao Google Gemini;
- estado da ultima validacao, caso seja persistido.

Salvar a chave nao precisa fazer uma chamada de teste. Uma analise concluida comprova que a chave funciona.

### Arquivos de referencia

- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/food/infrastructure/openfoodfacts/OpenFoodFactsCredentialsRepositoryImpl.kt`
- `app/src/androidMain/kotlin/com/maksimowiczm/foodyou/common/infrastructure/crypto/AndroidMasterCrypto.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/settings/SettingsScreen.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/common/component/SettingsListItem.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/navigation/FoodYouAppNavHost.kt`

### Testes

- salvar, carregar, substituir e remover;
- nunca expor a chave no estado de exibicao;
- rejeitar limites invalidos;
- comportamento com bytes criptografados invalidos;
- teste Android de round-trip com `MasterCrypto`.

### Criterio de conclusao

A chave nao aparece em texto puro no DataStore, logs, estado de tela apos salvar ou relatorios de erro.

## Fase 3: aquisicao e preparacao de imagens no Android

### Implementacao

1. Definir em `commonMain` um contrato de foto que represente somente os dados necessarios para analise.
2. Implementar no Android:
   - captura com camera traseira;
   - selecao pela galeria/photo picker;
   - leitura por `ContentResolver`;
   - deteccao de MIME;
   - decodificacao limitada de dimensoes;
   - correcao de orientacao;
   - redimensionamento e compressao.
3. Reutilizar o padrao de permissao e abertura de configuracoes do scanner de codigo de barras.
4. Manter a URI somente durante o fluxo e liberar buffers ao substituir, cancelar ou sair.

### Regras

- aceitar JPEG, PNG e WebP;
- rejeitar arquivo maior que 10 MiB antes de carrega-lo integralmente;
- rejeitar dimensoes invalidas;
- limitar o maior lado da imagem processada, inicialmente a 1.800 px;
- usar JPEG com qualidade conservadora quando nao houver necessidade de transparencia;
- manter o payload final dentro do limite definido;
- nao copiar a imagem para armazenamento persistente alem do necessario pelo contrato de captura.

### Testes

- MIME permitido e proibido;
- arquivo ausente ou inacessivel;
- limite de tamanho;
- dimensao invalida;
- cancelamento de leitura;
- imagem processada dentro do limite.

### Criterio de conclusao

Camera e galeria produzem o mesmo contrato validado, sem o fluxo de IA conhecer APIs Android.

## Fase 4: cliente Gemini compartilhado

### Implementacao

1. Adicionar suporte a `MockEngine` nos testes Ktor.
2. Criar um `HttpClient` qualificado para Gemini.
3. Implementar o envelope REST de `generateContent`.
4. Implementar serializacao da imagem Base64 e schema de resposta.
5. Classificar respostas HTTP e erros do provedor.
6. Aplicar timeout explicito e preservar cancelamento.
7. Atualizar a data de validacao somente apos resposta analisada e validada.

### Prompt da refeicao

Orientar o modelo a:

- retornar totais da porcao inteira, nunca valores por 100 g;
- priorizar o peso conhecido quando fornecido;
- estimar peso conservadoramente quando ausente;
- considerar oleo, molhos, recheios e ingredientes ocultos;
- listar somente componentes plausiveis e visiveis;
- reduzir confianca e emitir alertas diante de incerteza;
- evitar precisao inventada.

### Prompt do rotulo

Orientar o modelo a:

- ler tabela nutricional brasileira;
- manter macros e energia na mesma base impressa;
- priorizar a coluna de 100 g;
- ignorar `%VD`;
- distinguir gorduras totais de saturadas e trans;
- preservar decimais;
- retornar `null` quando um valor estiver ilegivel;
- identificar peso de porcao quando a base for por porcao.

### Diagnosticos

Registrar somente:

- tipo de operacao;
- etapa da falha;
- MIME;
- tamanho do arquivo;
- modelo;
- duracao aproximada;
- categoria de erro.

Nunca registrar:

- chave;
- header de autorizacao;
- Base64;
- foto;
- prompt completo;
- corpo bruto de resposta.

### Testes

- corpo da solicitacao e schema enviados corretamente;
- parsing da resposta do envelope Gemini;
- 400, 401, 403, 429 e 5xx;
- timeout, falha de rede e cancelamento;
- resposta vazia, bloqueada ou invalida;
- ausencia da chave.

### Criterio de conclusao

Os dois extratores funcionam contra `MockEngine` e nenhuma parte da UI depende do formato REST do Gemini.

## Fase 5: estimativa de refeicao e Quick Add

### Ponto de entrada

Adicionar uma acao de foto ao contexto da refeicao em `MealCard.kt`. A acao recebe o mesmo `epochDay` e `mealId` usados por Quick Add e busca.

Adicionar uma rota tipada de estimativa em `FoodYouAppNavHost.kt`.

### Tela

Criar uma tela Compose seguindo os padroes de formulario atuais:

- `Scaffold` e `TopAppBar`;
- botao voltar com protecao contra descarte;
- conteudo vertical com 16 dp horizontal e espacamento de 8 dp;
- estado vazio com acoes “Tirar foto” e “Escolher da galeria”;
- previa da foto;
- campo opcional de peso total em gramas;
- orientacoes curtas de enquadramento;
- botao “Analisar refeicao”;
- progresso, cancelamento e erros recuperaveis.

### Estado

O ViewModel deve representar:

- `Idle`;
- `ImageSelected`;
- `Analyzing`;
- `Review`;
- `Error`.

Cada analise deve possuir uma identidade ou `Job` propria. Selecionar nova foto, cancelar ou sair invalida a requisicao anterior para impedir aplicacao de resultado atrasado.

### Revisao

Depois da analise, exibir:

- nome editavel;
- energia editavel;
- proteina, carboidratos e gordura editaveis;
- peso informado ou estimado;
- componentes detectados;
- confianca localizada;
- alertas.

Reutilizar o comportamento e os campos do Quick Add. Ajustar `QuickAddFormState` para aceitar preenchimento posterior ou criar a tela de revisao com o mesmo componente de formulario.

Quando a energia retornada diferir do calculo 4/4/9, iniciar `autoCalculateEnergy = false`. A estimativa explicita do provedor nao pode ser sobrescrita automaticamente antes da revisao.

### Persistencia

Ao confirmar:

- construir `NutritionFacts` com os valores revisados;
- inserir por `ManualDiaryEntryRepository`;
- manter a semantica atual de entrada manual;
- navegar de volta ao diario somente depois da insercao concluida;
- mostrar erro e preservar o formulario se a insercao falhar.

Nao persistir peso, componentes, confianca, alertas ou foto.

### Arquivos de referencia

- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/home/meals/card/MealCard.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/QuickAddForm.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/QuickAddFormState.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/CreateQuickAddViewModel.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/fooddiary/domain/entity/ManualDiaryEntry.kt`

### Testes

- peso com ponto, virgula, zero, negativo e texto invalido;
- transicoes de estado;
- requisicao anterior nao altera a nova selecao;
- energia Gemini preservada;
- edicao antes de salvar;
- erro de persistencia mantem os dados;
- resultado vira `ManualDiaryEntry`, nao produto ou receita.

### Criterio de conclusao

O usuario fotografa uma refeicao, revisa todos os totais e a encontra no cartao correto do diario como entrada manual.

## Fase 6: leitura de tabela nutricional no formulario de produto

### Ponto de entrada

Adicionar um `AssistChip` “Importar tabela nutricional” em `CreateProductScreen.kt`, proximo ao chip de download e antes de `ProductForm`.

O recurso deve ser inicialmente limitado a criacao. Suporte a edicao de produto pode ser adicionado depois, evitando sobrescrever dados existentes sem uma decisao explicita.

### Interface

Abrir um dialogo ou bottom sheet Material 3 contendo:

- instrucoes para enquadrar somente a tabela;
- acoes de camera e galeria;
- previa;
- progresso e cancelamento;
- erros com nova tentativa;
- aviso de revisao antes da aplicacao.

Se nao houver chave, apresentar a explicacao e uma acao para navegar a “Recursos de IA”, sem iniciar a chamada.

### Normalizacao

Criar uma funcao pura e testavel:

```text
valor por 100 g = valor por porcao * 100 / peso da porcao em gramas
```

Regras:

- `per_100g`: aplicar diretamente;
- `per_serving` com peso valido: normalizar para 100 g;
- `per_serving` sem peso: nao aplicar automaticamente e informar o problema;
- `unknown`: nao presumir base nem preencher silenciosamente;
- arredondar para no maximo tres casas decimais;
- preservar `null` para campo ilegivel.

Depois de normalizar, atualizar o seletor de base do `ProductFormState` para 100 g. Isso evita que `CreateProductViewModel` normalize os valores pela segunda vez.

### Aplicacao ao formulario

- preencher proteina, carboidratos, gordura e energia quando presentes;
- manter campos ausentes vazios;
- desligar o calculo automatico de energia quando a energia vier do rotulo;
- nao alterar nome, marca, codigo de barras, notas ou outros nutrientes;
- marcar o formulario como modificado;
- exibir “Valores importados por IA. Revise os campos antes de criar o produto.”;
- nunca enviar ou salvar o produto automaticamente.

### Arquivos de referencia

- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/product/create/CreateProductScreen.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/product/create/CreateProductApp.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/product/ProductForm.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/product/ProductFormState.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/product/create/CreateProductViewModel.kt`

### Testes

- valores ja em 100 g;
- conversao por porcao;
- porcao sem peso;
- base desconhecida;
- arredondamento;
- `null` nao vira zero;
- seletor de base evita dupla normalizacao;
- aplicacao nao altera campos fora do escopo;
- falha nao apaga dados digitados anteriormente.

### Criterio de conclusao

Uma foto valida preenche corretamente o formulario, mas o produto so e persistido apos revisao e acao de salvar do usuario.

## Fase 7: privacidade, textos e acabamento

### Privacidade

Atualizar `docs/docs/privacy-policy.md`. O texto atual afirma que dados gerados nunca sao transmitidos, o que deixara de ser verdadeiro quando o usuario optar pela analise.

Documentar:

- envio opcional da foto ao Google Gemini;
- envio direto do dispositivo, sem servidor FoodYou;
- finalidade dos dois fluxos;
- chave fornecida e armazenada localmente;
- ausencia de persistencia da imagem pelo FoodYou;
- politicas e retencao sob responsabilidade do Google;
- como remover a chave e interromper o uso.

Mostrar consentimento contextual antes do primeiro envio, persistindo apenas a confirmacao. Nao repetir o dialogo a cada foto.

### Localizacao

Adicionar todas as strings ao sistema Compose Resources. O ingles deve ser a fonte inicial. Atualizar pelo menos `values-pt-rBR` para validar o fluxo em portugues. As demais traducoes podem permanecer com fallback ate o processo normal de localizacao, desde que a validacao do build aceite esse comportamento.

### Acessibilidade e UX

- content descriptions para camera, galeria, remover foto e alertas;
- alvos de toque de pelo menos 48 dp;
- estados de progresso anunciaveis;
- erros em texto, nao somente por cor;
- suporte a tema claro, escuro, dinamico e AMOLED;
- layout de coluna unica consistente com o restante do app;
- protecao contra descarte de revisoes editadas.

### Criterio de conclusao

A funcionalidade comunica claramente quando dados saem do dispositivo e continua utilizavel com leitor de tela e todos os temas atuais.

## Estrutura estimada de arquivos novos

Os nomes finais devem seguir os pacotes encontrados durante a implementacao, mas a divisao esperada e:

```text
app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/domain/...
app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/...
app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/ai/settings/...
app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/mealphoto/...
app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/product/nutritionlabel/...
app/src/androidMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/image/...
app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/...
```

Evitar criar helpers ou camadas adicionais sem reutilizacao concreta.

## Dependencias entre fases

```text
Fase 1: contratos e validacao
  |
  +-- Fase 2: credenciais
  +-- Fase 3: imagens Android
  |
  +-- Fase 4: cliente Gemini
        |
        +-- Fase 5: foto de refeicao
        +-- Fase 6: tabela nutricional
                    |
                    +-- Fase 7: privacidade e acabamento
```

As fases 2 e 3 podem ser executadas em paralelo depois da Fase 1. As fases 5 e 6 podem ser executadas em paralelo depois da Fase 4.

## Verificacao final

Executar:

```bash
./gradlew :app:testDebugUnitTest
./gradlew check
./gradlew assembleDebug
```

Quando houver dispositivo ou emulador disponivel:

```bash
./gradlew connectedAndroidTest
```

Roteiro manual:

1. abrir Recursos de IA, salvar uma chave e confirmar que somente o sufixo aparece;
2. remover e configurar novamente a chave;
3. abrir uma refeicao, tirar foto, informar peso e analisar;
4. revisar nome, energia e macros e salvar;
5. confirmar a entrada manual no dia e refeicao corretos;
6. criar produto, importar um rotulo por camera e revisar os valores;
7. repetir com imagem da galeria;
8. validar rotulo por porcao e confirmar normalizacao para 100 g;
9. testar sem chave, sem rede, com chave invalida, quota e imagem grande;
10. cancelar uma chamada, trocar a foto e confirmar que o resultado antigo nao aparece;
11. confirmar que nenhuma foto foi adicionada ao banco ou armazenamento permanente;
12. verificar temas claro, escuro, dinamico e AMOLED.

## Criterios de aceite globais

- O usuario configura e remove sua chave Gemini com armazenamento criptografado.
- A chave nunca aparece integralmente depois de salva.
- Refeicoes podem ser analisadas por camera ou galeria.
- O resultado da refeicao sempre passa por revisao humana.
- A refeicao e salva como `ManualDiaryEntry` na data e refeicao corretas.
- Rotulos podem ser analisados por camera ou galeria durante a criacao de produto.
- Valores por porcao sao normalizados corretamente para 100 g.
- Base desconhecida ou porcao sem peso nao gera preenchimento silencioso incorreto.
- O produto nao e criado automaticamente.
- Fotos e respostas brutas nao sao persistidas.
- Falhas e cancelamentos nao apagam dados nem aplicam resultados atrasados.
- O aplicativo continua funcionando normalmente sem chave e sem internet; apenas os recursos de IA ficam indisponiveis.
- A interface segue o Material 3 e os padroes atuais do FoodYou.
- A politica de privacidade descreve corretamente o envio opcional ao Gemini.
- Testes, `check` e build debug passam.

## Riscos principais

| Risco | Mitigacao planejada |
| --- | --- |
| Estimativa nutricional incorreta | Revisao obrigatoria, confianca e alertas visiveis |
| Chave exposta | Android Keystore, DataStore criptografado e redacao de logs |
| Foto muito grande causar memoria excessiva | Limites antes da leitura integral, redimensionamento e compressao |
| Resposta estruturalmente valida mas absurda | Validacao de finitude, intervalos e limites de colecao |
| Dupla normalizacao do rotulo | Normalizar uma vez e atualizar explicitamente a base do formulario |
| Resultado atrasado sobrescrever nova foto | Cancelamento de `Job` e identidade por requisicao |
| Politica de privacidade inconsistente | Atualizacao obrigatoria antes da entrega |
| Modelo Gemini indisponivel | Constante central e confirmacao do identificador na implementacao |
| Dependencia exclusiva de rede para rotulos | Estado de erro claro; OCR permanece fora deste escopo por decisao |

## Sequencia recomendada de entregas

1. PR 1: contratos, validadores, credenciais e tela de configuracao.
2. PR 2: aquisicao de imagem e cliente Gemini testado com `MockEngine`.
3. PR 3: fluxo completo de foto de refeicao.
4. PR 4: fluxo completo de tabela nutricional.
5. PR 5: privacidade, acessibilidade, localizacao e testes finais.

Cada entrega deve permanecer compilavel e testavel. Os pontos de entrada dos fluxos devem ser mantidos ocultos ou desabilitados ate suas dependencias estarem completas, evitando disponibilizar uma interface parcialmente funcional.
