# Apresentacao do projeto Monify

Este material foi preparado a partir do codigo existente no repositorio. Ele pode ser usado como base para uma apresentacao de ate 15 minutos da disciplina de Programacao Web.

## 1. Visao geral do projeto

### Nome e proposta

O **Monify** e uma aplicacao web de organizacao financeira pessoal. O problema tratado e a dificuldade de acompanhar ganhos, gastos, contas futuras e saldo em diferentes contas ou cartoes.

O publico-alvo e qualquer pessoa que queira organizar as proprias financas de forma centralizada e visual. O objetivo principal e permitir que o usuario registre receitas e despesas, acompanhe movimentacoes realizadas e pendentes e consulte resumos e relatorios.

### Cenario de uso

Um usuario cria seu cadastro, entra no sistema e cadastra uma conta corrente ou poupanca. Depois registra o salario como receita mensal, o aluguel como despesa mensal e compras no cartao como despesas. O dashboard mostra saldos, faturas, contas pendentes, maiores gastos e limites. Quando uma conta pendente e paga ou recebida, o sistema atualiza a origem financeira e, se ela for recorrente, cria a proxima ocorrencia.

## 2. Requisitos e funcionalidades realmente implementadas

### Cadastro, login e perfil

- O cadastro recebe nome, e-mail e senha.
- O e-mail e unico e a senha precisa ter pelo menos seis caracteres no DTO.
- O login procura o usuario pelo e-mail e compara a senha recebida.
- O frontend guarda os dados basicos do usuario no `localStorage` para manter a sessao visual.
- O perfil permite editar nome e e-mail, impedindo que o novo e-mail ja pertenca a outro usuario.
- O menu de perfil oferece configuracoes e logout.

Limitacao importante: nao ha Spring Security, JWT, sessao no servidor nem BCrypt. A senha e armazenada e comparada em texto puro. Isso funciona como autenticacao academica basica, mas nao e adequado para producao real.

### Dashboard

O dashboard e montado no navegador a partir das APIs de transacoes, contas, cartoes e limites. Ele exibe:

- saudacao de acordo com o horario;
- receitas e despesas realizadas no mes atual;
- saldo geral;
- contas e respectivos saldos;
- total utilizado nos cartoes;
- contas a pagar e a receber;
- maiores gastos do mes por categoria em grafico de rosca;
- limites de gastos do mes atual;
- atalhos para cadastrar receita ou despesa.

Quando existem contas cadastradas, o saldo geral e a soma dos saldos das contas. Sem contas, o frontend usa receitas menos despesas realizadas no mes atual como alternativa.

### Receitas e despesas

Uma transacao armazena valor, tipo, descricao, data, categoria, usuario, periodicidade, situacao e origem financeira. Os tipos sao `INCOME` e `EXPENSE`.

As operacoes implementadas sao:

- cadastrar;
- listar por usuario;
- consultar por id;
- filtrar por tipo e categoria;
- pesquisar por texto no frontend;
- editar;
- excluir;
- consultar por intervalo de datas;
- efetivar uma transacao pendente.

O valor precisa ser positivo. Descricao, data, tipo e categoria sao obrigatorios. A origem tambem e validada pelo service.

### Situacao, contas a pagar e contas a receber

As situacoes sao `PENDING` e `COMPLETED`. Contas a pagar e contas a receber **nao sao tabelas separadas**:

- conta a pagar = transacao `EXPENSE` pendente;
- conta a receber = transacao `INCOME` pendente.

Ao clicar em **Pagar** ou **Receber**, o endpoint `PATCH /api/transactions/{id}/settle`:

1. confirma que o lancamento pertence ao usuario;
2. impede uma segunda efetivacao;
3. exige uma origem financeira valida;
4. altera o status para `COMPLETED`;
5. grava `settledAt`;
6. atualiza a conta ou o cartao;
7. cria a proxima ocorrencia quando houver recorrencia.

Por deixar de ser pendente, o registro desaparece automaticamente da secao de contas a pagar ou receber.

### Periodicidade

As periodicidades sao:

- `SINGLE`: ocorre uma vez;
- `MONTHLY`: repete no mes seguinte;
- `ANNUAL`: repete no ano seguinte.

Ao efetivar uma transacao mensal ou anual, o service cria uma nova transacao pendente com a proxima data. Uma `recurrenceKey` identifica a serie e o repository evita duplicar uma ocorrencia com a mesma chave e data.

### Origem e impacto financeiro

A origem pode ser:

- conta corrente;
- poupanca;
- cartao de credito.

A entidade `Account` tambem aceita dinheiro e investimento, mas o `TransactionService` permite como origem de lancamentos somente conta corrente e poupanca. Cartao de credito e permitido apenas para despesas.

Regras de impacto:

- receita realizada em conta: soma ao saldo;
- despesa realizada em conta: subtrai do saldo;
- despesa realizada no cartao: aumenta o valor utilizado;
- exclusao de transacao realizada: desfaz o impacto;
- edicao de transacao realizada: desfaz o valor antigo e aplica o novo;
- despesa no cartao acima do limite disponivel: rejeitada.

### Categorias

As categorias ficam na tabela `categories` e sao compartilhadas pelo sistema. Na inicializacao, o `DataInitializer` garante doze categorias padrao: Alimentacao, Transporte, Saude, Lazer, Salario, Educacao, Utilidades, Outros, Moradia, Compras, Investimentos e Presentes.

Categorias existentes nao sao apagadas. A API permite listar, consultar e criar categorias. A mesma resposta de `GET /api/categories` alimenta:

- formulario de lancamento;
- filtro de lancamentos;
- filtro e agrupamento dos relatorios;
- formulario e visualizacao de limites.

Assim, nao existe uma lista hardcoded separada para os relatorios.

### Contas

Cada conta possui nome, tipo, saldo inicial e usuario. Os tipos persistidos sao conta corrente, poupanca, dinheiro e investimento. O sistema permite cadastrar, listar e excluir contas.

O nome nao pode se repetir para o mesmo usuario. Uma conta vinculada a alguma transacao nao pode ser excluida. O saldo e ajustado pelas receitas e despesas realizadas.

### Cartoes

Cada cartao possui nome, limite, valor utilizado, quatro ultimos digitos, bandeira e usuario. O nome nao pode se repetir para o mesmo usuario.

O frontend apresenta uma mascara visual com `**** **** ****` e o final. O cartao nao pode ser excluido quando possui transacoes vinculadas. O service impede que uma nova despesa ultrapasse o limite.

Compras no cartao podem ser a vista ou parceladas. No parcelamento, o valor total e dividido em transacoes mensais ligadas por uma chave de grupo. Cada descricao recebe `Parcela N/Total`, a ultima parcela absorve eventuais centavos de arredondamento e a soma utilizada do cartao permanece exatamente igual ao valor total. Parcelamento exige despesa, cartao, pelo menos duas parcelas e periodicidade unica.

### Limites de gastos

Um limite armazena usuario, categoria, mes de referencia, valor total, valor utilizado e tipo. A chave unica combina usuario, categoria, mes e tipo.

O backend valida que:

- o valor limite seja maior que zero;
- o valor utilizado nao seja negativo;
- o valor utilizado nao ultrapasse o limite;
- a categoria exista na fonte oficial de categorias;
- nao haja duplicidade para a mesma categoria, mes e tipo.

O DTO calcula valor restante e percentual utilizado. O frontend exibe barra de progresso, alerta visual a partir de 75% e estado excedido em 100%.

Limitacao real: embora a entidade aceite limites de entrada e saida, a interface atual cadastra e consulta somente `EXPENSE`. O valor utilizado e informado manualmente; ele nao e recalculado automaticamente a partir das transacoes.

### Relatorios e graficos

Os relatorios sao calculados no JavaScript com as transacoes realizadas recebidas da API; nao existe entidade ou tabela `Report`.

Existem duas visoes principais:

- **Categorias:** totais de receitas e despesas, quantidade de lancamentos, distribuicao por categoria, graficos de rosca e evolucao do saldo.
- **Entradas x Saidas:** agrupamento diario, semanal, mensal ou acumulado, com grafico de barras, linha de saldo e tabela.

Os filtros usam tipo, categorias e origem da movimentacao. Somente categorias retornadas pela API oficial sao aceitas. Os graficos usam Chart.js e sao destruidos antes de uma nova renderizacao para evitar sobreposicao.

## 3. Arquitetura do sistema

O projeto e um monolito Spring Boot em camadas. O mesmo processo publica a API REST e os arquivos estaticos do frontend.

```text
Navegador
  |-- index.html + styles.css + app.js + Chart.js
  |          |
  |          | fetch JSON em /api/...
  v          v
Controllers REST
  |
  | DTOs validam entrada e definem resposta
  v
Services transacionais
  |-- regras de negocio
  |-- validacao de propriedade e duplicidade
  |-- calculos e impacto financeiro
  v
Repositories Spring Data JPA
  v
Hibernate / JDBC
  v
PostgreSQL em producao | H2 local e testes
```

### Papel das camadas

- **Controllers:** expõem endpoints HTTP, recebem JSON e parametros, chamam services e convertem excecoes em respostas HTTP.
- **Services:** concentram as regras de negocio e usam `@Transactional` para manter operacoes consistentes.
- **Repositories:** estendem `JpaRepository`, executam CRUD, consultas derivadas e consultas JPQL/nativas.
- **Entities:** representam as tabelas e relacionamentos JPA.
- **DTOs:** transportam dados sem expor todo o grafo das entidades e aplicam validacoes com Jakarta Validation.
- **Frontend:** controla navegacao, modais, estado visual, filtros, requisicoes REST e graficos.

## 4. Tecnologias utilizadas

| Tecnologia | Uso real no Monify |
|---|---|
| Java 17 | Linguagem do backend e versao configurada no Maven e Docker. |
| Spring Boot 3.2 | Inicializacao, injecao de dependencias, servidor web e configuracao da aplicacao. |
| Spring Web | Controllers REST e entrega dos arquivos estaticos. |
| Spring Data JPA | Repositories e acesso ao banco por entidades. |
| Hibernate | Implementacao ORM usada pelo JPA e criacao/atualizacao do esquema. |
| Jakarta Validation | Validacao dos DTOs com `@NotBlank`, `@Email`, `@Positive` e outras. |
| Lombok | Gera getters, setters, construtores e builders. |
| Maven | Dependencias, compilacao, testes e geracao do JAR. |
| PostgreSQL | Banco principal em producao, hospedado no Neon. |
| H2 | Banco em memoria para perfil local e testes; nao e o banco principal. |
| HTML5 | Estrutura de login, dashboard, paginas e modais. |
| CSS3 | Identidade visual, grid, responsividade, animacoes e componentes. |
| JavaScript | Estado do frontend, `fetch`, filtros, CRUD e renderizacao dinamica. |
| Chart.js | Graficos de rosca, barras e linhas. |
| Git/GitHub | Versionamento e repositorio remoto. |
| GitHub Actions | CI em push/PR para `main` ou `master`, usando JDK 17, package e testes. |
| Docker | Build em duas etapas e execucao do JAR em uma imagem Java 17. |
| Render | Web Service gratuito, Docker, health check e deploy automatico. |
| Neon | PostgreSQL online configurado por variaveis de ambiente. |

Observacao: apesar de Bootstrap aparecer na descricao original do projeto, o `index.html` atual nao carrega CSS ou JavaScript do Bootstrap. Os componentes visuais sao implementados em CSS proprio. Portanto, na apresentacao, nao se deve afirmar que Bootstrap esta ativo na versao atual.

## 5. Banco de dados e DER simplificado

O Hibernate usa `ddl-auto=update` em producao. As tabelas reais correspondem a seis entidades:

```text
USERS
  1 |-- N ACCOUNTS
  1 |-- N CREDIT_CARDS
  1 |-- N SPENDING_LIMITS
  1 |-- N TRANSACTIONS

CATEGORIES
  1 |-- N TRANSACTIONS

ACCOUNTS
  1 |-- N TRANSACTIONS (origem opcional)

CREDIT_CARDS
  1 |-- N TRANSACTIONS (origem opcional)
```

### Tabelas

- `users`: nome, e-mail unico, senha e datas de auditoria.
- `categories`: nome unico, icone e cor.
- `accounts`: nome, tipo, saldo, data de criacao e usuario; nome unico por usuario.
- `credit_cards`: nome, limite, valor usado, final, bandeira e usuario; nome unico por usuario.
- `transactions`: dados financeiros, tipo, data, status, periodicidade, recorrencia, liquidacao, usuario, categoria e origem.
- `spending_limits`: categoria, mes, valores, tipo e usuario; combinacao unica por usuario/categoria/mes/tipo.

Nao existem tabelas de contas a pagar, contas a receber ou relatorios. Essas visoes sao derivadas de `transactions`.

## 6. Fluxo completo dos dados

Exemplo: cadastrar e receber um salario mensal.

1. O usuario preenche receita, categoria Salario, periodicidade mensal, status pendente e conta corrente.
2. `app.js` envia JSON para `POST /api/transactions?userId=...`.
3. O controller valida o DTO e chama `TransactionService.createTransaction`.
4. O service busca usuario, categoria e conta, valida a origem e salva a entidade pelo repository.
5. Como esta pendente, o saldo ainda nao muda.
6. O frontend busca novamente `GET /api/transactions/user/{userId}` e mostra o item em Contas a receber.
7. Ao clicar em Receber, o frontend chama o endpoint de liquidacao.
8. O service marca como realizada, credita a conta e cria a proxima parcela mensal pendente.
9. O frontend recarrega transacoes, contas e dashboard.
10. Relatorios e graficos filtram as transacoes realizadas e recalculam os totais no navegador.

## 7. Dificuldades evidenciadas e solucoes adotadas

Estas dificuldades sao sustentadas pelo codigo atual e pelo historico recente de commits, sem atribuir causas pessoais aos integrantes.

- **Lombok e compilacao:** builders/getters dependem do annotation processor. O `pom.xml` fixa Lombok 1.18.30 e configura o `maven-compiler-plugin` para Java 17.
- **Integracao frontend/backend:** os contratos foram centralizados em DTOs e em uma base de API relativa a `window.location.origin`, permitindo local e deploy no mesmo frontend.
- **Consistencia de saldo:** o service aplica e desfaz impacto financeiro em criacao, edicao, exclusao e liquidacao de transacoes realizadas.
- **Pendencias:** contas a pagar/receber foram modeladas pelo status da propria transacao, evitando tabelas e fluxos paralelos.
- **Recorrencia:** uma chave de recorrencia e a verificacao por data evitam duplicar a proxima ocorrencia.
- **Categorias divergentes:** formularios, filtros, limites e relatorios passaram a consumir a mesma API de categorias.
- **Datas no navegador:** `parseLocalDate` interpreta `yyyy-MM-dd` no fuso local para evitar deslocamento de dia por UTC.
- **Graficos:** instancias Chart.js anteriores sao destruidas antes da nova renderizacao.
- **Banco online:** credenciais sairam do codigo e passaram para variaveis de ambiente; PostgreSQL fica no Neon.
- **Deploy:** Docker multi-stage gera o JAR e o Render publica automaticamente a branch conectada.
- **Compatibilidade de dados antigos:** o `DataInitializer` preenche status e periodicidade ausentes em transacoes legadas.
- **Serializacao circular:** `CategoryDTO` impede que a API de categorias serialize a lista de transacoes e todo o grafo JPA.

## 8. Melhorias futuras baseadas na arquitetura atual

1. Adicionar Spring Security, BCrypt e JWT ou sessao segura.
2. Remover o endpoint publico de listagem completa de usuarios ou protege-lo por papel.
3. Restringir CORS em vez de usar origem `*`.
4. Calcular automaticamente o consumo dos limites a partir das transacoes do mes e categoria.
5. Expor relatorios agregados no backend para reduzir processamento e trafego no navegador.
6. Adicionar paginacao na listagem de transacoes.
7. Criar migracoes versionadas com Flyway ou Liquibase em vez de depender apenas de `ddl-auto=update`.
8. Adicionar testes de controllers, contas, cartoes, autorizacao por usuario e frontend.
9. Tratar erros com `@ControllerAdvice` e respostas padronizadas.
10. Implementar alteracao segura de senha e recuperacao de acesso.
11. Adicionar exportacao de relatorios em CSV ou PDF.
12. Criar notificacoes de vencimento e metas financeiras.
13. Separar o JavaScript unico em modulos por dominio.
14. Remover funcoes frontend antigas/duplicadas de contas pendentes.
15. Corrigir textos com problemas de codificacao e substituir os emojis remanescentes por uma biblioteca consistente de icones.
16. Decidir entre incluir Bootstrap de fato ou retirar sua mencao da documentacao tecnica.
17. Desabilitar explicitamente `spring.jpa.open-in-view` e manter consultas dentro da camada de servico.
18. Remover a configuracao explicita do dialeto H2, que o Hibernate ja detecta automaticamente.

## 9. Roteiro de apresentacao para 15 minutos

### 0:00 a 1:15 - Introducao e problema

> O Monify e uma aplicacao web para organizacao financeira pessoal. Ele centraliza receitas, despesas, saldos, contas futuras, cartoes, limites e relatorios. O publico e formado por pessoas que precisam entender para onde o dinheiro vai e quais compromissos ainda estao pendentes.

### 1:15 a 2:15 - Escopo implementado

Apresente cadastro/login, CRUD de lancamentos, categorias, contas, cartoes, pendencias, recorrencia, limites, dashboard e relatorios. Explique que contas a pagar/receber sao transacoes pendentes.

### 2:15 a 3:30 - Tecnologias

Mostre Java 17, Spring Boot, JPA/Hibernate, PostgreSQL, HTML, CSS, JavaScript, Chart.js, Maven, JUnit, Docker, GitHub Actions, Neon e Render. Seja transparente: o visual atual usa CSS proprio, nao Bootstrap carregado.

### 3:30 a 5:00 - Arquitetura

Mostre o fluxo navegador -> controller -> DTO -> service -> repository -> banco. Destaque que services concentram regras e transacoes de banco.

### 5:00 a 10:00 - Demonstracao

1. Entrar ou criar usuario.
2. Cadastrar conta corrente.
3. Cadastrar receita mensal pendente com origem na conta.
4. Mostrar a receita em Contas a receber.
5. Clicar em Receber e mostrar saldo atualizado e proxima ocorrencia.
6. Cadastrar despesa no cartao e mostrar fatura utilizada.
7. Abrir Lancamentos, pesquisar, filtrar e editar.
8. Abrir Relatorios e alternar categorias/evolucao/entradas x saidas.
9. Criar um limite e mostrar a barra no dashboard.

### 10:00 a 11:30 - Banco e regras

Mostre as seis entidades e os relacionamentos. Explique status, origem, impacto no saldo e recorrencia. Reforce que relatorios nao sao armazenados: sao calculados com transacoes realizadas.

### 11:30 a 13:00 - Testes, deploy e dificuldades

Informe que existem 22 testes automatizados passando em quatro classes. Mostre CI no GitHub Actions, Docker, PostgreSQL Neon e deploy Render. Cite consistencia de saldo, recorrencia, categorias e configuracao por ambiente como desafios resolvidos.

### 13:00 a 14:00 - Limitacoes e evolucao

Assuma tecnicamente as limitacoes: senha sem hash, ausencia de Spring Security, consumo manual dos limites e JavaScript concentrado. Apresente as melhorias planejadas.

### 14:00 a 15:00 - Conclusao e perguntas

> O projeto atende o nucleo do controle financeiro pessoal e demonstra uma aplicacao web completa, com frontend integrado a API REST, persistencia relacional, regras de negocio, testes, CI e deploy. O principal proximo passo e fortalecer seguranca e automatizar ainda mais os indicadores.

## 10. Perguntas que o professor pode fazer

1. **Por que Spring Boot?** Porque simplifica configuracao, injecao de dependencias, API REST e execucao em servidor embutido.
2. **Por que PostgreSQL?** Porque os dados financeiros sao estruturados e relacionais, exigindo consistencia, restricoes e consultas confiaveis.
3. **Qual e a funcao do controller?** Receber HTTP, validar entrada, chamar o service e devolver status e JSON.
4. **Qual e a funcao do service?** Centralizar regras de negocio e coordenar operacoes transacionais entre repositories.
5. **Qual e a funcao do repository?** Abstrair o acesso ao banco por `JpaRepository` e consultas derivadas, JPQL ou SQL.
6. **Qual a diferenca entre Entity e DTO?** Entity representa uma tabela JPA; DTO define os dados do contrato da API e evita expor o grafo persistente.
7. **Como o JPA funciona aqui?** O Hibernate mapeia objetos Java para tabelas e executa SQL por meio dos repositories.
8. **Como o saldo e calculado?** No backend, o resumo subtrai despesas realizadas das receitas realizadas; no dashboard, contas cadastradas fazem o saldo ser a soma de seus saldos.
9. **Por que somente transacoes realizadas entram nos relatorios?** Porque pendencias sao previsoes e ainda nao causaram impacto financeiro.
10. **Como funciona uma conta a receber?** E uma transacao de receita com status pendente; ao receber, vira realizada e credita a conta.
11. **Como funciona a recorrencia?** A liquidacao cria a proxima ocorrencia mensal ou anual como pendente e usa uma chave para evitar duplicidade.
12. **Como uma despesa de cartao altera o sistema?** Ela aumenta `usedAmount`; o service bloqueia o valor que ultrapassaria o limite.
13. **Por que `@Transactional` nos services?** Para que alteracao da transacao e impacto em conta/cartao sejam confirmados ou revertidos como uma unidade.
14. **Como categorias ficam consistentes?** Lancamentos, filtros, relatorios e limites usam a mesma API `/api/categories`.
15. **Os relatorios ficam salvos no banco?** Nao. O frontend agrupa e calcula transacoes realizadas recebidas da API.
16. **Como o frontend conversa com o backend?** Com `fetch`, JSON e endpoints relativos a `/api` no mesmo dominio.
17. **Como o sistema evita excluir conta usada?** O service consulta se existe transacao com aquele `accountId` e rejeita a exclusao.
18. **Como os dados sao validados?** DTOs usam Jakarta Validation e services aplicam validacoes de negocio adicionais.
19. **Como o sistema e testado?** Com Spring Boot Test, JUnit 5, MockMvc e H2 em memoria; ha 22 testes em quatro classes.
20. **Como e feito o deploy?** O GitHub aciona CI; o Render constroi o Dockerfile, executa o JAR e acessa PostgreSQL Neon por variaveis de ambiente.
21. **Por que usar Docker multi-stage?** Para compilar com Maven numa imagem e executar somente com o JRE numa imagem final menor.
22. **As senhas estao seguras?** Ainda nao; hoje estao em texto puro. A melhoria correta e Spring Security com BCrypt e autenticacao segura.
23. **Bootstrap esta sendo usado?** Nao na versao atual: nao ha import do Bootstrap; o layout e implementado em CSS proprio.
24. **Qual a diferenca entre PostgreSQL e H2 no projeto?** PostgreSQL e o banco de producao; H2 e usado localmente e nos testes para execucao rapida e isolada.
25. **O consumo do limite e automatico?** Nao. O valor utilizado e persistido e informado manualmente; automatiza-lo por transacoes e uma melhoria futura.

## 11. Resumo curto para consulta durante a apresentacao

- **Problema:** dificuldade de acompanhar receitas, despesas, pendencias e saldo.
- **Solucao:** Monify, aplicacao web de controle financeiro pessoal.
- **Nucleo:** usuarios, categorias, transacoes, contas, cartoes e limites.
- **Arquitetura:** frontend estatico -> API REST -> services -> JPA -> PostgreSQL.
- **Regra principal:** somente transacoes realizadas afetam saldo e relatorios.
- **Pendencias:** despesas pendentes sao contas a pagar; receitas pendentes sao contas a receber.
- **Origem:** conta corrente/poupanca para receitas e despesas; cartao somente para despesas.
- **Recorrencia:** mensal/anual cria automaticamente a proxima ocorrencia pendente.
- **Relatorios:** calculados no JavaScript e exibidos com Chart.js.
- **Qualidade:** 22 testes, GitHub Actions, Docker e configuracao por ambiente.
- **Deploy:** Render + PostgreSQL Neon.
- **Limitacoes:** autenticacao basica, limite utilizado manual e frontend monolitico.
- **Proximo passo:** Spring Security/BCrypt e calculo automatico dos limites.

## Referencias principais no codigo

- `src/main/java/com/monify/entity`: modelo persistente.
- `src/main/java/com/monify/service/TransactionService.java`: regras financeiras, liquidacao e recorrencia.
- `src/main/java/com/monify/service/SpendingLimitService.java`: regras dos limites.
- `src/main/java/com/monify/controller`: contratos REST.
- `src/main/resources/static/index.html`: telas e formularios.
- `src/main/resources/static/app.js`: integracao, filtros, dashboard e relatorios.
- `src/main/resources/static/styles.css`: identidade visual e responsividade.
- `src/test/java/com/monify`: testes automatizados.
- `pom.xml`, `Dockerfile`, `render.yaml` e `.github/workflows/ci-cd.yml`: build, CI e deploy.
