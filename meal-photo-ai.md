# Leitura de refeição e tabela nutricional com Gemini

Este documento descreve as implementações atuais da estimativa nutricional de uma refeição e da leitura de uma tabela nutricional a partir de fotos no Macroflex.

> As duas funcionalidades são fluxos separados. A foto de refeição estima os totais da porção fotografada e não possui fallback por OCR. A leitura de rótulos preenche os macros durante a criação de alimentos e pode recorrer ao OCR local.

## Visão geral

O usuário envia ou tira uma foto de uma refeição e pode informar o peso total conhecido. O Gemini analisa a imagem e retorna:

- nome sugerido para a refeição;
- calorias totais;
- proteína total;
- carboidratos totais;
- gordura total;
- peso estimado;
- componentes identificados;
- nível de confiança;
- alertas sobre possíveis imprecisões.

Antes de adicionar o resultado ao diário, o usuário pode revisar e alterar o nome, as calorias e os macronutrientes. O resultado final é salvo como uma **entrada rápida de macros**, sem vínculo com um alimento do catálogo.

Existem dois caminhos de execução:

```text
Web
Navegador -> API do Macroflex -> Gemini -> revisão -> API do diário -> PostgreSQL

Android local-first
Aplicativo -> Gemini -> revisão -> repositório local -> SQLite/outbox
```

## Dependências e modelo

A integração utiliza:

- SDK `@google/genai`;
- modelo `gemini-3.1-flash-lite`;
- `zod` para validar a resposta;
- resposta estruturada em JSON por meio de `responseMimeType` e `responseJsonSchema`.

As dependências estão declaradas em `package.json`. A implementação do cliente Gemini está em:

- `src/lib/nutrition-label/meal-photo-extractor.ts`

O cliente `GoogleGenAI` é criado a cada análise usando a chave do usuário:

```ts
const ai = new GoogleGenAI({ apiKey });
```

Não há configuração explícita de temperatura, `topP`, `topK`, limite de tokens, timeout, retry ou safety settings. São usados os padrões do SDK e do modelo.

## Configuração da chave Gemini

### Modelo BYOK

O Macroflex usa o modelo BYOK, ou *Bring Your Own Key*. Cada usuário deve fornecer sua própria chave da API Gemini na tela **Conta**.

Não existe uma variável de ambiente global `GEMINI_API_KEY` ou `GOOGLE_API_KEY` na implementação atual. A chave não é compartilhada entre usuários.

Para configurar:

1. Crie ou obtenha uma chave do Gemini no Google AI Studio.
2. Abra a tela **Conta** no Macroflex.
3. Expanda a seção de configuração do Gemini, atualmente exibida como **Leitura de rótulos**.
4. Cole a chave no campo **Chave Gemini**.
5. Selecione **Salvar chave**.

A mesma chave é usada tanto pela leitura de rótulos quanto pela estimativa de refeições por foto.

A interface aceita chaves entre 20 e 500 caracteres. Ao exibir o estado da configuração, somente os quatro últimos caracteres são mostrados.

> Salvar a chave não executa uma chamada de validação no Gemini. Na Web, `lastValidatedAt` só é atualizado depois de uma estimativa concluída com sucesso.

### Configuração da aplicação Web

Na Web, a aplicação precisa de `AI_CREDENTIALS_ENCRYPTION_KEY` para criptografar as chaves Gemini antes de gravá-las no banco de dados.

Exemplo em `.env.local`:

```dotenv
AI_CREDENTIALS_ENCRYPTION_KEY=<chave-base64-de-32-bytes>
```

Uma chave compatível pode ser gerada com:

```bash
openssl rand -base64 32
```

Essa variável:

- é obrigatória para salvar ou ler credenciais Gemini na Web;
- deve representar exatamente 32 bytes codificados em Base64;
- deve ser mantida apenas no servidor;
- não deve ser exposta com prefixo `NEXT_PUBLIC_`;
- não deve ser substituída sem um plano de rotação, pois as chaves já armazenadas deixariam de ser decifráveis.

As demais variáveis necessárias para executar o fluxo Web pertencem à infraestrutura geral da aplicação:

```dotenv
DATABASE_URL=postgres://...
BETTER_AUTH_SECRET=...
BETTER_AUTH_URL=http://localhost:3000
AI_CREDENTIALS_ENCRYPTION_KEY=...
NEXT_PUBLIC_APP_RUNTIME=web
```

`GOOGLE_CLIENT_ID` e `GOOGLE_CLIENT_SECRET` são usados pelo login opcional com Google. Eles não são a chave da API Gemini.

O arquivo de referência é `.env.example`.

### Armazenamento da chave na Web

O componente `src/app/configurations/ai-settings-card.tsx` envia a chave para:

```http
PATCH /api/ai-settings
Content-Type: application/json
```

Payload para salvar:

```json
{
  "apiKey": "chave-fornecida-pelo-usuario"
}
```

Payload para remover:

```json
{
  "remove": true
}
```

A rota `src/app/api/ai-settings/route.ts` exige uma sessão autenticada e grava na tabela `ai_settings`:

- provedor, atualmente `gemini`;
- chave criptografada;
- sufixo de quatro caracteres;
- data da última validação;
- timestamps.

A criptografia implementada em `src/lib/ai-credentials.ts` usa:

- AES-256-GCM;
- IV aleatório de 12 bytes;
- authentication tag;
- chave mestra fornecida por `AI_CREDENTIALS_ENCRYPTION_KEY`.

O valor armazenado é a concatenação `IV + authentication tag + ciphertext`, codificada em Base64. A chave Gemini em texto puro é recuperada somente no servidor, imediatamente antes da chamada ao SDK.

### Armazenamento da chave no Android

No Android local-first, a chave não é enviada ao endpoint `/api/ai-settings` e não é armazenada no PostgreSQL.

O bridge TypeScript está em:

- `src/lib/local-ai-credentials.ts`

O plugin nativo está em:

- `android/app/src/main/java/com/macroflex/app/AiCredentialsPlugin.java`

O plugin utiliza:

- uma chave AES criada no `AndroidKeyStore`;
- `AES/GCM/NoPadding`;
- IV aleatório;
- ciphertext e IV em `SharedPreferences`;
- somente os quatro últimos caracteres em texto claro para exibição.

Se uma entrada inválida do Keystore permanecer após um reset de emulador, o plugin remove a entrada antiga e tenta criar a chave novamente.

Nesse runtime, a chave é decifrada no dispositivo e fornecida diretamente ao SDK Gemini. A foto não passa pelo servidor do Macroflex.

## Entrada do usuário

O estimador é renderizado por `src/components/meal-photo-estimator.tsx` dentro do painel usado para adicionar itens a uma refeição.

O usuário pode:

- tirar uma foto;
- escolher uma imagem da galeria;
- informar opcionalmente o peso total conhecido em gramas.

Na Web, a câmera usa um `input` de arquivo com `capture="environment"`. No Android, o aplicativo utiliza `@capacitor/camera` com câmera traseira, qualidade 85 e resultado por URI.

O peso aceita ponto ou vírgula decimal. Somente um número finito e maior que zero é enviado. Um valor inválido é tratado no cliente como peso não informado.

### Validação da imagem

No componente, a validação inicial verifica apenas se o MIME começa com `image/`.

Na API Web, a validação é mais restrita:

- JPEG (`image/jpeg`);
- PNG (`image/png`);
- WebP (`image/webp`);
- tamanho máximo de 10 MiB.

Atualmente não há redimensionamento, compressão, validação de dimensões ou limite de tamanho equivalente antes da chamada direta no Android.

## Fluxo Web

### 1. Envio da imagem

O cliente cria um `FormData`:

```text
image: File
knownWeight: string opcional
```

E realiza:

```http
POST /api/meal-photo/extract
Content-Type: multipart/form-data
```

Esse código está em `estimateRemotely`, dentro de `src/components/meal-photo-estimator.tsx`.

### 2. Autenticação e credencial

A rota `src/app/api/meal-photo/extract/route.ts`:

1. exige um usuário autenticado por `requireApiUser()`;
2. valida formato e tamanho da imagem;
3. valida o peso opcional;
4. consulta `ai_settings` pelo ID do usuário;
5. retorna erro se o usuário ainda não configurou o Gemini;
6. decifra a chave com `decryptApiKey()`;
7. chama `estimateMealFromPhoto()`;
8. atualiza `lastValidatedAt` após sucesso;
9. retorna a estimativa em JSON.

As APIs não dependem apenas da proteção de páginas feita pelo proxy. A rota executa sua própria verificação de autenticação.

### 3. Chamada ao Gemini

A imagem completa é carregada em memória, convertida para `Uint8Array` e depois codificada em Base64. Ela é enviada ao Gemini como `inlineData`, mantendo o MIME recebido.

O prompt atual instrui o modelo a:

- estimar os totais da refeição fotografada, nunca valores por 100 g;
- usar o peso informado como referência principal, quando disponível;
- estimar o peso de forma conservadora quando ele não for informado;
- identificar os componentes visíveis;
- considerar óleo, molhos, recheios e ingredientes ocultos;
- evitar precisão inventada;
- reduzir a confiança e gerar alertas quando houver incerteza;
- retornar calorias e macros para a porção inteira.

Não há um system prompt separado. O texto e a imagem são enviados como partes de uma única mensagem com papel `user`.

## Fluxo Android local-first

O runtime é selecionado por:

```dotenv
NEXT_PUBLIC_APP_RUNTIME=android
```

Nesse modo, `estimateLocally()`:

1. solicita a chave ao plugin `AiCredentials`;
2. lê os bytes do arquivo no dispositivo;
3. chama `estimateMealFromPhoto()` diretamente;
4. recebe e valida a resposta no aplicativo;
5. apresenta a revisão ao usuário.

Não há autenticação remota ou Route Handler do Next.js nesse caminho. A requisição vai diretamente do dispositivo para o Gemini.

## Contrato de resposta do Gemini

O Gemini deve retornar um objeto JSON com todos os campos abaixo:

```json
{
  "label": "Arroz, feijão e frango grelhado",
  "calories": 620,
  "protein": 44,
  "carbs": 68,
  "fat": 18,
  "estimatedWeightGrams": 480,
  "components": ["arroz", "feijão", "frango grelhado"],
  "confidence": "média",
  "warnings": ["A quantidade de óleo não pode ser confirmada pela imagem."]
}
```

O JSON Schema enviado ao modelo exige todos os campos e rejeita propriedades adicionais.

Depois do `JSON.parse`, a resposta é validada pelo schema Zod `mealPhotoEstimateSchema`:

| Campo | Regra atual |
| --- | --- |
| `label` | String entre 1 e 100 caracteres |
| `calories` | Número finito entre 0 e 99.999 |
| `protein` | Número finito entre 0 e 99.999 |
| `carbs` | Número finito entre 0 e 99.999 |
| `fat` | Número finito entre 0 e 99.999 |
| `estimatedWeightGrams` | Número positivo ou `null` |
| `components` | Até 12 strings |
| `confidence` | `baixa`, `média` ou `alta` |
| `warnings` | Até 8 strings |

Se o Gemini retornar JSON malformado, `JSON.parse` lança um erro. Se o JSON for válido, mas não respeitar o schema Zod, a função lança `A IA retornou uma estimativa de refeição inválida.`

Não há reparo de JSON, coerção de números em strings ou segunda tentativa automática.

## Revisão pelo usuário

Após a análise, o componente entra no estado `review`. O usuário pode editar:

- nome da refeição;
- proteína;
- carboidratos;
- gordura;
- calorias.

Também são exibidos os componentes identificados, o nível de confiança e os alertas.

Embora `estimatedWeightGrams` faça parte da resposta, atualmente esse campo não é exibido nem persistido. Ao confirmar, seguem para o diário apenas:

```ts
{
  label,
  calories,
  protein,
  carbs,
  fat,
}
```

Os componentes, a confiança, os alertas, o peso informado e o peso estimado são descartados depois da revisão.

## Persistência

### Tipo da entrada

A estimativa é encaminhada para `addQuickMacrosToMeal()` em `src/components/macroflex-app.tsx`.

Ela é salva como uma entrada rápida:

- `foodId: null`;
- `grams: null`;
- nome opcional;
- calorias e macros próprios.

O sistema não cria um novo alimento e não associa a estimativa a um alimento existente.

Se calorias fossem omitidas, o fluxo de macros rápidos poderia calculá-las por `4 kcal/g` de proteína, `4 kcal/g` de carboidrato e `9 kcal/g` de gordura. No fluxo Gemini, as calorias estão presentes e o valor estimado pelo modelo é preservado, mesmo que não corresponda exatamente ao cálculo `4/4/9`.

### Web

Após a revisão, a aplicação chama:

```http
POST /api/daily-log/entries
Content-Type: application/json
```

Com uma entrada do tipo `quick`. A API valida os dados, confirma que a refeição pertence ao usuário e insere o registro em `meal_entries` no PostgreSQL.

Arquivos principais:

- `src/app/api/daily-log/entries/route.ts`;
- `src/db/index.ts`;
- `src/db/schema.ts`;
- `drizzle/0007_quick_macro_entries.sql`.

A interface faz uma atualização otimista e substitui a entrada temporária pela resposta da API. Se a persistência falhar, a atualização otimista é revertida.

### Android

No Android local-first, a aplicação chama diretamente o repositório local. A implementação:

- valida os valores;
- confirma que a refeição pertence ao usuário local;
- insere a entrada em `meal_entries` no SQLite;
- adiciona uma operação à outbox de sincronização.

Arquivos principais:

- `src/data/local/sqlite-nutrition-repository.ts`;
- `src/data/repositories/types.ts`;
- `drizzle-sqlite/0000_local_first.sql`.

## Respostas e erros da API Web

O endpoint de extração pode retornar:

| Status | Situação |
| --- | --- |
| `200` | Estimativa concluída |
| `400` | Imagem, formato, tamanho ou peso inválido |
| `401` | Usuário não autenticado |
| `422` | Chave Gemini não configurada |
| `502` | Falha ao decifrar a chave, chamar o Gemini, interpretar a resposta ou concluir a atualização de validação |

No erro `502`, a API registra no servidor o nome e a mensagem da exceção, mas devolve ao cliente uma mensagem genérica:

```text
Não foi possível estimar esta refeição. Tente novamente.
```

Atualmente a rota de refeição não diferencia chave recusada, quota excedida, rate limit, resposta inválida ou indisponibilidade temporária do provedor.

No cliente, erros também são enviados a `captureDiagnostic()` com MIME, tamanho do arquivo e indicação booleana de peso conhecido. A chave da API não é incluída nesses metadados. O mecanismo de diagnóstico redige padrões sensíveis como API keys, tokens, secrets e cabeçalhos de autorização.

## Privacidade e ciclo de vida da imagem

O Macroflex não grava a foto no banco de dados ou no sistema de arquivos durante esse fluxo.

No cliente, a imagem permanece em memória como `File` e é exibida por meio de uma object URL. Essa URL é revogada ao substituir a imagem, reiniciar o formulário ou desmontar o componente.

Na Web:

1. a imagem é enviada ao servidor Macroflex;
2. o servidor a mantém em memória;
3. o servidor a envia ao Gemini como Base64.

No Android:

1. a imagem é lida no dispositivo;
2. o aplicativo a envia diretamente ao Gemini como Base64.

Não existe configuração explícita no SDK sobre retenção de dados pelo provedor. As políticas do Google aplicáveis à API e à conta que gerou a chave devem ser avaliadas separadamente.

## Limites e comportamentos atuais

- A API Web aceita JPEG, PNG e WebP de até 10 MiB.
- O Android não aplica o mesmo limite explícito antes da chamada direta.
- Não há limite máximo para o peso conhecido.
- Não há compressão ou redimensionamento da foto da refeição.
- Não há timeout ou cancelamento da requisição em andamento.
- Não há retry automático.
- Não há rate limiting ou quota interna por usuário.
- Não há idempotency key ou deduplicação de análises.
- Não há fallback por OCR para fotos de refeições.
- A resposta bruta do Gemini não é persistida.
- Modelo, prompt, duração, tokens e custo não são registrados por análise.
- O schema do Gemini aceita nomes de até 100 caracteres, mas a persistência Web de entradas rápidas aceita até 80. Um nome entre 81 e 100 caracteres pode passar pela estimativa e falhar ao salvar.
- A leitura de rótulos possui fallback OCR, mas isso não se aplica à estimativa de refeições.

## Troubleshooting

### A aplicação pede para configurar a chave Gemini

Confirme que a chave foi salva na tela **Conta** do runtime que está sendo usado. A configuração Web e a configuração Android são armazenadas separadamente.

### A Web não consegue salvar ou acessar a chave

Verifique:

- se `AI_CREDENTIALS_ENCRYPTION_KEY` está configurada no servidor;
- se o valor decodifica para exatamente 32 bytes;
- se a mesma chave de criptografia usada para gravar as credenciais continua configurada;
- se o usuário está autenticado;
- se o PostgreSQL está disponível e com as migrações aplicadas.

### A análise retorna erro genérico

Consulte o log do servidor por `[meal-photo] Gemini estimation failed`. Entre as causas possíveis estão:

- chave Gemini inválida ou revogada;
- quota ou rate limit do Google;
- modelo indisponível para a chave ou projeto;
- erro de rede;
- resposta JSON inválida;
- resposta incompatível com o schema Zod;
- falha ao decifrar a credencial.

### A estimativa funciona, mas não é adicionada ao diário

Verifique o endpoint `/api/daily-log/entries`, a propriedade da refeição, os limites numéricos e o tamanho do nome. A extração e a persistência são operações separadas.

### O Android perdeu a configuração da chave

A chave Android depende do `AndroidKeyStore` e dos dados locais do aplicativo. Limpar os dados, reinstalar o aplicativo ou resetar o emulador pode exigir que a chave seja configurada novamente.

## Testes existentes

O comando geral é:

```bash
bun test
```

Atualmente não existem testes específicos para:

- `estimateMealFromPhoto()`;
- prompt e schema da refeição;
- parsing da resposta Gemini;
- endpoint `/api/meal-photo/extract`;
- autenticação e validação de upload desse endpoint;
- componente `MealPhotoEstimator`;
- plugin Android `AiCredentialsPlugin`;
- integração Gemini com mock.

Há cobertura indireta da persistência local de entradas rápidas em `src/data/local/local-database.test.ts`, mas esse teste não chama o Gemini.

## Arquivos principais

| Responsabilidade | Arquivo |
| --- | --- |
| Interface de foto e revisão | `src/components/meal-photo-estimator.tsx` |
| Integração, prompt e schema Gemini | `src/lib/nutrition-label/meal-photo-extractor.ts` |
| API Web de extração | `src/app/api/meal-photo/extract/route.ts` |
| API Web de configuração | `src/app/api/ai-settings/route.ts` |
| Interface Web de configuração | `src/app/configurations/ai-settings-card.tsx` |
| Criptografia Web | `src/lib/ai-credentials.ts` |
| Persistência das configurações | `src/db/index.ts` e `src/db/schema.ts` |
| Bridge de credenciais Android | `src/lib/local-ai-credentials.ts` |
| Plugin Android/Keystore | `android/app/src/main/java/com/macroflex/app/AiCredentialsPlugin.java` |
| Integração com o painel da refeição | `src/components/macroflex-app.tsx` |
| API de persistência no diário | `src/app/api/daily-log/entries/route.ts` |
| Repositório local Android | `src/data/local/sqlite-nutrition-repository.ts` |
| Variáveis de ambiente de exemplo | `.env.example` |

## Leitura de tabela nutricional com IA

A leitura de tabela nutricional é usada no fluxo de criação de alimentos. O objetivo é extrair proteína, carboidratos e gorduras totais de uma tabela nutricional brasileira e preencher automaticamente os campos do formulário com valores normalizados por 100 g.

Ela compartilha a mesma chave Gemini descrita neste documento, mas possui prompt, schema, endpoint e tratamento de erros próprios. Também possui fallback local com Tesseract, que não existe na estimativa de refeições.

### Visão geral do fluxo

```text
Web com Gemini disponível
Navegador -> API do Macroflex -> Gemini -> normalização para 100 g -> formulário do alimento

Web sem Gemini ou com falha
Navegador -> tentativa da API -> pré-processamento local -> Tesseract -> parser -> normalização para 100 g -> formulário

Android com chave configurada
Aplicativo -> Gemini -> normalização para 100 g -> formulário do alimento

Android sem chave ou com falha no Gemini
Aplicativo -> pré-processamento local -> Tesseract -> parser -> normalização para 100 g -> formulário
```

O resultado não cria o alimento automaticamente. Os macros extraídos apenas preenchem o formulário de criação, e o usuário deve revisar os valores e concluir o cadastro.

### Ponto de entrada e captura da imagem

O componente `NutritionLabelImport`, em `src/components/nutrition-label-import.tsx`, é renderizado no diálogo **Criar alimento** de `src/app/foods/new/food-creator.tsx`.

O usuário pode:

- tirar uma foto da tabela nutricional;
- escolher uma imagem da galeria;
- cancelar uma análise local em andamento;
- revisar os macros preenchidos antes de criar o alimento.

A interface recomenda enquadrar somente a tabela, evitar reflexos e sombras, manter o celular paralelo ao rótulo e garantir que os números estejam legíveis.

Na Web, a câmera utiliza um `input` com `capture="environment"`. No Android, a captura usa `@capacitor/camera` com:

- câmera traseira;
- qualidade 90;
- resultado por URI;
- nome local `nutrition-label.jpg`;
- MIME retornado pela câmera ou `image/jpeg` como fallback.

O cliente aceita apenas:

- `image/jpeg`;
- `image/png`;
- `image/webp`.

Assim que uma imagem válida é selecionada, a análise começa automaticamente. Não existe um botão separado para confirmar o início da leitura.

### Seleção entre Gemini e OCR

O comportamento é controlado por `NEXT_PUBLIC_APP_RUNTIME`.

#### Web

Na Web, o cliente tenta primeiro:

```http
POST /api/nutrition-label/extract
Content-Type: multipart/form-data
```

O `FormData` contém somente o campo `image`.

Se a API retornar qualquer erro, se a resposta não for JSON compatível ou se o `fetch` falhar, o componente captura a falha e executa o OCR local no navegador.

Isso inclui o caso em que a chave Gemini não está configurada. A API retorna `422`, mas o componente não interrompe o fluxo: ele usa Tesseract como alternativa.

#### Android local-first

No Android, o cliente consulta `getLocalAiApiKey()`.

- Se houver uma chave, chama o Gemini diretamente pelo dispositivo.
- Se a chamada Gemini falhar, registra o diagnóstico e usa OCR local.
- Se não houver chave, usa OCR local imediatamente.

O fluxo Android não chama `/api/nutrition-label/extract` e não depende de sessão remota.

### API Web de leitura

A rota está em `src/app/api/nutrition-label/extract/route.ts`.

Ela executa:

1. autenticação por `requireApiUser()`;
2. leitura do `FormData`;
3. validação de formato e tamanho da imagem;
4. consulta das configurações Gemini do usuário;
5. decifragem da chave com `decryptApiKey()`;
6. chamada de `extractNutritionLabelWithGemini()`;
7. atualização de `lastValidatedAt` após sucesso;
8. retorno da extração em JSON.

A API aceita JPEG, PNG e WebP de até 10 MiB.

| Status | Situação |
| --- | --- |
| `200` | Leitura Gemini concluída |
| `400` | Arquivo ausente, formato inválido ou imagem maior que 10 MiB |
| `401` | Usuário não autenticado |
| `422` | Chave Gemini não configurada |
| `502` | Chave recusada, cota, solicitação inválida ou outra falha do Gemini |

No erro `502`, a rota tenta classificar a causa:

| Causa detectada | Mensagem retornada |
| --- | --- |
| HTTP `401`/`403` ou erro relacionado a API key/permissão | O Gemini recusou a chave |
| HTTP `429` ou erro relacionado a quota/rate limit | A cota do Gemini foi atingida |
| HTTP `400` ou erro relacionado a argumento, schema ou modelo | O Gemini recusou a solicitação |
| Demais falhas | O Gemini não respondeu e o OCR local foi usado como alternativa |

A resposta também contém `diagnostic` com o status numérico do erro, quando disponível. Independentemente da mensagem, o componente Web tenta o OCR local depois de receber a falha.

### Chamada ao Gemini

A implementação está em `src/lib/nutrition-label/gemini-extractor.ts` e utiliza o mesmo SDK e modelo da foto de refeição:

```text
SDK: @google/genai
Modelo: gemini-3.1-flash-lite
```

A imagem original é convertida para `Uint8Array`, codificada em Base64 e enviada como `inlineData`, preservando o MIME original.

O prompt atual orienta o modelo a:

- ler uma tabela nutricional brasileira;
- extrair somente proteína, carboidratos e gorduras totais;
- manter os três valores na mesma base impressa;
- priorizar a coluna de 100 g;
- nunca usar `%VD`;
- nunca confundir gorduras totais com gorduras saturadas ou trans;
- preservar valores decimais;
- retornar `null` quando um valor estiver ilegível, em vez de inventá-lo.

Não há system prompt separado. A instrução e a imagem são enviadas em uma mensagem com papel `user`.

Assim como na foto de refeição, não há configuração explícita de temperatura, `topP`, `topK`, limite de tokens, timeout, retry ou safety settings.

### Contrato da resposta Gemini

O Gemini deve retornar:

```json
{
  "basis": "per_100g",
  "servingGrams": null,
  "protein": 6.3,
  "carbs": 65,
  "fat": 23,
  "warnings": []
}
```

O JSON Schema exige todos os campos e rejeita propriedades adicionais.

| Campo | Regra atual |
| --- | --- |
| `basis` | `per_100g`, `per_serving` ou `unknown` |
| `servingGrams` | Número positivo ou `null` |
| `protein` | Número não negativo ou `null` |
| `carbs` | Número não negativo ou `null` |
| `fat` | Número não negativo ou `null` |
| `warnings` | Até 6 strings |

A resposta passa por `JSON.parse` e pelo schema Zod `responseSchema`. JSON malformado lança um erro de parsing. Uma estrutura incompatível gera `A IA retornou uma leitura inválida.`

Não há reparo do JSON, coerção de strings numéricas ou segunda tentativa automática no Gemini. A recuperação acontece pelo fallback OCR.

### Confiança atribuída ao Gemini

O Gemini não retorna níveis de confiança individuais. Depois da validação, a aplicação cria o objeto `confidence` internamente:

- macro presente: `0.9`;
- macro retornado como `null`: `0`;
- base `per_100g` ou `per_serving`: `0.9`;
- base `unknown`: `0`.

Essa confiança acompanha o contrato interno `NutritionLabelExtraction`, mas não é apresentada para revisão no formulário atual.

### Fallback com OCR local

O fallback usa Tesseract no próprio navegador ou WebView. Ele não envia a imagem ao servidor Macroflex nem ao Gemini.

Antes do OCR, `preprocessNutritionLabelImage()` em `src/lib/nutrition-label/preprocess-image.ts`:

1. rejeita imagens maiores que 10 MiB;
2. carrega a imagem respeitando a orientação EXIF aplicada pelo navegador;
3. redimensiona ou amplia a imagem, com lado máximo de 1.800 pixels e escala máxima de 2x;
4. converte os pixels para tons de cinza;
5. aumenta o contraste por um fator de 1,35;
6. preserva antialiasing para evitar apagar vírgulas decimais;
7. exporta a imagem processada em PNG.

O worker Tesseract é criado por `src/lib/nutrition-label/tesseract-extractor.ts` com idioma português e assets locais:

```text
/assets/ocr/worker.min.js
/assets/ocr/core/tesseract-core-lstm.wasm.js
/assets/ocr
```

O worker retorna linhas, palavras, níveis de confiança e coordenadas. Esses dados são convertidos para `OcrLine[]` e enviados a `parseNutritionLabel()`.

O usuário pode cancelar a análise local. O componente invalida a requisição corrente e encerra o worker Tesseract. Isso não implementa cancelamento da chamada remota ao Gemini que já estiver em andamento; apenas impede que um resultado antigo seja aplicado depois que a requisição foi invalidada.

### Parser do OCR

O parser está em `src/lib/nutrition-label/parse-label.ts`. Ele procura:

- cabeçalho da coluna `100 g`;
- coluna de `%VD`, para não utilizá-la como valor nutricional;
- peso da porção em gramas;
- linhas de proteína;
- linhas de carboidratos;
- linha de gorduras totais.

Ele ignora explicitamente linhas de gorduras saturadas e gorduras trans.

Quando encontra uma coluna de 100 g, escolhe o candidato numérico mais próximo da posição horizontal desse cabeçalho. Quando não encontra, usa a primeira coluna numérica viável depois do nome do macro, evitando a posição de `%VD`.

O parser também trata erros comuns do OCR:

- converte `O` em `0` dentro de valores;
- aceita ponto ou vírgula decimal;
- recompõe decimais tokenizados como `6`, `,`, `3`;
- entende valores como `<0,5` e gera alerta de valor aproximado;
- descarta valores cuja confiança calculada seja menor que `0.6`;
- adiciona alertas quando a base ou algum macro não pode ser identificado.

A confiança do OCR combina:

- confiança da palavra reconhecida;
- reconhecimento do nome do macro;
- proximidade da coluna de 100 g;
- identificação da base dos valores.

### Normalização para 100 g

Gemini e OCR retornam valores na base identificada na tabela. Antes de preencher o formulário, `normalizeNutritionLabel()` em `src/lib/nutrition-label/normalize-label.ts` converte valores por porção para 100 g quando `basis` é `per_serving` e `servingGrams` é válido:

```text
valor por 100 g = valor por porção * 100 / peso da porção em gramas
```

O resultado é arredondado para até três casas decimais.

Se a base já for `per_100g`, se for `unknown` ou se o peso da porção for inválido ou ausente, os valores são mantidos sem conversão.

### Aplicação ao formulário do alimento

Depois da normalização, somente os três macros são aplicados:

```ts
{
  protein: string,
  carbs: string,
  fat: string,
}
```

Um macro `null` vira uma string vazia para revisão manual. Os campos de nome, categoria e calorias não são extraídos da tabela.

O formulário exibe a mensagem **Valores importados por IA. Revise os campos antes de criar o alimento.** Mesmo quando o resultado efetivo veio do fallback Tesseract.

Após preencher os campos, o fluxo:

- marca que existem macros importados;
- rola a tela até os dados principais;
- permite ao usuário corrigir os valores;
- só persiste o alimento quando o formulário completo é enviado.

Os campos `basis`, `servingGrams`, `confidence` e `warnings` não são exibidos nem persistidos no cadastro do alimento.

### Privacidade da imagem do rótulo

A imagem não é persistida pelo Macroflex.

Na Web com Gemini, ela passa pelo servidor e é encaminhada ao Google. No Android com Gemini, vai diretamente do dispositivo ao Google. No fallback OCR, o processamento ocorre localmente.

A prévia usa uma object URL, revogada ao trocar a imagem, reiniciar o componente ou desmontá-lo. O blob pré-processado e o resultado do OCR permanecem apenas em memória durante a operação.

### Diagnósticos e logs

Falhas Gemini no Android são registradas por `captureDiagnostic()` com MIME e tamanho do arquivo. Falhas gerais da análise usam o mesmo mecanismo com o estágio `analyze`.

O componente também escreve mensagens de depuração no console com o prefixo `[nutrition-label-import]`. O extrator Tesseract registra progresso e, atualmente, o conteúdo das linhas e palavras reconhecidas com suas coordenadas.

A API Web registra falhas Gemini com o prefixo `[nutrition-label] Gemini extraction failed`, incluindo nome e mensagem da exceção, sem registrar explicitamente a chave.

### Limitações atuais da leitura de rótulos

- Extrai somente proteína, carboidratos e gorduras totais.
- Não extrai calorias, fibras, sódio, açúcares, nome ou tamanho da embalagem.
- Não há timeout ou retry para a chamada Gemini.
- Não há rate limiting ou quota interna por usuário.
- Não há persistência da resposta bruta, prompt, modelo, tokens, custo ou duração.
- O Android só aplica o limite de 10 MiB quando entra no fallback de pré-processamento; a chamada direta Gemini não faz essa validação antecipada.
- A interface não informa claramente se o preenchimento veio do Gemini ou do Tesseract.
- Confiança, base e alertas da extração são descartados antes da revisão.
- Se a base for `unknown`, os valores podem preencher o formulário sem normalização para 100 g.
- O cancelamento da interface não aborta um `fetch` ou uma chamada Gemini já iniciada.

### Testes do fluxo de rótulos

`src/lib/nutrition-label/parse-label.test.ts` cobre o parser OCR nos seguintes cenários:

- preferência pela coluna de 100 g e rejeição de `%VD`;
- identificação de valores por porção;
- rejeição de gorduras saturadas;
- suporte a valores aproximados e `O` no lugar de zero;
- reconstrução de vírgulas decimais separadas pelo OCR;
- aviso quando a base não é identificada.

Atualmente não há testes específicos para:

- `extractNutritionLabelWithGemini()`;
- schema ou prompt Gemini do rótulo;
- endpoint `/api/nutrition-label/extract`;
- fallback automático no componente;
- pré-processamento em canvas;
- normalização por porção em `normalizeNutritionLabel()`;
- integração do preenchimento com o formulário;
- worker Tesseract real no navegador ou Android.

### Arquivos principais da leitura de rótulos

| Responsabilidade | Arquivo |
| --- | --- |
| Interface, seleção Gemini/OCR e aplicação | `src/components/nutrition-label-import.tsx` |
| Ponto de entrada no formulário | `src/app/foods/new/food-creator.tsx` |
| API Web | `src/app/api/nutrition-label/extract/route.ts` |
| Prompt, schema e integração Gemini | `src/lib/nutrition-label/gemini-extractor.ts` |
| Contratos internos | `src/lib/nutrition-label/types.ts` |
| Pré-processamento da imagem | `src/lib/nutrition-label/preprocess-image.ts` |
| Worker Tesseract | `src/lib/nutrition-label/tesseract-extractor.ts` |
| Parser OCR | `src/lib/nutrition-label/parse-label.ts` |
| Normalização para 100 g | `src/lib/nutrition-label/normalize-label.ts` |
| Testes do parser | `src/lib/nutrition-label/parse-label.test.ts` |

Os textos da tela Conta como **OCR local ativo** dizem respeito a este fluxo de leitura de rótulos. Eles não representam um fallback para a estimativa nutricional de uma refeição fotografada.
