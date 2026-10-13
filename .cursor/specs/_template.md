# Spec: <nome da funcionalidade>

- **Modo:** MANUAL | ASSISTIDO | DELEGADO
- **Módulo:** catalog | customers | orders | payments | delivery | analytics | admin | shared
- **Status:** RASCUNHO | APROVADA | IMPLEMENTADA
- **Passo no PROJECT.md:** <ex.: 2. Módulo catalog>

## 1. Contexto e objetivo

Por que essa funcionalidade existe? Que problema resolve e para quem (cliente, admin)?

## 2. Fora do escopo

O que **não** será feito nesta spec (evita que a implementação cresça).

## 3. Regras de negócio

Liste as regras de forma numerada e verificável.

1. ...
2. ...

## 4. Modelo de dados

Tabelas/colunas novas ou alteradas, constraints e índices. Mencione o changeset Liquibase esperado.

## 5. Contrato da API

Para cada endpoint:

### `METHOD /api/v1/...`

- **Autenticação:** pública | cliente | ADMIN
- **Request:** campos, tipos, validações
- **Response de sucesso:** status e corpo
- **Erros:** status, quando ocorre, formato
- **Se for listagem (obrigatoriamente paginada):** tamanho padrão e máximo de página, campos permitidos para ordenação e ordenação padrão, filtros aceitos e formato da resposta paginada

## 6. Casos de borda e falhas

O que acontece quando...

- o dado já existe / não existe?
- a requisição chega duplicada?
- o serviço externo falha?
- dois usuários agem ao mesmo tempo?

## 7. Segurança e privacidade

Quem pode acessar? Que dado sensível é manipulado? Existe risco de abuso?

## 8. Critérios de aceite

Cada item deve virar ao menos um teste.

- [ ] Dado ... quando ... então ...
- [ ] ...

## 9. Decisões e dúvidas em aberto

| Decisão / dúvida | Opções | Escolha | Motivo |
|---|---|---|---|
| | | | |

## 10. Registro de implementação

- Quem implementou: eu | Cursor | juntos
- Divergências em relação à spec (e se a spec foi atualizada):
- O que aprendi / o que preciso estudar:
